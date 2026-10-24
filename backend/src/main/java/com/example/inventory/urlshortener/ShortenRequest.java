package com.example.inventory.urlshortener;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/shorten}.
 */
public class ShortenRequest {

    @NotBlank(message = "longUrl must not be blank")
    @Pattern(
        regexp = "^https?://.*",
        message = "longUrl must start with http:// or https://"
    )
    private String longUrl;

    /**
     * Optional TTL in days (default 90, max 365).
     * Callers may omit this field; the service enforces the maximum.
     */
    private Integer ttlDays;

    public String getLongUrl()              { return longUrl; }
    public void   setLongUrl(String url)    { this.longUrl = url; }

    public Integer getTtlDays()             { return ttlDays; }
    public void    setTtlDays(Integer ttl)  { this.ttlDays = ttl; }
}
