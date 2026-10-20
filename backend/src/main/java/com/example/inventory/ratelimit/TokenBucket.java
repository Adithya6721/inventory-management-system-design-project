package com.example.inventory.ratelimit;

import java.util.concurrent.TimeUnit;

/**
 * Thread-safe Token Bucket implementation for rate limiting.
 * Smoothly accommodates burst traffic up to 'capacity', while strictly
 * replenishing tokens at a continuous rate of 'refillTokens' per 'refillPeriodSeconds'.
 */
public class TokenBucket {

    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;

    private double availableTokens;
    private long lastRefillTimeNanos;
    private volatile long lastAccessTimeMillis;

    public TokenBucket(long capacity, long refillTokens, long refillPeriodSeconds) {
        if (capacity <= 0 || refillTokens <= 0 || refillPeriodSeconds <= 0) {
            throw new IllegalArgumentException("Capacity, refillTokens, and refillPeriodSeconds must all be positive");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = TimeUnit.SECONDS.toNanos(refillPeriodSeconds);
        this.availableTokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
        this.lastAccessTimeMillis = System.currentTimeMillis();
    }

    /**
     * Attempts to consume the specified number of tokens.
     *
     * @param tokensToConsume number of tokens requested (typically 1 for an HTTP request)
     * @return ConsumptionResult indicating whether request was allowed, remaining tokens, and retry-after.
     */
    public synchronized ConsumptionResult tryConsume(long tokensToConsume) {
        this.lastAccessTimeMillis = System.currentTimeMillis();
        refill();

        if (availableTokens >= tokensToConsume) {
            availableTokens -= tokensToConsume;
            return new ConsumptionResult(true, (long) availableTokens, 0L);
        }

        // Calculate time in seconds until at least 'tokensToConsume' are available
        double missingTokens = tokensToConsume - availableTokens;
        double nanosPerToken = (double) refillPeriodNanos / refillTokens;
        long nanosUntilAvailable = (long) Math.ceil(missingTokens * nanosPerToken);
        long retryAfterSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(nanosUntilAvailable));

        return new ConsumptionResult(false, (long) availableTokens, retryAfterSeconds);
    }

    private void refill() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillTimeNanos;

        if (elapsedNanos > 0) {
            double tokensToAdd = ((double) elapsedNanos / refillPeriodNanos) * refillTokens;
            availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
            lastRefillTimeNanos = nowNanos;
        }
    }

    public long getCapacity() {
        return capacity;
    }

    public synchronized long getAvailableTokens() {
        refill();
        return (long) availableTokens;
    }

    public long getLastAccessTimeMillis() {
        return lastAccessTimeMillis;
    }

    public record ConsumptionResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {}
}
