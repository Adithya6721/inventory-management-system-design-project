package com.example.inventory.idgen;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Twitter Snowflake ID generator.
 * Supports up to 32 datacenters (5 bits) and 32 worker nodes (5 bits) per datacenter.
 */
@Component
@ConfigurationProperties(prefix = "app.snowflake")
public class SnowflakeProperties {

    /**
     * Datacenter identifier (0 - 31).
     */
    private long datacenterId = 1L;

    /**
     * Node / Worker identifier (0 - 31).
     */
    private long nodeId = 1L;

    public long getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
    }

    public long getNodeId() {
        return nodeId;
    }

    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }
}
