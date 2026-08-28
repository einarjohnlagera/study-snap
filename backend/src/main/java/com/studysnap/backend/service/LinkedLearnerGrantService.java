package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerActivityGrantResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerProgressGrantNotAllowedException;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinkedLearnerGrantService {
    private static final String ROLE_METADATA = "role";

    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerGrantRepository grantRepository;
    private final AuthService authService;
    private final AnalyticsService analyticsService;

    @Transactional
    public LinkedLearnerActivityGrantResponse setActivityGrant(
            UUID callerUserId,
            UUID relationshipId,
            boolean granted
    ) {
        return setGrant(callerUserId, relationshipId, LinkedLearnerGrantScope.ACTIVITY, granted);
    }

    @Transactional
    public LinkedLearnerActivityGrantResponse setProgressGrant(
            UUID callerUserId,
            UUID relationshipId,
            boolean granted
    ) {
        return setGrant(callerUserId, relationshipId, LinkedLearnerGrantScope.PROGRESS, granted);
    }

    private LinkedLearnerActivityGrantResponse setGrant(
            UUID callerUserId,
            UUID relationshipId,
            LinkedLearnerGrantScope scope,
            boolean granted
    ) {
        authService.requireEmailVerified(callerUserId);
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        // ⚠️ ASYMMETRIC BY DESIGN: granting requires ACCEPTED, WITHDRAWING never does.
        // Gating both on ACCEPTED locked a learner out of turning sharing OFF at exactly the moment it
        // matters most: a downward birth-year correction pauses the relationship to PENDING
        // (`pauseAcceptedForConsent`), the live grant row SURVIVES the pause, and re-acceptance makes
        // momentum readable again with no fresh act of sharing. Withdrawing consent must never require
        // the counterparty's cooperation or a status the learner does not control. Granting still
        // requires ACCEPTED, because widening access on a paused relationship is the thing the pause
        // exists to prevent.
        if (granted && relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
            throw new LinkedLearnerNotFoundException();
        }
        // Membership is required on BOTH branches — a non-party may not touch the row either way.
        UUID toUserId = resolveOtherParty(relationship, callerUserId);
        if (granted
                && scope == LinkedLearnerGrantScope.PROGRESS
                && !callerUserId.equals(relationship.getLearnerUserId())) {
            throw new LinkedLearnerProgressGrantNotAllowedException();
        }

        int affectedRows;
        AnalyticsEventType eventType;
        if (granted) {
            affectedRows = grantRepository.insertLiveIfAbsent(
                    UUID.randomUUID(), relationshipId, callerUserId, toUserId,
                    scope.name(), OffsetDateTime.now(ZoneOffset.UTC));
            if (affectedRows == 0 && grantRepository
                    .findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                            relationshipId, callerUserId, scope)
                    .filter(existing -> toUserId.equals(existing.getToUserId()))
                    .isEmpty()) {
                // Zero is ambiguous after the ACCEPTED predicate: it means either idempotent success
                // or that the relationship stopped being accepted after the initial read.
                throw new LinkedLearnerNotFoundException();
            }
            eventType = sharedEvent(scope);
        } else {
            affectedRows = grantRepository.revokeLive(
                    relationshipId, callerUserId, scope,
                    OffsetDateTime.now(ZoneOffset.UTC));
            eventType = revokedEvent(scope);
        }
        if (affectedRows > 0) {
            trackAnalytics(
                    callerUserId,
                    eventType,
                    relationshipId,
                    Map.of(ROLE_METADATA, resolveCallerRole(relationship, callerUserId).name())
            );
        }
        return new LinkedLearnerActivityGrantResponse(granted);
    }

    private AnalyticsEventType sharedEvent(LinkedLearnerGrantScope scope) {
        return scope == LinkedLearnerGrantScope.ACTIVITY
                ? AnalyticsEventType.CONNECTION_ACTIVITY_SHARED
                : AnalyticsEventType.CONNECTION_PROGRESS_SHARED;
    }

    private AnalyticsEventType revokedEvent(LinkedLearnerGrantScope scope) {
        return scope == LinkedLearnerGrantScope.ACTIVITY
                ? AnalyticsEventType.CONNECTION_ACTIVITY_SHARE_REVOKED
                : AnalyticsEventType.CONNECTION_PROGRESS_SHARE_REVOKED;
    }

    private void trackAnalytics(
            UUID callerUserId,
            AnalyticsEventType eventType,
            UUID relationshipId,
            Map<String, Object> metadata
    ) {
        try {
            analyticsService.trackEvent(callerUserId, eventType, relationshipId, metadata);
        } catch (RuntimeException analyticsFault) {
            // Analytics must never roll back a privacy decision that already changed the grant row.
            // ⚠️ Defence in depth: AnalyticsService.trackEvent already swallows and logs internally, so
            // this cannot fire today. It logs rather than ignoring precisely because it cannot fire — a
            // silent catch here would mean a future throwing trackEvent drops grant events with no trace,
            // and a funnel that goes quiet without saying so is worse than one that was never built.
            log.warn(
                    "action=track_grant_analytics outcome=failed userId={} eventType={} relationshipId={}",
                    callerUserId,
                    eventType,
                    relationshipId,
                    analyticsFault
            );
        }
    }

    private LinkedLearnerSide resolveCallerRole(
            LinkedLearnerRelationshipEntity relationship,
            UUID callerUserId
    ) {
        return callerUserId.equals(relationship.getSupporterUserId())
                ? LinkedLearnerSide.SUPPORTER
                : LinkedLearnerSide.LEARNER;
    }

    private UUID resolveOtherParty(LinkedLearnerRelationshipEntity relationship, UUID callerUserId) {
        if (callerUserId.equals(relationship.getSupporterUserId())) {
            return relationship.getLearnerUserId();
        }
        if (callerUserId.equals(relationship.getLearnerUserId())) {
            return relationship.getSupporterUserId();
        }
        throw new LinkedLearnerNotFoundException();
    }
}
