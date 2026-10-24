package com.example.inventory.sharding;

import com.mongodb.client.MongoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.LinkedHashMap;

@Configuration
@ConditionalOnProperty(prefix = "inventory.sharding", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ShardingProperties.class)
public class ShardingConfiguration {
    @Bean
    @Primary
    ShardedInventoryPersistenceRouter shardedInventoryPersistenceRouter(
            MongoClient client, MongoTemplate mongoTemplate, ShardingProperties properties) {
        var shards = new LinkedHashMap<String, MongoShard>();
        properties.getDatabases().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getValue().equals(mongoTemplate.getDb().getName())) {
                        throw new IllegalArgumentException("Shard databases must differ from the core database");
                    }
                    shards.put(entry.getKey(), new MongoShard(entry.getValue(),
                            new TransactionalShardTemplate(mongoTemplate, entry.getValue())));
                });
        var router = new ShardedInventoryPersistenceRouter(shards, mongoTemplate, properties);
        router.initialize();
        return router;
    }
}
