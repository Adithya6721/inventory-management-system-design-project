package com.example.inventory.urlshortener;

import java.time.Instant;

/**
 * Response body returned by {@code POST /api/shorten} and
 * {@code GET /api/shorten/{code}/info}.
 */
public class ShortenResponse {

    private String code;
    private String shortUrl;
    private String longUrl;
    private long   clickCount;
    private Instant createdAt;
    private Instant expiresAt;

    public ShortenResponse() {
    }

    public ShortenResponse(String code, String shortUrl, String longUrl,
                           long clickCount, Instant createdAt, Instant expiresAt) {
        this.code       = code;
        this.shortUrl   = shortUrl;
        this.longUrl    = longUrl;
        this.clickCount = clickCount;
        this.createdAt  = createdAt;
        this.expiresAt  = expiresAt;
    }

    public static ShortenResponse from(ShortUrl entity, String baseUrl) {
        return new ShortenResponse(
            entity.getCode(),
            baseUrl + "/r/" + entity.getCode(),
            entity.getLongUrl(),
            entity.getClickCount(),
            entity.getCreatedAt(),
            entity.getExpiresAt()
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getCode()        { return code; }
    public String  getShortUrl()    { return shortUrl; }
    public String  getLongUrl()     { return longUrl; }
    public long    getClickCount()  { return clickCount; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getExpiresAt()   { return expiresAt; }
}
