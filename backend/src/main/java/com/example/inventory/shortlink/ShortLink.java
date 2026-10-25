package com.example.inventory.shortlink;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("short_links")
public record ShortLink(@Id String code, String url, Instant createdAt, long redirects) { }
