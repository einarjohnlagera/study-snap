package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingGuardService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void assertProfileComplete(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        // Gate only the legacy "completed onboarding but null profileType" cohort. Users still
        // mid-onboarding (onboardingCompletedAt == null) persist profileType at the final step, and
        // copy-on-signup runs before onboarding completes; both must remain exempt so the activation
        // funnel is never blocked.
        if (user.getProfileType() == null && user.getOnboardingCompletedAt() != null) {
            throw new ProfileSetupRequiredException();
        }
    }
}
