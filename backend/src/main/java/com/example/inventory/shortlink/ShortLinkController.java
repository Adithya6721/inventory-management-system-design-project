package com.example.inventory.shortlink;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;

@RestController
public class ShortLinkController {
    private final ShortLinkService service;
    public ShortLinkController(ShortLinkService service) { this.service = service; }

    public record CreateLink(@NotBlank @Size(max = 2048) String url) { }
    public record LinkResponse(String code, String url, String path, Instant createdAt, long redirects) {
        static LinkResponse from(ShortLink link) {
            return new LinkResponse(link.code(), link.url(), "/r/" + link.code(), link.createdAt(), link.redirects());
        }
    }

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLink request) {
        var link = service.create(request.url());
        return ResponseEntity.created(URI.create("/api/links/" + link.code())).body(LinkResponse.from(link));
    }

    @GetMapping("/api/links/{code}")
    public ResponseEntity<LinkResponse> details(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(LinkResponse.from(service.get(code)));
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        var link = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(link.url()))
                .cacheControl(CacheControl.noStore()).build();
    }
}
