package com.example.inventory.evaluation;

import com.example.inventory.cache.RedisInventoryCacheAdapter;
import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.product.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in read-only counters for isolated local load experiments. */
@RestController
@RequestMapping("/internal/evaluation")
@ConditionalOnProperty(prefix = "app.evaluation", name = "enabled", havingValue = "true")
public class EvaluationController {
    private final InventoryCachePort cache;
    private final ProductService products;
    private final boolean cacheEnabled;

    public EvaluationController(InventoryCachePort cache, ProductService products,
                                @Value("${app.cache.enabled:true}") boolean cacheEnabled) {
        this.cache = cache;
        this.products = products;
        this.cacheEnabled = cacheEnabled;
    }

    @GetMapping("/metrics")
    public Metrics metrics() {
        return new Metrics(ProcessHandle.current().pid(), cacheEnabled, cache.getMetrics(),
                cache instanceof RedisInventoryCacheAdapter redis ? redis.getDiagnostics() : null,
                products.getReadMetrics());
    }

    public record Metrics(long pid, boolean cacheEnabled, InventoryCachePort.CacheMetrics cache,
                           RedisInventoryCacheAdapter.Diagnostics diagnostics,
                           ProductService.ReadMetrics productReads) { }
}
