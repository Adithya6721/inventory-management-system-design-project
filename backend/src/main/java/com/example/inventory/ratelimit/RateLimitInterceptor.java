package com.example.inventory.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;

    public RateLimitInterceptor(RateLimiterService rateLimiterService, RateLimiterProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientKey = resolveClientKey(request);

        TokenBucket.ConsumptionResult result = rateLimiterService.consume(clientKey);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded for client '%s'. Token bucket capacity exhausted. Please retry after %d seconds.",
                            clientKey, result.retryAfterSeconds()),
                    result.retryAfterSeconds(),
                    result.remainingTokens()
            );
        }

        return true;
    }

    private String resolveClientKey(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "anonymous_client";
    }
}
