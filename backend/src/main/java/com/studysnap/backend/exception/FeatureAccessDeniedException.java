package com.studysnap.backend.exception;

import com.studysnap.backend.entity.Feature;
import org.springframework.http.HttpStatus;

public class FeatureAccessDeniedException extends AppException {
    public FeatureAccessDeniedException(Feature feature) {
        super(
                "PREMIUM_FEATURE_REQUIRED",
                feature.getAccessDeniedMessage(),
                "feature=" + feature.name(),
                HttpStatus.FORBIDDEN
        );
    }
}
