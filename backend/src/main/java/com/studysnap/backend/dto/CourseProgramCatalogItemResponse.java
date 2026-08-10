package com.studysnap.backend.dto;

import java.util.UUID;

public record CourseProgramCatalogItemResponse(
        UUID id,
        String name,
        UUID programFamilyId,
        String programFamilyName
) {
}
