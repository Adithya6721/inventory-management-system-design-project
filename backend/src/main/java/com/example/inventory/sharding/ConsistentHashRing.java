package com.example.inventory.sharding;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable SHA-256 ring. Each logical server owns several clockwise intervals. */
public final class ConsistentHashRing {
    private final int virtualNodes;
    private final List<String> servers;
    private final NavigableMap<BigInteger, String> ring = new TreeMap<>();

    public ConsistentHashRing(Collection<String> servers, int virtualNodes) {
        if (virtualNodes < 1) throw new IllegalArgumentException("virtualNodes must be positive");
        this.virtualNodes = virtualNodes;
        TreeSet<String> sorted = new TreeSet<>();
        for (String server : servers) {
            if (server == null || server.isBlank() || !sorted.add(server)) {
                throw new IllegalArgumentException("Server IDs must be nonblank and unique");
            }
        }
        this.servers = List.copyOf(sorted);
        for (String server : this.servers) {
            for (int i = 0; i < virtualNodes; i++) {
                if (ring.put(hash(server + "#" + i), server) != null) {
                    throw new IllegalArgumentException("Virtual node hash collision");
                }
            }
        }
    }

    public String route(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("Cannot route on an empty ring");
        var next = ring.ceilingEntry(hash(key));
        return (next == null ? ring.firstEntry() : next).getValue();
    }

    public List<String> servers() { return servers; }
    public int virtualNodes() { return virtualNodes; }
    public int size() { return ring.size(); }

    public static BigInteger hash(String value) {
        try {
            return new BigInteger(1, MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
