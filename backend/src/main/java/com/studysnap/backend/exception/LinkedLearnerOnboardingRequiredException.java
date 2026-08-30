package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller has not finished onboarding, so they may not act on a connection invitation.
 *
 * <p>⚠️ Covers BOTH paths as of v0.98.0 — invitation-link actions and email invitation acceptance — which
 * is why the message names neither. They form the identical relationship, so they answer to one bar.
 *
 * <p>⚠️ Deliberately NOT {@link ProfileSetupRequiredException}, whose message and
 * {@code COMPLETE_PROFILE_TYPE} action are specifically about choosing a profile type before
 * generating study content. That is a different remedy from "finish onboarding", and reusing it
 * would send the caller to the wrong place.
 *
 * <p>⚠️ It is thrown BEFORE any token lookup, which is what keeps it from becoming an oracle: the
 * caller learns something about their own account and nothing about whether the token exists. The
 * single not-found contract for unknown, revoked, expired and redeemed tokens (v0.90.0) is
 * unaffected.
 */
public class LinkedLearnerOnboardingRequiredException extends AppException {
    private static final String CODE = "ONBOARDING_REQUIRED";
    private static final String MESSAGE = "Finish setting up your account before connecting with someone.";
    private static final String DETAILS = "onboardingCompletedAt is required";
    private static final String ACTION = "COMPLETE_ONBOARDING";

    public LinkedLearnerOnboardingRequiredException() {
        super(CODE, MESSAGE, DETAILS, ACTION, HttpStatus.FORBIDDEN);
    }
}
