package com.example.inventory.urlshortener;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistent short-URL record stored in the {@code short_urls} MongoDB collection.
 *
 * <p>Each document maps a 6-character Base62 code to a long URL. The
 * {@code createdAt} field is indexed with a TTL so entries automatically
 * expire after {@code ttlDays} days (default 90).  A separate unique index
 * on {@code code} guarantees O(1) redirect lookup.
 *
 * <p>Member 6 — URL Shortener system-design component.
 */
@Document(collection = "short_urls")
public class ShortUrl {

    @Id
    private String id;

    /** 6-character Base62 alias, unique across the collection. */
    @Indexed(unique = true)
    private String code;

    /** The original long URL the code resolves to. */
    private String longUrl;

    /** Cumulative redirect (click) count. Incremented atomically on each redirect. */
    private long clickCount = 0;

    /** UTC instant the record was created. */
    @Indexed(expireAfter = "0s")   // TTL is controlled by expiresAt, not this field
    private Instant createdAt;

    /**
     * TTL expiry instant. Spring Data MongoDB uses this field's value to remove
     * the document automatically.  Set to {@code createdAt + 90 days} by default.
     */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    public ShortUrl() {
    }

    public ShortUrl(String code, String longUrl, int ttlDays) {
        this.code      = code;
        this.longUrl   = longUrl;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds((long) ttlDays * 24 * 60 * 60);
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId()               { return id; }
    public void   setId(String id)      { this.id = id; }

    public String getCode()             { return code; }
    public void   setCode(String code)  { this.code = code; }

    public String getLongUrl()              { return longUrl; }
    public void   setLongUrl(String url)    { this.longUrl = url; }

    public long   getClickCount()              { return clickCount; }
    public void   setClickCount(long n)        { this.clickCount = n; }

    public Instant getCreatedAt()              { return createdAt; }
    public void    setCreatedAt(Instant t)     { this.createdAt = t; }

    public Instant getExpiresAt()              { return expiresAt; }
    public void    setExpiresAt(Instant t)     { this.expiresAt = t; }
}
