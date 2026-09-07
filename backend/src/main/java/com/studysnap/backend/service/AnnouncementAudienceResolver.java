package com.studysnap.backend.service;

import com.studysnap.backend.entity.AnnouncementAudience;
import com.studysnap.backend.entity.AnnouncementEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.exception.InvalidAnnouncementRequestException;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves an announcement audience to the user ids that should receive it.
 *
 * <p>⚠️ EDITORIAL, NEVER AUTHORIZATION. This class decides who is likely to CARE about a message.
 * It grants nothing, revokes nothing and is never consulted for access — that stays with
 * {@code FeatureGateService} and each feature's own rules. ⚠️ It deliberately holds NO reference to
 * {@code FeatureGateService}, and a test asserts that, so "reuse the targeting for gating" cannot
 * happen by accident.
 *
 * <p>⚠️ THREE AUDIENCES, NO QUERY BUILDER. There is no SQL or segment-expression targeting, and later
 * system-defined segments wait for evidence.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementAudienceResolver {
    static final String AUDIENCE_VALUE_FIELD = "audienceValue";

    private static final Set<PlanType> PAID_PLANS = EnumSet.of(PlanType.PLUS, PlanType.PRO);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public List<UUID> resolve(AnnouncementEntity announcement) {
        return switch (announcement.getAudience()) {
            case EVERYONE -> userRepository.findAllUserIds();
            case PROFILE_TYPE -> userRepository.findActiveUserIdsByProfileType(
                    parseProfileType(announcement.getAudienceValue())
            );
            case PLAN_TYPE -> resolveByPlan(parsePlanType(announcement.getAudienceValue()));
        };
    }

    /**
     * ⚠️ THE PLAN LEG MIRRORS {@code SubscriptionService.resolvePlan}, INCLUDING ITS PRECEDENCE. A user
     * holding both an active PLUS and an active PRO subscription resolves to PRO there, so they are in
     * the PRO audience and NOT in the PLUS one; a naive "has a PLUS row" query would mail them twice
     * across two campaigns and contradict the plan the product is showing them.
     *
     * <p>FREE is the complement, matching {@code resolvePlan}'s own fallback: no paid subscription in
     * its active window means FREE.
     */
    private List<UUID> resolveByPlan(PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> activeUserIds = userRepository.findAllUserIds();

        if (planType == PlanType.FREE) {
            Set<UUID> paidUserIds = new HashSet<>(
                    subscriptionRepository.findUserIdsWithSubscriptionInActiveWindow(PAID_PLANS, now)
            );
            return activeUserIds.stream().filter(userId -> !paidUserIds.contains(userId)).toList();
        }

        Set<UUID> onPlan = new HashSet<>(
                subscriptionRepository.findUserIdsWithSubscriptionInActiveWindow(Set.of(planType), now)
        );
        if (planType == PlanType.PLUS) {
            onPlan.removeAll(subscriptionRepository.findUserIdsWithSubscriptionInActiveWindow(Set.of(PlanType.PRO), now));
        }
        return activeUserIds.stream().filter(onPlan::contains).toList();
    }

    /**
     * ⚠️ VALIDATED AND CANONICALISED AT WRITE TIME, not at publish time. An audience value that is only
     * checked during fan-out fails in front of an audience, half way through a send; checked here it
     * fails in the draft form, where the admin can fix it.
     *
     * @return the canonical enum name, or {@code null} for {@code EVERYONE} so a stray value can never
     *         sit unread on the row
     */
    public String normalizeAudienceValue(AnnouncementAudience audience, String rawAudienceValue) {
        return switch (audience) {
            case EVERYONE -> null;
            case PROFILE_TYPE -> parseProfileType(rawAudienceValue).name();
            case PLAN_TYPE -> parsePlanType(rawAudienceValue).name();
        };
    }

    private ProfileType parseProfileType(String audienceValue) {
        return parseEnum(ProfileType.class, audienceValue, AnnouncementAudience.PROFILE_TYPE);
    }

    private PlanType parsePlanType(String audienceValue) {
        return parseEnum(PlanType.class, audienceValue, AnnouncementAudience.PLAN_TYPE);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String audienceValue, AnnouncementAudience audience) {
        if (audienceValue == null || audienceValue.isBlank()) {
            throw new InvalidAnnouncementRequestException(
                    AUDIENCE_VALUE_FIELD,
                    "is required when audience is " + audience.name() + "."
            );
        }
        try {
            return Enum.valueOf(type, audienceValue.trim());
        } catch (IllegalArgumentException unknownValue) {
            throw new InvalidAnnouncementRequestException(
                    AUDIENCE_VALUE_FIELD,
                    "is not a known " + audience.name() + " value."
            );
        }
    }
}
