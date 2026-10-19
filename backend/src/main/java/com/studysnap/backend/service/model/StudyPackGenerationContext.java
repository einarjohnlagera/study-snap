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
        LearnerLevel noteLearnerLevel,
        LlmRouting routing
) {
    public StudyPackGenerationContext(
            LearnerLevel learnerLevel, String courseProgram, String subject, List<String> tags,
            DomainContext domainContext, LearnerLevel noteLearnerLevel
    ) {
        this(learnerLevel, courseProgram, subject, tags, domainContext, noteLearnerLevel, null);
    }

    public StudyPackGenerationContext withRouting(LlmRouting value) {
        return new StudyPackGenerationContext(learnerLevel, courseProgram, subject, tags,
                domainContext, noteLearnerLevel, value);
    }

    public StudyPackGenerationContext(
            LearnerLevel learnerLevel,
            String courseProgram,
            String subject,
            List<String> tags
    ) {
        this(learnerLevel, courseProgram, subject, tags, null, null, null);
    }
}
