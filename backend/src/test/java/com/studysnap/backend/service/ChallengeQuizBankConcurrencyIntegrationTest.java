package com.studysnap.backend.service;

import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.LongExamProgressRequest;
import com.studysnap.backend.dto.LongExamCompleteRequest;
import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.dto.QuickReviewAnswerRequest;
import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import com.studysnap.backend.exception.QuickReviewSessionStaleException;
import com.studysnap.backend.exception.MonthlyChallengeQuizLimitReachedException;
import com.studysnap.backend.exception.PreparedChallengeExpiredException;
import com.studysnap.backend.exception.InterviewPracticeAnswerAlreadyRecordedException;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankOwnerProjection;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.studysnap.backend.service.event.ChallengeQuizReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the real service proxies and PostgreSQL transactions, including commit-time flushes. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '2s'"
})
@Testcontainers
class ChallengeQuizBankConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired ChallengeQuizService challengeQuizService;
    @Autowired QuickReviewSessionService quickReviewSessionService;
    @Autowired InterviewPracticeService interviewPracticeService;
    @Autowired LongExamService longExamService;
    @Autowired QuickReviewAdaptivePracticeService adaptivePracticeService;
    @Autowired com.studysnap.backend.repository.QuickReviewSessionRepository sessionRepository;
    @Autowired ChallengeQuizQuestionBankService bankService;
    @Autowired StudySnapProperties properties;
    @Autowired OfficialChallengeQuizTemplateService templateService;
    @Autowired AdminStudyPackTransactionHelper adminStudyPackTransactionHelper;
    @Autowired StudyPackRepository studyPackRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ChallengeQuizQuestionBankRepository bankRepository;
    @Autowired GenerationRecoveryRowWriter generationRecoveryRowWriter;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockitoBean AuthService authService;
    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean QuizGenerationService quizGenerationService;
    @MockitoBean StudyPackGenerationContextResolver generationContextResolver;
    @MockitoBean UserUsageService userUsageService;
    @MockitoBean BillingUsagePeriodService billingUsagePeriodService;
    @MockitoBean AnalyticsService analyticsService;
    @MockitoBean ActivityTrackingService activityTrackingService;
    @MockitoBean ConceptHealthService conceptHealthService;
    @MockitoBean StudyPackQuizMasteryService studyPackQuizMasteryService;
    @MockitoBean LlmStudyPackService llmStudyPackService;
    @MockitoBean StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;

    @BeforeEach
    void configureCollaborators() {
        when(subscriptionService.resolvePlan(any())).thenReturn(PlanType.FREE);
        when(generationContextResolver.resolveForStudyPack(any(), any()))
                .thenReturn(new StudyPackGenerationContext(null, null, null, List.of()));
        when(userUsageService.getMonthlyUsage(any(), any())).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(billingUsagePeriodService.resolveUsagePeriod(any(), any())).thenAnswer(invocation ->
                new BillingUsagePeriodService.UsagePeriod(PlanType.FREE, null,
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), 2026, 9));
    }

    @Test
    void duplicateInsertSkipsAtTheStatementAndPreservesTheFirstRowAcrossLevels() {
        Fixture fixture = seed(false);
        UUID firstId = jdbc.queryForObject("select id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId);
        QuizItem duplicate = question("Bank question");
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestions(fixture.userId, fixture.packId, null,
                    LearnerLevel.COLLEGE, 0L, List.of(duplicate));
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(1);
        assertThat(bankRepository.findById(firstId).orElseThrow().getOriginSessionId()).isEqualTo(fixture.sessionId);
        assertThat(bankRepository.findById(firstId).orElseThrow().getClaimedSessionId()).isEqualTo(fixture.sessionId);

        jdbc.update("update challenge_quiz_question_bank set learner_level = null where id = ?", firstId);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestions(fixture.userId, fixture.packId, null,
                    null, 0L, List.of(duplicate));
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(1);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestions(fixture.userId, fixture.packId, null,
                    LearnerLevel.SENIOR_HIGH, 0L, List.of(duplicate));
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(2);
    }

    @Test
    void failedPreparationQuestionsRemainClaimableByTheNextSession() {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        UUID failedSessionId = fixture.sessionId;
        jdbc.update("update quick_review_sessions set status = 'FAILED' where id = ?", failedSessionId);
        UUID nextSessionId = insertGeneratingSession(fixture);
        List<QuizItem> generated = List.of(question("Saved after failure"));
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestionsUnclaimed(fixture.userId, fixture.packId, failedSessionId,
                    LearnerLevel.COLLEGE, 0L, generated);
            return null;
        });

        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isNull();
        List<QuizItem> reclaimed = new TransactionTemplate(transactionManager).execute(status ->
                bankService.claimEligibleQuestions(fixture.userId, fixture.packId, LearnerLevel.COLLEGE,
                        nextSessionId, Set.of(), 1));

        assertThat(reclaimed).containsExactlyElementsOf(generated);
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isEqualTo(nextSessionId);
    }

    @Test
    void completionRecordsOutcomesAndReleasesClaimsAcrossTwoPacks() {
        Fixture fixture = seed(false);
        UUID secondPackId = addSecondPackWithQuestion(fixture, "Second bank question");
        // 5b will make cross-pack claims reachable from a multi-note start. Today this direct
        // claim uses the production claim method and the same session id to test the release seam.
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                fixture.userId, secondPackId, LearnerLevel.COLLEGE, fixture.sessionId, Set.of(), 1)))
                .extracting(QuizItem::question).containsExactly("Second bank question");
        jdbc.update("""
                update quick_review_sessions set total_questions = 2,
                session_state = jsonb_set(session_state, '{quiz}',
                    '[{"question":"Bank question","choices":["A","B"],"correctIndex":0,"keyConcept":"Concept"},
                      {"question":"Second bank question","choices":["A","B"],"correctIndex":0,"keyConcept":"Concept"}]'::jsonb)
                    || '{"selectedChoices":{"0":0}}'::jsonb
                where id = ?
                """, fixture.sessionId);

        challengeQuizService.completeSession(fixture.sessionId.toString(), fixture.userId,
                new ChallengeQuizCompleteRequest(1, 2, 10));

        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
        assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where study_pack_id = ?",
                String.class, fixture.packId)).isEqualTo("CORRECT");
        assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where study_pack_id = ?",
                String.class, secondPackId)).isEqualTo("UNANSWERED");
    }

    @Test
    @Timeout(15)
    void completionReleasesUnservedGenerateMoreClaimWithoutErasingMissedOutcome() throws Exception {
        Fixture fixture = seed(false);
        UUID missed = insertBankQuestion(fixture, "Previously missed", "INCORRECT",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        var job = captureMoreJob();
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    modelEntered.countDown();
                    assertThat(releaseModel.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(moreQuestions());
                });

        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> running = executor.submit(job.get());
            assertThat(modelEntered.await(8, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                    UUID.class, missed)).isEqualTo(fixture.sessionId);

            challengeQuizService.completeSession(fixture.sessionId.toString(), fixture.userId,
                    new ChallengeQuizCompleteRequest(0, 1, 10));
            assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                    UUID.class, missed)).isNull();
            assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where id = ?",
                    String.class, missed)).isEqualTo("INCORRECT");
            assertThat(bankService.countEligibleIncorrectQuestions(fixture.userId, fixture.packId,
                    LearnerLevel.COLLEGE)).isEqualTo(1);

            releaseModel.countDown();
            running.get(8, TimeUnit.SECONDS);
        } finally { releaseModel.countDown(); }
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(1);
        assertThat(saved.getSessionState().get("timeLimitSeconds")).isEqualTo(900);
        assertThat(QuizSessionStateUtils.extractMoreFailed(saved.getSessionState())).isFalse();
        assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where id = ?",
                String.class, missed)).isEqualTo("INCORRECT");
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and question_key like 'fresh %' and claimed_session_id is null
                """, Integer.class, fixture.packId)).isEqualTo(4);
    }

    @Test
    void preparedDiscardReleasesClaimsAcrossTwoPacksAndSecondReleaseIsNoOp() {
        Fixture fixture = seed(false);
        UUID secondPackId = addSecondPackWithQuestion(fixture, "Second bank question");
        // 5b will first cause this cross-pack state; claim through the real bank path now.
        new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                fixture.userId, secondPackId, LearnerLevel.COLLEGE, fixture.sessionId, Set.of(), 1));
        setPrepared(fixture, "challenge", OffsetDateTime.now(ZoneOffset.UTC));

        challengeQuizService.forfeitSession(fixture.sessionId.toString(), fixture.userId);
        List<String> afterDiscard = jdbc.queryForList("""
                select to_jsonb(bank)::text from challenge_quiz_question_bank bank
                where study_pack_id in (?, ?) order by id
                """, String.class, fixture.packId, secondPackId);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(fixture.userId, fixture.sessionId);
            return null;
        });

        assertThat(jdbc.queryForList("""
                select to_jsonb(bank)::text from challenge_quiz_question_bank bank
                where study_pack_id in (?, ?) order by id
                """, String.class, fixture.packId, secondPackId)).containsExactlyElementsOf(afterDiscard);
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?",
                String.class, fixture.sessionId)).isEqualTo("FORFEITED");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
    }

    @Test
    void releaseOfSessionALeavesSessionBClaimsOnTheSamePackUntouched() {
        Fixture fixture = seed(false);
        UUID otherPackId = addSecondPackWithQuestion(fixture, "Primary bank question");
        UUID otherSessionId = UUID.randomUUID();
        jdbc.update("""
                insert into quick_review_sessions
                    (id, user_id, study_pack_id, note_id, session_mode, status,
                     current_question_index, current_round, total_questions, session_state,
                     quota_exempt, created_at)
                select ?, session.user_id, pack.id, pack.note_id, 'CHALLENGE', 'IN_PROGRESS',
                       0, 'INITIAL', 1,
                       jsonb_set(session.session_state, '{quiz}',
                           '[{"question":"Other bank question","choices":["A","B"],"correctIndex":0,"keyConcept":"Concept"}]'::jsonb),
                       true, now()
                from quick_review_sessions session
                join study_packs pack on pack.id = ?
                where session.id = ?
                """, otherSessionId, otherPackId, fixture.sessionId);
        UUID otherQuestionId = insertBankQuestion(fixture, "Other bank question", "UNANSWERED",
                OffsetDateTime.now(ZoneOffset.UTC));
        // Item 5b's multi-note Challenge shape claims pack P from a session anchored on pack Q.
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                fixture.userId, fixture.packId, LearnerLevel.COLLEGE, otherSessionId, Set.of(), 1)))
                .extracting(QuizItem::question).containsExactly("Other bank question");

        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(fixture.userId, fixture.sessionId);
            return null;
        });

        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                UUID.class, otherQuestionId)).isEqualTo(otherSessionId);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();

        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(fixture.userId, otherSessionId);
            return null;
        });
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                UUID.class, otherQuestionId)).isNull();
    }

    @Test
    @Timeout(15)
    void concurrentCompletionAndDiscardLeaveOneTerminalSessionAndNoClaims() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("""
                update quick_review_sessions
                set session_state = jsonb_set(session_state, '{selectedChoices}', '{"0":0}'::jsonb)
                where id = ?
                """, fixture.sessionId);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Exception> completion = executor.submit(() -> {
                start.await();
                try {
                    challengeQuizService.completeSession(fixture.sessionId.toString(), fixture.userId,
                            new ChallengeQuizCompleteRequest(0, 1, 10));
                    return null;
                } catch (Exception exception) {
                    return exception;
                }
            });
            Future<Exception> discard = executor.submit(() -> {
                start.await();
                try {
                    challengeQuizService.forfeitSession(fixture.sessionId.toString(), fixture.userId);
                    return null;
                } catch (Exception exception) {
                    return exception;
                }
            });
            start.countDown();
            completion.get(10, TimeUnit.SECONDS);
            discard.get(10, TimeUnit.SECONDS);
        }

        String status = jdbc.queryForObject("select status from quick_review_sessions where id = ?",
                String.class, fixture.sessionId);
        assertThat(status).isIn("COMPLETED", "FORFEITED");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
        assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where study_pack_id = ?",
                String.class, fixture.packId)).isEqualTo("COMPLETED".equals(status) ? "CORRECT" : "UNANSWERED");
    }

    @Test
    void unsetCutoffExcludesStoredLearnerStatementPairsFromClaimCountAndRedo() {
        Fixture fixture = seed(false);
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                update challenge_quiz_question_bank
                set claimed_session_id = null, last_known_outcome = 'INCORRECT', generated_at = ?
                where study_pack_id = ?
                """, generatedAt.minusDays(3), fixture.packId);
        UUID excludedId = insertBankQuestion(fixture, "Statement 1: Alpha. Statement 2: Beta?",
                "INCORRECT", generatedAt.minusDays(30));
        insertBankQuestion(fixture, "Ordinary missed two", "INCORRECT", generatedAt.minusDays(2));
        String before = bankRowJson(excludedId);

        assertThat(bankService.countEligibleIncorrectQuestions(fixture.userId, fixture.packId,
                LearnerLevel.COLLEGE)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> bankService.claimIncorrectQuestions(fixture.userId, fixture.packId,
                        LearnerLevel.COLLEGE, fixture.sessionId, 5, 3)))
                .isInstanceOf(com.studysnap.backend.exception.NotEnoughMissedChallengeQuestionsException.class);
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                fixture.userId, fixture.packId, LearnerLevel.COLLEGE, fixture.sessionId, Set.of(), 2)))
                .extracting(QuizItem::question).containsExactly("Bank question", "Ordinary missed two");
        assertThat(bankRowJson(excludedId)).isEqualTo(before);
    }

    @Test
    void cutoffExcludesOnlyEarlierLearnerStatementPairs() {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        UUID older = insertBankQuestion(fixture, "Statement 1: Old. Statement 2: Old?",
                "INCORRECT", cutoff.minusDays(1));
        UUID newer = insertBankQuestion(fixture, "Statement 1: New. Statement 2: New?",
                "INCORRECT", cutoff.plusDays(1));
        String olderBefore = bankRowJson(older);
        properties.getChallengeQuiz().setStatementPairLegacyCutoff(cutoff);
        try {
            assertThat(bankService.countEligibleIncorrectQuestions(fixture.userId, fixture.packId,
                    LearnerLevel.COLLEGE)).isEqualTo(1);
            assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimIncorrectQuestions(
                    fixture.userId, fixture.packId, LearnerLevel.COLLEGE, fixture.sessionId, 1, 1)))
                    .extracting(QuizItem::question).containsExactly("Statement 1: New. Statement 2: New?");
            assertThat(bankRowJson(older)).isEqualTo(olderBefore);
            new TransactionTemplate(transactionManager).execute(status -> {
                bankService.releaseClaims(fixture.userId, fixture.sessionId);
                return null;
            });
            assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                    fixture.userId, fixture.packId, LearnerLevel.COLLEGE, fixture.sessionId, Set.of(), 2)))
                    .extracting(QuizItem::question).containsExactly("Statement 1: New. Statement 2: New?");
            assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                    UUID.class, newer)).isEqualTo(fixture.sessionId);
            assertThat(bankRowJson(older)).isEqualTo(olderBefore);
        } finally {
            properties.getChallengeQuiz().setStatementPairLegacyCutoff(null);
        }
    }

    @Test
    void oldOfficialTemplateCopyStaysEligibleForOrdinaryRedoCountAndRedoClaim() {
        Fixture official = seed(false);
        Fixture adopter = seed(false);
        jdbc.update("update users set role = 'ADMIN' where id = ?", official.userId);
        UUID officialNoteId = jdbc.queryForObject("select note_id from study_packs where id = ?",
                UUID.class, official.packId);
        jdbc.update("update notes set visibility = 'PUBLIC' where id = ?", officialNoteId);
        UUID adopterNoteId = jdbc.queryForObject("select note_id from study_packs where id = ?",
                UUID.class, adopter.packId);
        jdbc.update("update notes set copied_from_note_id = ? where id = ?", officialNoteId, adopterNoteId);
        String text = "Statement 1: Template. Statement 2: Source?";
        insertBankQuestion(official, text, "INCORRECT", OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        UUID copyId = insertBankQuestion(adopter, text, "INCORRECT",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));

        assertThat(bankService.countEligibleIncorrectQuestions(adopter.userId, adopter.packId,
                LearnerLevel.COLLEGE)).isEqualTo(1);
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimIncorrectQuestions(
                adopter.userId, adopter.packId, LearnerLevel.COLLEGE, adopter.sessionId, 1, 1)))
                .extracting(QuizItem::question).containsExactly(text);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(adopter.userId, adopter.sessionId);
            return null;
        });
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                adopter.userId, adopter.packId, LearnerLevel.COLLEGE, adopter.sessionId,
                Set.of("bank question"), 1)))
                .extracting(QuizItem::question).containsExactly(text);
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                UUID.class, copyId)).isEqualTo(adopter.sessionId);
        // The owner resolves to its own Official rows, so it has the same exemption on every path.
        assertThat(bankService.countEligibleIncorrectQuestions(official.userId, official.packId,
                LearnerLevel.COLLEGE)).isEqualTo(1);
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimIncorrectQuestions(
                official.userId, official.packId, LearnerLevel.COLLEGE, official.sessionId, 1, 1)))
                .extracting(QuizItem::question).containsExactly(text);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(official.userId, official.sessionId);
            return null;
        });
        assertThat((List<QuizItem>) new TransactionTemplate(transactionManager).execute(status -> bankService.claimEligibleQuestions(
                official.userId, official.packId, LearnerLevel.COLLEGE, official.sessionId,
                Set.of("bank question"), 1)))
                .extracting(QuizItem::question).containsExactly(text);
    }

    @Test
    void generateMoreSkipsExcludedStatementPairAndUsesLaterRows() {
        Fixture fixture = seed(false);
        UUID excludedId = insertBankQuestion(fixture, "Statement 1: Legacy. Statement 2: Legacy?",
                "UNANSWERED", OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        String before = bankRowJson(excludedId);
        for (int index = 0; index < 5; index++) {
            insertBankQuestion(fixture, "Later bank " + index, "UNANSWERED",
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).plusMinutes(index));
        }

        var job = captureMoreJob();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();

        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isEqualTo(6);
        assertThat(bankRowJson(excludedId)).isEqualTo(before);
    }

    @Test
    @Timeout(15)
    void concurrentDuplicateInsertWaitsForCommitThenSkipsWithoutPoisoningEitherTransaction() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(insertBankRow(fixture, "race key", "COLLEGE")).isEqualTo(1);
                inserted.countDown();
                try { assertThat(release.await(8, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); }
                return null;
            }));
            assertThat(inserted.await(8, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                secondStarted.countDown();
                assertThat(insertBankRow(fixture, "race key", "COLLEGE")).isZero();
                return null;
            }));
            assertThat(secondStarted.await(8, TimeUnit.SECONDS)).isTrue();
            long beforeCommit = System.nanoTime();
            Thread.sleep(150);
            assertThat(second.isDone()).isFalse();
            release.countDown();
            first.get(8, TimeUnit.SECONDS);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeCommit)).isLessThan(1800);
            second.get(8, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    void concurrentPreparedStartsWithOneAttemptLeftChargeExactlyOnce() throws Exception {
        Fixture first = seed(false);
        UUID secondNoteId = UUID.randomUUID();
        UUID secondPackId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        String readyState = "{\"mode\":\"challenge\",\"difficulty\":\"medium\","
                + "\"quiz\":[{\"question\":\"Ready question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}],"
                + "\"timeLimitSeconds\":90,\"preparedNotStarted\":true,\"preparedGenerationStamp\":0,"
                + "\"preparedAtEpochSeconds\":" + OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() + "}";
        jdbc.update("update quick_review_sessions set session_state = ?::jsonb, quota_exempt = true where id = ?",
                readyState, first.sessionId);
        jdbc.update("insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type, created_at, updated_at)"
                        + " values (?, ?, 'Second ready', 'body', 'PRIVATE', ARRAY[]::text[], 'STUDENT', now(), now())",
                secondNoteId, first.userId);
        jdbc.update("insert into study_packs (id, owner_user_id, note_id, input_type, title, summary, key_concepts, quiz, model_used, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'TEXT', 'Second ready', 'summary', '[]'::jsonb, '[]'::jsonb, 'test', 'DONE', now(), now())",
                secondPackId, first.userId, secondNoteId);
        jdbc.update("insert into quick_review_sessions (id, user_id, study_pack_id, note_id, session_mode, status, current_question_index, current_round, total_questions, session_state, quota_exempt, created_at)"
                        + " values (?, ?, ?, ?, 'CHALLENGE', 'IN_PROGRESS', 0, 'INITIAL', 1, ?::jsonb, true, now())",
                secondSessionId, first.userId, secondPackId, secondNoteId, readyState);
        java.util.concurrent.atomic.AtomicInteger used = new java.util.concurrent.atomic.AtomicInteger(19);
        when(userUsageService.getMonthlyUsage(eq(first.userId), any())).thenAnswer(invocation ->
                new UserUsageService.MonthlyUsage(null, null, 0, used.get(), 0, 0, 0, 0, 0, 0, 0, 0, 0));
        CountDownLatch firstAtCharge = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            firstAtCharge.countDown();
            assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            used.incrementAndGet();
            return null;
        }).when(userUsageService).incrementChallengeQuizGeneration(any(), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ChallengeQuizStartResponse> firstStart = executor.submit(
                    () -> challengeQuizService.startQuiz(first.sessionId.toString(), first.userId));
            assertThat(firstAtCharge.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ChallengeQuizStartResponse> secondStart = executor.submit(
                    () -> challengeQuizService.startQuiz(secondSessionId.toString(), first.userId));
            Thread.sleep(100);
            assertThat(secondStart.isDone()).isFalse();
            releaseFirst.countDown();
            assertThat(firstStart.get(5, TimeUnit.SECONDS).preparedNotStarted()).isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> secondStart.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(MonthlyChallengeQuizLimitReachedException.class);
        } finally {
            releaseFirst.countDown();
        }
        assertThat(used).hasValue(20);
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?",
                Boolean.class, secondSessionId)).isTrue();
        assertThat(jdbc.queryForObject("select session_state ->> 'preparedNotStarted' from quick_review_sessions where id = ?",
                String.class, secondSessionId)).isEqualTo("true");
    }

    @Test
    void stalePreparedStartCommitsForfeitAndClaimReleaseDespiteNotInProgressError() {
        Fixture fixture = seed(false);
        String readyState = "{\"mode\":\"challenge\",\"difficulty\":\"medium\","
                + "\"quiz\":[{\"question\":\"Bank question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}],"
                + "\"timeLimitSeconds\":90,\"preparedNotStarted\":true,\"preparedGenerationStamp\":0,"
                + "\"preparedAtEpochSeconds\":" + OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() + "}";
        jdbc.update("update quick_review_sessions set session_state = ?::jsonb, quota_exempt = true where id = ?",
                readyState, fixture.sessionId);
        jdbc.update("update study_packs set generation_stamp = 1 where id = ?", fixture.packId);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> challengeQuizService.startQuiz(fixture.sessionId.toString(), fixture.userId))
                .isInstanceOf(com.studysnap.backend.exception.ChallengeQuizSessionNotInProgressException.class);

        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo("FORFEITED");
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?", Boolean.class,
                fixture.sessionId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
    }

    @Test
    void expiredPreparedStartCommitsForfeitAndClaimReleaseDespiteExpiryError() {
        Fixture fixture = seed(false);
        String readyState = "{\"mode\":\"challenge\",\"difficulty\":\"medium\","
                + "\"quiz\":[{\"question\":\"Bank question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}],"
                + "\"timeLimitSeconds\":90,\"preparedNotStarted\":true,\"preparedGenerationStamp\":0,"
                + "\"preparedAtEpochSeconds\":" + OffsetDateTime.now(ZoneOffset.UTC).minusDays(8).toEpochSecond() + "}";
        jdbc.update("update quick_review_sessions set session_state = ?::jsonb, quota_exempt = true where id = ?",
                readyState, fixture.sessionId);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> challengeQuizService.startQuiz(fixture.sessionId.toString(), fixture.userId))
                .isInstanceOf(PreparedChallengeExpiredException.class);

        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo("FORFEITED");
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?", Boolean.class,
                fixture.sessionId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
    }

    @Test
    void expiredPreparationRowWriterCommitsForfeitAndSkipsStartedYoungAndBoardRows() {
        Fixture expired = seed(false);
        Fixture started = seed(false);
        Fixture young = seed(false);
        Fixture board = seed(false);
        setPrepared(expired, "challenge", OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        setPrepared(young, "challenge", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        setPrepared(board, "board_exam", OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        jdbc.update("update quick_review_sessions set created_at = now() - interval '8 days', quota_exempt = true where id = ?",
                started.sessionId);
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);

        assertThat(generationRecoveryRowWriter.recoverExpiredPreparedChallengeSession(expired.sessionId, cutoff)).isPresent();
        assertThat(generationRecoveryRowWriter.recoverExpiredPreparedChallengeSession(started.sessionId, cutoff)).isEmpty();
        assertThat(generationRecoveryRowWriter.recoverExpiredPreparedChallengeSession(young.sessionId, cutoff)).isEmpty();
        assertThat(generationRecoveryRowWriter.recoverExpiredPreparedChallengeSession(board.sessionId, cutoff)).isEmpty();

        assertRecovered(expired, "FORFEITED");
        assertUntouched(started, "IN_PROGRESS");
        assertUntouched(young, "IN_PROGRESS");
        assertUntouched(board, "IN_PROGRESS");
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    void expiredPreparationCandidatesUsePreparationTimeForFilteringAndPaging() {
        OffsetDateTime cutoff = OffsetDateTime.parse("1980-01-10T00:00:00Z");
        Fixture youngPreparation = seed(false);
        Fixture expired = seed(false);
        setPrepared(youngPreparation, "challenge", cutoff.plusDays(1));
        setPrepared(expired, "challenge", cutoff.minusDays(2));
        jdbc.update("update quick_review_sessions set created_at = ? where id = ?",
                cutoff.minusDays(9), youngPreparation.sessionId);
        jdbc.update("update quick_review_sessions set created_at = ? where id = ?",
                cutoff.minusDays(8), expired.sessionId);

        // Mutant: created_at-only filtering lets the young preparation fill this one-row page.
        assertThat(sessionRepository.findExpiredPreparedChallengeCandidateIds(cutoff, PageRequest.of(0, 1)))
                .containsExactly(expired.sessionId);

        Fixture missingStamp = seed(false);
        Fixture malformedStamp = seed(false);
        setPrepared(missingStamp, "challenge", cutoff.minusDays(6));
        setPrepared(malformedStamp, "challenge", cutoff.minusDays(5));
        jdbc.update("update quick_review_sessions set session_state = session_state - 'preparedAtEpochSeconds' "
                + "where id = ?", missingStamp.sessionId);
        jdbc.update("update quick_review_sessions set session_state = "
                + "jsonb_set(session_state, '{preparedAtEpochSeconds}', '\"abc\"'::jsonb) where id = ?",
                malformedStamp.sessionId);

        // Missing and malformed stamps use creation time; ordering must use preparation time too.
        assertThat(sessionRepository.findExpiredPreparedChallengeCandidateIds(cutoff, PageRequest.of(0, 3)))
                .containsExactly(missingStamp.sessionId, malformedStamp.sessionId, expired.sessionId);
    }

    @Test
    void stuckGenerationRowWriterCommitsFailureAndSkipsStartedYoungAndBoardRows() {
        Fixture stuck = seed(false);
        Fixture started = seed(false);
        Fixture young = seed(false);
        Fixture board = seed(false);
        setGenerating(stuck, "challenge", true);
        setGenerating(young, "challenge", false);
        setGenerating(board, "board_exam", true);
        jdbc.update("update quick_review_sessions set created_at = now() - interval '1 hour', quota_exempt = true where id = ?",
                started.sessionId);
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(20);

        assertThat(generationRecoveryRowWriter.recoverStuckChallengeGeneration(stuck.sessionId, cutoff)).isPresent();
        assertThat(generationRecoveryRowWriter.recoverStuckChallengeGeneration(started.sessionId, cutoff)).isEmpty();
        assertThat(generationRecoveryRowWriter.recoverStuckChallengeGeneration(young.sessionId, cutoff)).isEmpty();
        assertThat(generationRecoveryRowWriter.recoverStuckChallengeGeneration(board.sessionId, cutoff)).isEmpty();

        assertRecovered(stuck, "FAILED");
        assertUntouched(started, "IN_PROGRESS");
        assertUntouched(young, "GENERATING");
        assertUntouched(board, "GENERATING");
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    @Timeout(15)
    void expirySweepRacingStartQuizCannotStartOrChargeExpiredPreparation() throws Exception {
        Fixture fixture = seed(false);
        setPrepared(fixture, "challenge", OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        CountDownLatch sweepLockedPack = new CountDownLatch(1);
        CountDownLatch releaseSweep = new CountDownLatch(1);
        CountDownLatch startInvoked = new CountDownLatch(1);
        MethodInterceptor pauseSweepAfterPackLock = invocation -> {
            Object result = invocation.proceed();
            if (Thread.currentThread().getName().startsWith("expiry-sweep")
                    && invocation.getMethod().getName().equals("findByIdAndOwnerUserIdForUpdate")) {
                sweepLockedPack.countDown();
                assertThat(releaseSweep.await(8, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        };
        ((Advised) studyPackRepository).addAdvice(0, pauseSweepAfterPackLock);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> sweep = executor.submit(() -> {
                Thread.currentThread().setName("expiry-sweep-row-writer");
                return generationRecoveryRowWriter.recoverExpiredPreparedChallengeSession(
                        fixture.sessionId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(7));
            });
            assertThat(sweepLockedPack.await(8, TimeUnit.SECONDS)).isTrue();
            Future<?> start = executor.submit(() -> {
                startInvoked.countDown();
                return challengeQuizService.startQuiz(fixture.sessionId.toString(), fixture.userId);
            });
            assertThat(startInvoked.await(8, TimeUnit.SECONDS)).isTrue();
            releaseSweep.countDown();
            sweep.get(8, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> start.get(8, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.studysnap.backend.exception.PreparedChallengeExpiredException.class);
        } finally {
            releaseSweep.countDown();
            ((Advised) studyPackRepository).removeAdvice(pauseSweepAfterPackLock);
        }
        assertRecovered(fixture, "FORFEITED");
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    @Timeout(15)
    void stuckSweepRacingPreparationFinishLeavesGeneratedQuestionsUnclaimed() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        CountDownLatch inGeneration = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenAnswer(invocation -> {
            inGeneration.countDown();
            assertThat(releaseGeneration.await(8, TimeUnit.SECONDS)).isTrue();
            return GeneratedChallengeQuizContent.withoutUsage(java.util.stream.IntStream.range(0, 12)
                    .mapToObj(index -> question("Recovered generation " + index)).toList());
        });
        ChallengeQuizStartResponse preparing = challengeQuizService.startSession(
                fixture.packId.toString(), fixture.userId, null);
        UUID preparingId = UUID.fromString(preparing.sessionId());
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> finish = executor.submit(() -> runDispatchedChallengeJob());
            assertThat(inGeneration.await(8, TimeUnit.SECONDS)).isTrue();
            assertThat(generationRecoveryRowWriter.recoverStuckChallengeGeneration(
                    preparingId, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1))).isPresent();
            releaseGeneration.countDown();
            finish.get(8, TimeUnit.SECONDS);
        } finally {
            releaseGeneration.countDown();
        }
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                preparingId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select session_state ->> 'preparedNotStarted' from quick_review_sessions where id = ?",
                String.class, preparingId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ?",
                Integer.class, preparingId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, preparingId)).isZero();
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?",
                Boolean.class, preparingId)).isTrue();
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    void discardThenQuotaFailureRollsBackForfeitClaimsAndAbandonedEvent() {
        Fixture fixture = seed(false);
        setPrepared(fixture, "challenge", OffsetDateTime.now(ZoneOffset.UTC));
        when(userUsageService.getMonthlyUsage(eq(fixture.userId), any())).thenReturn(
                new UserUsageService.MonthlyUsage(null, null, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        AtomicInteger committedAbandonments = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(1) == com.studysnap.backend.entity.AnalyticsEventType.CHALLENGE_QUIZ_PREPARATION_ABANDONED) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { committedAbandonments.incrementAndGet(); }
                });
            }
            return null;
        }).when(analyticsService).trackEvent(any(), any(), any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> challengeQuizService.startSession(
                fixture.packId.toString(), fixture.userId,
                new ChallengeQuizStartRequest("board_exam", null, null, fixture.sessionId.toString())))
                .isInstanceOf(MonthlyChallengeQuizLimitReachedException.class);

        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?",
                Boolean.class, fixture.sessionId)).isTrue();
        verify(analyticsService).trackEvent(eq(fixture.userId),
                eq(com.studysnap.backend.entity.AnalyticsEventType.CHALLENGE_QUIZ_PREPARATION_ABANDONED),
                eq(fixture.packId), org.mockito.ArgumentMatchers.argThat(metadata ->
                        "DISCARDED".equals(metadata.get("reason"))));
        assertThat(committedAbandonments).hasValue(0);
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    private void setPrepared(Fixture fixture, String mode, OffsetDateTime preparedAt) {
        String state = "{\"mode\":\"" + mode + "\",\"difficulty\":\"medium\","
                + "\"quiz\":[{\"question\":\"Bank question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}],"
                + "\"timeLimitSeconds\":90,\"preparedNotStarted\":true,\"preparedGenerationStamp\":0,"
                + "\"preparedAtEpochSeconds\":" + preparedAt.toEpochSecond() + "}";
        jdbc.update("update quick_review_sessions set session_state = ?::jsonb, quota_exempt = true, "
                        + "created_at = ? where id = ?", state, preparedAt, fixture.sessionId);
    }

    private void setGenerating(Fixture fixture, String mode, boolean old) {
        jdbc.update("update quick_review_sessions set status = 'GENERATING', session_state = ?::jsonb, "
                        + "quota_exempt = true, created_at = ? where id = ?",
                "{\"mode\":\"" + mode + "\"}",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(old ? 30 : 1), fixture.sessionId);
    }

    private void assertRecovered(Fixture fixture, String status) {
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo(status);
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?", Boolean.class,
                fixture.sessionId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isZero();
    }

    private void assertUntouched(Fixture fixture, String status) {
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo(status);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, fixture.sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?", Boolean.class,
                fixture.sessionId)).isTrue();
    }

    private int insertBankRow(Fixture fixture, String key, String level) {
        return bankRepository.insertIfAbsent(UUID.randomUUID(), fixture.userId, fixture.packId, 0L,
                null, key, "{\"question\":\"Race key\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0}",
                level, "UNANSWERED", null, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void nativeJsonInsertRoundTripsEveryQuizItemShapeThroughTheEntity() {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        List<QuizItem> items = List.of(
                QuizItem.fromStoredComponents("Multiple choice", List.of("A. B. Smith", "D.C. generator"), 0,
                        "concept", "explanation", null, null, "MCQ", "working", null,
                        null, "key", List.of("answer"), List.of(List.of("group")), "source"),
                QuizItem.fromStoredComponents("Multi select", List.of("first", "second"), 0,
                        "concept", "explanation", null, "MULTI_SELECT", "MULTI_SELECT", "working",
                        List.of(0, 1), null, "key", List.of("answer"), List.of(List.of("group")), "source"),
                QuizItem.fromStoredComponents("Identification", List.of(), null, "concept", "explanation",
                        null, "IDENTIFICATION", "IDENTIFICATION", "working", null, null,
                        "key", List.of("one", "two"), List.of(List.of("group")), "source"),
                QuizItem.fromStoredComponents("Enumeration", List.of(), null, "concept", "explanation",
                        null, "ENUMERATION", "ENUMERATION", "working", null, null,
                        "key", List.of("one"), List.of(List.of("first", "second")), "source"),
                QuizItem.fromStoredComponents("Matching group", List.of("left", "right"), 0,
                        "concept", "explanation", null, null, "MATCHING", "working", null,
                        "group one", "key", List.of("answer"), List.of(List.of("group")), "source"));
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestions(fixture.userId, fixture.packId, null,
                    LearnerLevel.COLLEGE, 0L, items);
            return null;
        });
        List<QuizItem> reloaded = bankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(
                fixture.userId, fixture.packId).stream().map(ChallengeQuizQuestionBankEntity::getQuestion).toList();
        assertThat(reloaded).containsExactlyInAnyOrderElementsOf(items);
    }

    @Test
    void quickReviewQuizStampRejectsEveryStaleWriteAndMastery() {
        Fixture fixture = seed(false);
        jdbc.update("update study_packs set quiz = '[{\"question\":\"Q\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0,\"keyConcept\":\"Concept\"}]'::jsonb where id = ?", fixture.packId);
        jdbc.update("update quick_review_sessions set session_mode = 'QUICK_REVIEW', quiz_stamp_at_creation = 0 where id = ?", fixture.sessionId);
        jdbc.update("update study_packs set quiz_stamp = quiz_stamp + 1 where id = ?", fixture.packId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quickReviewSessionService.answerQuestion(
                fixture.sessionId.toString(), fixture.userId, new QuickReviewAnswerRequest(0, 0, 0, null)))
                .isInstanceOf(QuickReviewSessionStaleException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quickReviewSessionService.updateSessionProgress(
                fixture.sessionId.toString(), fixture.userId,
                new QuickReviewSessionProgressRequest(0, QuickReviewRound.INITIAL, 0, null)))
                .isInstanceOf(QuickReviewSessionStaleException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quickReviewSessionService.completeSession(
                fixture.sessionId.toString(), fixture.userId,
                new QuickReviewSessionCompleteRequest(1, 1, 0, 1, null)))
                .isInstanceOf(QuickReviewSessionStaleException.class);

        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now(), verified_correct_answers = 1 where id = ?", fixture.sessionId);
        UUID noteId = jdbc.queryForObject("select note_id from study_packs where id = ?", UUID.class, fixture.packId);
        assertThat(sessionRepository.findQuizMasteredAt(fixture.userId, fixture.packId, 1, noteId)).isNull();
        jdbc.update("update quick_review_sessions set quiz_stamp_at_creation = null where id = ?", fixture.sessionId);
        assertThat(sessionRepository.findQuizMasteredAt(fixture.userId, fixture.packId, 1, noteId)).isNotNull();
        jdbc.update("update quick_review_sessions set status = 'IN_PROGRESS', completed_at = null where id = ?", fixture.sessionId);
        jdbc.update("update notes set generation_enqueued_at = now() + interval '1 hour' where id = ?", noteId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quickReviewSessionService.answerQuestion(
                fixture.sessionId.toString(), fixture.userId, new QuickReviewAnswerRequest(0, 0, 0, null)))
                .isInstanceOf(QuickReviewSessionStaleException.class);
    }

    @Test
    @Timeout(15)
    void interviewAnswersMergeDifferentIndexesWithoutHoldingTheSessionLockDuringCritique() throws Exception {
        Fixture fixture = seed(false);
        new TransactionTemplate(transactionManager).execute(status -> {
            var session = sessionRepository.findById(fixture.sessionId).orElseThrow();
            session.setSessionMode(com.studysnap.backend.entity.QuickReviewSessionMode.ADAPTIVE);
            session.setTotalQuestions(2);
            session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                    List.of(question("First"), question("Second")), "INTERVIEW", 120));
            sessionRepository.save(session);
            return null;
        });
        CountDownLatch bothInLlm = new CountDownLatch(2);
        CountDownLatch releaseLlm = new CountDownLatch(1);
        when(quizGenerationService.generateInterviewCritique(any(), anyInt())).thenAnswer(invocation -> {
            bothInLlm.countDown();
            assertThat(releaseLlm.await(8, TimeUnit.SECONDS)).isTrue();
            return new InterviewPracticeCritique("WORKABLE", "Stored critique", "Follow up");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> interviewPracticeService.answerQuestion(fixture.sessionId, fixture.userId,
                    new InterviewPracticeAnswerRequest(0, "A", 10)));
            var second = executor.submit(() -> interviewPracticeService.answerQuestion(fixture.sessionId, fixture.userId,
                    new InterviewPracticeAnswerRequest(1, "B", 12)));
            assertThat(bothInLlm.await(8, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from quick_review_sessions where id = ? for update nowait",
                        UUID.class, fixture.sessionId);
                return null;
            });
            releaseLlm.countDown();
            first.get(8, TimeUnit.SECONDS);
            second.get(8, TimeUnit.SECONDS);
        } finally {
            releaseLlm.countDown();
        }
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        var quiz = QuizSessionStateUtils.extractQuiz(saved.getSessionState());
        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(saved.getSessionState(), quiz))
                .containsEntry(0, 0).containsEntry(1, 1);
        assertThat(QuizSessionStateUtils.extractInterviewFeedback(saved.getSessionState(), 0)).isPresent();
        assertThat(QuizSessionStateUtils.extractInterviewFeedback(saved.getSessionState(), 1)).isPresent();
        assertThat(saved.getCurrentQuestionIndex()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> interviewPracticeService.answerQuestion(
                fixture.sessionId, fixture.userId, new InterviewPracticeAnswerRequest(0, "B", 15)))
                .isInstanceOf(InterviewPracticeAnswerAlreadyRecordedException.class);
    }

    /**
     * The test above calls answerQuestion() directly from bare executor threads, so each phase gets its
     * own fresh EntityManager — it cannot see a bug that only exists when one EntityManager spans both
     * phases of a single request, which is what actually happens in production: this app runs with
     * spring.jpa.open-in-view at its Spring Boot default (true; see application.yaml's Tomcat-thread
     * comment for where that is documented), so Spring's OpenEntityManagerInViewInterceptor binds ONE
     * EntityManager to the whole HTTP request/thread. This test reproduces that binding manually — the
     * same mechanism the interceptor itself uses — around each worker thread's call.
     */
    @Test
    @Timeout(15)
    void interviewAnswersMergeDifferentIndexesUnderASharedRequestScopedEntityManager() throws Exception {
        Fixture fixture = seed(false);
        new TransactionTemplate(transactionManager).execute(status -> {
            var session = sessionRepository.findById(fixture.sessionId).orElseThrow();
            session.setSessionMode(com.studysnap.backend.entity.QuickReviewSessionMode.ADAPTIVE);
            session.setTotalQuestions(2);
            session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                    List.of(question("First"), question("Second")), "INTERVIEW", 120));
            sessionRepository.save(session);
            return null;
        });
        CountDownLatch bothInLlm = new CountDownLatch(2);
        CountDownLatch releaseLlm = new CountDownLatch(1);
        when(quizGenerationService.generateInterviewCritique(any(), anyInt())).thenAnswer(invocation -> {
            bothInLlm.countDown();
            assertThat(releaseLlm.await(8, TimeUnit.SECONDS)).isTrue();
            return new InterviewPracticeCritique("WORKABLE", "Stored critique", "Follow up");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> answerUnderBoundEntityManager(fixture, 0, "A", 10));
            var second = executor.submit(() -> answerUnderBoundEntityManager(fixture, 1, "B", 12));
            assertThat(bothInLlm.await(8, TimeUnit.SECONDS)).isTrue();
            releaseLlm.countDown();
            first.get(8, TimeUnit.SECONDS);
            second.get(8, TimeUnit.SECONDS);
        } finally {
            releaseLlm.countDown();
        }
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        var quiz = QuizSessionStateUtils.extractQuiz(saved.getSessionState());
        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(saved.getSessionState(), quiz))
                .containsEntry(0, 0).containsEntry(1, 1);
        assertThat(QuizSessionStateUtils.extractInterviewFeedback(saved.getSessionState(), 0)).isPresent();
        assertThat(QuizSessionStateUtils.extractInterviewFeedback(saved.getSessionState(), 1)).isPresent();
    }

    private Object answerUnderBoundEntityManager(Fixture fixture, int questionIndex, String choice, int timeSpentSeconds) {
        jakarta.persistence.EntityManager em = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(em));
        try {
            return interviewPracticeService.answerQuestion(fixture.sessionId, fixture.userId,
                    new InterviewPracticeAnswerRequest(questionIndex, choice, timeSpentSeconds));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
    }

    @Test
    @Timeout(15)
    void challengeProgressWaitsForCompletionAndCannotResurrectSession() throws Exception {
        Fixture fixture = seed(false);
        assertTerminalAfterWaitingWriter(fixture, () -> challengeQuizService.updateSessionProgress(
                fixture.sessionId.toString(), fixture.userId, new ChallengeQuizProgressRequest(0, java.util.Map.of())));
    }

    @Test
    @Timeout(15)
    void quickReviewForfeitWaitsForCompletionAndCannotResurrectSession() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'QUICK_REVIEW' where id = ?", fixture.sessionId);
        assertTerminalAfterWaitingWriter(fixture, () -> quickReviewSessionService.forfeitSession(
                fixture.sessionId.toString(), fixture.userId));
    }

    @Test
    @Timeout(15)
    void longExamProgressWaitsForCompletionAndCannotResurrectSession() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'LONG_EXAM' where id = ?", fixture.sessionId);
        assertTerminalAfterWaitingWriter(fixture, () -> longExamService.saveProgress(
                fixture.sessionId, fixture.userId, new LongExamProgressRequest(0, 0)));
    }

    @Test
    @Timeout(15)
    void adaptiveCompletionWaitsForCompletionAndCannotResurrectSession() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'ADAPTIVE' where id = ?", fixture.sessionId);
        assertTerminalAfterWaitingWriter(fixture, () -> adaptivePracticeService.completeAdaptiveSession(
                fixture.sessionId.toString(), fixture.userId, 0, 1, 1, List.of()));
    }

    @Test
    @Timeout(15)
    void interviewCompletionWaitsForCompletionAndCannotResurrectSession() throws Exception {
        Fixture fixture = seed(false);
        new TransactionTemplate(transactionManager).execute(status -> {
            var session = sessionRepository.findById(fixture.sessionId).orElseThrow();
            session.setSessionMode(com.studysnap.backend.entity.QuickReviewSessionMode.ADAPTIVE);
            session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                    List.of(question("Interview")), "INTERVIEW", 120));
            return null;
        });
        assertTerminalAfterWaitingWriter(fixture, () -> interviewPracticeService.completeSession(
                fixture.sessionId, fixture.userId));
    }

    @Test
    @Timeout(30)
    void remainingChallengeQuickReviewAndInterviewWritersWaitForTerminalState() throws Exception {
        Fixture challenge = seed(false);
        assertTerminalAfterWaitingWriter(challenge, () -> challengeQuizService.forfeitSession(
                challenge.sessionId.toString(), challenge.userId));

        Fixture reviewComplete = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'QUICK_REVIEW' where id = ?", reviewComplete.sessionId);
        assertTerminalAfterWaitingWriter(reviewComplete, () -> quickReviewSessionService.completeSession(
                reviewComplete.sessionId.toString(), reviewComplete.userId,
                new QuickReviewSessionCompleteRequest(0, 1, 0, 1, null)));

        Fixture reviewConfidence = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'QUICK_REVIEW' where id = ?", reviewConfidence.sessionId);
        assertTerminalAfterWaitingWriter(reviewConfidence, () -> quickReviewSessionService.saveConfidenceLevel(
                reviewConfidence.sessionId.toString(), reviewConfidence.userId, QuickReviewConfidenceLevel.HIGH));

        Fixture interview = seed(false);
        new TransactionTemplate(transactionManager).execute(status -> {
            var session = sessionRepository.findById(interview.sessionId).orElseThrow();
            session.setSessionMode(com.studysnap.backend.entity.QuickReviewSessionMode.ADAPTIVE);
            session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                    List.of(question("Interview")), "INTERVIEW", 120));
            return null;
        });
        assertTerminalAfterWaitingWriter(interview, () -> interviewPracticeService.forfeitSession(
                interview.sessionId, interview.userId));
    }

    @Test
    @Timeout(30)
    void remainingLongExamWritersWaitForTerminalState() throws Exception {
        Fixture pause = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'LONG_EXAM' where id = ?", pause.sessionId);
        assertTerminalAfterWaitingWriter(pause, () -> longExamService.pauseSession(pause.sessionId, pause.userId));

        Fixture resume = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'LONG_EXAM', status = 'PAUSED' where id = ?", resume.sessionId);
        assertTerminalAfterWaitingWriter(resume, () -> longExamService.resumeSession(resume.sessionId, resume.userId));

        Fixture complete = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'LONG_EXAM' where id = ?", complete.sessionId);
        assertTerminalAfterWaitingWriter(complete, () -> longExamService.completeSession(
                complete.sessionId, complete.userId, new LongExamCompleteRequest(1)));

        Fixture forfeit = seed(false);
        jdbc.update("update quick_review_sessions set session_mode = 'LONG_EXAM' where id = ?", forfeit.sessionId);
        assertTerminalAfterWaitingWriter(forfeit, () -> longExamService.forfeitSession(
                forfeit.sessionId, forfeit.userId));
    }

    private void assertTerminalAfterWaitingWriter(Fixture fixture, Runnable writer) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var completion = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from quick_review_sessions where id = ? for update",
                        UUID.class, fixture.sessionId);
                held.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                jdbc.update("update quick_review_sessions set status = 'COMPLETED', correct_answers = 1, score_percentage = 100, completed_at = now() where id = ?", fixture.sessionId);
                return null;
            }));
            assertThat(held.await(8, TimeUnit.SECONDS)).isTrue();
            var attempted = executor.submit(() -> {
                try { writer.run(); } catch (RuntimeException expected) { return; }
            });
            boolean waiting = false;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
            while (System.nanoTime() < deadline && !waiting) {
                waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists (select 1 from pg_stat_activity
                        where wait_event_type = 'Lock' and query ilike '%quick_review_sessions%')
                        """, Boolean.class));
                if (!waiting) Thread.sleep(10);
            }
            assertThat(waiting).isTrue();
            release.countDown();
            completion.get(8, TimeUnit.SECONDS);
            attempted.get(8, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?",
                String.class, fixture.sessionId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select score_percentage from quick_review_sessions where id = ?",
                java.math.BigDecimal.class, fixture.sessionId)).isEqualByComparingTo("100");
    }

    @Test
    @Timeout(15)
    void generateMoreFailureDoesNotWaitOnItsOwnBankLock() {
        Fixture fixture = seed(false);
        jdbc.update("""
                update challenge_quiz_question_bank
                set question_key = 'extra bank question',
                    question = '{"question":"Extra bank question","choices":["A","B"],"correctIndex":0,"keyConcept":"Concept"}'::jsonb
                where study_pack_id = ?
                """, fixture.packId);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(),
                any(Integer.class), any(), any())).thenThrow(new IllegalStateException("LLM failure"));
        long started = System.nanoTime();
        var job = captureMoreJob();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();
        assertThat(QuizSessionStateUtils.extractMoreFailed(
                sessionRepository.findById(fixture.sessionId).orElseThrow().getSessionState())).isTrue();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(elapsedMillis).isLessThan(1800);
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isNull();
        List<QuizItem> retryQuestions = new TransactionTemplate(transactionManager).execute(status ->
                bankService.claimEligibleQuestions(fixture.userId, fixture.packId, LearnerLevel.COLLEGE,
                        fixture.sessionId, java.util.Set.of("bank question"), 1));
        assertThat(retryQuestions).extracting(QuizItem::question).containsExactly("Extra bank question");
    }

    @Test
    @Timeout(15)
    void expiredGenerateMoreRollsBackForfeitAndKeepsClaim() {
        Fixture fixture = seed(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(
                fixture.sessionId.toString(), fixture.userId)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                fixture.sessionId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isEqualTo(fixture.sessionId);
    }

    @Test
    @Timeout(15)
    void failedPreparationReleasesItsCommittedClaim() {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = null where study_pack_id = ?",
                fixture.packId);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenThrow(new IllegalStateException("LLM failure"));
        var response = challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null);
        assertThat(response.status().name()).isEqualTo("GENERATING");
        runDispatchedChallengeJob();
        UUID failedSessionId = jdbc.queryForObject("""
                select id from quick_review_sessions
                where study_pack_id = ? and status = 'FAILED' order by created_at desc limit 1
                """, UUID.class, fixture.packId);
        assertThat(failedSessionId).isNotNull();
        assertThat(failedSessionId).isEqualTo(UUID.fromString(response.sessionId()));
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isNull();
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?",
                Boolean.class, failedSessionId)).isTrue();
        verify(userUsageService, org.mockito.Mockito.never()).incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    @Timeout(15)
    void emptyStartCommitsFailedWithoutCharging() {
        assertBelowFloorStartFailsAndReleasesGenerated(0);
    }

    @Test
    @Timeout(15)
    void belowFloorStartCommitsFailedAndReleasesGeneratedForNextSession() {
        assertBelowFloorStartFailsAndReleasesGenerated(10);
    }

    private void assertBelowFloorStartFailsAndReleasesGenerated(int generatedCount) {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        int countedBefore = jdbc.queryForObject("""
                select count(*) from quick_review_sessions where user_id = ?
                and session_mode = 'CHALLENGE' and status in ('IN_PROGRESS', 'COMPLETED', 'FORFEITED')
                """, Integer.class, fixture.userId);
        List<QuizItem> generated = java.util.stream.IntStream.range(0, generatedCount)
                .mapToObj(index -> question("Below floor " + index)).toList();
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenReturn(GeneratedChallengeQuizContent.withoutUsage(generated));

        var response = challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null);
        assertThat(response.status().name()).isEqualTo("GENERATING");
        runDispatchedChallengeJob();

        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                UUID.fromString(response.sessionId()))).isEqualTo("FAILED");
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementMultiNoteGeneration(any(), any());
        int countedAfter = jdbc.queryForObject("""
                select count(*) from quick_review_sessions where user_id = ?
                and session_mode = 'CHALLENGE' and status in ('IN_PROGRESS', 'COMPLETED', 'FORFEITED')
                """, Integer.class, fixture.userId);
        assertThat(countedAfter).isEqualTo(countedBefore);
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and claimed_session_id is not null
                """, Integer.class, fixture.packId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(generatedCount);
        if (generatedCount > 0) {
            UUID nextSessionId = insertGeneratingSession(fixture);
            List<QuizItem> nextClaim = new TransactionTemplate(transactionManager).execute(status ->
                    bankService.claimEligibleQuestions(fixture.userId, fixture.packId, LearnerLevel.COLLEGE,
                            nextSessionId, java.util.Set.of(), generatedCount));
            assertThat(nextClaim).hasSize(generatedCount);
            assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                    Integer.class, nextSessionId)).isEqualTo(generatedCount);
        }
    }

    @Test
    @Timeout(15)
    void asyncPreparationInsertsBankRowsAfterSessionCommit() {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        List<QuizItem> generated = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> question("Started " + index)).toList();
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenReturn(GeneratedChallengeQuizContent.withoutUsage(generated));

        var response = challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null);
        assertThat(response.status().name()).isEqualTo("GENERATING");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isZero();
        runDispatchedChallengeJob();

        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank bank
                join quick_review_sessions session on session.id = bank.claimed_session_id
                where bank.study_pack_id = ? and bank.origin_session_id = session.id
                  and session.status = 'IN_PROGRESS'
                """, Integer.class, fixture.packId)).isEqualTo(generated.size());
    }

    @Test
    @Timeout(15)
    void asyncReadyNotificationIsCommittedAndDeduplicatedAfterCommit() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        var generated = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> question("Ready notification " + index)).toList();
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(generated));

        var response = challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null);
        assertThat(response.status()).isEqualTo(com.studysnap.backend.entity.QuickReviewSessionStatus.GENERATING);
        runDispatchedChallengeJob();
        UUID readySessionId = UUID.fromString(response.sessionId());
        awaitNotification(fixture.userId, readySessionId);
        assertThat(jdbc.queryForObject("select type from notifications where recipient_user_id = ? and dedup_key = ?",
                String.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + readySessionId))
                .isEqualTo("CHALLENGE_QUIZ_READY");

        // Re-run the same completion task: its status guard must leave the ready row intact.
        runDispatchedChallengeJob();
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?",
                String.class, readySessionId)).isEqualTo("IN_PROGRESS");

        UUID noteId = jdbc.queryForObject("select note_id from study_packs where id = ?", UUID.class, fixture.packId);
        eventPublisher.publishEvent(new ChallengeQuizReadyEvent(fixture.userId, noteId, readySessionId));
        challengeQuizService.markReadySeen(readySessionId.toString(), fixture.userId);
        awaitNotification(fixture.userId, readySessionId);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                Integer.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + readySessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select read_at is not null from notifications where recipient_user_id = ? and dedup_key = ?",
                Boolean.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + readySessionId)).isTrue();
    }

    @Test
    @Timeout(15)
    void readySeenFirstLeavesOneReadRowAndNeverWritesTheSession() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set session_state = "
                + "'{\"mode\":\"challenge\",\"preparedNotStarted\":true,\"preparedAtEpochSeconds\":1800000000}'::jsonb where id = ?",
                fixture.sessionId);
        String before = jdbc.queryForObject("select session_state::text from quick_review_sessions where id = ?",
                String.class, fixture.sessionId);
        challengeQuizService.markReadySeen(fixture.sessionId.toString(), fixture.userId);
        assertThat(jdbc.queryForObject("select session_state::text from quick_review_sessions where id = ?",
                String.class, fixture.sessionId)).isEqualTo(before);
        UUID noteId = jdbc.queryForObject("select note_id from study_packs where id = ?", UUID.class, fixture.packId);
        eventPublisher.publishEvent(new ChallengeQuizReadyEvent(fixture.userId, noteId, fixture.sessionId));
        awaitNotification(fixture.userId, fixture.sessionId);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                Integer.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + fixture.sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select read_at is not null from notifications where recipient_user_id = ? and dedup_key = ?",
                Boolean.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + fixture.sessionId)).isTrue();
    }

    @Test
    @Timeout(15)
    void rolledBackReadyEventNeverDelivers() throws Exception {
        Fixture fixture = seed(false);
        UUID noteId = jdbc.queryForObject("select note_id from study_packs where id = ?", UUID.class, fixture.packId);
        new TransactionTemplate(transactionManager).execute(status -> {
            eventPublisher.publishEvent(new ChallengeQuizReadyEvent(fixture.userId, noteId, fixture.sessionId));
            status.setRollbackOnly();
            return null;
        });
        Thread.sleep(100);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                Integer.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + fixture.sessionId)).isZero();
    }

    @Test
    @Timeout(15)
    void statusAndReadySeenRejectAnotherOwnerWithoutChangingTheSession() {
        Fixture fixture = seed(false);
        UUID stranger = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                challengeQuizService.getSessionStatus(fixture.sessionId.toString(), stranger))
                .isInstanceOf(com.studysnap.backend.exception.ChallengeQuizSessionNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                challengeQuizService.markReadySeen(fixture.sessionId.toString(), stranger))
                .isInstanceOf(com.studysnap.backend.exception.ChallengeQuizSessionNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ?",
                Integer.class, stranger)).isZero();
    }

    private void awaitNotification(UUID userId, UUID sessionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                    Integer.class, userId, "CHALLENGE_QUIZ_READY:" + sessionId);
            if (count != null && count > 0) return;
            Thread.sleep(20);
        }
        org.junit.jupiter.api.Assertions.fail("ready notification did not commit");
    }

    @Test
    @Timeout(15)
    void officialSeedAndAdopterCopyBothPersistBankRows() {
        Fixture official = seed(false);
        UUID officialNoteId = jdbc.queryForObject("select note_id from study_packs where id = ?",
                UUID.class, official.packId);
        jdbc.update("update users set role = 'ADMIN' where id = ?", official.userId);
        jdbc.update("update notes set visibility = 'PUBLIC' where id = ?", officialNoteId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", official.packId);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenReturn(GeneratedChallengeQuizContent.withoutUsage(
                List.of(question("Official seeded question"))));

        templateService.seedTemplateAsync(officialNoteId, official.packId);

        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and origin_session_id is null
                """, Integer.class, official.packId)).isEqualTo(1);

        Fixture adopter = seed(false);
        UUID adopterNoteId = jdbc.queryForObject("select note_id from study_packs where id = ?",
                UUID.class, adopter.packId);
        jdbc.update("update notes set copied_from_note_id = ? where id = ?", officialNoteId, adopterNoteId);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", adopter.packId);
        List<QuizItem> copied = new TransactionTemplate(transactionManager).execute(status ->
                templateService.copyTemplateQuestions(adopter.userId, adopter.packId, LearnerLevel.COLLEGE,
                        0L, adopter.sessionId, java.util.Set.of(), 1));

        assertThat(copied).extracting(QuizItem::question).containsExactly("Official seeded question");
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and origin_session_id = ? and claimed_session_id = ?
                """, Integer.class, adopter.packId, adopter.sessionId, adopter.sessionId)).isEqualTo(1);
    }

    /**
     * `resolveExistingChallengeSession` reads the existing session once unlocked (to capture
     * {@code observedStatus}), then again locked — the same shape as Interview Practice's original bug.
     * Both reads resolve to the SAME Java object by Hibernate identity within one persistence context,
     * so without a refresh, "lockedExisting.getStatus() != observedStatus" compares a value to itself
     * and can never catch a status a concurrent transaction committed between the two reads. This test
     * injects exactly that commit between the two reads via a paused repository call.
     */
    @Test
    @Timeout(15)
    void startSessionDetectsAConcurrentCompletionBetweenItsTwoSessionReads() throws Exception {
        Fixture fixture = seed(false);
        CountDownLatch firstReadDone = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        MethodInterceptor pauseBetweenReads = invocation -> {
            Object result = invocation.proceed();
            if (!invocation.getMethod().getName()
                    .equals("findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc")) {
                return result;
            }
            firstReadDone.countDown();
            assertThat(proceed.await(8, TimeUnit.SECONDS)).isTrue();
            return result;
        };
        ((Advised) sessionRepository).addAdvice(0, pauseBetweenReads);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any(), any(), any())).thenThrow(new IllegalStateException("LLM failure"));
        try (var executor = Executors.newFixedThreadPool(1)) {
            var start = executor.submit(() ->
                    challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null));
            assertThat(firstReadDone.await(8, TimeUnit.SECONDS)).isTrue();
            // A concurrent request committing the session as COMPLETED between the two reads above.
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                        fixture.sessionId);
                return null;
            });
            proceed.countDown();
            var response = start.get(8, TimeUnit.SECONDS);
            assertThat(response.status().name()).isEqualTo("GENERATING");
            assertThat(UUID.fromString(response.sessionId())).isNotEqualTo(fixture.sessionId);
        } finally {
            ((Advised) sessionRepository).removeAdvice(pauseBetweenReads);
        }
        runDispatchedChallengeJob();
        // Without the refresh fix, the stale guard sees no status change and startSession incorrectly
        // resumes the now-COMPLETED session instead of treating it as gone and starting a new one.
        UUID failedSessionId = jdbc.queryForObject("""
                select id from quick_review_sessions
                where study_pack_id = ? and status = 'FAILED' order by created_at desc limit 1
                """, UUID.class, fixture.packId);
        assertThat(failedSessionId).isNotNull().isNotEqualTo(fixture.sessionId);
    }

    @Test
    @Timeout(15)
    void regenerationDuringLlmCallLeavesStaleRowsUnclaimable() throws Exception {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        CountDownLatch generating = new CountDownLatch(1);
        CountDownLatch regenerated = new CountDownLatch(1);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(),
                any(Integer.class), any(), any())).thenAnswer(invocation -> {
                    generating.countDown();
                    assertThat(regenerated.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(List.of(
                            question("Old content one"), question("Old content two"),
                            question("Old content three"), question("Old content four"),
                            question("Old content five")));
                });
        var job = captureMoreJob();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var more = executor.submit(job.get());
            assertThat(generating.await(8, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.update("update study_packs set summary = 'new summary', generation_stamp = generation_stamp + 1 where id = ?", fixture.packId);
                bankService.invalidateForStudyPack(fixture.packId);
                return null;
            });
            regenerated.countDown();
            more.get(8, TimeUnit.SECONDS);
            jdbc.update("update challenge_quiz_question_bank set claimed_session_id = null where study_pack_id = ?",
                    fixture.packId);
            List<ChallengeQuizQuestionBankEntity> claimable = new TransactionTemplate(transactionManager)
                    .execute(status -> bankRepository.findClaimableForUpdate(fixture.userId, fixture.packId,
                            LearnerLevel.COLLEGE.name(), fixture.sessionId));
            assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                    Integer.class, fixture.packId)).isZero();
            assertThat(QuizSessionStateUtils.extractQuiz(sessionRepository.findById(fixture.sessionId)
                    .orElseThrow().getSessionState())).hasSize(1);
            assertThat(claimable).isEmpty();
        }
    }

    @Test
    @Timeout(15)
    void generatedRowsUseThePackDefaultStampAndRemainEligibleWithoutRegeneration() {
        Fixture fixture = seed(false);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", fixture.packId);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(),
                any(Integer.class), any(), any())).thenReturn(GeneratedChallengeQuizContent.withoutUsage(List.of(
                question("Current one"), question("Current two"), question("Current three"),
                question("Current four"), question("Current five"))));

        var job = captureMoreJob();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();

        assertThat(jdbc.queryForObject("select generation_stamp from study_packs where id = ?", Long.class,
                fixture.packId)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and generation_stamp = 0 and claimed_session_id = ?
                """, Integer.class, fixture.packId, fixture.sessionId)).isEqualTo(5);
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = null where study_pack_id = ?",
                fixture.packId);
        List<ChallengeQuizQuestionBankEntity> claimable = new TransactionTemplate(transactionManager).execute(status ->
                bankRepository.findClaimableForUpdate(fixture.userId, fixture.packId,
                        LearnerLevel.COLLEGE.name(), fixture.sessionId));
        assertThat(claimable).hasSize(5);
    }

    @Test
    @Timeout(15)
    void generateMoreServesAPreexistingUnclaimableDuplicateAndCommitsNewRows() {
        Fixture fixture = seed(false);
        jdbc.update("""
                update challenge_quiz_question_bank
                set question_key = 'duplicate generated',
                    question = '{"question":"Duplicate generated","choices":["A","B"],"correctIndex":0}'::jsonb,
                    generation_stamp = -1
                where study_pack_id = ?
                """, fixture.packId);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(),
                any(Integer.class), any(), any())).thenReturn(GeneratedChallengeQuizContent.withoutUsage(List.of(
                question("Duplicate generated"), question("New two"), question("New three"),
                question("New four"), question("New five"))));

        var job = captureMoreJob();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();

        var session = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(session.getSessionState()))
                .extracting(QuizItem::question).contains("Duplicate generated", "New five");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where study_pack_id = ? and generation_stamp = 0 and claimed_session_id = ?
                """, Integer.class, fixture.packId, fixture.sessionId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select claimed_session_id from challenge_quiz_question_bank
                where study_pack_id = ? and question_key = 'duplicate generated'
                """, UUID.class, fixture.packId)).isEqualTo(fixture.sessionId);
    }

    @Test
    @Timeout(15)
    void legacyNullBankStampRemainsEligibleButStaleStampedRowsDoNot() {
        Fixture fixture = seed(false);
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = null, last_known_outcome = 'INCORRECT' where study_pack_id = ?",
                fixture.packId);
        jdbc.update("update study_packs set generation_stamp = 1 where id = ?", fixture.packId);
        List<ChallengeQuizQuestionBankEntity> legacy = new TransactionTemplate(transactionManager).execute(status ->
                bankRepository.findClaimableForUpdate(fixture.userId, fixture.packId,
                        LearnerLevel.COLLEGE.name(), fixture.sessionId));
        assertThat(legacy).hasSize(1);
        assertThat(bankService.countEligibleIncorrectQuestions(fixture.userId, fixture.packId,
                LearnerLevel.COLLEGE)).isEqualTo(1);
        List<ChallengeQuizQuestionBankEntity> missed = new TransactionTemplate(transactionManager).execute(status ->
                bankRepository.findIncorrectClaimableForUpdate(fixture.userId, fixture.packId,
                        LearnerLevel.COLLEGE.name(), "INCORRECT"));
        assertThat(missed).hasSize(1);
        jdbc.update("update challenge_quiz_question_bank set generation_stamp = 0 where study_pack_id = ?",
                fixture.packId);
        List<ChallengeQuizQuestionBankEntity> stale = new TransactionTemplate(transactionManager).execute(status ->
                bankRepository.findClaimableForUpdate(fixture.userId, fixture.packId,
                        LearnerLevel.COLLEGE.name(), fixture.sessionId));
        assertThat(stale).isEmpty();
        assertThat(bankService.countEligibleIncorrectQuestions(fixture.userId, fixture.packId,
                LearnerLevel.COLLEGE)).isZero();
        List<ChallengeQuizQuestionBankEntity> staleMissed = new TransactionTemplate(transactionManager)
                .execute(status -> bankRepository.findIncorrectClaimableForUpdate(fixture.userId,
                        fixture.packId, LearnerLevel.COLLEGE.name(), "INCORRECT"));
        assertThat(staleMissed).isEmpty();
        assertThat(bankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(
                fixture.userId, fixture.packId)).isEmpty();

        // ⚠️ The next three are DELIBERATELY NOT stamp-filtered: each guards a WRITE (skip a re-seed,
        // skip re-copying a key the caller already holds), not a read that hands out content. Filtering
        // them would make a stale-but-present row invisible to the guard, so a fresh write could reuse
        // that row's question_key and re-serve it; the duplicate bank insert would then be skipped.
        assertThat(bankRepository.findQuestionKeysByUserIdAndStudyPackId(
                fixture.userId, fixture.packId)).containsExactly("bank question");
        assertThat(bankRepository.existsByUserIdAndStudyPackId(fixture.userId, fixture.packId)).isTrue();
        assertThat(bankRepository.findOwnerStudyPackPairsByStudyPackIdIn(List.of(fixture.packId)))
                .containsExactly(new ChallengeQuizQuestionBankOwnerProjection(fixture.userId, fixture.packId));
    }

    @Test
    @Timeout(15)
    void owningSessionReleaseIsNeverGenerationStampFiltered() {
        Fixture fixture = seed(false);
        jdbc.update("update challenge_quiz_question_bank set generation_stamp = 0 where study_pack_id = ?",
                fixture.packId);
        jdbc.update("update study_packs set generation_stamp = 1 where id = ?", fixture.packId);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(fixture.userId, fixture.sessionId);
            return null;
        });
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isNull();
    }

    @Test
    @Timeout(15)
    void adminSummaryRegenerationAdvancesStampAndDeletesBankInOneTransaction() {
        Fixture fixture = seed(false);
        when(generationContextResolver.resolve(any(), any()))
                .thenReturn(new StudyPackGenerationContext(null, null, null, List.of()));
        when(llmStudyPackService.regenerateSummary(any(), any()))
                .thenReturn("new summary without enriched marker");

        assertThat(adminStudyPackTransactionHelper.regenerateOnePack(
                studyPackRepository.findById(fixture.packId).orElseThrow())).isTrue();

        assertThat(jdbc.queryForObject("select summary from study_packs where id = ?", String.class,
                fixture.packId)).isEqualTo("new summary without enriched marker");
        assertThat(jdbc.queryForObject("select generation_stamp from study_packs where id = ?", Long.class,
                fixture.packId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isZero();
    }

    private QuizItem question(String text) {
        return new QuizItem(text, List.of("A", "B"), 0, "Concept", "Explanation");
    }

    private void runDispatchedChallengeJob() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(studyPackGenerationTaskDispatcher).executeChallengePreparation(task.capture());
        // Run after startSession returns so the request transaction has committed and closed.
        task.getValue().run();
    }

    private Fixture availableMultiNoteFixture() {
        Fixture fixture = seed(false);
        jdbc.update("update notes set subject = 'Nursing' where id = (select note_id from study_packs where id = ?)",
                fixture.packId);
        jdbc.update("update study_packs set subject = 'Nursing' where id = ?", fixture.packId);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.releaseClaims(fixture.userId, fixture.sessionId);
            return null;
        });
        when(generationContextResolver.resolveForStudyPack(any(), any()))
                .thenReturn(new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of()));
        return fixture;
    }

    private UUID addMultiPack(Fixture fixture, String questionText) {
        UUID id = addSecondPackWithQuestion(fixture, questionText);
        jdbc.update("update notes set subject = 'Nursing' where id = (select note_id from study_packs where id = ?)", id);
        jdbc.update("update study_packs set subject = 'Nursing' where id = ?", id);
        return id;
    }

    private void bankExtra(Fixture fixture, UUID packId, String prefix, int count,
                           LearnerLevel level, long stamp) {
        new TransactionTemplate(transactionManager).execute(status -> {
            bankService.persistGeneratedQuestionsUnclaimed(fixture.userId, packId, fixture.sessionId,
                    level, stamp, java.util.stream.IntStream.range(0, count)
                            .mapToObj(index -> question(prefix + " " + index)).toList());
            return null;
        });
    }

    @Test
    void multiNoteFullBankHitClaimsBothRealPacksWithoutDispatchOrModelCall() throws Exception {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, secondary, "Secondary extra", 8, LearnerLevel.COLLEGE, 0);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));

        assertThat(response.status().name()).isEqualTo("IN_PROGRESS");
        assertThat(response.preparedNotStarted()).isTrue();
        assertThat(response.requestedQuestionCount()).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, UUID.fromString(response.sessionId()))).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(distinct study_pack_id) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, UUID.fromString(response.sessionId()))).isEqualTo(2);
        org.mockito.Mockito.verify(quizGenerationService, org.mockito.Mockito.never())
                .generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        org.mockito.Mockito.verify(studyPackGenerationTaskDispatcher, org.mockito.Mockito.never())
                .executeChallengePreparation(any());
        awaitNotification(fixture.userId, UUID.fromString(response.sessionId()));
        challengeQuizService.getSession(response.sessionId(), fixture.userId);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                Integer.class, fixture.userId, "CHALLENGE_QUIZ_READY:" + response.sessionId())).isEqualTo(1);
    }

    @Test
    void multiNoteShortfallWritesOnlySecondaryBankWithItsLevelStampAndClaim() {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, secondary, "Secondary extra", 5, LearnerLevel.PROFESSIONAL, 7);
        jdbc.update("update study_packs set generation_stamp = 7 where id = ?", secondary);
        jdbc.update("update challenge_quiz_question_bank set generation_stamp = 7, learner_level = 'PROFESSIONAL' where study_pack_id = ?", secondary);
        when(generationContextResolver.resolveForStudyPack(any(), any())).thenAnswer(invocation ->
                new StudyPackGenerationContext(
                        ((com.studysnap.backend.entity.StudyPackEntity) invocation.getArgument(1)).getId().equals(secondary)
                                ? LearnerLevel.PROFESSIONAL : LearnerLevel.COLLEGE,
                        null, null, List.of()));
        when(quizGenerationService.generateChallengeQuiz(eq("Second bank"), any(), any(), any(), eq(3), any(), any()))
                .thenReturn(new GeneratedChallengeQuizContent(List.of(question("Secondary new 0"),
                        question("Secondary new 1"), question("Secondary new 2")), "mini", 23, 11, 2));

        ChallengeQuizStartResponse start = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));
        assertThat(start.status().name()).isEqualTo("GENERATING");
        org.mockito.Mockito.verify(quizGenerationService, org.mockito.Mockito.never())
                .generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        runDispatchedChallengeJob();

        UUID sessionId = UUID.fromString(start.sessionId());
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                sessionId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ?",
                Integer.class, sessionId, secondary)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ?",
                Integer.class, sessionId, fixture.packId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ? and generation_stamp = 7 and learner_level = 'PROFESSIONAL' and claimed_session_id = ?",
                Integer.class, sessionId, secondary, sessionId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select input_tokens from quick_review_sessions where id = ?", Integer.class,
                sessionId)).isEqualTo(23);
        challengeQuizService.forfeitSession(start.sessionId(), fixture.userId);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, sessionId)).isZero();
        ChallengeQuizStartResponse single = challengeQuizService.startSession(secondary.toString(), fixture.userId, null);
        assertThat(single.status().name()).isEqualTo("GENERATING");
        assertThat(QuizSessionStateUtils.extractPreparationQuiz(sessionRepository.findById(
                UUID.fromString(single.sessionId())).orElseThrow().getSessionState()))
                .extracting(QuizItem::question)
                .contains("Secondary new 0", "Secondary new 1", "Secondary new 2");
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank
                where origin_session_id = ? and study_pack_id = ? and claimed_session_id = ?
                """, Integer.class, sessionId, secondary, UUID.fromString(single.sessionId()))).isEqualTo(3);
    }

    @Test
    void regeneratedNonPrimaryBetweenReadyAndStartForfeitsAndReleasesBothPacks() {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, secondary, "Secondary extra", 8, LearnerLevel.COLLEGE, 0);
        ChallengeQuizStartResponse ready = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));
        assertThat(ready.status().name()).isEqualTo("IN_PROGRESS");
        jdbc.update("update study_packs set generation_stamp = generation_stamp + 1 where id = ?", secondary);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                challengeQuizService.startQuiz(ready.sessionId(), fixture.userId))
                .isInstanceOf(com.studysnap.backend.exception.ChallengeQuizSessionNotInProgressException.class);
        UUID sessionId = UUID.fromString(ready.sessionId());
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                sessionId)).isEqualTo("FORFEITED");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, sessionId)).isZero();
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    @Timeout(20)
    void parallelSourcesAndRegeneratedNonPrimaryFailWhileSalvagingUnchangedPack() throws Exception {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 5, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, secondary, "Secondary extra", 5, LearnerLevel.COLLEGE, 0);
        CountDownLatch bothInModel = new CountDownLatch(2);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(3), any(), any()))
                .thenAnswer(invocation -> {
                    String title = invocation.getArgument(0);
                    bothInModel.countDown();
                    assertThat(releaseModel.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(List.of(
                            question(title + " new 0"), question(title + " new 1"), question(title + " new 2")));
                });
        ChallengeQuizStartResponse preparing = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));
        assertThat(preparing.status().name()).isEqualTo("GENERATING");

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> work = executor.submit(this::runDispatchedChallengeJob);
            assertThat(bothInModel.await(8, TimeUnit.SECONDS))
                    .as("two calls must enter the parallel executor before either returns").isTrue();
            new TransactionTemplate(transactionManager).execute(status -> {
                bankService.invalidateForStudyPack(secondary);
                jdbc.update("update study_packs set generation_stamp = generation_stamp + 1 where id = ?", secondary);
                return null;
            });
            releaseModel.countDown();
            work.get(8, TimeUnit.SECONDS);
        } finally {
            releaseModel.countDown();
        }
        UUID sessionId = UUID.fromString(preparing.sessionId());
        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                sessionId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ? and claimed_session_id is null",
                Integer.class, sessionId, fixture.packId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ?",
                Integer.class, sessionId, secondary)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, sessionId)).isZero();
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    void nonPrimarySavedQuizQuestionIsExcludedFromRealBankClaimAndDelivery() {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, secondary, "Secondary extra", 8, LearnerLevel.COLLEGE, 0);
        jdbc.update("""
                update study_packs set quiz =
                '[{"question":"Second bank question","choices":["A","B"],"correctIndex":0}]'::jsonb
                where id = ?
                """, secondary);
        when(quizGenerationService.generateChallengeQuiz(eq("Second bank"), any(), any(), any(), eq(1), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(List.of(question("Safe replacement"))));

        ChallengeQuizStartResponse preparing = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));
        assertThat(preparing.status().name()).isEqualTo("GENERATING");
        assertThat(QuizSessionStateUtils.extractPreparationQuiz(sessionRepository.findById(
                UUID.fromString(preparing.sessionId())).orElseThrow().getSessionState()))
                .extracting(QuizItem::question).doesNotContain("Second bank question");
        runDispatchedChallengeJob();
        var ready = sessionRepository.findById(UUID.fromString(preparing.sessionId())).orElseThrow();
        assertThat(ready.getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(QuizSessionStateUtils.extractQuiz(ready.getSessionState()))
                .extracting(QuizItem::question).doesNotContain("Second bank question");
        assertThat(jdbc.queryForObject("""
                select count(*) from challenge_quiz_question_bank where study_pack_id = ?
                and question_key = ? and claimed_session_id is null
                """, Integer.class, secondary,
                com.studysnap.backend.util.QuizDeduplicationUtils.normalizeQuestion("Second bank question")))
                .isEqualTo(1);
    }

    @Test
    void belowFloorFailureReleasesRealClaimsAndSalvagesValidGeneratedQuestionsUnclaimed() {
        Fixture fixture = availableMultiNoteFixture();
        UUID secondary = addMultiPack(fixture, "Second bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 5, LearnerLevel.COLLEGE, 0);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", secondary);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    String title = invocation.getArgument(0);
                    if ("Second bank".equals(title)) throw new IllegalStateException("source unavailable");
                    return GeneratedChallengeQuizContent.withoutUsage(List.of(question("Primary safe 0"),
                            question("Primary safe 1"), question("Primary safe 2")));
                });

        ChallengeQuizStartResponse preparing = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge", List.of(secondary.toString())));
        runDispatchedChallengeJob();
        UUID sessionId = UUID.fromString(preparing.sessionId());

        assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                sessionId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select quota_exempt from quick_review_sessions where id = ?", Boolean.class,
                sessionId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where claimed_session_id = ?",
                Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where origin_session_id = ? and study_pack_id = ? and claimed_session_id is null",
                Integer.class, sessionId, fixture.packId)).isEqualTo(3);
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    void oneFailedSourceStillProducesUnderfilledReadyQuizWhenBothFloorsPass() {
        Fixture fixture = availableMultiNoteFixture();
        UUID second = addMultiPack(fixture, "Second bank question");
        UUID failed = addMultiPack(fixture, "Failed bank question");
        bankExtra(fixture, fixture.packId, "Primary extra", 5, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, second, "Second extra", 5, LearnerLevel.COLLEGE, 0);
        jdbc.update("delete from challenge_quiz_question_bank where study_pack_id = ?", failed);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(6), any(), any()))
                .thenThrow(new IllegalStateException("one source failed"));

        ChallengeQuizStartResponse preparing = challengeQuizService.startSession(fixture.packId.toString(),
                fixture.userId, new ChallengeQuizStartRequest("challenge",
                        List.of(second.toString(), failed.toString())));
        assertThat(preparing.status().name()).isEqualTo("GENERATING");
        runDispatchedChallengeJob();
        var ready = challengeQuizService.getSession(preparing.sessionId(), fixture.userId);

        assertThat(ready.status().name()).isEqualTo("IN_PROGRESS");
        assertThat(ready.totalQuestions()).isEqualTo(12);
        assertThat(ready.requestedQuestionCount()).isEqualTo(18);
        assertThat(ready.sessionState()).containsEntry("sourceContributionCount", 2);
        assertThat(ready.preparedNotStarted()).isTrue();
        org.mockito.Mockito.verify(userUsageService, org.mockito.Mockito.never())
                .incrementChallengeQuizGeneration(any(), any());
    }

    @Test
    @Timeout(15)
    void overlappingMultiNoteStartsLockSharedPacksInSortedOrderWithoutDeadlock() throws Exception {
        Fixture fixture = availableMultiNoteFixture();
        UUID shared = addMultiPack(fixture, "Shared bank question");
        UUID otherPrimary = addMultiPack(fixture, "Other primary bank question");
        bankExtra(fixture, fixture.packId, "First primary extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, shared, "Shared extra", 8, LearnerLevel.COLLEGE, 0);
        bankExtra(fixture, otherPrimary, "Other primary extra", 8, LearnerLevel.COLLEGE, 0);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ChallengeQuizStartResponse> first = executor.submit(() -> {
                go.await();
                return challengeQuizService.startSession(fixture.packId.toString(), fixture.userId,
                        new ChallengeQuizStartRequest("challenge", List.of(shared.toString())));
            });
            Future<ChallengeQuizStartResponse> second = executor.submit(() -> {
                go.await();
                return challengeQuizService.startSession(otherPrimary.toString(), fixture.userId,
                        new ChallengeQuizStartRequest("challenge", List.of(shared.toString())));
            });
            go.countDown();
            assertThat(first.get(8, TimeUnit.SECONDS).sessionId()).isNotBlank();
            assertThat(second.get(8, TimeUnit.SECONDS).sessionId()).isNotBlank();
        } finally {
            go.countDown();
        }
    }

    private UUID insertGeneratingSession(Fixture fixture) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                insert into quick_review_sessions
                    (id, user_id, study_pack_id, note_id, session_mode, status,
                     current_question_index, current_round, total_questions, session_state,
                     quota_exempt, created_at)
                select ?, user_id, study_pack_id, note_id, 'CHALLENGE', 'GENERATING',
                       0, 'INITIAL', 0, '{}'::jsonb, true, now()
                from quick_review_sessions where id = ?
                """, sessionId, fixture.sessionId);
        return sessionId;
    }

    @Test
    @Timeout(15)
    void completionLocksItsClaimedRowsSoDeleteCommitsAfterCompletion() throws Exception {
        Fixture fixture = seed(false);
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        MethodInterceptor pauseAfterLockedRead = invocation -> {
            Object result = invocation.proceed();
            if (!invocation.getMethod().getName().equals("findByUserIdAndClaimedSessionId")) {
                return result;
            }
            @SuppressWarnings("unchecked")
            List<ChallengeQuizQuestionBankEntity> rows = (List<ChallengeQuizQuestionBankEntity>) result;
            assertThat(rows).hasSize(1);
            loaded.countDown();
            assertThat(proceed.await(8, TimeUnit.SECONDS)).isTrue();
            return rows;
        };
        ((Advised) bankRepository).addAdvice(0, pauseAfterLockedRead);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var completion = executor.submit(() -> {
                try {
                    challengeQuizService.completeSession(fixture.sessionId.toString(), fixture.userId,
                            new ChallengeQuizCompleteRequest(0, 1, 10));
                    return null;
                } catch (Exception exception) {
                    return exception;
                }
            });
            assertThat(loaded.await(8, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> {
                new TransactionTemplate(transactionManager).execute(status -> {
                    bankService.invalidateForStudyPack(fixture.packId);
                    return null;
                });
            });
            boolean waitingOnLock = false;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
            while (System.nanoTime() < deadline && !waitingOnLock) {
                waitingOnLock = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists (select 1 from pg_stat_activity
                        where wait_event_type = 'Lock'
                          and query ilike '%challenge_quiz_question_bank%')
                        """, Boolean.class));
                if (!waitingOnLock) {
                    Thread.sleep(10);
                }
            }
            assertThat(waitingOnLock).isTrue();
            assertThat(deletion.isDone()).isFalse();
            proceed.countDown();
            Exception failure = completion.get(8, TimeUnit.SECONDS);
            assertThat(failure).isNull();
            deletion.get(8, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select status from quick_review_sessions where id = ?", String.class,
                    fixture.sessionId)).isEqualTo("COMPLETED");
        } finally {
            ((Advised) bankRepository).removeAdvice(pauseAfterLockedRead);
        }
    }

    private java.util.concurrent.atomic.AtomicReference<Runnable> captureMoreJob() {
        var job = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        org.mockito.Mockito.doAnswer(invocation -> {
            job.set(invocation.getArgument(0));
            return null;
        }).when(studyPackGenerationTaskDispatcher).executeChallengePreparation(any(Runnable.class));
        return job;
    }

    private List<QuizItem> moreQuestions() {
        return List.of(question("Fresh one"), question("Fresh two"), question("Fresh three"),
                question("Fresh four"), question("Fresh five"));
    }

    @Test
    void rejectedGenerateMoreDispatchCommitsFailureMarkerInFreshTransaction() {
        Fixture fixture = seed(false);
        org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("full"))
                .when(studyPackGenerationTaskDispatcher).executeChallengePreparation(any(Runnable.class));
        assertThat(challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId)
                .morePending()).isTrue();
        var state = sessionRepository.findById(fixture.sessionId).orElseThrow().getSessionState();
        assertThat(QuizSessionStateUtils.extractMoreToken(state)).isNull();
        assertThat(QuizSessionStateUtils.extractMoreFailed(state)).isTrue();
    }

    private void stubMoreGeneration(List<QuizItem> questions) {
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(questions));
    }

    @Test
    @Timeout(15)
    void generateMoreAndProgressSaveRaceOnRealSessionRowWithoutLosingEitherWrite() throws Exception {
        Fixture fixture = seed(false);
        var job = captureMoreJob();
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    modelEntered.countDown();
                    assertThat(releaseModel.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(moreQuestions());
                });
        var response = challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        assertThat(response.morePending()).isTrue();
        verify(quizGenerationService, org.mockito.Mockito.never()).generateMoreChallengeQuiz(
                any(), any(), any(), any(), any(), anyInt(), any(), any());
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseRow = new CountDownLatch(1);
        CountDownLatch writersAtLock = new CountDownLatch(2);
        MethodInterceptor observeBothWriters = invocation -> {
            if (invocation.getMethod().getName().equals("findByIdAndUserIdAndSessionModeForUpdate")
                    && fixture.sessionId.equals(invocation.getArguments()[0])) {
                writersAtLock.countDown();
            }
            return invocation.proceed();
        };
        try (var executor = Executors.newFixedThreadPool(3)) {
            Future<?> finish = executor.submit(job.get());
            assertThat(modelEntered.await(8, TimeUnit.SECONDS)).isTrue();
            // This save completes while the model is still in flight: the job holds no session lock.
            challengeQuizService.updateSessionProgress(
                    fixture.sessionId.toString(), fixture.userId,
                    new ChallengeQuizProgressRequest(0, java.util.Map.of("selectedChoices", java.util.Map.of("0", 1))));
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from quick_review_sessions where id = ? for update",
                        UUID.class, fixture.sessionId);
                rowLocked.countDown();
                try { assertThat(releaseRow.await(8, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return null;
            }));
            assertThat(rowLocked.await(8, TimeUnit.SECONDS)).isTrue();
            ((Advised) sessionRepository).addAdvice(0, observeBothWriters);
            CountDownLatch progressStarted = new CountDownLatch(1);
            Future<?> progress = executor.submit(() -> {
                progressStarted.countDown();
                challengeQuizService.updateSessionProgress(fixture.sessionId.toString(), fixture.userId,
                        new ChallengeQuizProgressRequest(0,
                                java.util.Map.of("selectedChoices", java.util.Map.of("0", 1))));
            });
            assertThat(progressStarted.await(8, TimeUnit.SECONDS)).isTrue();
            releaseModel.countDown();
            // Both whole-JSONB writers have reached the locked repository path before either may commit.
            assertThat(writersAtLock.await(8, TimeUnit.SECONDS)).isTrue();
            releaseRow.countDown();
            lockHolder.get(8, TimeUnit.SECONDS);
            progress.get(8, TimeUnit.SECONDS);
            finish.get(8, TimeUnit.SECONDS);
        } finally {
            releaseModel.countDown();
            releaseRow.countDown();
            ((Advised) sessionRepository).removeAdvice(observeBothWriters);
        }
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(6);
        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(saved.getSessionState(),
                QuizSessionStateUtils.extractQuiz(saved.getSessionState()))).containsEntry(0, 1);
        assertThat(saved.getSessionState().get("timeLimitSeconds")).isEqualTo(1350);
    }

    @Test
    void concurrentGenerateMoreRequestsReturnOnePendingAndOneMoreInProgress() throws Exception {
        Fixture fixture = seed(false);
        captureMoreJob();
        CountDownLatch begin = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                begin.await();
                try { return challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId).morePending()
                        ? "PENDING" : "OTHER"; }
                catch (com.studysnap.backend.exception.ChallengeMoreInProgressException expected) { return "MORE_IN_PROGRESS"; }
            })).toList();
            begin.countDown();
            assertThat(List.of(calls.get(0).get(8, TimeUnit.SECONDS), calls.get(1).get(8, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PENDING", "MORE_IN_PROGRESS");
        }
    }

    @Test
    void stoppedGenerateMoreBanksGeneratedQuestionsUnclaimedAndDoesNotExtend() throws Exception {
        Fixture fixture = seed(false);
        var job = captureMoreJob();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(moreQuestions());
                });
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> running = executor.submit(job.get());
            assertThat(entered.await(8, TimeUnit.SECONDS)).isTrue();
            challengeQuizService.stopGenerateMore(fixture.sessionId.toString(), fixture.userId);
            release.countDown();
            running.get(8, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(1);
        assertThat(saved.getSessionState().get("timeLimitSeconds")).isEqualTo(900);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ? and claimed_session_id is null",
                Integer.class, fixture.packId)).isEqualTo(5);
    }

    @Test
    @Timeout(15)
    void stoppedJobCannotReleaseRetryClaimsOnRealBankRows() throws Exception {
        Fixture fixture = seed(false);
        insertBankQuestion(fixture, "Reusable one", "UNANSWERED", OffsetDateTime.now(ZoneOffset.UTC));
        insertBankQuestion(fixture, "Reusable two", "UNANSWERED", OffsetDateTime.now(ZoneOffset.UTC));
        var job = captureMoreJob();
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    modelEntered.countDown();
                    assertThat(releaseModel.await(8, TimeUnit.SECONDS)).isTrue();
                    return GeneratedChallengeQuizContent.withoutUsage(List.of(
                            question("Generated one"), question("Generated two"), question("Generated three")));
                });
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        Runnable oldJob = job.get();
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> oldFinish = executor.submit(oldJob);
            assertThat(modelEntered.await(8, TimeUnit.SECONDS)).isTrue();
            challengeQuizService.stopGenerateMore(fixture.sessionId.toString(), fixture.userId);
            assertThat(sessionRepository.findById(fixture.sessionId).orElseThrow().getSessionState())
                    .satisfies(state -> {
                        assertThat(QuizSessionStateUtils.extractMoreToken(state)).isNotNull();
                        assertThat(QuizSessionStateUtils.extractMoreStopped(state)).isTrue();
                    });
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId))
                    .isInstanceOf(com.studysnap.backend.exception.ChallengeMoreInProgressException.class);
            releaseModel.countDown();
            oldFinish.get(8, TimeUnit.SECONDS);
        } finally { releaseModel.countDown(); }
        assertThat(QuizSessionStateUtils.extractMoreToken(
                sessionRepository.findById(fixture.sessionId).orElseThrow().getSessionState())).isNull();
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        Set<String> quizKeys = com.studysnap.backend.util.QuizDeduplicationUtils.toNormalizedQuestionSet(
                QuizSessionStateUtils.extractQuiz(saved.getSessionState()));
        List<String> claimedKeys = jdbc.queryForList(
                "select question_key from challenge_quiz_question_bank where claimed_session_id = ?",
                String.class, fixture.sessionId);
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(6);
        assertThat(claimedKeys).isNotEmpty().allMatch(quizKeys::contains);
    }

    @Test
    void staleStoppedMarkerReleasesOnlyUnappendedClaimsBeforeRetry() {
        Fixture fixture = seed(false);
        UUID unappended = insertBankQuestion(fixture, "Abandoned claim", "UNANSWERED",
                OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = ? where id = ?",
                fixture.sessionId, unappended);
        jdbc.update("""
                update quick_review_sessions set session_state = session_state ||
                jsonb_build_object('moreToken', 'old-token', 'moreStopped', true,
                                   'moreStartedAtEpochSeconds', 1)
                where id = ?
                """, fixture.sessionId);
        captureMoreJob();
        assertThat(challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId)
                .morePending()).isTrue();
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                UUID.class, unappended)).isNull();
        assertThat(jdbc.queryForObject("""
                select claimed_session_id from challenge_quiz_question_bank
                where study_pack_id = ? and question_key = 'bank question'
                """, UUID.class, fixture.packId)).isEqualTo(fixture.sessionId);
    }

    @Test
    void stalePendingMarkerWithoutWorkerReleasesOldClaimsAndAllowsNewGenerateMore() {
        Fixture fixture = seed(false);
        UUID abandoned = insertBankQuestion(fixture, "Abandoned missed question", "INCORRECT",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = ? where id = ?",
                fixture.sessionId, abandoned);
        jdbc.update("""
                update quick_review_sessions set session_state = session_state ||
                jsonb_build_object('moreToken', 'lost-worker', 'moreStartedAtEpochSeconds', 1)
                where id = ?
                """, fixture.sessionId);
        var job = captureMoreJob();
        stubMoreGeneration(moreQuestions());

        assertThat(challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId)
                .morePending()).isTrue();
        var pending = sessionRepository.findById(fixture.sessionId).orElseThrow().getSessionState();
        assertThat(QuizSessionStateUtils.extractMoreToken(pending)).isNotEqualTo("lost-worker");
        assertThat(QuizSessionStateUtils.extractMoreFailed(pending)).isFalse();
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where id = ?",
                UUID.class, abandoned)).isNull();
        assertThat(jdbc.queryForObject("select last_known_outcome from challenge_quiz_question_bank where id = ?",
                String.class, abandoned)).isEqualTo("INCORRECT");
        assertThat(jdbc.queryForObject("""
                select claimed_session_id from challenge_quiz_question_bank
                where study_pack_id = ? and question_key = 'bank question'
                """, UUID.class, fixture.packId)).isEqualTo(fixture.sessionId);

        job.get().run();
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractMoreToken(saved.getSessionState())).isNull();
        assertThat(QuizSessionStateUtils.extractMoreFailed(saved.getSessionState())).isFalse();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(6);
    }

    @Test
    void timerExpiringDuringGenerationPreventsAppendAndExtension() {
        Fixture fixture = seed(false);
        var job = captureMoreJob();
        stubMoreGeneration(moreQuestions());
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        // The worker has not loaded yet; make it expire after its short load instead via a model callback.
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    jdbc.update("update quick_review_sessions set session_state = jsonb_set(session_state, '{timerStartedAtEpochSeconds}', '1'::jsonb) where id = ?", fixture.sessionId);
                    return GeneratedChallengeQuizContent.withoutUsage(moreQuestions());
                });
        job.get().run();
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(1);
        assertThat(saved.getSessionState().get("timeLimitSeconds")).isEqualTo(900);
    }

    @Test
    void regeneratedPackDuringGenerateMoreDoesNotAppendOrBankStaleQuestions() {
        Fixture fixture = seed(false);
        var job = captureMoreJob();
        when(quizGenerationService.generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    jdbc.update("update study_packs set generation_stamp = generation_stamp + 1 where id = ?", fixture.packId);
                    return GeneratedChallengeQuizContent.withoutUsage(moreQuestions());
                });
        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);
        job.get().run();
        var saved = sessionRepository.findById(fixture.sessionId).orElseThrow();
        assertThat(QuizSessionStateUtils.extractQuiz(saved.getSessionState())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from challenge_quiz_question_bank where study_pack_id = ?",
                Integer.class, fixture.packId)).isEqualTo(1);
    }

    private Fixture seed(boolean expired) {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        jdbc.update("insert into users (id, email, username, password_hash, role, first_name, last_name, created_at, updated_at)"
                        + " values (?, ?, ?, 'x', 'USER', 'Test', 'User', now(), now())",
                userId, userId + "@example.test", "test-" + userId.toString().substring(0, 8));
        jdbc.update("insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type, created_at, updated_at)"
                        + " values (?, ?, 'Bank race', 'body', 'PRIVATE', ARRAY[]::text[], 'STUDENT', now(), now())",
                noteId, userId);
        jdbc.update("insert into study_packs (id, owner_user_id, note_id, input_type, title, summary, key_concepts, quiz, model_used, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'TEXT', 'Bank race', 'old summary', '[]'::jsonb, '[]'::jsonb, 'test', 'DONE', now(), now())",
                packId, userId, noteId);
        String state = "{\"mode\":\"challenge\",\"difficulty\":\"medium\",\"quiz\":[{\"question\":\"Bank question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0,\"keyConcept\":\"Concept\"}],\"timeLimitSeconds\":900,\"timerStartedAtEpochSeconds\":"
                + (expired ? 1 : java.time.Instant.now().getEpochSecond()) + "}";
        jdbc.update("insert into quick_review_sessions (id, user_id, study_pack_id, note_id, session_mode, status, current_question_index, current_round, total_questions, session_state, created_at)"
                        + " values (?, ?, ?, ?, 'CHALLENGE', 'IN_PROGRESS', 0, 'INITIAL', 1, ?::jsonb, now())",
                sessionId, userId, packId, noteId, state);
        jdbc.update("insert into challenge_quiz_question_bank (id, user_id, study_pack_id, origin_session_id, question_key, question, learner_level, last_known_outcome, claimed_session_id, generated_at)"
                        + " values (?, ?, ?, ?, 'bank question', '{\"question\":\"Bank question\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0,\"keyConcept\":\"Concept\"}'::jsonb, 'COLLEGE', 'UNANSWERED', ?, now())",
                UUID.randomUUID(), userId, packId, sessionId, sessionId);
        return new Fixture(userId, packId, sessionId);
    }

    private UUID addSecondPackWithQuestion(Fixture fixture, String questionText) {
        UUID noteId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        jdbc.update("""
                insert into notes (id, owner_user_id, title, content, visibility, tags,
                                   target_profile_type, created_at, updated_at)
                values (?, ?, 'Second bank', 'body', 'PRIVATE', ARRAY[]::text[], 'STUDENT', now(), now())
                """, noteId, fixture.userId);
        jdbc.update("""
                insert into study_packs (id, owner_user_id, note_id, input_type, title, summary,
                                         key_concepts, quiz, model_used, status, created_at, updated_at)
                values (?, ?, ?, 'TEXT', 'Second bank', 'summary', '[]'::jsonb, '[]'::jsonb,
                        'test', 'DONE', now(), now())
                """, packId, fixture.userId, noteId);
        jdbc.update("""
                insert into challenge_quiz_question_bank
                    (id, user_id, study_pack_id, origin_session_id, question_key, question,
                     learner_level, last_known_outcome, claimed_session_id, generated_at)
                values (?, ?, ?, ?, ?, ?::jsonb, 'COLLEGE', 'UNANSWERED', null, now())
                """, UUID.randomUUID(), fixture.userId, packId, fixture.sessionId,
                com.studysnap.backend.util.QuizDeduplicationUtils.normalizeQuestion(questionText),
                "{\"question\":\"" + questionText + "\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0,\"keyConcept\":\"Concept\"}");
        return packId;
    }

    private UUID insertBankQuestion(Fixture fixture, String text, String outcome, OffsetDateTime generatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into challenge_quiz_question_bank
                    (id, user_id, study_pack_id, origin_session_id, question_key, question,
                     learner_level, last_known_outcome, claimed_session_id, generated_at)
                values (?, ?, ?, ?, ?, ?::jsonb, 'COLLEGE', ?, null, ?)
                """, id, fixture.userId, fixture.packId, fixture.sessionId,
                com.studysnap.backend.util.QuizDeduplicationUtils.normalizeQuestion(text),
                "{\"question\":\"" + text + "\",\"choices\":[\"A\",\"B\"],\"correctIndex\":0,\"keyConcept\":\"Concept\"}",
                outcome, generatedAt);
        return id;
    }

    private String bankRowJson(UUID id) {
        return jdbc.queryForObject("select to_jsonb(bank)::text from challenge_quiz_question_bank bank where id = ?",
                String.class, id);
    }

    private record Fixture(UUID userId, UUID packId, UUID sessionId) {}
}
