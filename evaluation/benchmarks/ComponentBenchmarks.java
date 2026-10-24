import com.example.inventory.idgen.SnowflakeIdGenerator;
import com.example.inventory.sharding.ConsistentHashRing;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Standalone evaluation of actual application classes; no changes to production behaviour. */
public class ComponentBenchmarks {
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static long number(Document d, String key) { return ((Number)d.get(key)).longValue(); }
    static String key(Document d) { return d.getString("productId") + ":" + d.getString("warehouseId"); }
    static List<Document> documents(com.mongodb.client.MongoDatabase db, String name) {
        return db.getCollection(name).find().sort(new Document("_id", 1)).into(new ArrayList<>());
    }

    static Document snapshot(int port, String database) {
        try (var client = MongoClients.create("mongodb://127.0.0.1:" + port + "/?replicaSet=inventory-rs")) {
            var core = client.getDatabase(database);
            List<Document> rows = new ArrayList<>();
            Map<String,Long> counts = new TreeMap<>();
            Map<String,Document> stocks = new HashMap<>();
            Document topology = core.getCollection("shard_topology").find().first();
            ConsistentHashRing ring = topology == null ? null : new ConsistentHashRing(
                    topology.getList("activeShards", String.class), topology.getInteger("virtualNodes"));
            for (int i = 0; i <= 4; i++) {
                String name = i == 0 ? database : database + "_shard_" + i;
                var values = documents(client.getDatabase(name), "inventory");
                counts.put(name, (long)values.size());
                for (Document value : values) {
                    require(stocks.put(key(value), value) == null, "Duplicate physical stock owner: " + key(value));
                    long q = number(value, "quantity"), r = number(value, "reservedQuantity");
                    require(q >= 0 && r >= 0 && r <= q, "Invalid stock: " + key(value));
                    if (ring != null) require(i > 0 && ring.route(key(value)).equals("shard-" + i), "Wrong shard owner");
                    else require(i == 0, "Unexpected shard record in single-store baseline");
                    rows.add(new Document("database", name).append("document", value));
                }
            }
            rows.sort(Comparator.comparing(d -> d.get("document", Document.class).getString("_id")));
            var orders = documents(core, "sales_orders");
            var movements = documents(core, "inventory_movements");
            Map<String,Long> expected = new HashMap<>();
            Map<String,String> orderKeys = new HashMap<>();
            Map<String,Integer> auditCounts = new HashMap<>();
            for (Document order : orders) {
                require("RESERVED".equals(order.getString("status")), "Unexpected order status");
                String oid = order.getString("orderId");
                var items = order.getList("items", Document.class);
                require(items.size() == 1, "Fixture order must have one line");
                Document item = items.get(0);
                require(orderKeys.put(oid, key(item)) == null, "Duplicate order ID");
                expected.merge(key(item), number(item, "quantity"), Long::sum);
            }
            for (Document movement : movements) {
                if (!"SALE_RESERVE".equals(movement.getString("movementType"))) continue;
                String oid = movement.getString("referenceId");
                require(key(movement).equals(orderKeys.get(oid)), "Orphan/wrong-key reservation audit");
                require(number(movement, "quantityDelta") == -1, "Wrong reservation audit quantity");
                auditCounts.merge(oid, 1, Integer::sum);
            }
            for (String oid : orderKeys.keySet()) require(auditCounts.getOrDefault(oid, 0) == 1, "Missing/duplicate order audit");
            for (var entry : stocks.entrySet()) require(number(entry.getValue(), "reservedQuantity") == expected.getOrDefault(entry.getKey(), 0L), "Orphan/missing reservation");
            for (String k : expected.keySet()) require(stocks.containsKey(k), "Order has no stock record");
            return new Document("passed", true).append("recordCounts", counts).append("inventory", rows)
                    .append("orders", orders).append("movements", movements)
                    .append("reservedOrders", orders.size()).append("duplicates", 0);
        }
    }

    static Document ids(int repeats) throws Exception {
        // Enough samples for a quick comparison; no uniqueness-set work inside the timed region.
        int total = 250_000;
        var cpu = (com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        List<Document> results = new ArrayList<>();
        for (int threads : new int[]{1,8,32}) {
            for (int repeat = 1; repeat <= repeats; repeat++) {
                for (String mode : repeat % 2 == 1 ? List.of("uuid", "snowflake") : List.of("snowflake", "uuid")) {
                    var generator = new SnowflakeIdGenerator(1, 6);
                    for (int i = 0; i < 20_000; i++) {
                        if (mode.equals("snowflake")) generator.generateId("bench"); else UUID.randomUUID().toString();
                    }
                    String[] values = new String[total];
                    ExecutorService pool = Executors.newFixedThreadPool(threads);
                    CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
                    List<Future<?>> futures = new ArrayList<>();
                    try {
                        for (int t = 0; t < threads; t++) {
                            final int begin = total * t / threads, end = total * (t + 1) / threads;
                            futures.add(pool.submit(() -> {
                                ready.countDown(); go.await();
                                for (int i = begin; i < end; i++) values[i] = mode.equals("snowflake") ? generator.generateId("bench") : "bench_" + UUID.randomUUID();
                                return null;
                            }));
                        }
                        ready.await(); long cpuStart = cpu.getProcessCpuTime(), start = System.nanoTime(); go.countDown();
                        for (Future<?> future : futures) future.get();
                        long ns = System.nanoTime() - start, cpuNs = cpu.getProcessCpuTime() - cpuStart;
                        int collisions = total - new HashSet<>(Arrays.asList(values)).size();
                        require(collisions == 0, "Generated ID collision");
                        results.add(new Document("mode", mode).append("threads", threads).append("repeat", repeat)
                            .append("ids", total).append("milliseconds", ns / 1e6).append("ids_per_second", total * 1e9 / ns)
                            .append("collisions", collisions).append("cpu_milliseconds", cpuNs / 1e6)
                            .append("mean_id_characters", Arrays.stream(values).mapToInt(String::length).average().orElse(0)));
                    } finally { pool.shutdownNow(); }
                }
            }
        }
        final long fixedTime = System.currentTimeMillis();
        class FixedClock extends SnowflakeIdGenerator {
            FixedClock(long node) { super(1, node); }
            @Override protected long timeGen() { return fixedTime; }
        }
        Map<String,Integer> collisions = new LinkedHashMap<>();
        for (boolean duplicate : List.of(false,true)) {
            var first = new FixedClock(6); var second = new FixedClock(duplicate ? 6 : 7);
            Set<Long> unique = new HashSet<>();
            for (int i = 0; i < 1000; i++) { unique.add(first.nextId()); unique.add(second.nextId()); }
            collisions.put(duplicate ? "duplicate_node_ids" : "distinct_node_ids", 2000 - unique.size());
        }
        require(collisions.get("distinct_node_ids") == 0 && collisions.get("duplicate_node_ids") == 1000, "Node identity check failed");
        return new Document("results", results).append("node_configuration_check", collisions)
            .append("scope", "Same total IDs per point; one shared Snowflake node across threads, full UUID v4 baseline; warmed direct Java microbenchmark. Duplicate-node fixed-clock case is deliberate misconfiguration.");
    }

    static Document hashes() {
        List<Document> results = new ArrayList<>();
        var three = List.of("shard-1","shard-2","shard-3");
        var four = List.of("shard-1","shard-2","shard-3","shard-4");
        for (int count : new int[]{1000,10000,100000}) {
            for (int nodes : new int[]{1,32,128}) {
                var old = new ConsistentHashRing(three, nodes); var added = new ConsistentHashRing(four, nodes);
                Map<String,Integer> distribution = new TreeMap<>(), traffic = new TreeMap<>();
                int ringMoved = 0, moduloMoved = 0;
                long start = System.nanoTime();
                String hotOwner = old.route("product-0:warehouse-0");
                for (int i = 0; i < count; i++) {
                    String key = "product-" + i + ":warehouse-" + i % 5;
                    String from = old.route(key), to = added.route(key);
                    distribution.merge(from, 1, Integer::sum);
                    if (!from.equals(to)) { require(to.equals("shard-4"), "Addition moved a key to an old shard"); ringMoved++; }
                    var hash = ConsistentHashRing.hash(key);
                    if (!three.get(hash.mod(java.math.BigInteger.valueOf(3)).intValue()).equals(four.get(hash.mod(java.math.BigInteger.valueOf(4)).intValue()))) moduloMoved++;
                    traffic.merge(i % 5 == 0 ? from : hotOwner, 1, Integer::sum);
                }
                long ns = System.nanoTime() - start;
                double average = count / 3d;
                double cv = Math.sqrt(three.stream().mapToDouble(s -> Math.pow(distribution.getOrDefault(s,0) - average,2)).sum() / 3) / average;
                results.add(new Document("keys", count).append("virtual_nodes", nodes).append("record_distribution", distribution)
                    .append("coefficient_of_variation", cv).append("hot_traffic_distribution", traffic)
                    .append("ring_add_remapped_pct", ringMoved * 100d / count).append("ring_remove_fourth_remapped_pct", ringMoved * 100d / count)
                    .append("modulo_add_remapped_pct", moduloMoved * 100d / count).append("comparison_total_ms", ns / 1e6));
            }
        }
        return new Document("results", results).append("scope", "Actual ConsistentHashRing vs SHA256(key) modulo N, fixed deterministic keys; 80% hot-key traffic is simulated. This is placement/remapping, not a server capacity test.");
    }

    public static void main(String[] args) throws Exception {
        Document result = switch (args[0]) {
            case "snapshot" -> snapshot(Integer.parseInt(args[2]), args[3]);
            case "ids" -> ids(Integer.parseInt(args[2]));
            case "hash" -> hashes();
            default -> throw new IllegalArgumentException("Unknown task");
        };
        Files.writeString(Path.of(args[1]), result.toJson());
    }
}
