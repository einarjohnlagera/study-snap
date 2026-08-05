package com.studysnap.backend.dto;

import java.util.List;

public record AdminNoteApplicableProgramsPageResponse(
        List<AdminNoteApplicableProgramsItemResponse> items,
        int page,
        int size,
        long totalElements
) {
}
