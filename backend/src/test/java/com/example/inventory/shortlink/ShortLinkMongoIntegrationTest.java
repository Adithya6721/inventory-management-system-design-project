package com.example.inventory.shortlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.example.inventory.integration.IdGeneratorPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "consistency.mongo.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:${MONGO_PORT:27019}/inventory_shortlinks_test?replicaSet=inventory-rs",
        "app.ratelimit.enabled=false", "app.cache.enabled=false", "logging.level.root=WARN",
        "logging.level.com.example.inventory=WARN",
        "logging.level.org.springframework.data.mongodb.core.MongoTemplate=WARN"
})
class ShortLinkMongoIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate mongo;
    @Autowired IdGeneratorPort ids;
    @LocalServerPort int port;

    @Test void storedLinkRedirectsExactlyAndCountsConcurrentRequests() throws Exception {
        String destination = "https://supplier.example/products?sku=DEMO-001&warehouse=central#availability";
        var created = http.postForEntity("/api/links", Map.of("url", destination), ShortLinkController.LinkResponse.class);
        assertEquals(201, created.getStatusCode().value());
        var link = created.getBody();
        assertNotNull(link);
        assertTrue(link.code().matches("[a-zA-Z0-9]+"));
        // A fresh service reads the persisted mapping, without relying on an in-memory map.
        assertEquals(destination, new ShortLinkService(mongo, ids).get(link.code()).url());
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = pool.invokeAll(IntStream.range(0, 20).<java.util.concurrent.Callable<Integer>>mapToObj(i -> () -> {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + link.path())).GET().build(), HttpResponse.BodyHandlers.discarding());
                assertEquals(destination, response.headers().firstValue("Location").orElseThrow());
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                return response.statusCode();
            }).toList());
            for (var future : futures) assertEquals(302, future.get());
        } finally { pool.shutdownNow(); }
        assertEquals(20, http.getForObject("/api/links/" + link.code(), ShortLinkController.LinkResponse.class).redirects());
        assertEquals(400, http.postForEntity("/api/links", Map.of("url", "javascript:alert(1)"), String.class).getStatusCode().value());
        assertEquals(404, http.getForEntity("/r/nonexistent-link", String.class).getStatusCode().value());
    }
}
