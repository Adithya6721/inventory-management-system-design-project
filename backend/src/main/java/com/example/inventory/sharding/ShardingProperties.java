package com.example.inventory.sharding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "inventory.sharding")
public class ShardingProperties {
    private int virtualNodes = 32;
    private List<String> initialShards = List.of("shard-1", "shard-2", "shard-3");
    private Map<String, String> databases = new LinkedHashMap<>(Map.of(
            "shard-1", "inventory_shard_1", "shard-2", "inventory_shard_2",
            "shard-3", "inventory_shard_3", "shard-4", "inventory_shard_4"));

    public int getVirtualNodes() { return virtualNodes; }
    public void setVirtualNodes(int virtualNodes) { this.virtualNodes = virtualNodes; }
    public List<String> getInitialShards() { return initialShards; }
    public void setInitialShards(List<String> initialShards) { this.initialShards = initialShards; }
    public Map<String, String> getDatabases() { return databases; }
    public void setDatabases(Map<String, String> databases) { this.databases = databases; }
}
