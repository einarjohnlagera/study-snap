package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeResponse(
        String id,
        String email,
        String pendingEmail,
        String firstName,
        String lastName,
        String displayName,
        String username,
        String bio,
        LearnerLevel learnerLevel,
        String courseProgram,
        String studyGoal,
        List<String> focusSubjects,
        String schoolName,
        boolean publicProfileVisible,
        String countryCode,
        ProfileType profileType,
        LocalDate examDate,
        List<String> reviewDays,
        boolean reviewCommitmentOutstanding,
        EngagementMode engagementMode,
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        boolean weeklySummaryRemindersEnabled,
        boolean dueConceptsDigestRemindersEnabled,
        boolean knowledgeImpactDigestRemindersEnabled,
        boolean marketingEmailsEnabled,
        boolean mobileTabBarEnabled,
        ThemePreference themePreference,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime productOnboardingCompletedAt,
        UUID primaryCollectionId,
        Integer studyDaysPerWeek,
        long studyPackCount,
        UserRole role,
        UserStatus status,
        PlanType planType,
        SubscriptionPlanStatusResponse subscription
) {
    public MeResponse(
            String id,
            String email,
            String pendingEmail,
            String firstName,
            String lastName,
            String displayName,
            String username,
            String bio,
            LearnerLevel learnerLevel,
            String courseProgram,
            String studyGoal,
            List<String> focusSubjects,
            String schoolName,
            boolean publicProfileVisible,
            String countryCode,
            ProfileType profileType,
            LocalDate examDate,
            EngagementMode engagementMode,
            boolean inactivityRemindersEnabled,
            boolean weakConceptRemindersEnabled,
            boolean weeklySummaryRemindersEnabled,
            boolean dueConceptsDigestRemindersEnabled,
            boolean knowledgeImpactDigestRemindersEnabled,
            boolean marketingEmailsEnabled,
            boolean mobileTabBarEnabled,
            ThemePreference themePreference,
            OffsetDateTime emailVerifiedAt,
            OffsetDateTime onboardingCompletedAt,
            OffsetDateTime productOnboardingCompletedAt,
            UUID primaryCollectionId,
            Integer studyDaysPerWeek,
            long studyPackCount,
            UserRole role,
            UserStatus status,
            PlanType planType,
            SubscriptionPlanStatusResponse subscription
    ) {
        this(
                id, email, pendingEmail, firstName, lastName, displayName, username, bio, learnerLevel,
                courseProgram, studyGoal, focusSubjects, schoolName, publicProfileVisible, countryCode,
                profileType, examDate, List.of(), true, engagementMode, inactivityRemindersEnabled,
                weakConceptRemindersEnabled, weeklySummaryRemindersEnabled, dueConceptsDigestRemindersEnabled,
                knowledgeImpactDigestRemindersEnabled, marketingEmailsEnabled, mobileTabBarEnabled,
                themePreference, emailVerifiedAt, onboardingCompletedAt, productOnboardingCompletedAt,
                primaryCollectionId, studyDaysPerWeek, studyPackCount, role, status, planType, subscription
        );
    }
}
