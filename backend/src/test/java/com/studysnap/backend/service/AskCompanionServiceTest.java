package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AskCompanionQuestionRequest;
import com.studysnap.backend.dto.AskCompanionSessionResponse;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.AskCompanionSessionEntity;
import com.studysnap.backend.entity.AskCompanionSessionStatus;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AskCompanionQuotaExhaustedException;
import com.studysnap.backend.exception.AskCompanionTurnLimitReachedException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.CompanionNotAvailableException;
import com.studysnap.backend.repository.AskCompanionSessionRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskCompanionServiceTest {
    private static final String USAGE_PERIOD_START = "2026-07-01T00:00:00Z";
    private static final String USAGE_PERIOD_END = "2026-08-01T00:00:00Z";
    private static final String COMMON_MISTAKES_QUESTION = "What should I avoid?";
    @Mock
    private AskCompanionSessionRepository sessionRepository;
    @Mock
    private NoteCollectionRepository collectionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AskCompanionLlmService llmService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;
    @Mock
    private AuthService authService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private AiRateLimitService aiRateLimitService;

    private StudySnapProperties properties;
    private AskCompanionService service;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        service = new AskCompanionService(
                sessionRepository,
                collectionRepository,
                userRepository,
                llmService,
                subscriptionService,
                featureGateService,
                properties,
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService
        );
    }

    @Test
    void startOrResumeCreatesOneSessionAndIncrementsQuotaOnce() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        stubEligibleStart(userId, collectionId, collection, 0);
        when(sessionRepository.save(any(AskCompanionSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AskCompanionSessionResponse response = service.startOrResume(collectionId, userId);

        assertThat(response.status()).isEqualTo(AskCompanionSessionStatus.ACTIVE);
        assertThat(response.turnCount()).isZero();
        assertThat(response.turnsRemaining()).isEqualTo(AskCompanionService.TURN_LIMIT);
        verify(featureGateService).checkFeatureAccess(PlanType.PLUS, Feature.ASK_COMPANION);
        verify(userUsageService).incrementAskCompanionSession(eq(userId), any());
    }

    @Test
    void startOrResumeReturnsExistingActiveSessionWithoutDoubleCounting() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        AskCompanionSessionEntity existing = buildSession(userId, collectionId, 2, AskCompanionSessionStatus.ACTIVE);
        when(collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.of(collection));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(sessionRepository.findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                userId,
                collectionId,
                AskCompanionSessionStatus.ACTIVE
        )).thenReturn(Optional.of(existing));
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(usage(4));

        AskCompanionSessionResponse response = service.startOrResume(collectionId, userId);

        assertThat(response.sessionId()).isEqualTo(existing.getId());
        assertThat(response.turnCount()).isEqualTo(2);
        verify(userUsageService, never()).incrementAskCompanionSession(any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startOrResumeRejectsTwentyFirstSessionWithoutPhantomIncrement() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        stubEligibleStart(userId, collectionId, collection, 20);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any()))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PLUS,
                        com.studysnap.backend.entity.BillingCycle.MONTHLY,
                        OffsetDateTime.parse(USAGE_PERIOD_START),
                        OffsetDateTime.parse(USAGE_PERIOD_END),
                        2026,
                        7
                ));

        assertThatThrownBy(() -> service.startOrResume(collectionId, userId))
                .isInstanceOf(AskCompanionQuotaExhaustedException.class);
        verify(sessionRepository, never()).save(any());
        verify(userUsageService, never()).incrementAskCompanionSession(any(), any());
    }

    @Test
    void askQuestionPersistsSixthTurnEndsSessionAndRejectsSeventh() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        AskCompanionSessionEntity session = buildSession(userId, collectionId, 5, AskCompanionSessionStatus.ACTIVE);
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        when(sessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(llmService.answer(eq(collection.getCompanion()), any(), eq(COMMON_MISTAKES_QUESTION)))
                .thenReturn("Avoid skipping retrieval practice.");
        when(sessionRepository.save(session)).thenReturn(session);
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(usage(3));

        AskCompanionSessionResponse response = service.askQuestion(
                session.getId(),
                userId,
                new AskCompanionQuestionRequest(COMMON_MISTAKES_QUESTION)
        );

        assertThat(response.turnCount()).isEqualTo(6);
        assertThat(response.status()).isEqualTo(AskCompanionSessionStatus.ENDED);
        assertThat(response.turns()).hasSize(6);
        verify(aiRateLimitService).assertAllowed(userId, PlanType.PLUS, "ask-companion");

        AskCompanionQuestionRequest seventhQuestion = new AskCompanionQuestionRequest("One more?");
        assertThatThrownBy(() -> service.askQuestion(session.getId(), userId, seventhQuestion))
                .isInstanceOf(AskCompanionTurnLimitReachedException.class);
    }

    @Test
    void startOrResumeRejectsEmptyCompanionWithDedicatedState() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(
                collectionId,
                userId,
                new CompanionContent(" ", null, null, null, List.of(), List.of())
        );
        when(collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.startOrResume(collectionId, userId))
                .isInstanceOf(CompanionNotAvailableException.class);
        verify(featureGateService, never()).checkFeatureAccess(any(PlanType.class), any(Feature.class));
    }

    @Test
    void startOrResumeReturnsNotFoundForNonOwnerBeforeCompanionChecks() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startOrResume(collectionId, userId))
                .isInstanceOf(CollectionNotFoundException.class);
        verify(sessionRepository, never()).findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                any(),
                any(),
                any()
        );
    }

    @Test
    void getActiveReturnsExistingSessionWithPersistedTurnsOnRefresh() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        AskCompanionSessionEntity existing = buildSession(userId, collectionId, 2, AskCompanionSessionStatus.ACTIVE);
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(sessionRepository.findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                userId,
                collectionId,
                AskCompanionSessionStatus.ACTIVE
        )).thenReturn(Optional.of(existing));
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(usage(4));

        Optional<AskCompanionSessionResponse> response = service.getActive(collectionId, userId);

        assertThat(response).isPresent();
        assertThat(response.get().sessionId()).isEqualTo(existing.getId());
        assertThat(response.get().turnCount()).isEqualTo(2);
        assertThat(response.get().turns()).hasSize(2);
        assertThat(response.get().turnsRemaining()).isEqualTo(AskCompanionService.TURN_LIMIT - 2);
        verify(sessionRepository, never()).save(any());
        verify(userUsageService, never()).incrementAskCompanionSession(any(), any());
    }

    @Test
    void getActiveReturnsEmptyWhenNoConversationExists() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        NoteCollectionEntity collection = buildCollection(collectionId, userId, renderableCompanion());
        when(collectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(sessionRepository.findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                userId,
                collectionId,
                AskCompanionSessionStatus.ACTIVE
        )).thenReturn(Optional.empty());

        Optional<AskCompanionSessionResponse> response = service.getActive(collectionId, userId);

        assertThat(response).isEmpty();
    }

    private void stubEligibleStart(
            UUID userId,
            UUID collectionId,
            NoteCollectionEntity collection,
            int usedThisMonth
    ) {
        when(collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.of(collection));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(sessionRepository.findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
                userId,
                collectionId,
                AskCompanionSessionStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new UserEntity()));
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(usage(usedThisMonth));
    }

    private NoteCollectionEntity buildCollection(
            UUID collectionId,
            UUID userId,
            CompanionContent companion
    ) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(userId);
        collection.setCompanion(companion);
        return collection;
    }

    private CompanionContent renderableCompanion() {
        return new CompanionContent(
                "This Review Set covers core backend concepts.",
                "Practice retrieval after each section.",
                "Avoid reading without practice.",
                null,
                List.of(),
                List.of()
        );
    }

    private AskCompanionSessionEntity buildSession(
            UUID userId,
            UUID collectionId,
            int turnCount,
            AskCompanionSessionStatus status
    ) {
        AskCompanionSessionEntity session = new AskCompanionSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setCollectionId(collectionId);
        session.setStatus(status);
        session.setTurnCount(turnCount);
        List<AskCompanionTurn> turns = new ArrayList<>();
        for (int index = 0; index < turnCount; index++) {
            turns.add(new AskCompanionTurn(
                    "Question " + index,
                    "Answer " + index,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
        }
        session.setTurns(turns);
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return session;
    }

    private UserUsageService.MonthlyUsage usage(int askCompanionUsed) {
        OffsetDateTime start = OffsetDateTime.parse(USAGE_PERIOD_START);
        OffsetDateTime end = OffsetDateTime.parse(USAGE_PERIOD_END);
        return new UserUsageService.MonthlyUsage(
                start,
                end,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                askCompanionUsed
        );
    }
}
