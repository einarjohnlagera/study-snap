package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
@RequiredArgsConstructor
public class GuardianConsentPolicy {
    private final StudySnapProperties properties;

    public boolean requiresGuardianConsent(int birthYear) {
        int youngestPossibleAge = Year.now().getValue() - birthYear - 1;
        return youngestPossibleAge <= properties.getLinkedLearners().getGuardianConsentMaxAge();
    }
}
