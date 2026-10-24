package com.example.inventory.urlshortener;

import com.example.inventory.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UrlShortenerService}.
 *
 * <p>All MongoDB interactions are mocked so the suite runs without a running
 * database instance.
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService(repository, mongoTemplate);
    }

    // ── shorten() ─────────────────────────────────────────────────────────────

    @Test
    void shorten_newUrl_persistsAndReturnsEntity() {
        String longUrl = "https://example.com/very/long/inventory/report?page=0&size=50";

        when(repository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(repository.existsByCode(anyString())).thenReturn(false);

        ShortUrl saved = makeEntity("aB3dEf", longUrl, 90);
        when(repository.save(any(ShortUrl.class))).thenReturn(saved);

        ShortUrl result = service.shorten(longUrl, null);

        assertThat(result.getLongUrl()).isEqualTo(longUrl);
        assertThat(result.getCode()).isEqualTo("aB3dEf");

        verify(repository).save(argThat(e -> e.getLongUrl().equals(longUrl)));
    }

    @Test
    void shorten_sameUrlTwice_isIdempotent() {
        String longUrl = "https://example.com/report";
        ShortUrl existing = makeEntity("xyz123", longUrl, 90);

        when(repository.findByLongUrl(longUrl)).thenReturn(Optional.of(existing));

        ShortUrl result = service.shorten(longUrl, null);

        // Must reuse the existing record, never call save
        assertThat(result.getCode()).isEqualTo("xyz123");
        verify(repository, never()).save(any());
    }

    @Test
    void shorten_customTtlDays_respected() {
        String longUrl = "https://example.com/custom-ttl";
        when(repository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(repository.existsByCode(anyString())).thenReturn(false);

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.shorten(longUrl, 30);

        ShortUrl saved = captor.getValue();
        long ttlSeconds = saved.getExpiresAt().getEpochSecond()
                        - saved.getCreatedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(30L * 24 * 60 * 60);
    }

    @Test
    void shorten_ttlExceedsMax_clampedTo365Days() {
        String longUrl = "https://example.com/long-ttl";
        when(repository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(repository.existsByCode(anyString())).thenReturn(false);

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.shorten(longUrl, 9999);

        ShortUrl saved = captor.getValue();
        long ttlSeconds = saved.getExpiresAt().getEpochSecond()
                        - saved.getCreatedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(365L * 24 * 60 * 60);
    }

    @Test
    void shorten_codeCollision_retriesWithLongerCode() {
        String longUrl = "https://example.com/collision";
        when(repository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        // First code collides, second is free
        when(repository.existsByCode(anyString()))
            .thenReturn(true)  // 1st attempt: collision
            .thenReturn(false); // 2nd attempt: success

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.shorten(longUrl, null);

        assertThat(result.getCode()).hasSizeGreaterThan(6); // code grew by 1
        assertThat(service.getMetrics().codeCollisions()).isEqualTo(1);
    }

    // ── resolve() ─────────────────────────────────────────────────────────────

    @Test
    void resolve_validCode_returnsLongUrlAndIncrementsClick() {
        String code    = "aB3dEf";
        String longUrl = "https://example.com/report";

        ShortUrl updated = makeEntity(code, longUrl, 90);
        updated.setClickCount(1);

        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ShortUrl.class)))
            .thenReturn(updated);

        String result = service.resolve(code);

        assertThat(result).isEqualTo(longUrl);
        assertThat(service.getMetrics().redirectRequests()).isEqualTo(1);
    }

    @Test
    void resolve_unknownCode_throwsNotFoundException() {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ShortUrl.class)))
            .thenReturn(null);

        assertThatThrownBy(() -> service.resolve("xxxxxx"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("xxxxxx");
    }

    // ── getInfo() ─────────────────────────────────────────────────────────────

    @Test
    void getInfo_validCode_returnsEntityWithoutIncrementingClick() {
        String code = "aB3dEf";
        ShortUrl entity = makeEntity(code, "https://example.com", 90);
        entity.setClickCount(42);

        when(repository.findByCode(code)).thenReturn(Optional.of(entity));

        ShortUrl result = service.getInfo(code);

        assertThat(result.getClickCount()).isEqualTo(42);
        // findAndModify (which increments) must NOT have been called
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), any(Class.class));
    }

    @Test
    void getInfo_unknownCode_throwsNotFoundException() {
        when(repository.findByCode("xxxxxx")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInfo("xxxxxx"))
            .isInstanceOf(NotFoundException.class);
    }

    // ── metrics() ─────────────────────────────────────────────────────────────

    @Test
    void metrics_initialState_allZero() {
        UrlShortenerService.Metrics m = service.getMetrics();
        assertThat(m.shortenRequests()).isZero();
        assertThat(m.redirectRequests()).isZero();
        assertThat(m.codeCollisions()).isZero();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ShortUrl makeEntity(String code, String longUrl, int ttlDays) {
        ShortUrl e = new ShortUrl(code, longUrl, ttlDays);
        e.setId("mongo_id_" + code);
        return e;
    }
}
