package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AskCompanionQuestionRequest;
import com.studysnap.backend.dto.AskCompanionSessionResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.AskCompanionSessionEntity;
import com.studysnap.backend.entity.AskCompanionSessionStatus;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AskCompanionQuotaExhaustedException;
import com.studysnap.backend.exception.AskCompanionSessionEndedException;
import com.studysnap.backend.exception.AskCompanionSessionNotFoundException;
import com.studysnap.backend.exception.AskCompanionTurnLimitReachedException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.CompanionNotAvailableException;
import com.studysnap.backend.exception.InvalidAskCompanionRequestException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.AskCompanionSessionRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.CompanionContentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AskCompanionService {
    public static final int TURN_LIMIT = 6;
    private static final String AI_RATE_LIMIT_SCOPE = "ask-companion";
    private static final String METADATA_SESSION_ID = "sessionId";
    private static final String METADATA_COLLECTION_ID = "collectionId";
    private static final String METADATA_TURN_COUNT = "turnCount";
    private static final String METADATA_MONTHLY_LIMIT = "monthlyLimit";
    private static final String METADATA_PERIOD_START = "periodStart";

    private final AskCompanionSessionRepository sessionRepository;
    private final NoteCollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final AskCompanionLlmService llmService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;

    @Transactional(noRollbackFor = AskCompanionQuotaExhaustedException.class)
    public AskCompanionSessionResponse startOrResume(UUID collectionId, UUID userId) {
        authService.requireEmailVerified(userId);
        NoteCollectionEntity collection = findOwnedCollectionForUpdate(collectionId, userId);
        assertCompanionAvailable(collection);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ASK_COMPANION);

        Optional<AskCompanionSessionEntity> activeSession = sessionRepository
                .findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        collectionId,
                        AskCompanionSessionStatus.ACTIVE
                );
        if (activeSession.isPresent()) {
            return toResponse(activeSession.get(), userId, planType);
        }

        userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertQuotaAvailable(userId, planType, collectionId, now);

        AskCompanionSessionEntity session = new AskCompanionSessionEntity();
        session.setId(UUID.randomUUID());
        session.setCollectionId(collectionId);
        session.setUserId(userId);
        session.setStatus(AskCompanionSessionStatus.ACTIVE);
        session.setTurnCount(0);
        session.setTurns(new ArrayList<>());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        AskCompanionSessionEntity saved = sessionRepository.save(session);
        userUsageService.incrementAskCompanionSession(userId, now);
        trackAnalytics(userId, AnalyticsEventType.ASK_COMPANION_STARTED, collectionId, Map.of(
                METADATA_SESSION_ID, saved.getId().toString(),
                METADATA_COLLECTION_ID, collectionId.toString()
        ));
        return toResponse(saved, userId, planType);
    }

    @Transactional(readOnly = true)
    public Optional<AskCompanionSessionResponse> getActive(UUID collectionId, UUID userId) {
        authService.requireEmailVerified(userId);
        NoteCollectionEntity collection = collectionRepository.findByIdAndOwnerUserId(collectionId, userId)
                .orElseThrow(CollectionNotFoundException::new);
        assertCompanionAvailable(collection);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ASK_COMPANION);
        return sessionRepository.findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        collectionId,
                        AskCompanionSessionStatus.ACTIVE
                )
                .map(session -> toResponse(session, userId, planType));
    }

    @Transactional
    public AskCompanionSessionResponse askQuestion(
            UUID sessionId,
            UUID userId,
            AskCompanionQuestionRequest request
    ) {
        authService.requireEmailVerified(userId);
        AskCompanionSessionEntity session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(AskCompanionSessionNotFoundException::new);
        int turnCount = session.getTurnCount() == null ? 0 : session.getTurnCount();
        if (turnCount >= TURN_LIMIT) {
            throw new AskCompanionTurnLimitReachedException();
        }
        if (session.getStatus() != AskCompanionSessionStatus.ACTIVE) {
            throw new AskCompanionSessionEndedException();
        }

        NoteCollectionEntity collection = collectionRepository
                .findByIdAndOwnerUserId(session.getCollectionId(), userId)
                .orElseThrow(AskCompanionSessionNotFoundException::new);
        assertCompanionAvailable(collection);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ASK_COMPANION);

        String question = normalizeQuestion(request);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
        List<AskCompanionTurn> existingTurns = session.getTurns() == null
                ? List.of()
                : List.copyOf(session.getTurns());
        String answer = llmService.answer(collection.getCompanion(), existingTurns, question);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AskCompanionTurn> updatedTurns = new ArrayList<>(existingTurns);
        updatedTurns.add(new AskCompanionTurn(question, answer, now));
        int updatedTurnCount = turnCount + 1;
        session.setTurns(updatedTurns);
        session.setTurnCount(updatedTurnCount);
        session.setUpdatedAt(now);
        if (updatedTurnCount >= TURN_LIMIT) {
            session.setStatus(AskCompanionSessionStatus.ENDED);
            session.setEndedAt(now);
        }
        AskCompanionSessionEntity saved = sessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.ASK_COMPANION_MESSAGE_SENT, session.getCollectionId(), Map.of(
                METADATA_SESSION_ID, sessionId.toString(),
                METADATA_COLLECTION_ID, session.getCollectionId().toString(),
                METADATA_TURN_COUNT, updatedTurnCount
        ));
        return toResponse(saved, userId, planType);
    }

    private NoteCollectionEntity findOwnedCollectionForUpdate(UUID collectionId, UUID userId) {
        return collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId)
                .orElseThrow(CollectionNotFoundException::new);
    }

    private void assertCompanionAvailable(NoteCollectionEntity collection) {
        if (collection.getParentCollectionId() != null
                || !CompanionContentUtils.hasRenderableContent(collection.getCompanion())) {
            throw new CompanionNotAvailableException();
        }
    }

    private String normalizeQuestion(AskCompanionQuestionRequest request) {
        String question = request == null ? null : request.question();
        if (question == null || question.isBlank()) {
            throw new InvalidAskCompanionRequestException("Question is required.");
        }
        String normalized = question.trim();
        if (normalized.length() > 1000) {
            throw new InvalidAskCompanionRequestException("Question must be 1000 characters or fewer.");
        }
        return normalized;
    }

    private void assertQuotaAvailable(UUID userId, PlanType planType, UUID collectionId, OffsetDateTime now) {
        int monthlyLimit = properties.getPricing().resolveMonthlyAskCompanionLimit(planType);
        int usedThisMonth = userUsageService.getMonthlyUsage(userId, now).askCompanionUsedThisMonth();
        if (monthlyLimit > 0 && usedThisMonth < monthlyLimit) {
            return;
        }
        BillingUsagePeriodService.UsagePeriod period = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        trackAnalytics(userId, AnalyticsEventType.ASK_COMPANION_QUOTA_EXHAUSTED, collectionId, Map.of(
                METADATA_MONTHLY_LIMIT, monthlyLimit,
                METADATA_PERIOD_START, period.periodStart().toString()
        ));
        throw new AskCompanionQuotaExhaustedException();
    }

    private AskCompanionSessionResponse toResponse(
            AskCompanionSessionEntity session,
            UUID userId,
            PlanType planType
    ) {
        UserUsageService.MonthlyUsage usage = userUsageService.getMonthlyUsage(
                userId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        int turnCount = session.getTurnCount() == null ? 0 : session.getTurnCount();
        return new AskCompanionSessionResponse(
                session.getId(),
                session.getCollectionId(),
                session.getStatus(),
                turnCount,
                TURN_LIMIT,
                Math.clamp(TURN_LIMIT - turnCount, 0, TURN_LIMIT),
                session.getTurns() == null ? List.of() : List.copyOf(session.getTurns()),
                usage.askCompanionUsedThisMonth(),
                properties.getPricing().resolveMonthlyAskCompanionLimit(planType),
                usage.periodEnd()
        );
    }

    private void trackAnalytics(
            UUID userId,
            AnalyticsEventType eventType,
            UUID collectionId,
            Map<String, Object> metadata
    ) {
        try {
            analyticsService.trackEvent(userId, eventType, collectionId, metadata);
        } catch (RuntimeException ignored) {
            // Analytics must never block Ask Companion.
        }
    }
}
