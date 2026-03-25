package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

        throw new AppException(
                "PREMIUM_FEATURE_REQUIRED",
                feature.getAccessDeniedMessage(),
                "feature=" + feature.name(),
                HttpStatus.FORBIDDEN
        );
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
            case WEAK_CONCEPT_DETECTION -> true;
        };
    }
}
