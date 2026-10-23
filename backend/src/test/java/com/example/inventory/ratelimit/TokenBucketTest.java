package com.example.inventory.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    @DisplayName("Should allow burst traffic exactly up to bucket capacity")
    void shouldAllowBurstTrafficUpToCapacity() {
        TokenBucket bucket = new TokenBucket(10L, 5L, 1L);

        // First 10 requests should pass cleanly
        for (int i = 0; i < 10; i++) {
            TokenBucket.ConsumptionResult result = bucket.tryConsume(1);
            assertTrue(result.allowed(), "Request " + (i + 1) + " within burst capacity should be allowed");
        }

        // 11th request in the same instant should be rejected
        TokenBucket.ConsumptionResult rejected = bucket.tryConsume(1);
        assertFalse(rejected.allowed(), "Request 11 exceeding burst capacity must be rejected");
        assertEquals(0, rejected.remainingTokens());
        assertTrue(rejected.retryAfterSeconds() >= 1, "Retry-After should be at least 1 second");
    }

    @Test
    @DisplayName("Should smoothly refill tokens after burst exhaustion")
    void shouldRefillTokensOverTime() throws InterruptedException {
        // Capacity 5, refills 5 tokens every 1 second (1 token every 200ms)
        TokenBucket bucket = new TokenBucket(5L, 5L, 1L);

        // Exhaust all 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(1).allowed());
        }
        assertFalse(bucket.tryConsume(1).allowed());

        // Wait 450ms (should replenish ~2 tokens)
        Thread.sleep(450);

        TokenBucket.ConsumptionResult refilledResult = bucket.tryConsume(1);
        assertTrue(refilledResult.allowed(), "Request after sleep should be allowed due to token replenishment");
    }

    @Test
    @DisplayName("Should isolate rate limits across independent client identifiers")
    void shouldIsolatePerClientBuckets() {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setCapacity(5L);
        props.setRefillTokens(1L);
        props.setRefillPeriodSeconds(1L);

        RateLimiterService service = new RateLimiterService(props);

        // Client 1 (IP 192.168.1.10) exhausts their 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(service.consume("192.168.1.10").allowed());
        }
        assertFalse(service.consume("192.168.1.10").allowed(), "Client 1 should be throttled (429)");

        // Client 2 (IP 192.168.1.20) should still have full quota
        assertTrue(service.consume("192.168.1.20").allowed(), "Client 2 must NOT be impacted by Client 1");
        assertEquals(2, service.getActiveClientCount());

        RateLimiterService.RateLimiterMetrics metrics = service.getMetrics();
        assertEquals(7, metrics.totalRequests());
        assertEquals(6, metrics.allowedRequests());
        assertEquals(1, metrics.throttledRequests());
        assertTrue(metrics.rejectionRatePercent() > 0);
    }
}
