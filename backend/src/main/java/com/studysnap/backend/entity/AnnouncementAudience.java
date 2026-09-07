package com.studysnap.backend.entity;

/**
 * ⚠️ TARGETING IS EDITORIAL, NEVER AUTHORIZATION. These values select who is likely to CARE about a
 * message. They must never become feature gating or entitlement — that stays with
 * {@code FeatureGateService} and each feature's own rules.
 *
 * <p>There is deliberately no SQL or query-builder audience. Later system-defined segments are
 * derivable but wait for evidence.
 */
public enum AnnouncementAudience {
    /** Every ACTIVE account. */
    EVERYONE,
    /** Every ACTIVE account whose {@code profile_type} equals the audience value. */
    PROFILE_TYPE,
    /** Every ACTIVE account whose RESOLVED plan equals the audience value. */
    PLAN_TYPE
}
