package com.example.inventory.shortlink;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ShortLinkServiceTest {
    MongoTemplate mongo;
    IdGeneratorPort ids;
    ShortLinkService service;
    @BeforeEach void setup() {
        mongo = mock(MongoTemplate.class); ids = mock(IdGeneratorPort.class);
        service = new ShortLinkService(mongo, ids);
    }

    @Test void savesValidatedDestinationWithCompactId() {
        when(ids.generateId("")).thenReturn("3843");
        when(mongo.insert(any(ShortLink.class))).thenAnswer(call -> call.getArgument(0));
        var link = service.create(" https://supplier.example/items?q=a%20b#stock ");
        assertEquals("ZZ", link.code());
        assertEquals("https://supplier.example/items?q=a%20b#stock", link.url());
        assertEquals(0, link.redirects());
    }

    @Test void rejectsInvalidDestinationsBeforeWriting() {
        for (String value : new String[]{null, "", "javascript:alert(1)", "//example.com", "ftp://example.com",
                "https://", "https://user:password@example.com", "https://example.com/a b", "https://example.com:99999", "x".repeat(2049)})
            assertThrows(BadRequestException.class, () -> service.create(value));
        verifyNoInteractions(mongo, ids);
    }

    @Test void collisionAllocatesAnotherCodeWithoutOverwriting() {
        when(ids.generateId("")).thenReturn("62", "63");
        when(mongo.insert(any(ShortLink.class))).thenThrow(new DuplicateKeyException("duplicate"))
                .thenAnswer(call -> call.getArgument(0));
        assertEquals("11", service.create("http://localhost:8081/api/products").code());
        verify(mongo, times(2)).insert(any(ShortLink.class));
        verify(mongo, never()).save(any(ShortLink.class));
    }

    @Test void stopsAfterRepeatedCollisions() {
        when(ids.generateId("")).thenReturn("62");
        when(mongo.insert(any(ShortLink.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(ConflictException.class, () -> service.create("https://example.com"));
        verify(mongo, times(3)).insert(any(ShortLink.class));
    }

    @Test void missingLinkIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.get("missing"));
        assertThrows(NotFoundException.class, () -> service.resolve("missing"));
    }
}
