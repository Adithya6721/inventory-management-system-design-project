package com.example.inventory.shortlink;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;

@Service
public class ShortLinkService {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final MongoTemplate mongo;
    private final IdGeneratorPort ids;

    public ShortLinkService(MongoTemplate mongo, IdGeneratorPort ids) {
        this.mongo = mongo;
        this.ids = ids;
    }

    public ShortLink create(String value) {
        String url = validate(value);
        for (int attempt = 0; attempt < 3; attempt++) {
            String code = base62(Long.parseLong(ids.generateId("")));
            try {
                // insert, never save: a collision must not overwrite an existing destination.
                return mongo.insert(new ShortLink(code, url, Instant.now(), 0));
            } catch (DuplicateKeyException collision) { /* Allocate another ID. */ }
        }
        throw new ConflictException("Could not allocate a short link. Please retry.");
    }

    public ShortLink get(String code) {
        ShortLink link = mongo.findById(code, ShortLink.class);
        if (link == null) throw new NotFoundException("Short link not found.");
        return link;
    }

    public ShortLink resolve(String code) {
        ShortLink link = mongo.findAndModify(new Query(Criteria.where("_id").is(code)),
                new Update().inc("redirects", 1), FindAndModifyOptions.options().returnNew(true), ShortLink.class);
        if (link == null) throw new NotFoundException("Short link not found.");
        return link;
    }

    static String base62(long value) {
        if (value < 0) throw new IllegalArgumentException("Short-link ID must be positive.");
        StringBuilder result = new StringBuilder();
        do { result.append(BASE62.charAt((int) (value % 62))); value /= 62; } while (value > 0);
        return result.reverse().toString();
    }

    private static String validate(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 2048)
            throw new BadRequestException("Enter an HTTP or HTTPS URL, at most 2048 characters.");
        String clean = value.trim();
        try {
            URI uri = URI.create(clean);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Enter a valid HTTP or HTTPS URL without embedded credentials.");
        }
        return clean;
    }
}
