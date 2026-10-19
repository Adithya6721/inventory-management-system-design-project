package com.example.inventory.sharding;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/** targetShards journals an unfinished migration so a restart can resume it. */
@Document("shard_topology")
public record ShardTopology(@Id String id, List<String> activeShards, List<String> targetShards,
                            int virtualNodes, Map<String, String> databases) {
    static final String ID = "inventory";

    ShardTopology transition(List<String> active, List<String> target) {
        return new ShardTopology(id, List.copyOf(active), target == null ? null : List.copyOf(target),
                virtualNodes, databases);
    }
}
