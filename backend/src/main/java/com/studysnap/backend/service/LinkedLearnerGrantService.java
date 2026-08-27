package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerActivityGrantResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
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
        authService.requireEmailVerified(callerUserId);
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        if (relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
            throw new LinkedLearnerNotFoundException();
        }
        UUID toUserId = resolveOtherParty(relationship, callerUserId);

        int affectedRows;
        AnalyticsEventType eventType;
        if (granted) {
            affectedRows = grantRepository.insertLiveIfAbsent(
                    UUID.randomUUID(), relationshipId, callerUserId, toUserId,
                    LinkedLearnerGrantScope.ACTIVITY.name(), OffsetDateTime.now(ZoneOffset.UTC));
            eventType = AnalyticsEventType.CONNECTION_ACTIVITY_SHARED;
        } else {
            affectedRows = grantRepository.revokeLive(
                    relationshipId, callerUserId, LinkedLearnerGrantScope.ACTIVITY,
                    OffsetDateTime.now(ZoneOffset.UTC));
            eventType = AnalyticsEventType.CONNECTION_ACTIVITY_SHARE_REVOKED;
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
