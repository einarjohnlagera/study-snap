package com.studysnap.backend.dto;

import java.util.Map;

public record CreateSavedLibraryFilterRequest(
        String name,
        Map<String, Object> filterState
) {
}
