package com.example.inventory.idgen;

import com.example.inventory.integration.IdGeneratorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Member 3: Distributed 64-bit Unique ID Generator based on the Twitter Snowflake algorithm.
 *
 * Bit Layout:
 * +--------------------------------------------------------------------------------+
 * | 1 bit Sign | 41 bits Timestamp (ms) | 5 bits Datacenter | 5 bits Node | 12 bits Seq |
 * +--------------------------------------------------------------------------------+
 *
 * Characteristics:
 * - 64-bit long integer.
 * - Millisecond precision across ~69 years using custom base epoch.
 * - Supports 32 datacenters x 32 nodes = 1,024 independent server instances.
 * - 4,096 unique IDs per millisecond per node (>4 million IDs/sec).
 * - Naturally monotonic & time-sortable: eliminates MongoDB B-tree fragmentation.
 * - Zero database roundtrips and zero inter-node communication required.
 */
@Component
@Primary
public class SnowflakeIdGenerator implements IdGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // Custom base epoch: 2024-01-01T00:00:00Z (in milliseconds)
    public static final long CUSTOM_EPOCH = 1704067200000L;

    // Bit allocations
    private static final long SEQUENCE_BITS = 12L;
    private static final long NODE_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;

    // Bit masks & limits
    private static final long MAX_NODE_ID = ~(-1L << NODE_ID_BITS);          // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);        // 4095

    // Bit shifts
    private static final long NODE_ID_SHIFT = SEQUENCE_BITS;                                 // 12
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;            // 17
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + NODE_ID_BITS + DATACENTER_ID_BITS; // 22

    private final long datacenterId;
    private final long nodeId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this(1L, 1L);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SnowflakeIdGenerator(SnowflakeProperties properties) {
        this(properties != null ? properties.getDatacenterId() : 1L,
             properties != null ? properties.getNodeId() : 1L);
    }

    public SnowflakeIdGenerator(long datacenterId, long nodeId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("Datacenter ID must be between 0 and %d, provided: %d", MAX_DATACENTER_ID, datacenterId));
        }
        if (nodeId > MAX_NODE_ID || nodeId < 0) {
            throw new IllegalArgumentException(
                    String.format("Node ID must be between 0 and %d, provided: %d", MAX_NODE_ID, nodeId));
        }
        this.datacenterId = datacenterId;
        this.nodeId = nodeId;
        log.info("Initialized SnowflakeIdGenerator with datacenterId={}, nodeId={}", datacenterId, nodeId);
    }

    /**
     * Generates a prefixed unique ID (e.g., "prod_684920491820495").
     * Satisfies IdGeneratorPort contract for Member 1 and Member 2 services.
     */
    @Override
    public String generateId(String prefix) {
        long id = nextId();
        if (prefix == null || prefix.isBlank()) {
            return String.valueOf(id);
        }
        return prefix.toLowerCase().trim() + "_" + id;
    }

    /**
     * Generates the raw 64-bit unique snowflake long.
     */
    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        // Clock drift protection
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // Minor drift: wait it out
                try {
                    wait(offset << 1);
                    currentTimestamp = timeGen();
                    if (currentTimestamp < lastTimestamp) {
                        throw new IllegalStateException(String.format("Clock moved backwards by %d ms", offset));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting for system clock", e);
                }
            } else {
                throw new IllegalStateException(
                        String.format("Clock moved backwards by %d ms. Refusing to generate ID for %d ms", offset, offset));
            }
        }

        if (lastTimestamp == currentTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // Sequence overflow within the current millisecond: wait for next millisecond
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond reached: reset sequence counter
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    /**
     * Extracts the generation timestamp from a 64-bit Snowflake ID.
     */
    public static Instant parseInstant(long snowflakeId) {
        long timestampDelta = snowflakeId >> TIMESTAMP_LEFT_SHIFT;
        return Instant.ofEpochMilli(CUSTOM_EPOCH + timestampDelta);
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getNodeId() {
        return nodeId;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }
}
