package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class QuickReviewFirstCompletedQuizIntegrationTest {
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private ActivityEventRepository activityEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private QuickReviewSessionService quickReviewSessionService;
    private QuickReviewAdaptivePracticeService adaptivePracticeService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists quick_review_sessions");
        jdbcTemplate.execute("drop table if exists user_activity_events");
        jdbcTemplate.execute("""
                create table quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid not null,
                    note_id uuid not null,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    verified_correct_answers integer,
                    score_percentage numeric(5,2),
                    retry_count integer,
                    duration_seconds integer,
                    confidence_level varchar(16),
                    session_metadata json,
                    session_state json,
                    quota_exempt boolean not null default false,
                    model_used varchar(64),
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("""
                create table user_activity_events (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid,
                    note_id uuid,
                    activity_type varchar(64) not null,
                    created_at timestamp with time zone not null
                )
                """);

        StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);
        StudySnapProperties properties = new StudySnapProperties();
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, properties);
        quickReviewSessionService = new QuickReviewSessionService(
                quickReviewSessionRepository,
                studyPackRepository,
                activityEventRepository,
                mock(ActivityTrackingService.class),
                mock(AnalyticsService.class),
                subscriptionService,
                featureGateService,
                mock(ConceptHealthService.class),
                mock(StudyPackQuizMasteryService.class)
        ,
                org.mockito.Mockito.mock(com.studysnap.backend.service.NoteShareService.class));
        adaptivePracticeService = new QuickReviewAdaptivePracticeService(
                studyPackRepository,
                quickReviewSessionRepository,
                mock(QuizGenerationService.class),
                mock(ActivityTrackingService.class),
                subscriptionService,
                featureGateService,
                properties,
                mock(UserUsageService.class),
                mock(AuthService.class),
                mock(AnalyticsService.class),
                mock(AiRateLimitService.class),
                mock(StudyPackGenerationContextResolver.class),
                mock(ConceptHealthService.class),
                mock(com.studysnap.backend.repository.NoteCollectionRepository.class),
                mock(com.studysnap.backend.repository.NoteCollectionItemRepository.class),
                new LongExamPlanSourceSampler()
        );
    }

    @Test
    void completeSession_returnsFirstQuizFlagOnlyBeforeARealCompletedQuizActivityExists() {
        UUID userId = UUID.randomUUID();
        QuickReviewSessionEntity firstSession = saveInProgressSession(userId);

        QuickReviewSessionResponse firstResponse = complete(firstSession, userId);

        assertThat(firstResponse.isFirstCompletedQuiz()).isTrue();

        UserActivityEventEntity completedQuickReview = new UserActivityEventEntity();
        completedQuickReview.setId(UUID.randomUUID());
        completedQuickReview.setUserId(userId);
        completedQuickReview.setStudyPackId(firstSession.getStudyPackId());
        completedQuickReview.setActivityType(ActivityType.COMPLETED_QUICK_REVIEW);
        completedQuickReview.setCreatedAt(OffsetDateTime.now());
        activityEventRepository.save(completedQuickReview);

        QuickReviewSessionEntity secondSession = saveInProgressSession(userId);
        QuickReviewSessionResponse secondResponse = complete(secondSession, userId);

        assertThat(secondResponse.isFirstCompletedQuiz()).isFalse();
    }

    @Test
    void completeSession_reportsSecondCompletedSessionOnlyForTheSecondOwnerWideCompletion() {
        UUID userId = UUID.randomUUID();

        assertThat(quickReviewSessionRepository.countByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).isZero();
        assertThat(quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).isFalse();

        QuickReviewSessionEntity firstSession = saveInProgressSession(userId);
        QuickReviewSessionResponse firstResponse = complete(firstSession, userId);

        assertThat(firstResponse.isFirstCompletedSessionEver()).isTrue();
        assertThat(firstResponse.isSecondCompletedSessionEver()).isFalse();
        assertThat(quickReviewSessionRepository.countByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).isOne();

        QuickReviewSessionEntity secondSession = saveInProgressSession(userId, QuickReviewSessionMode.ADAPTIVE);
        var secondResponse = adaptivePracticeService.completeAdaptiveSession(
                secondSession.getId().toString(),
                userId,
                0,
                1,
                60,
                null
        );

        assertThat(secondResponse.isFirstCompletedSessionEver()).isFalse();
        assertThat(secondResponse.isSecondCompletedSessionEver()).isTrue();

        QuickReviewSessionResponse thirdResponse = complete(saveInProgressSession(userId), userId);

        assertThat(thirdResponse.isFirstCompletedSessionEver()).isFalse();
        assertThat(thirdResponse.isSecondCompletedSessionEver()).isFalse();
    }

    private QuickReviewSessionResponse complete(QuickReviewSessionEntity session, UUID userId) {
        return quickReviewSessionService.completeSession(
                session.getId().toString(),
                userId,
                new QuickReviewSessionCompleteRequest(0, 1, 0, 60, null)
        );
    }

    private QuickReviewSessionEntity saveInProgressSession(UUID userId) {
        return saveInProgressSession(userId, QuickReviewSessionMode.QUICK_REVIEW);
    }

    private QuickReviewSessionEntity saveInProgressSession(UUID userId, QuickReviewSessionMode sessionMode) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(sessionMode);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        return quickReviewSessionRepository.save(session);
    }
}
