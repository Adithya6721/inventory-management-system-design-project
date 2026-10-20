package com.example.inventory.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback UUID generator used until Member 3 provides the Snowflake/Redis ID generator.
 */
@Component
@ConditionalOnMissingBean(value = IdGeneratorPort.class, ignored = DefaultUuidGeneratorPort.class)
public class DefaultUuidGeneratorPort implements IdGeneratorPort {

    @Override
    public String generateId(String prefix) {
        String cleanUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (prefix == null || prefix.isBlank()) {
            return cleanUuid;
        }
        return prefix.toLowerCase() + "_" + cleanUuid;
    }
}
