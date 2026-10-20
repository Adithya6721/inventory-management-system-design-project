package com.example.inventory.sharding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shards")
@ConditionalOnProperty(prefix = "inventory.sharding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShardController {
    private final ShardedInventoryPersistenceRouter router;

    public ShardController(ShardedInventoryPersistenceRouter router) { this.router = router; }

    @GetMapping
    public ShardedInventoryPersistenceRouter.Status status() { return router.status(); }

    @GetMapping("/route")
    public ShardedInventoryPersistenceRouter.Route route(@RequestParam String productId,
                                                         @RequestParam String warehouseId) {
        return router.route(productId, warehouseId);
    }

    @PostMapping("/{shardId}")
    public ShardedInventoryPersistenceRouter.RebalanceResult add(@PathVariable String shardId) {
        return router.addShard(shardId);
    }

    @DeleteMapping("/{shardId}")
    public ShardedInventoryPersistenceRouter.RebalanceResult remove(@PathVariable String shardId) {
        return router.removeShard(shardId);
    }

    @PostMapping("/rebalance")
    public ShardedInventoryPersistenceRouter.RebalanceResult rebalance() { return router.rebalance(); }
}
