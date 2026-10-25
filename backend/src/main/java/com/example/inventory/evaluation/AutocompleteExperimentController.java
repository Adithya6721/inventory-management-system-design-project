package com.example.inventory.evaluation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/evaluation/autocomplete")
@ConditionalOnProperty(prefix = "app.evaluation", name = "enabled", havingValue = "true")
public class AutocompleteExperimentController {
    private final AutocompleteExperimentService service;
    public AutocompleteExperimentController(AutocompleteExperimentService service) { this.service = service; }
    @PostMapping("/rebuild") public Map<String,Object> rebuild() { return service.rebuild(); }
    @GetMapping("/catalogue") public List<AutocompleteExperimentService.Suggestion> catalogue(@RequestParam(defaultValue="100") int limit) { return service.catalogue(limit); }
    @GetMapping("/search")
    public ResponseEntity<AutocompleteExperimentService.SearchResult> search(@RequestParam String prefix,
            @RequestParam(defaultValue="8") int limit, @RequestParam(defaultValue="false") boolean cache,
            @RequestParam(defaultValue="manual") String run) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(prefix, limit, cache, run));
    }
    @GetMapping("/metrics") public AutocompleteExperimentService.Metrics metrics() { return service.metrics(); }
}
