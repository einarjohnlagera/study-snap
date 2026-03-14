package com.studysnap.backend.util;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public final class CreatedAtIdCursorUtils {
    private CreatedAtIdCursorUtils() {
    }

    public static String encode(OffsetDateTime createdAt, UUID id) {
        String payload = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorToken decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Cursor payload format is invalid.");
        }

        OffsetDateTime createdAt = OffsetDateTime.parse(parts[0]);
        UUID id = UUID.fromString(parts[1]);
        return new CursorToken(createdAt, id);
    }

    public record CursorToken(OffsetDateTime createdAt, UUID id) {
    }
}
