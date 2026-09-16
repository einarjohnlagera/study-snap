package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record UpdateCourseProgramCatalogRequest(List<UUID> programFamilyIds) {
    public UpdateCourseProgramCatalogRequest {
        programFamilyIds = programFamilyIds == null ? List.of() : List.copyOf(programFamilyIds);
    }
}
