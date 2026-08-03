package com.studysnap.backend.service;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.InvalidDomainContextException;
import com.studysnap.backend.exception.InvalidNoteLearnerLevelException;

public final class NoteAuthoringMetadataParser {
    private NoteAuthoringMetadataParser() {
    }

    public static DomainContext parseDomainContextOrThrow(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        DomainContext domainContext = DomainContext.fromString(value);
        if (domainContext == null) {
            throw new InvalidDomainContextException();
        }
        return domainContext;
    }

    public static LearnerLevel parseLearnerLevelOrThrow(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LearnerLevel learnerLevel = LearnerLevel.fromString(value);
        if (learnerLevel == null) {
            throw new InvalidNoteLearnerLevelException();
        }
        return learnerLevel;
    }
}
