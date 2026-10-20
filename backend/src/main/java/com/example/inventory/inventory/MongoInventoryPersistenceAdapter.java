package com.example.inventory.inventory;

import com.example.inventory.integration.InventoryPersistencePort;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnMissingBean(value = InventoryPersistencePort.class, ignored = MongoInventoryPersistenceAdapter.class)
public class MongoInventoryPersistenceAdapter implements InventoryPersistencePort {

    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;

    public MongoInventoryPersistenceAdapter(InventoryRepository inventoryRepository,
                                           MongoTemplate mongoTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId);
    }

    @Override
    public List<InventoryRecord> findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId);
    }

    @Override
    public List<InventoryRecord> findByWarehouseId(String warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId);
    }

    @Override
    public InventoryRecord save(InventoryRecord record) {
        record.setUpdatedAt(Instant.now());
        return inventoryRepository.save(record);
    }

    @Override
    public boolean reserveStock(String productId, String warehouseId, long quantity) {
        if (quantity <= 0) {
            return false;
        }

        // Atomic check: (quantity - reservedQuantity) >= delta
        Document queryDoc = new Document("productId", productId)
                .append("warehouseId", warehouseId)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$subtract", List.of("$quantity", "$reservedQuantity")),
                        quantity
                )));

        Query query = new BasicQuery(queryDoc);
        Update update = new Update()
                .inc("reservedQuantity", quantity)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());

        InventoryRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), InventoryRecord.class);

        return updated != null;
    }

    @Override
    public boolean releaseReservedStock(String productId, String warehouseId, long quantity) {
        if (quantity <= 0) {
            return false;
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("warehouseId").is(warehouseId)
                .and("reservedQuantity").gte(quantity));

        Update update = new Update()
                .inc("reservedQuantity", -quantity)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());

        InventoryRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), InventoryRecord.class);

        return updated != null;
    }

    @Override
    public boolean deductReservedStock(String productId, String warehouseId, long quantity) {
        if (quantity <= 0) {
            return false;
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("warehouseId").is(warehouseId)
                .and("reservedQuantity").gte(quantity)
                .and("quantity").gte(quantity));

        Update update = new Update()
                .inc("quantity", -quantity)
                .inc("reservedQuantity", -quantity)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());

        InventoryRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), InventoryRecord.class);

        return updated != null;
    }

    @Override
    public boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity) {
        if (quantity <= 0) {
            return false;
        }

        Document queryDoc = new Document("productId", productId)
                .append("warehouseId", warehouseId)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$subtract", List.of("$quantity", "$reservedQuantity")),
                        quantity
                )));

        Query query = new BasicQuery(queryDoc);
        Update update = new Update()
                .inc("quantity", -quantity)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());

        InventoryRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), InventoryRecord.class);

        return updated != null;
    }

    @Override
    public InventoryRecord creditStock(String productId, String warehouseId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Credit quantity must be positive");
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("warehouseId").is(warehouseId));

        Update update = new Update()
                .inc("quantity", quantity)
                .inc("version", 1L)
                .set("updatedAt", Instant.now())
                .setOnInsert("id", productId + "_" + warehouseId)
                .setOnInsert("productId", productId)
                .setOnInsert("warehouseId", warehouseId)
                .setOnInsert("reservedQuantity", 0L);

        return mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().upsert(true).returnNew(true), InventoryRecord.class);
    }

    @Override
    public boolean adjustStock(String productId, String warehouseId, long physicalDelta, long reservedDelta) {
        Optional<InventoryRecord> opt = findByProductIdAndWarehouseId(productId, warehouseId);
        if (opt.isEmpty()) {
            if (physicalDelta < 0 || reservedDelta < 0 || physicalDelta < reservedDelta) {
                return false;
            }
            InventoryRecord rec = new InventoryRecord(productId, warehouseId, physicalDelta, reservedDelta);
            save(rec);
            return true;
        }

        InventoryRecord current = opt.get();
        long newQuantity = current.getQuantity() + physicalDelta;
        long newReserved = current.getReservedQuantity() + reservedDelta;

        if (newQuantity < 0 || newReserved < 0 || newQuantity < newReserved) {
            return false;
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("warehouseId").is(warehouseId)
                .and("version").is(current.getVersion()));

        Update update = new Update()
                .set("quantity", newQuantity)
                .set("reservedQuantity", newReserved)
                .inc("version", 1L)
                .set("updatedAt", Instant.now());

        InventoryRecord updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), InventoryRecord.class);

        return updated != null;
    }
}
