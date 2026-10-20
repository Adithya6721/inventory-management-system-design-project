package com.example.inventory.sharding;

import com.example.inventory.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShardingExceptionHandler {
    @ExceptionHandler(ShardingUnavailableException.class)
    ResponseEntity<ApiError> unavailable(ShardingUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                503, "Service Unavailable", ex.getMessage(), request.getRequestURI()));
    }
}
