package com.example.inventory.evaluation;

import com.example.inventory.product.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/** Local opt-in experiment: an immutable token projection, separate from normal product search. */
@Service
@ConditionalOnProperty(prefix = "app.evaluation", name = "enabled", havingValue = "true")
public class AutocompleteExperimentService {
    static final String COLLECTION = "evaluation_autocomplete_products";
    private static final TypeReference<List<Suggestion>> LIST = new TypeReference<>() {};
    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String namespace;
    private volatile String revision = UUID.randomUUID().toString();
    private final LongAdder requests = new LongAdder(), databaseReads = new LongAdder(), hits = new LongAdder();
    private final LongAdder misses = new LongAdder(), failures = new LongAdder(), lookupNs = new LongAdder();

    public AutocompleteExperimentService(MongoTemplate mongo, StringRedisTemplate redis, ObjectMapper json,
                                        @Value("${app.cache.namespace:cache}") String namespace) {
        if (!namespace.matches("[a-zA-Z0-9:_-]+")) throw new IllegalArgumentException("Invalid experiment namespace");
        this.mongo = mongo; this.redis = redis; this.json = json; this.namespace = namespace;
    }

    public record Suggestion(String productId, String sku, String name, String category) {}
    public record SearchResult(List<Suggestion> results, String source) {}
    public record Metrics(long requests, long databaseReads, long redisHits, long redisMisses,
                          long redisFailures, long totalRedisLookupNs) {}

    static Set<String> tokens(Suggestion value) {
        Set<String> result = new LinkedHashSet<>();
        for (String text : Arrays.asList(value.sku(), value.name(), value.category())) {
            if (text == null) continue;
            for (String word : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]", " ").split("[\\s-]+")) {
                if (!word.isEmpty()) result.add(word);
            }
        }
        if (value.sku() != null) {
            String raw = value.sku().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!raw.isEmpty()) result.add(raw);
        }
        return result;
    }

    public synchronized Map<String, Object> rebuild() {
        List<Document> documents = mongo.findAll(Product.class).stream().map(p -> {
            Suggestion item = new Suggestion(p.getProductId(), p.getSku(), p.getName(), p.getCategory());
            return new Document("_id", item.productId()).append("productId", item.productId())
                    .append("sku", item.sku()).append("name", item.name()).append("category", item.category())
                    .append("tokens", new ArrayList<>(tokens(item)));
        }).toList();
        mongo.remove(new Query(), COLLECTION);
        if (!documents.isEmpty()) mongo.insert(documents, COLLECTION);
        mongo.indexOps(COLLECTION).ensureIndex(new Index().on("tokens", Sort.Direction.ASC));
        mongo.indexOps(COLLECTION).ensureIndex(new Index().on("productId", Sort.Direction.ASC).unique());
        // A new projection never trusts cached entries from an earlier catalogue.
        revision = UUID.randomUUID().toString();
        return Map.of("products", documents.size(), "revision", revision);
    }

    private Suggestion suggestion(Document value) {
        return new Suggestion(value.getString("productId"), value.getString("sku"), value.getString("name"), value.getString("category"));
    }

    public List<Suggestion> catalogue(int limit) {
        if (limit < 1 || limit > 10000) throw new IllegalArgumentException("Invalid catalogue limit");
        return mongo.find(new Query().with(Sort.by("productId")).limit(limit), Document.class, COLLECTION)
                .stream().map(this::suggestion).toList();
    }

    public SearchResult search(String prefix, int limit, boolean cache, String run) {
        if (prefix == null || prefix.length() > 80 || limit < 1 || limit > 8 || !run.matches("[a-zA-Z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid autocomplete experiment query");
        }
        requests.increment();
        String clean = prefix.trim().toLowerCase(Locale.ROOT);
        String key = namespace + ":autocomplete:" + revision + ":" + run + ":" + limit + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(clean.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean lookupHealthy = true;
        if (cache) {
            long started = System.nanoTime();
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    List<Suggestion> found = json.readValue(cached, LIST);
                    hits.increment();
                    return new SearchResult(found, "redis");
                }
            } catch (Exception error) { failures.increment(); lookupHealthy = false; }
            finally { lookupNs.add(System.nanoTime() - started); }
            misses.increment();
        }
        databaseReads.increment();
        List<Suggestion> found = clean.isEmpty() ? List.of() : mongo.find(
                new Query(Criteria.where("tokens").regex("^" + Pattern.quote(clean)))
                        .with(Sort.by("productId")).limit(limit), Document.class, COLLECTION)
                .stream().map(this::suggestion).toList();
        if (cache && lookupHealthy) {
            try { redis.opsForValue().set(key, json.writeValueAsString(found), Duration.ofMinutes(5)); }
            catch (Exception error) { failures.increment(); }
        }
        return new SearchResult(found, "mongo");
    }

    public Metrics metrics() {
        return new Metrics(requests.sum(), databaseReads.sum(), hits.sum(), misses.sum(), failures.sum(), lookupNs.sum());
    }
}
