package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LearnerLevel;

import java.util.List;
import java.util.UUID;

public record AdminNoteApplicableProgramsItemResponse(
        UUID noteId,
        String title,
        String courseProgram,
        String domainContext,
        LearnerLevel learnerLevel,
        List<ApplicableProgramResponse> applicablePrograms
) {
}
