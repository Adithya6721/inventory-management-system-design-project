package com.example.inventory.sharding;

import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.InventoryRecord;
import com.example.inventory.inventory.InventoryRepository;
import com.example.inventory.inventory.MongoInventoryPersistenceAdapter;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.util.ArrayList;
import java.util.List;

/** One logical database; reuse Member 1's conditional atomic stock updates. */
final class MongoShard {
    private final String database;
    private final MongoTemplate template;
    private final InventoryPersistencePort persistence;

    MongoShard(String database, MongoTemplate template) {
        this.database = database;
        this.template = template;
        this.persistence = new MongoInventoryPersistenceAdapter(
                new MongoRepositoryFactory(template).getRepository(InventoryRepository.class), template);
    }

    String database() { return database; }
    InventoryPersistencePort persistence() { return persistence; }

    void ensureIndexes() {
        var indexes = template.indexOps(InventoryRecord.class);
        indexes.ensureIndex(new Index().on("productId", Sort.Direction.ASC)
                .on("warehouseId", Sort.Direction.ASC).unique().named("prod_wh_unique_idx"));
        indexes.ensureIndex(new Index().on("productId", Sort.Direction.ASC));
        indexes.ensureIndex(new Index().on("warehouseId", Sort.Direction.ASC));
    }

    List<Document> documents() {
        return template.getCollection("inventory").find().into(new ArrayList<>());
    }

    void copy(Document document) {
        // Raw replacement preserves _id, version, timestamps and any future fields exactly.
        template.getCollection("inventory").replaceOne(new Document("_id", document.get("_id")),
                document, new ReplaceOptions().upsert(true));
    }

    void delete(Document document) {
        template.getCollection("inventory").deleteOne(new Document("_id", document.get("_id")));
    }

    long count() { return template.getCollection("inventory").countDocuments(); }
}
