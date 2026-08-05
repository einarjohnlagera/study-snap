package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record AdminNoteApplicableProgramsItemResponse(
        UUID noteId,
        String title,
        String ownerEmail,
        String courseProgram,
        List<ApplicableProgramResponse> applicablePrograms
) {
}
