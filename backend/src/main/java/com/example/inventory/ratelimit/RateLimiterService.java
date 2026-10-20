package com.example.inventory.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimiterProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> clientBuckets = new ConcurrentHashMap<>();

    // Telemetry & metrics for Deliverable 4
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong throttledRequests = new AtomicLong();

    public RateLimiterService(RateLimiterProperties properties) {
        this.properties = properties;
        log.info("Initialized RateLimiterService (enabled={}, capacity={}, refill={} tokens/{}s)",
                properties.isEnabled(), properties.getCapacity(), properties.getRefillTokens(),
                properties.getRefillPeriodSeconds());
    }

    public TokenBucket.ConsumptionResult consume(String clientKey) {
        if (!properties.isEnabled()) {
            return new TokenBucket.ConsumptionResult(true, properties.getCapacity(), 0L);
        }

        totalRequests.incrementAndGet();

        TokenBucket bucket = clientBuckets.computeIfAbsent(
                clientKey,
                k -> new TokenBucket(properties.getCapacity(), properties.getRefillTokens(), properties.getRefillPeriodSeconds())
        );

        TokenBucket.ConsumptionResult result = bucket.tryConsume(1);
        if (result.allowed()) {
            allowedRequests.incrementAndGet();
            log.debug("[RateLimiter PASS] Client '{}' allowed. Tokens remaining: {}", clientKey, result.remainingTokens());
        } else {
            throttledRequests.incrementAndGet();
            log.warn("[RateLimiter THROTTLE 429] Client '{}' exceeded limit! Retry after: {}s",
                    clientKey, result.retryAfterSeconds());
        }

        return result;
    }

    /**
     * Periodic cleanup of idle client buckets (older than 10 minutes) to prevent memory leak.
     */
    public void cleanupIdleBuckets() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        clientBuckets.entrySet().removeIf(entry -> entry.getValue().getLastAccessTimeMillis() < cutoff);
    }

    public int getActiveClientCount() {
        return clientBuckets.size();
    }

    public RateLimiterMetrics getMetrics() {
        long total = totalRequests.get();
        long allowed = allowedRequests.get();
        long throttled = throttledRequests.get();
        double rejectionRate = total == 0 ? 0.0 : ((double) throttled / total) * 100.0;
        return new RateLimiterMetrics(total, allowed, throttled, rejectionRate, clientBuckets.size());
    }

    public void resetMetrics() {
        totalRequests.set(0);
        allowedRequests.set(0);
        throttledRequests.set(0);
    }

    public record RateLimiterMetrics(long totalRequests, long allowedRequests, long throttledRequests,
                                     double rejectionRatePercent, int activeClients) {}
}
