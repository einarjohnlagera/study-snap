package com.studysnap.backend.service;

import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankOwnerProjection;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Autowired ChallengeQuizService challengeQuizService;
    @Autowired ChallengeQuizQuestionBankService bankService;
    @Autowired AdminStudyPackTransactionHelper adminStudyPackTransactionHelper;
    @Autowired StudyPackRepository studyPackRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ChallengeQuizQuestionBankRepository bankRepository;

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
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(
                fixture.sessionId.toString(), fixture.userId)).isInstanceOf(RuntimeException.class);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(elapsedMillis).isLessThan(1800);
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isEqualTo(fixture.sessionId);
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
    void failedStartReleasesItsUncommittedClaim() {
        Fixture fixture = seed(false);
        jdbc.update("update quick_review_sessions set status = 'COMPLETED', completed_at = now() where id = ?",
                fixture.sessionId);
        jdbc.update("update challenge_quiz_question_bank set claimed_session_id = null where study_pack_id = ?",
                fixture.packId);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(),
                any(Integer.class), any(), any())).thenThrow(new IllegalStateException("LLM failure"));
        challengeQuizService.startSession(fixture.packId.toString(), fixture.userId, null);
        UUID failedSessionId = jdbc.queryForObject("""
                select id from quick_review_sessions
                where study_pack_id = ? and status = 'FAILED' order by created_at desc limit 1
                """, UUID.class, fixture.packId);
        assertThat(failedSessionId).isNotNull();
        assertThat(jdbc.queryForObject("select claimed_session_id from challenge_quiz_question_bank where study_pack_id = ?",
                UUID.class, fixture.packId)).isNull();
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
        try (var executor = Executors.newSingleThreadExecutor()) {
            var more = executor.submit(() -> challengeQuizService.generateMoreQuestions(
                    fixture.sessionId.toString(), fixture.userId));
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
                    Integer.class, fixture.packId)).isEqualTo(5);
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

        challengeQuizService.generateMoreQuestions(fixture.sessionId.toString(), fixture.userId);

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
        // that row's question_key and collide with uq_challenge_quiz_question_bank_user_pack_key.
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
            bankService.releaseClaims(fixture.userId, fixture.packId, fixture.sessionId);
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

    @Test
    @Timeout(15)
    void completionLocksItsClaimedRowsSoDeleteCommitsAfterCompletion() throws Exception {
        Fixture fixture = seed(false);
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        MethodInterceptor pauseAfterLockedRead = invocation -> {
            Object result = invocation.proceed();
            if (!invocation.getMethod().getName().equals("findByUserIdAndStudyPackIdAndClaimedSessionId")) {
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

    private record Fixture(UUID userId, UUID packId, UUID sessionId) {}
}
