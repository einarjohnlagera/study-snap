package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.exception.FeatureAccessDeniedException;
import com.studysnap.backend.exception.LongExamNotAvailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureGateService {
    private final SubscriptionService subscriptionService;
    private final StudySnapProperties properties;

    public void checkFeatureAccess(UUID userId, Feature feature) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        checkFeatureAccess(planType, feature);
    }

    public void checkFeatureAccess(PlanType planType, Feature feature) {
        if (hasFeatureAccess(planType, feature)) {
            return;
        }

        if (feature == Feature.LONG_EXAM_SESSION) {
            throw new LongExamNotAvailableException();
        }

        throw new FeatureAccessDeniedException(feature);
    }

    public boolean hasFeatureAccess(UUID userId, Feature feature) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        return hasFeatureAccess(planType, feature);
    }

    public boolean hasFeatureAccess(PlanType planType, Feature feature) {
        if (planType == null || feature == null) {
            return false;
        }
        return switch (feature) {
            case ADAPTIVE_QUIZ -> properties.getPricing().isAdaptivePracticeAvailable(planType);
            case DIFFICULTY_SELECTION -> properties.getPricing().isDifficultySelectionAvailable(planType);
            case LONG_EXAM_SESSION -> properties.getPricing().isLongExamAvailable(planType);
            case INTERVIEW_PRACTICE -> properties.getPricing().isInterviewPracticeAvailable(planType);
            case WEAK_CONCEPT_DETECTION -> true;
        };
    }

    public Integer resolveMonthlyDocxExportLimit(PlanType planType, ProfileType profileType) {
        return properties.getPricing().resolveMonthlyDocxExportLimit(planType, profileType);
    }

    public Integer resolveMonthlyPdfExportLimit(PlanType planType) {
        return properties.getPricing().resolveMonthlyPdfExportLimit(planType);
    }
}
