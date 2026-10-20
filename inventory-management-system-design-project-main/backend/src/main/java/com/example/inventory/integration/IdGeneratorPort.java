package com.example.inventory.integration;

/**
 * Port for distributed unique ID generation.
 * Member 3 will supply the production Snowflake / Redis-based unique ID generator.
 */
public interface IdGeneratorPort {
    String generateId(String prefix);
}
