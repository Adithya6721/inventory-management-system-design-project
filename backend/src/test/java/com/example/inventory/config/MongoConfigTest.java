package com.example.inventory.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoConfigTest {
    @Test
    void refusesStandaloneInsteadOfSilentlyRunningNonAtomicWorkflows() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.executeCommand("{ hello: 1 }")).thenReturn(new Document("ok", 1));
        assertThrows(IllegalStateException.class, () -> new MongoConfig().requireTransactionalMongo(template).run(null));
    }

    @Test
    void acceptsReplicaSetAndMongos() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.executeCommand("{ hello: 1 }")).thenReturn(new Document("setName", "inventory-rs"));
        assertDoesNotThrow(() -> new MongoConfig().requireTransactionalMongo(template).run(null));
        when(template.executeCommand("{ hello: 1 }")).thenReturn(new Document("msg", "isdbgrid"));
        assertDoesNotThrow(() -> new MongoConfig().requireTransactionalMongo(template).run(null));
    }
}
