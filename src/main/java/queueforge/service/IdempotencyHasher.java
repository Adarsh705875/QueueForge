package queueforge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

public final class IdempotencyHasher {
    private IdempotencyHasher() { }

    public static String hash(String jobType, String payload, Integer priority,
                              Integer maxAttempts, LocalDateTime scheduledAt) {
        String canonical = part(jobType) + part(payload) + part(String.valueOf(priority))
                + part(String.valueOf(maxAttempts)) + part(String.valueOf(scheduledAt));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String part(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }
}
