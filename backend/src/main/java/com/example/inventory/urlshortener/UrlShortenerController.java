package com.example.inventory.urlshortener;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST controller for the URL Shortener system-design component (Component #6).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/shorten}           — create a short URL</li>
 *   <li>{@code GET  /r/{code}}              — redirect to the long URL (HTTP 302),
 *       emits {@code X-Redirect-Latency-Ms} header for evaluation</li>
 *   <li>{@code GET  /api/shorten/{code}/info} — inspect a short URL without redirect</li>
 *   <li>{@code GET  /api/shorten/metrics}   — component evaluation metrics</li>
 * </ul>
 *
 * <p>The redirect path {@code /r/{code}} is deliberately outside {@code /api/}
 * so the Rate Limiter interceptor does not throttle redirects (they are not
 * inventory-API calls).  Add {@code /r/**} to the interceptor's
 * {@code addPathPatterns} if throttling redirects is desired.
 */
@RestController
public class UrlShortenerController {

    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    // ── POST /api/shorten ──────────────────────────────────────────────────────

    /**
     * Shortens a long URL.  Returns an existing record if the same URL was
     * already shortened (idempotent).
     *
     * <p>Request body:
     * <pre>{@code { "longUrl": "https://...", "ttlDays": 30 }}</pre>
     *
     * <p>Response (201 Created):
     * <pre>{@code
     * {
     *   "code":       "aB3dEf",
     *   "shortUrl":   "http://localhost:8080/r/aB3dEf",
     *   "longUrl":    "https://...",
     *   "clickCount": 0,
     *   "createdAt":  "2026-10-24T...",
     *   "expiresAt":  "2027-01-22T..."
     * }
     * }</pre>
     */
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {

        ShortUrl entity    = service.shorten(request.getLongUrl(), request.getTtlDays());
        String   baseUrl   = resolveBaseUrl(httpRequest);
        ShortenResponse body = ShortenResponse.from(entity, baseUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ── GET /r/{code} — redirect ───────────────────────────────────────────────

    /**
     * Resolves a short code and redirects to the original URL.
     *
     * <p>The {@code X-Redirect-Latency-Ms} response header carries the time
     * (in milliseconds) taken to look up the code in MongoDB, enabling
     * component-specific latency evaluation as required by Deliverable 4.
     */
    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        long start = System.nanoTime();

        String longUrl = service.resolve(code);

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(longUrl))
            .header("X-Redirect-Latency-Ms", String.valueOf(latencyMs))
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .build();
    }

    // ── GET /api/shorten/{code}/info ──────────────────────────────────────────

    /**
     * Returns metadata for a short code without performing a redirect or
     * incrementing the click counter.
     */
    @GetMapping("/api/shorten/{code}/info")
    public ResponseEntity<ShortenResponse> info(
            @PathVariable String code,
            HttpServletRequest httpRequest) {

        ShortUrl entity  = service.getInfo(code);
        String   baseUrl = resolveBaseUrl(httpRequest);
        return ResponseEntity.ok(ShortenResponse.from(entity, baseUrl));
    }

    // ── GET /api/shorten/metrics ──────────────────────────────────────────────

    /**
     * Returns evaluation metrics for the URL shortener component:
     * total shorten requests, total redirects, and code collision count.
     *
     * <p>These values directly address the component-specific metric
     * "redirection latency and ID generation performance" required by
     * Deliverable 4 of the project brief.
     */
    @GetMapping("/api/shorten/metrics")
    public ResponseEntity<UrlShortenerService.Metrics> metrics() {
        return ResponseEntity.ok(service.getMetrics());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host   = request.getServerName();
        int    port   = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                           || ("https".equals(scheme) && port == 443);
        return defaultPort
            ? scheme + "://" + host
            : scheme + "://" + host + ":" + port;
    }
}
