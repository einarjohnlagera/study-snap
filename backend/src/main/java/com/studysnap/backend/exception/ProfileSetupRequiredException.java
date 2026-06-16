package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ProfileSetupRequiredException extends AppException {
    private static final String CODE = "ONBOARDING_REQUIRED";
    private static final String MESSAGE = "Choose your profile type before creating or generating study content.";
    private static final String DETAILS = "profileType is required";
    private static final String ACTION = "COMPLETE_PROFILE_TYPE";

    public ProfileSetupRequiredException() {
        super(CODE, MESSAGE, DETAILS, ACTION, HttpStatus.FORBIDDEN);
    }
}
