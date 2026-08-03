package com.studysnap.backend.service.model;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;

import java.util.List;

public record StudyPackGenerationContext(
        LearnerLevel learnerLevel,
        String courseProgram,
        String subject,
        List<String> tags,
        DomainContext domainContext,
        LearnerLevel noteLearnerLevel
) {
    public StudyPackGenerationContext(
            LearnerLevel learnerLevel,
            String courseProgram,
            String subject,
            List<String> tags
    ) {
        this(learnerLevel, courseProgram, subject, tags, null, null);
    }
}
