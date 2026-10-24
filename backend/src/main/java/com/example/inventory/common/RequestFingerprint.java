package com.example.inventory.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Canonical request identity; stored hashes are safe even for keys containing dots. */
public final class RequestFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RequestFingerprint() { }

    public static String key(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String of(Object... values) {
        try {
            return hash(JSON.writeValueAsString(values));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot fingerprint request", ex);
        }
    }

    public record Line(String product, String warehouse, long quantity) { }

    public static <T> List<Line> lines(Collection<T> items, Function<T, String> product,
                                      Function<T, String> warehouse, ToLongFunction<T> quantity) {
        return items.stream().map(i -> new Line(product.apply(i), warehouse.apply(i), quantity.applyAsLong(i)))
                .sorted(Comparator.comparing(Line::product).thenComparing(Line::warehouse)
                        .thenComparingLong(Line::quantity)).toList();
    }

    public static void requireSame(String prior, String current) {
        if (!prior.equals(current)) {
            throw new ConflictException("Idempotency key was already used for a different request.");
        }
    }
}
