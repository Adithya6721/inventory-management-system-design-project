package com.example.inventory.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutocompleteExperimentServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
    AutocompleteExperimentService service;
    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        service = new AutocompleteExperimentService(mongo, redis, new ObjectMapper(), "test");
    }
    @Test void tokensMatchSkuWordsCategoriesAndContiguousSku() {
        assertThat(AutocompleteExperimentService.tokens(new AutocompleteExperimentService.Suggestion("p", "AS-001", "Apple Sensor", "Office-Tools")))
                .containsExactlyInAnyOrder("as", "001", "as001", "apple", "sensor", "office", "tools");
    }
    @Test void uncachedModeNeverUsesRedis() {
        when(mongo.find(any(Query.class), eq(Document.class), eq(AutocompleteExperimentService.COLLECTION))).thenReturn(List.of());
        assertThat(service.search("absent",8,false,"run").results()).isEmpty();
        verifyNoInteractions(redis);
        assertThat(service.metrics().databaseReads()).isEqualTo(1);
    }
    @Test void cachedEmptyListIsAHitWithoutMongoRead() {
        when(values.get(anyString())).thenReturn("[]");
        assertThat(service.search("absent",8,true,"run").source()).isEqualTo("redis");
        verifyNoInteractions(mongo);
        assertThat(service.metrics().redisHits()).isEqualTo(1);
    }
    @Test void cacheMissQueriesMongoAndCachesResponse() {
        when(mongo.find(any(Query.class), eq(Document.class), eq(AutocompleteExperimentService.COLLECTION)))
                .thenReturn(List.of(new Document("productId","p").append("sku","AS-1").append("name","Sensor").append("category","Tools")));
        assertThat(service.search("sensor",8,true,"run").results()).hasSize(1);
        verify(values).set(anyString(), contains("Sensor"), eq(Duration.ofMinutes(5)));
    }
    @Test void failedLookupFallsBackWithoutSecondRedisWait() {
        when(values.get(anyString())).thenThrow(new RuntimeException("offline"));
        when(mongo.find(any(Query.class), eq(Document.class), eq(AutocompleteExperimentService.COLLECTION))).thenReturn(List.of());
        assertThat(service.search("x",8,true,"run").source()).isEqualTo("mongo");
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        assertThat(service.metrics().redisFailures()).isEqualTo(1);
    }
}
