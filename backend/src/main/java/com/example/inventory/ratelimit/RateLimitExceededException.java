package com.example.inventory.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;
    private final long remainingTokens;

    public RateLimitExceededException(String message, long retryAfterSeconds, long remainingTokens) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingTokens = remainingTokens;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }
}
