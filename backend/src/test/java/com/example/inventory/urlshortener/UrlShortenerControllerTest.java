package com.example.inventory.urlshortener;

import com.example.inventory.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerControllerTest {

    @Mock
    private UrlShortenerService service;

    @InjectMocks
    private UrlShortenerController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
    }

    @Test
    void shorten_returnsCreatedWithFormattedResponse() {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com/inventory/audit");
        req.setTtlDays(30);

        ShortUrl entity = new ShortUrl("x9K2pQ", "https://example.com/inventory/audit", 30);
        entity.setId("test_id");
        when(service.shorten(eq("https://example.com/inventory/audit"), eq(30)))
                .thenReturn(entity);

        ResponseEntity<ShortenResponse> resp = controller.shorten(req, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("x9K2pQ");
        assertThat(resp.getBody().getShortUrl()).isEqualTo("http://localhost:8080/r/x9K2pQ");
        assertThat(resp.getBody().getLongUrl()).isEqualTo("https://example.com/inventory/audit");
    }

    @Test
    void redirect_returnsFound302WithLocationAndLatencyHeader() {
        when(service.resolve("x9K2pQ")).thenReturn("https://example.com/target-dest");

        ResponseEntity<Void> resp = controller.redirect("x9K2pQ");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resp.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com/target-dest"));
        assertThat(resp.getHeaders().getFirst("X-Redirect-Latency-Ms")).isNotNull();
        assertThat(resp.getHeaders().getFirst("Cache-Control")).contains("no-cache");
    }

    @Test
    void redirect_notFound_bubblesException() {
        when(service.resolve("missing")).thenThrow(new NotFoundException("Code not found"));

        assertThatThrownBy(() -> controller.redirect("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void info_returnsShortenResponseWithoutRedirect() {
        ShortUrl entity = new ShortUrl("x9K2pQ", "https://example.com/target", 60);
        entity.setClickCount(15);
        when(service.getInfo("x9K2pQ")).thenReturn(entity);

        ResponseEntity<ShortenResponse> resp = controller.info("x9K2pQ", request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("x9K2pQ");
        assertThat(resp.getBody().getClickCount()).isEqualTo(15);
    }

    @Test
    void metrics_returnsCurrentMetrics() {
        when(service.getMetrics()).thenReturn(new UrlShortenerService.Metrics(20, 50, 0));

        ResponseEntity<UrlShortenerService.Metrics> resp = controller.metrics();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().shortenRequests()).isEqualTo(20);
        assertThat(resp.getBody().redirectRequests()).isEqualTo(50);
        assertThat(resp.getBody().codeCollisions()).isZero();
    }
}
