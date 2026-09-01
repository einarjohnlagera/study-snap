package com.studysnap.backend.service;

import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;

public final class CuratorAuthoringPredicate {
    private CuratorAuthoringPredicate() {
    }

    /**
     * Nobody curates during onboarding. The flow collects personal learning context and has no catalog
     * picker, so treating an ADMIN or TEACHER as a curator before onboarding completes makes those
     * authoring paths impossible to finish. Once onboarding is complete, the account is a full curator.
     */
    public static boolean isCurator(UserEntity user) {
        if (user.getOnboardingCompletedAt() == null) {
            return false;
        }
        return user.getRole() == UserRole.ADMIN || user.getProfileType() == ProfileType.TEACHER;
    }
}
