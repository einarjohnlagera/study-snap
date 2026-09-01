package com.studysnap.backend.dto;

import java.util.List;

public record NoteApplicableProgramsResponse(
        List<ApplicableProgramResponse> programs,
        boolean courseProgramShadowed,
        String effectiveWritingDomain
) {
}
