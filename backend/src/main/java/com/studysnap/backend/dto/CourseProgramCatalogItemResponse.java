package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record CourseProgramCatalogItemResponse(
        UUID id,
        String name,
        List<ProgramFamilyResponse> programFamilies,
        UUID programFamilyId,
        String programFamilyName,
        boolean isActive
) {
    public CourseProgramCatalogItemResponse {
        programFamilies = programFamilies == null ? List.of() : List.copyOf(programFamilies);
    }

    public CourseProgramCatalogItemResponse(UUID id, String name, UUID programFamilyId,
                                            String programFamilyName, boolean isActive) {
        this(id, name,
                programFamilyId == null ? List.of() : List.of(new ProgramFamilyResponse(programFamilyId, programFamilyName)),
                programFamilyId, programFamilyName, isActive);
    }
}
