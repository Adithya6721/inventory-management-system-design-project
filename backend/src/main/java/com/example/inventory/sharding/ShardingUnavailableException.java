package com.example.inventory.sharding;

public class ShardingUnavailableException extends RuntimeException {
    public ShardingUnavailableException(String message, Throwable cause) { super(message, cause); }
    public ShardingUnavailableException(String message) { super(message); }
}
