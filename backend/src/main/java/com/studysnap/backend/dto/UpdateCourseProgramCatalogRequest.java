package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record UpdateCourseProgramCatalogRequest(List<UUID> programFamilyIds, Boolean isActive) {
    public UpdateCourseProgramCatalogRequest {
        programFamilyIds = programFamilyIds == null ? null : List.copyOf(programFamilyIds);
    }

    public UpdateCourseProgramCatalogRequest(List<UUID> programFamilyIds) {
        this(programFamilyIds, null);
    }
}
