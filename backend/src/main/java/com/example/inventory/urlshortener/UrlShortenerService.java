package com.example.inventory.urlshortener;

import com.example.inventory.common.NotFoundException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * URL Shortener service — system-design component #6.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>If the same {@code longUrl} has already been shortened, return the
 *       existing record (idempotent for the same URL).</li>
 *   <li>Generate a random 6-character Base62 code using {@link SecureRandom}.</li>
 *   <li>Retry up to {@value #MAX_RETRIES} times on the (rare) collision detected
 *       by the unique MongoDB index; each retry increases the code length by 1
 *       character to reduce collision probability.</li>
 *   <li>Persist the record and return the full short URL.</li>
 * </ol>
 *
 * <h3>Redirect latency measurement</h3>
 * The controller records {@code System.nanoTime()} before calling
 * {@link #resolve(String)} and attaches the elapsed time as an
 * {@code X-Redirect-Latency-Ms} response header so evaluations can measure
 * lookup performance without a separate benchmarking tool.
 *
 * <h3>Collision probability</h3>
 * With a 6-char Base62 alphabet ({@code 62^6 ≈ 56 billion} codes) collisions
 * are negligible for typical project datasets. The retry loop with increasing
 * code length provides a hard safety net.
 */
@Service
public class UrlShortenerService {

    /** Base62 alphabet used to encode short codes. */
    private static final String ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int DEFAULT_CODE_LENGTH = 6;
    private static final int MAX_RETRIES         = 5;
    private static final int DEFAULT_TTL_DAYS    = 90;
    private static final int MAX_TTL_DAYS        = 365;

    private final ShortUrlRepository repository;
    private final MongoTemplate      mongoTemplate;
    private final SecureRandom       random = new SecureRandom();

    // ── Evaluation counters ────────────────────────────────────────────────────
    private final LongAdder totalShortenRequests  = new LongAdder();
    private final LongAdder totalRedirectRequests = new LongAdder();
    private final LongAdder collisions            = new LongAdder();

    public UrlShortenerService(ShortUrlRepository repository, MongoTemplate mongoTemplate) {
        this.repository    = repository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Shortens {@code longUrl}, returning an existing record if the same URL
     * was shortened before (idempotent).
     *
     * @param longUrl the URL to shorten (must be http/https)
     * @param ttlDays optional expiry in days; defaults to {@value #DEFAULT_TTL_DAYS}
     * @return persisted {@link ShortUrl} entity
     */
    public ShortUrl shorten(String longUrl, Integer ttlDays) {
        totalShortenRequests.increment();

        // Idempotency: return existing short URL for the same long URL
        var existing = repository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return existing.get();
        }

        int effectiveTtl = (ttlDays != null && ttlDays > 0)
            ? Math.min(ttlDays, MAX_TTL_DAYS)
            : DEFAULT_TTL_DAYS;

        int codeLength = DEFAULT_CODE_LENGTH;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = generateCode(codeLength);
            if (!repository.existsByCode(code)) {
                ShortUrl entity = new ShortUrl(code, longUrl, effectiveTtl);
                return repository.save(entity);
            }
            // Collision: increment counter and try a longer code next time
            collisions.increment();
            codeLength++;
        }
        throw new IllegalStateException(
            "Unable to generate a unique short code after " + MAX_RETRIES + " attempts. " +
            "This is extremely unlikely; please retry.");
    }

    /**
     * Resolves a short code to its target URL, atomically incrementing the
     * click counter in MongoDB.
     *
     * @param code the Base62 short code
     * @return the long URL to redirect to
     * @throws NotFoundException when the code does not exist
     */
    public String resolve(String code) {
        totalRedirectRequests.increment();

        Query  query  = Query.query(Criteria.where("code").is(code));
        Update update = new Update().inc("clickCount", 1);

        ShortUrl updated = mongoTemplate.findAndModify(
            query, update,
            FindAndModifyOptions.options().returnNew(true),
            ShortUrl.class
        );

        if (updated == null) {
            throw new NotFoundException("Short URL not found for code: " + code);
        }
        return updated.getLongUrl();
    }

    /**
     * Returns the full {@link ShortUrl} record for a code without incrementing
     * the click counter (used by the info endpoint).
     */
    public ShortUrl getInfo(String code) {
        return repository.findByCode(code)
            .orElseThrow(() -> new NotFoundException("Short URL not found for code: " + code));
    }

    // ── Evaluation metrics ─────────────────────────────────────────────────────

    public record Metrics(long shortenRequests, long redirectRequests, long codeCollisions) {}

    public Metrics getMetrics() {
        return new Metrics(
            totalShortenRequests.sum(),
            totalRedirectRequests.sum(),
            collisions.sum()
        );
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
