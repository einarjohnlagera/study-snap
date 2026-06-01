package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SavedLibraryFilterResponse(
        UUID id,
        String name,
        Map<String, Object> filterState,
        Instant createdAt
) {
}
