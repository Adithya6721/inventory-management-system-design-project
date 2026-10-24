package com.example.inventory.sharding;

import com.mongodb.client.MongoDatabase;
import org.springframework.data.mongodb.MongoDatabaseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

/** A different logical database on the SAME factory and transaction-bound session. */
final class TransactionalShardTemplate extends MongoTemplate {
    private final String database;

    TransactionalShardTemplate(MongoTemplate core, String database) {
        super(core.getMongoDatabaseFactory(), core.getConverter());
        this.database = database;
    }

    @Override
    protected MongoDatabase doGetDatabase() {
        return MongoDatabaseUtils.getDatabase(database, getMongoDatabaseFactory());
    }
}
