package com.example.inventory.sharding;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.InventoryRecord;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Single-process routing coordinator. Membership changes exclude all inventory operations. */
public class ShardedInventoryPersistenceRouter implements InventoryPersistencePort {
    private final Map<String, MongoShard> shards;
    private final Map<String, String> databases;
    private final Map<String, LongAdder> requests = new LinkedHashMap<>();
    private final MongoTemplate metadata;
    private final ShardingProperties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private ShardTopology topology;
    private ConsistentHashRing ring;
    private boolean ready;
    private final ThreadLocal<Boolean> transactionReadLock = new ThreadLocal<>();

    /** Keep membership fixed until the WHOLE business transaction commits/aborts. */
    private boolean acquireInventoryLock() {
        if (Boolean.TRUE.equals(transactionReadLock.get())) return false;
        lock.readLock().lock();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return true;
        transactionReadLock.set(true);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                transactionReadLock.remove();
                lock.readLock().unlock();
            }
        });
        requireReady();
        // The transaction may have read master data before taking this lock.
        // Never combine a pre-migration snapshot with post-migration routing.
        ShardTopology visible = metadata.findById(ShardTopology.ID, ShardTopology.class);
        if (visible == null || !new TreeSet<>(visible.activeShards()).equals(new TreeSet<>(ring.servers()))) {
            throw new ConflictException("Shard membership changed after this transaction began; retry the request.");
        }
        return false;
    }

    ShardedInventoryPersistenceRouter(Map<String, MongoShard> shards, MongoTemplate metadata,
                                      ShardingProperties properties) {
        this.shards = Map.copyOf(shards);
        this.metadata = metadata;
        this.properties = properties;
        this.databases = new LinkedHashMap<>();
        shards.forEach((id, shard) -> {
            if (!id.matches("[a-zA-Z0-9-]+") || shard.database().isBlank()) {
                throw new IllegalArgumentException("Invalid shard ID or database");
            }
            databases.put(id, shard.database());
            requests.put(id, new LongAdder());
        });
        if (shards.isEmpty() || new TreeSet<>(databases.values()).size() != shards.size()) {
            throw new IllegalArgumentException("Configure distinct nonempty shard databases");
        }
    }

    void initialize() {
        lock.writeLock().lock();
        try {
            topology = metadata.findById(ShardTopology.ID, ShardTopology.class);
            if (topology == null) {
                validateMembers(properties.getInitialShards());
                topology = new ShardTopology(ShardTopology.ID, List.copyOf(properties.getInitialShards()),
                        null, properties.getVirtualNodes(), Map.copyOf(databases));
                // Construct before persisting to reject invalid virtual-node configuration.
                new ConsistentHashRing(topology.activeShards(), topology.virtualNodes());
                metadata.insert(topology);
            }
            validateTopology();
            ring = new ConsistentHashRing(topology.activeShards(), topology.virtualNodes());
            topology.activeShards().forEach(id -> shards.get(id).ensureIndexes());
            if (topology.targetShards() != null) migrate();
            ready = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void validateTopology() {
        if (topology.virtualNodes() != properties.getVirtualNodes() || !topology.databases().equals(databases)) {
            throw new IllegalStateException("Persisted shard catalog/virtual-nodes differs from configuration; "
                    + "restore the original settings to preserve routing");
        }
        validateMembers(topology.activeShards());
        if (topology.targetShards() != null) validateMembers(topology.targetShards());
    }

    private void validateMembers(List<String> members) {
        if (members == null || members.isEmpty() || !shards.keySet().containsAll(members)
                || new TreeSet<>(members).size() != members.size()) {
            throw new IllegalArgumentException("Membership must contain unique configured shards");
        }
    }

    public static String inventoryKey(String productId, String warehouseId) {
        validateId(productId);
        validateId(warehouseId);
        return productId + ":" + warehouseId;
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank() || id.contains(":")) {
            throw new BadRequestException("Product and warehouse IDs must be nonblank and must not contain ':'");
        }
    }

    private static String key(Document document) {
        return inventoryKey(document.getString("productId"), document.getString("warehouseId"));
    }

    private void requireReady() {
        if (!ready) throw new ShardingUnavailableException(
                "Shard migration needs recovery; retry POST /api/shards/rebalance or restart the backend");
    }

    private <T> T routed(String productId, String warehouseId, Function<InventoryPersistencePort, T> operation) {
        String key = inventoryKey(productId, warehouseId);
        boolean release = acquireInventoryLock();
        try {
            requireReady();
            String owner = ring.route(key);
            requests.get(owner).increment();
            return operation.apply(shards.get(owner).persistence());
        } finally {
            if (release) lock.readLock().unlock();
        }
    }

    private List<InventoryRecord> scatter(Function<InventoryPersistencePort, List<InventoryRecord>> operation) {
        boolean release = acquireInventoryLock();
        try {
            requireReady();
            List<InventoryRecord> result = new ArrayList<>();
            for (String owner : ring.servers()) {
                requests.get(owner).increment();
                result.addAll(operation.apply(shards.get(owner).persistence()));
            }
            return result;
        } finally {
            if (release) lock.readLock().unlock();
        }
    }

    @Override
    public Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId) {
        return routed(productId, warehouseId, port -> port.findByProductIdAndWarehouseId(productId, warehouseId));
    }

    @Override
    public List<InventoryRecord> findByProductId(String productId) {
        validateId(productId);
        return scatter(port -> port.findByProductId(productId));
    }

    @Override
    public List<InventoryRecord> findByWarehouseId(String warehouseId) {
        validateId(warehouseId);
        return scatter(port -> port.findByWarehouseId(warehouseId));
    }

    @Override
    public InventoryRecord save(InventoryRecord record) {
        return routed(record.getProductId(), record.getWarehouseId(), port -> port.save(record));
    }

    @Override
    public boolean reserveStock(String productId, String warehouseId, long quantity) {
        return routed(productId, warehouseId, port -> port.reserveStock(productId, warehouseId, quantity));
    }

    @Override
    public boolean releaseReservedStock(String productId, String warehouseId, long quantity) {
        return routed(productId, warehouseId, port -> port.releaseReservedStock(productId, warehouseId, quantity));
    }

    @Override
    public boolean deductReservedStock(String productId, String warehouseId, long quantity) {
        return routed(productId, warehouseId, port -> port.deductReservedStock(productId, warehouseId, quantity));
    }

    @Override
    public boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity) {
        return routed(productId, warehouseId, port -> port.deductAvailableStockDirect(productId, warehouseId, quantity));
    }

    @Override
    public InventoryRecord creditStock(String productId, String warehouseId, long quantity) {
        return routed(productId, warehouseId, port -> port.creditStock(productId, warehouseId, quantity));
    }

    @Override
    public boolean adjustStock(String productId, String warehouseId, long physicalDelta, long reservedDelta) {
        return routed(productId, warehouseId,
                port -> port.adjustStock(productId, warehouseId, physicalDelta, reservedDelta));
    }

    public Route route(String productId, String warehouseId) {
        lock.readLock().lock();
        try {
            requireReady();
            String key = inventoryKey(productId, warehouseId);
            String owner = ring.route(key);
            return new Route(key, String.format("%064x", ConsistentHashRing.hash(key)), owner, databases.get(owner));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Status status() {
        lock.readLock().lock();
        try {
            List<ShardStatus> result = databases.keySet().stream().sorted()
                    .map(id -> new ShardStatus(id, databases.get(id), ring.servers().contains(id),
                            shards.get(id).count(), requests.get(id).sum())).toList();
            return new Status(ready ? "READY" : "RECOVERY_REQUIRED", ring.virtualNodes(), ring.size(),
                    ring.servers(), topology.targetShards(), result);
        } finally {
            lock.readLock().unlock();
        }
    }

    public RebalanceResult addShard(String id) {
        return changeMembership(id, true);
    }

    public RebalanceResult removeShard(String id) {
        return changeMembership(id, false);
    }

    private RebalanceResult changeMembership(String id, boolean add) {
        lock.writeLock().lock();
        try {
            requireReady();
            if (!shards.containsKey(id)) throw new BadRequestException("Unknown configured shard: " + id);
            TreeSet<String> members = new TreeSet<>(ring.servers());
            if (add && !members.add(id)) throw new ConflictException("Shard is already active: " + id);
            if (!add) {
                if (!members.remove(id)) throw new NotFoundException("Shard is not active: " + id);
                if (members.isEmpty()) throw new ConflictException("Cannot remove the last shard");
            }
            ready = false;
            try {
                // Journal before copying anything. An uncertain write is resolved by reloading on retry.
                persist(topology.transition(topology.activeShards(), List.copyOf(members)));
                RebalanceResult result = migrate();
                ready = true;
                return result;
            } catch (RuntimeException ex) {
                throw new ShardingUnavailableException("Migration interrupted; inventory paused until recovery", ex);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public RebalanceResult rebalance() {
        lock.writeLock().lock();
        try {
            ready = false;
            topology = metadata.findById(ShardTopology.ID, ShardTopology.class);
            if (topology == null) throw new IllegalStateException("Missing persisted topology");
            validateTopology();
            ring = new ConsistentHashRing(topology.activeShards(), topology.virtualNodes());
            RebalanceResult result = topology.targetShards() == null
                    ? new RebalanceResult(ring.servers(), ring.servers(), 0, 0, 0, 0) : migrate();
            ready = true;
            return result;
        } catch (RuntimeException ex) {
            throw new ShardingUnavailableException("Migration recovery failed; inventory remains paused", ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private RebalanceResult migrate() {
        long started = System.nanoTime();
        ConsistentHashRing previous = new ConsistentHashRing(topology.activeShards(), topology.virtualNodes());
        ConsistentHashRing target = new ConsistentHashRing(topology.targetShards(), topology.virtualNodes());
        target.servers().forEach(id -> shards.get(id).ensureIndexes());
        long scanned = 0;
        long moved = 0;
        for (String source : previous.servers()) {
            for (Document document : shards.get(source).documents()) {
                String key = key(document);
                // Ignore staged copies from an earlier interrupted attempt.
                if (!previous.route(key).equals(source)) continue;
                scanned++;
                String destination = target.route(key);
                if (!destination.equals(source)) {
                    shards.get(destination).copy(document);
                    moved++;
                }
            }
        }
        // All destination writes have succeeded. Persist the new authority before deleting originals.
        persist(topology.transition(target.servers(), target.servers()));
        ring = target;
        for (String source : shards.keySet()) {
            for (Document document : shards.get(source).documents()) {
                if (!target.route(key(document)).equals(source)) shards.get(source).delete(document);
            }
        }
        persist(topology.transition(target.servers(), null));
        return new RebalanceResult(previous.servers(), target.servers(), scanned, moved,
                scanned == 0 ? 0 : 100.0 * moved / scanned, (System.nanoTime() - started) / 1_000_000);
    }

    private void persist(ShardTopology value) {
        metadata.save(value);
        topology = value;
    }

    public record Route(String key, String hash, String shardId, String database) { }
    public record ShardStatus(String shardId, String database, boolean active, long records, long requests) { }
    public record Status(String state, int virtualNodesPerShard, int ringPositions,
                         List<String> activeShards, List<String> pendingShards, List<ShardStatus> shards) { }
    public record RebalanceResult(List<String> previousShards, List<String> activeShards,
                                  long recordsScanned, long recordsMoved, double remappedPercent, long durationMs) { }
}
