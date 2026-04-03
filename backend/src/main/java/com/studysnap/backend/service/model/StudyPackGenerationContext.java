package com.studysnap.backend.service.model;

import com.studysnap.backend.entity.LearnerLevel;

import java.util.List;

public record StudyPackGenerationContext(
        LearnerLevel learnerLevel,
        String courseProgram,
        String subject,
        List<String> tags
) {
}
