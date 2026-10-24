package com.example.inventory.urlshortener;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link ShortUrl} documents.
 *
 * <p>Primary lookup is by {@code code} (the Base62 alias), which has a unique
 * index in MongoDB so the query never performs a full-collection scan.
 */
public interface ShortUrlRepository extends MongoRepository<ShortUrl, String> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    Optional<ShortUrl> findByLongUrl(String longUrl);
}
