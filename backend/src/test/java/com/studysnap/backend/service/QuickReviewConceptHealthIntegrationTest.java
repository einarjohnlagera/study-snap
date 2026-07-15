package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class QuickReviewConceptHealthIntegrationTest {
    private static final String MASTERED_CONCEPT = "Cardiac Output";
    private static final String WEAK_CONCEPT = "Renal Clearance";

    @Autowired
    private ConceptHealthRepository conceptHealthRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private QuickReviewSessionRepository quickReviewSessionRepository;
    private StudyPackRepository studyPackRepository;
    private ActivityEventRepository activityEventRepository;
    private SubscriptionService subscriptionService;
    private QuickReviewSessionService quickReviewSessionService;
    private ConceptHealthService conceptHealthService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                create table if not exists concept_health (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid not null,
                    concept varchar(500) not null,
                    last_correct_at timestamp with time zone,
                    last_incorrect_at timestamp with time zone,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    constraint uq_concept_health_user_study_pack_concept
                        unique (user_id, study_pack_id, concept)
                )
                """);
        jdbcTemplate.execute("delete from concept_health");

        quickReviewSessionRepository = mock(QuickReviewSessionRepository.class);
        studyPackRepository = mock(StudyPackRepository.class);
        activityEventRepository = mock(ActivityEventRepository.class);
        ActivityTrackingService activityTrackingService = mock(ActivityTrackingService.class);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        subscriptionService = mock(SubscriptionService.class);
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, new StudySnapProperties());
        conceptHealthService = new ConceptHealthService(
                conceptHealthRepository,
                studyPackRepository,
                subscriptionService,
                featureGateService
        );
        quickReviewSessionService = new QuickReviewSessionService(
                quickReviewSessionRepository,
                studyPackRepository,
                activityEventRepository,
                activityTrackingService,
                analyticsService,
                subscriptionService,
                featureGateService,
                conceptHealthService
        );
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.PRO);
    }

    @Test
    void completeQuickReviewSession_recordsConceptHealthFromPersistedSelections() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.QUICK_REVIEW
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(1, 2, 0, 60, null)
        );

        List<String> dueConcepts = conceptHealthService.getDueConcepts(
                userId,
                studyPackId,
                studyPack.getKeyConcepts(),
                session.getCompletedAt()
        );

        assertThat(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId))
                .extracting(entry -> entry.getConcept())
                .containsExactlyInAnyOrder(MASTERED_CONCEPT, WEAK_CONCEPT);
        assertThat(dueConcepts).containsExactly(WEAK_CONCEPT);
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID userId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Physiology");
        studyPack.setSummary("Summary");
        studyPack.setKeyConcepts(List.of(MASTERED_CONCEPT, WEAK_CONCEPT));
        studyPack.setQuiz(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", MASTERED_CONCEPT, "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "B", WEAK_CONCEPT, "Explanation")
        ));
        return studyPack;
    }

    private QuickReviewSessionEntity buildInProgressSession(UUID sessionId, UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(2);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        session.setSessionState(Map.of(
                "selectedChoices",
                Map.of("0", "A", "1", "C")
        ));
        return session;
    }
}
