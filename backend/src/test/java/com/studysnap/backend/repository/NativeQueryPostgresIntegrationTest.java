package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CombinedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerInvitationLinkEntity;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.dto.BulkRegenerateNotesRequest;
import com.studysnap.backend.dto.NoteRegenerationPreflightRequest;
import com.studysnap.backend.dto.NoteRegenerationPreflightResponse;
import com.studysnap.backend.exception.BulkNoteRegenerationQuotaExceededException;
import com.studysnap.backend.service.BulkGenerationFailureReasonNormalizer;
import com.studysnap.backend.service.MePlanService;
import com.studysnap.backend.exception.BulkRegenerationNotPermittedException;
import com.studysnap.backend.service.BulkRegenerationAccessGuard;
import com.studysnap.backend.service.NoteBulkRegenerationService;
import com.studysnap.backend.service.NoteBulkRegenerationTaskDispatcher;
import com.studysnap.backend.service.NoteRegenerationConsequenceService;
import com.studysnap.backend.service.NoteRegenerationPreflightService;
import com.studysnap.backend.service.NoteBulkRegenerationReceiptService;
import com.studysnap.backend.service.NoteRegenerationReadinessService;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.ActivityTrackingService;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingUsagePeriodService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.ContentModerationService;
import com.studysnap.backend.service.EmailService;
import com.studysnap.backend.service.ExamQuestionPoolService;
import com.studysnap.backend.service.ExportUsageProtectionService;
import com.studysnap.backend.service.FeatureGateService;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.EmailTemplateService;
import com.studysnap.backend.service.GuardianConsentPolicy;
import com.studysnap.backend.service.LinkedLearnerService;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.NoteGenerationService;
import com.studysnap.backend.service.NoteGenerationUsageProtectionService;
import com.studysnap.backend.service.NoteShareService;
import com.studysnap.backend.service.OcrService;
import com.studysnap.backend.service.OcrUsageProtectionService;
import com.studysnap.backend.service.OfficialChallengeQuizTemplateService;
import com.studysnap.backend.service.OnboardingGuardService;
import com.studysnap.backend.service.QuizDocxExportService;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.StudyPackGenerationTaskDispatcher;
import com.studysnap.backend.service.StudyPackQuizMasteryService;
import com.studysnap.backend.service.StudyPackService;
import com.studysnap.backend.service.StudyPackUsageService;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.service.jobs.LinkedLearnerRequestExpiryWorker;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.security.InvitationRateLimitService;
import com.studysnap.backend.model.NoteLibraryReadiness;
import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.PublicLibrarySort;
import com.studysnap.backend.model.PublicLibrarySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Executes the repository's native SQL against the production database engine and the full Flyway schema.
 *
 * <p>⚠️ Flyway is enabled only for this isolated test context. The shared test {@code application.yaml}
 * deliberately keeps Flyway disabled and H2 configured for the rest of the suite.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(NativeQueryPostgresIntegrationTest.DockerRequiredUnlessOptedOut.class)
@Import(LinkedLearnerRequestExpiryWorker.class)
class NativeQueryPostgresIntegrationTest {
    private static final String SKIP_PROPERTY = "nativequery.pg.skip";
    private static final String SKIP_FLAG = "-D" + SKIP_PROPERTY + "=true";
    private static final String CLASS_SUFFIX = ".class";
    private static final String SEARCH_PATTERN = "%heart%";
    /**
     * The exact count present today. ADDING a native query fails this until the number is raised —
     * deliberately, so the addition is noticed. A looser bound was tried first and rejected at the
     * v0.93.0 pressure test: at 25 against an actual 31, the reflective scan could silently degrade by
     * six queries and stay green, which is the same false comfort the harness exists to remove.
     */
    private static final int EXPECTED_NATIVE_QUERIES = 40;
    private static final String REPOSITORY_CLASSES =
            "classpath*:com/studysnap/backend/repository/**/*.class";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;

    @Autowired
    private NoteLibraryRepositoryImpl noteLibraryRepository;

    @Autowired
    private PublicLibraryRepositoryImpl publicLibraryRepository;

    @Autowired
    private CombinedQuizRepository combinedQuizRepository;
    @Autowired
    private LinkedLearnerGrantRepository grantRepository;

    @Autowired
    private LinkedLearnerRelationshipRepository relationshipRepository;

    @Autowired
    private LinkedLearnerInvitationRepository invitationRepository;

    @Autowired
    private LinkedLearnerGuardianConsentRepository consentRepository;

    @Autowired
    private LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUsageRepository userUsageRepository;

    @Autowired
    private LinkedLearnerInvitationLinkRepository invitationLinkRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private StudyPackRepository studyPackRepository;

    @Autowired
    private GeneratedQuizRepository generatedQuizRepository;

    @Autowired
    private QuizShareLinkRepository quizShareLinkRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ConceptHealthRepository conceptHealthRepository;

    @Autowired
    private NoteBulkRegenerationItemRepository bulkRegenerationItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LinkedLearnerRequestExpiryWorker requestExpiryWorker;

    /** Killing test for removing the pack index or changing its non-null predicate to the wrong leg. */
    @Test
    void activeNoteScopedSessionsOnTheSamePackStillCollide() {
        UUID userId = seedUser("session-pack-collision");
        UUID noteA = seedPublicNote(userId, "Pack collision A", new String[] {});
        UUID noteB = seedPublicNote(userId, "Pack collision B", new String[] {});
        UUID packId = seedStudyPack(userId, noteA, "Pack collision");
        seedQuizSession(userId, packId, noteA, null);

        assertThatThrownBy(() -> seedQuizSession(userId, packId, noteB, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_quick_review_sessions_one_active_generation");
    }

    /** Killing test for removing the note index or changing its non-null predicate to the wrong leg. */
    @Test
    void activeNoteScopedSessionsOnTheSameNoteStillCollide() {
        UUID userId = seedUser("session-note-collision");
        UUID noteA = seedPublicNote(userId, "Note collision A", new String[] {});
        UUID noteB = seedPublicNote(userId, "Note collision B", new String[] {});
        UUID packA = seedStudyPack(userId, noteA, "Note collision pack A");
        UUID packB = seedStudyPack(userId, noteB, "Note collision pack B");
        seedQuizSession(userId, packA, noteA, null);

        assertThatThrownBy(() -> seedQuizSession(userId, packB, noteA, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_quick_review_sessions_one_active_generation_note");
    }

    /**
     * Pins that the SCHEMA permits a collection-anchored and a note-anchored ADAPTIVE session to be
     * active at once -- the contention this release exists to end.
     *
     * <p>⚠️ This is NOT a killing test for the service accidentally retaining a borrowed pack anchor.
     * Both rows are inserted here with their anchor shapes hardcoded, so reverting the service would
     * not fail it. That property is guarded one layer up, where the defect would actually live:
     * {@code QuickReviewAdaptivePracticeServiceTest.collectionScoped_writesOnlyTheCollectionAnchorAndKeepsTheLegacyJsonKey}.
     */
    @Test
    void activeCollectionAndNoteScopedAdaptiveSessionsCoexist() {
        UUID userId = seedUser("session-anchor-coexist");
        UUID noteId = seedPublicNote(userId, "Shared source", new String[] {});
        UUID packId = seedStudyPack(userId, noteId, "Shared source pack");
        UUID collectionId = seedCollection(userId, "Shared source plan");
        seedCollectionItem(collectionId, noteId);

        UUID noteSession = seedQuizSession(userId, packId, noteId, null);
        UUID collectionSession = seedQuizSession(userId, null, null, collectionId);

        assertThat(jdbcTemplate.queryForList(
                "select id from quick_review_sessions where id in (?, ?)",
                UUID.class,
                noteSession,
                collectionSession
        )).containsExactlyInAnyOrder(noteSession, collectionSession);
    }

    /** Killing test for removing V133's collection-scoped partial unique index. */
    @Test
    void activeCollectionScopedSessionsOnTheSamePlanStillCollide() {
        UUID userId = seedUser("session-collection-collision");
        UUID collectionId = seedCollection(userId, "Collision plan");
        seedQuizSession(userId, null, null, collectionId);

        assertThatThrownBy(() -> seedQuizSession(userId, null, null, collectionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_quick_review_sessions_one_active_generation_collection");
    }

    /** Killing test for removing V133's anchor CHECK entirely. */
    @Test
    void anchorlessQuizSessionIsRejected() {
        UUID userId = seedUser("session-anchorless");

        assertThatThrownBy(() -> seedQuizSession(userId, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_quick_review_sessions_anchor");
    }

    /**
     * Killing test for changing {@code <=} to {@code <}, dropping the PENDING predicate, or selecting
     * a future request. The exact-boundary row is deliberate: an "old enough" fixture cannot
     * distinguish the two comparison operators.
     */
    @Test
    void dueRequestFinderIncludesTheExactBoundaryAndOnlyPendingRows() {
        OffsetDateTime boundary = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        UUID due = seedRelationship(seedUser("due-supporter"), seedUser("due-learner"), "PENDING");
        UUID future = seedRelationship(seedUser("future-supporter"), seedUser("future-learner"), "PENDING");
        UUID accepted = seedRelationship(
                seedUser("accepted-due-supporter"), seedUser("accepted-due-learner"), "ACCEPTED");
        setRelationshipExpiry(due, boundary);
        setRelationshipExpiry(future, boundary.plusSeconds(1));
        setRelationshipExpiry(accepted, boundary.minusDays(1));

        assertThat(relationshipRepository.findDuePendingIds(boundary, 500))
                .containsExactly(due);
    }

    /**
     * Killing test for changing the transition literal from PENDING to ACCEPTED and for deleting
     * its status predicate. Both a target PENDING row and a different terminal row are required.
     */
    @Test
    void markExpiredIfPendingTransitionsOnlyItsPendingTarget() {
        OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        UUID pending = seedRelationship(seedUser("expire-supporter"), seedUser("expire-learner"), "PENDING");
        // A due deadline is part of the fixture, not scenery: the statement re-checks it, so a
        // PENDING row with no deadline is deliberately unexpirable.
        setRelationshipExpiry(pending, expiredAt.minusDays(1));
        UUID accepted = seedRelationship(
                seedUser("not-expire-supporter"), seedUser("not-expire-learner"), "ACCEPTED");
        UUID undated = seedRelationship(
                seedUser("undated-supporter"), seedUser("undated-learner"), "PENDING");

        assertThat(relationshipRepository.markExpiredIfPending(accepted, expiredAt)).isZero();
        assertThat(relationshipRepository.markExpiredIfPending(undated, expiredAt))
                .as("a PENDING row with no deadline is a consent pause, not an unconfirmed request")
                .isZero();
        assertThat(relationshipRepository.markExpiredIfPending(pending, expiredAt)).isOne();
        assertThat(relationshipStatus(pending)).isEqualTo("EXPIRED");
        assertThat(relationshipStatus(accepted)).isEqualTo("ACCEPTED");
        assertThat(relationshipStatus(undated)).isEqualTo("PENDING");
    }

    /** Killing test for removing expires_at = null from markAcceptedIfPending. */
    @Test
    void acceptingPendingRelationshipClearsItsExpiryDeadline() {
        UUID relationshipId = seedRelationship(
                seedUser("accept-expiry-supporter"), seedUser("accept-expiry-learner"), "PENDING");
        setRelationshipExpiry(relationshipId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));

        assertThat(relationshipRepository.markAcceptedIfPending(
                relationshipId, OffsetDateTime.now(ZoneOffset.UTC))).isOne();

        assertThat(relationshipStatus(relationshipId)).isEqualTo("ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "select expires_at is null from linked_learner_relationships where id = ?",
                Boolean.class, relationshipId)).isTrue();
    }

    @Test
    void expiredPairCanCreateANewRelationshipWhileInvitationStatusVocabularyStaysClosed() {
        UUID supporter = seedUser("reinvite-supporter");
        UUID learner = seedUser("reinvite-learner");
        seedRelationship(supporter, learner, "EXPIRED");
        UUID replacement = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(relationshipRepository.insertPendingIfAbsent(
                replacement, supporter, learner, LinkedLearnerSide.SUPPORTER.name(),
                createdAt, createdAt.plusDays(30))).isOne();
        assertThat(relationshipStatus(replacement)).isEqualTo("PENDING");

        String invitationConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                  from pg_constraint
                 where conname = 'ck_linked_learner_invitation_status'
                """, String.class);
        assertThat(invitationConstraint)
                .contains("PENDING", "ACCEPTED", "REVOKED")
                .doesNotContain("EXPIRED");
    }

    @Test
    void expiryWorkerDeletesProvisionalYearOnlyAfterWinningThePendingTransition() {
        UUID pendingLearner = seedUser("worker-pending-learner");
        UUID pending = seedRelationship(seedUser("worker-pending-supporter"), pendingLearner, "PENDING");
        setRelationshipExpiry(pending, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        seedProvisional(pending, Year.now().getValue() - 12);
        UUID acceptedLearner = seedUser("worker-accepted-learner");
        UUID accepted = seedRelationship(seedUser("worker-accepted-supporter"), acceptedLearner, "ACCEPTED");
        seedProvisional(accepted, Year.now().getValue() - 13);

        assertThat(requestExpiryWorker.expire(pending, OffsetDateTime.now(ZoneOffset.UTC))).isTrue();
        assertThat(relationshipStatus(pending)).isEqualTo("EXPIRED");
        assertThat(provisionalRows(pending)).isZero();

        assertThat(requestExpiryWorker.expire(accepted, OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
        assertThat(relationshipStatus(accepted)).isEqualTo("ACCEPTED");
        assertThat(provisionalRows(accepted))
                .as("a zero-row conditional transition must not delete the declaration").isOne();
    }

    /**
     * Item 6: real PostgreSQL rows, two real transactions and both commit orders for every writer
     * sharing the learner lock. The invariant is checked after each interleaving, not inferred from
     * affected-row counts: ACCEPTED + provisional + null account year is unremediable and forbidden.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requestExpiryRacesCannotStrandAnAcceptedLearnerInEitherCommitOrder() throws Exception {
        runAcceptExpiryRace(true, "accept-first");
        runAcceptExpiryRace(false, "expiry-first");
        runRevokeExpiryRace(true, "revoke-first");
        runRevokeExpiryRace(false, "expiry-before-revoke");
        runCorrectionExpiryRace(true, "correction-first");
        runCorrectionExpiryRace(false, "expiry-before-correction");
    }

    @Test
    void everyAnnotatedNativeQueryPreparesAgainstPostgres16() throws Exception {
        List<NativeQueryMethod> queries = findNativeQueries();

        // ⚠️ Exact by design. `isNotEmpty()` or a lower bound would stay green if the reflective scan
        // silently lost queries. Adding a native query must raise EXPECTED_NATIVE_QUERIES deliberately.
        assertThat(queries)
                .as("reflective scan over repository @Query(nativeQuery = true) methods")
                .hasSize(EXPECTED_NATIVE_QUERIES);
        for (int index = 0; index < queries.size(); index++) {
            prepare(queries.get(index), index);
        }
        System.out.printf("PREPARED %d repository-native queries against PostgreSQL 16.%n", queries.size());
    }

    /**
     * ⚠️ The ONLY test that executes the live-grant insert against real data.
     *
     * <p>A cold-agent pressure test mutated this statement two ways at once — {@code 'ACCEPTED'} to
     * {@code 'PENDING'} AND the {@code relationship.id = :relationshipId} predicate deleted, so the
     * insert matches any row in the table — and <strong>all 1,760 tests passed</strong>, because every
     * {@code LinkedLearnerGrantRepository} reference in the test tree is a Mockito mock. The
     * {@code PREPARE} sweep above did not help: it validates syntax, types and {@code ON CONFLICT}
     * arbiter resolution, never predicate correctness.
     *
     * <p>The second relationship is not padding — it is what kills the deleted-id-predicate mutant.
     * Without another ACCEPTED row present while this one is PENDING, an insert that stopped filtering
     * on the relationship id would still return 0 and the mutation would survive.
     */
    @Test
    void liveGrantInsertIsScopedToItsOwnRelationshipAndRequiresAccepted() {
        UUID supporter = seedUser("grant-supporter");
        UUID learner = seedUser("grant-learner");
        UUID pausedRelationship = seedRelationship(supporter, learner, "PENDING");
        seedRelationship(seedUser("other-supporter"), seedUser("other-learner"), "ACCEPTED");

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("insert against a PENDING relationship while a DIFFERENT relationship is ACCEPTED")
                .isZero();
        assertThat(liveGrants(pausedRelationship)).isZero();

        jdbcTemplate.update(
                "update linked_learner_relationships set status = 'ACCEPTED' where id = ?", pausedRelationship);

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("insert once this relationship is ACCEPTED")
                .isEqualTo(1);
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("repeat insert is idempotent via the live-row partial unique index")
                .isZero();
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);

        jdbcTemplate.update(
                "update linked_learner_grants set revoked_at = now() where relationship_id = ?",
                pausedRelationship);
        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("a revoked row does not block re-granting")
                .isEqualTo(1);
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);
    }

    /** Executes the single-use predicate against real rows and two real PostgreSQL transactions. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInvitationLinkRedemptionHasExactlyOneWinner() throws Exception {
        UUID creator = seedUser("link-creator");
        UUID firstRedeemer = seedUser("link-redeemer-one");
        UUID secondRedeemer = seedUser("link-redeemer-two");
        String token = "RaceTokn0123456789AbCd";
        seedInvitationLink(token, creator);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Integer> results = race(
                () -> invitationLinkRepository.markRedeemedIfUsable(token, firstRedeemer, now),
                () -> invitationLinkRepository.markRedeemedIfUsable(token, secondRedeemer, now));

        assertThat(results).containsExactlyInAnyOrder(0, 1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from linked_learner_invitation_links where token = ? and redeemed_at is not null",
                Integer.class,
                token
        )).isEqualTo(1);
    }

    /**
     * ⚠️ THE DETERMINISTIC COUNTERPART to the race below, and the reason it exists is recorded in
     * v0.94.0: dropping {@code redeemedAt is null} from {@code markRevokedIfUsable} SURVIVED FOUR
     * TARGETED RUNS of that race and was caught once under full-suite load — and then only as a
     * DataIntegrityViolationException from the terminal-state CHECK constraint, not as the assertion
     * failing. A test that detects a defect one time in five is a sampler, not a guard.
     *
     * <p>This pins the predicate directly: once a link is redeemed it is TERMINAL, so revoking it
     * must affect zero rows and must not disturb the redemption. No threads, no timing.
     *
     * <p>⚠️ In production the CHECK constraint is the real guard, so the JPQL clause is defence in
     * depth — which is exactly why it needs its own test rather than relying on a race to notice it.
     */
    @Test
    void revokingAnAlreadyRedeemedLinkAffectsNothingAndLeavesTheRedemptionIntact() {
        UUID creator = seedUser("terminal-revoke-creator");
        UUID redeemer = seedUser("terminal-revoke-redeemer");
        UUID linkId = UUID.randomUUID();
        String token = "TerminalRvk123456789Ab";
        seedInvitationLink(linkId, token, creator);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(invitationLinkRepository.markRedeemedIfUsable(token, redeemer, now)).isOne();

        assertThat(invitationLinkRepository.markRevokedIfUsable(linkId, creator, now))
                .as("a redeemed link is terminal; revoking it must be a no-op")
                .isZero();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select revoked_at, redeemed_at from linked_learner_invitation_links where id = ?", linkId);
        assertThat(row.get("redeemed_at")).as("the redemption must survive").isNotNull();
        assertThat(row.get("revoked_at")).as("and must not be overwritten by a late revoke").isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invitationLinkRevocationAndRedemptionHaveExactlyOneWinner() throws Exception {
        UUID creator = seedUser("revoke-creator");
        UUID redeemer = seedUser("revoke-redeemer");
        UUID linkId = UUID.randomUUID();
        String token = "RevokeRac0123456789AbC";
        seedInvitationLink(linkId, token, creator);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Integer> results = race(
                () -> invitationLinkRepository.markRedeemedIfUsable(token, redeemer, now),
                () -> invitationLinkRepository.markRevokedIfUsable(linkId, creator, now));

        assertThat(results).containsExactlyInAnyOrder(0, 1);
        Map<String, Object> terminal = jdbcTemplate.queryForMap(
                "select revoked_at, redeemed_at from linked_learner_invitation_links where id = ?", linkId);
        assertThat((terminal.get("revoked_at") == null) ^ (terminal.get("redeemed_at") == null)).isTrue();
    }

    private List<Integer> race(ThrowingIntSupplier first, ThrowingIntSupplier second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> inTransaction(ready, start, first));
            Future<Integer> secondResult = executor.submit(() -> inTransaction(ready, start, second));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private int inTransaction(
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingIntSupplier operation
    ) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ready.countDown();
            try {
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to start conditional-write race");
                }
                return operation.getAsInt();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Conditional-write race was interrupted", interrupted);
            }
        });
    }

    private void runAcceptExpiryRace(boolean acceptCommitsFirst, String fixture) throws Exception {
        UUID supporter = seedUser(fixture + "-supporter");
        UUID learner = seedUser(fixture + "-learner");
        UUID relationshipId = seedRelationship(
                supporter, learner, "PENDING", LinkedLearnerSide.LEARNER.name());
        seedProvisional(relationshipId, Year.now().getValue() - 25);
        setRelationshipExpiry(relationshipId, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        LinkedLearnerService service = linkedLearnerService();
        ThrowingRunnable accept = () -> {
            try {
                service.accept(relationshipId, supporter, new AcceptLinkedLearnerRequest(null, false));
            } catch (LinkedLearnerInvalidStateException | LinkedLearnerBirthYearRequiredException expected) {
                // Expiry won. The final-row invariant below distinguishes this legitimate loss from
                // a swallowed acceptance defect.
            }
        };
        ThrowingRunnable expire = () -> requestExpiryWorker.expire(
                relationshipId, OffsetDateTime.now(ZoneOffset.UTC));

        runWithFirstCommit(learner, acceptCommitsFirst ? accept : expire, acceptCommitsFirst ? expire : accept);
        assertNoUnremediableAcceptedRelationship(relationshipId);
        // ⚠️ runWithFirstCommit SERIALISES the two writers, so this is deterministic, not racy:
        // accept() never inspects expires_at, so it wins unconditionally on a PENDING row.
        // The old isIn("ACCEPTED","EXPIRED") permitted the exact regression this exists to
        // catch — accept() throwing, the catch below swallowing it, expiry then winning.
        assertThat(relationshipStatus(relationshipId))
                .isEqualTo(acceptCommitsFirst ? "ACCEPTED" : "EXPIRED");
    }

    private void runRevokeExpiryRace(boolean revokeCommitsFirst, String fixture) throws Exception {
        UUID supporter = seedUser(fixture + "-supporter");
        UUID learner = seedUser(fixture + "-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");
        seedProvisional(relationshipId, Year.now().getValue() - 12);
        setRelationshipExpiry(relationshipId, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        LinkedLearnerService service = linkedLearnerService();
        ThrowingRunnable revoke = () -> service.revoke(relationshipId, learner);
        ThrowingRunnable expire = () -> requestExpiryWorker.expire(
                relationshipId, OffsetDateTime.now(ZoneOffset.UTC));

        runWithFirstCommit(learner, revokeCommitsFirst ? revoke : expire, revokeCommitsFirst ? expire : revoke);
        assertNoUnremediableAcceptedRelationship(relationshipId);
        // Same determinism as the accept race above; revoke wins on both live statuses.
        assertThat(relationshipStatus(relationshipId))
                .isEqualTo(revokeCommitsFirst ? "REVOKED" : "EXPIRED");
        assertThat(provisionalRows(relationshipId)).isZero();
    }

    private void runCorrectionExpiryRace(boolean correctionCommitsFirst, String fixture) throws Exception {
        UUID supporter = seedUser(fixture + "-supporter");
        UUID learner = seedUser(fixture + "-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);
        // A real row makes the cleanup consequence observable if expiry wins after correction
        // pauses the relationship back to PENDING.
        seedProvisional(relationshipId, Year.now().getValue() - 12);
        LinkedLearnerService service = linkedLearnerService();
        ThrowingRunnable correction = () -> service.correctBirthYear(
                learner, Year.now().getValue() - 10);
        ThrowingRunnable expire = () -> requestExpiryWorker.expire(
                relationshipId, OffsetDateTime.now(ZoneOffset.UTC));

        runWithFirstCommit(
                learner,
                correctionCommitsFirst ? correction : expire,
                correctionCommitsFirst ? expire : correction);
        assertNoUnremediableAcceptedRelationship(relationshipId);
        assertThat(accountBirthYear(learner)).isEqualTo(Year.now().getValue() - 10);
        // Both orders converge on PENDING: expiry loses to an ACCEPTED row, and a paused row has
        // no deadline. Previously this permitted "EXPIRED", which is what let the defect pass.
        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");
    }

    /** Hold the first writer's learner lock through its write until the second thread is in-flight. */
    private void runWithFirstCommit(
            UUID learnerUserId,
            ThrowingRunnable first,
            ThrowingRunnable second
    ) throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstResult = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        userRepository.findByIdForUpdate(learnerUserId).orElseThrow();
                        first.run();
                        firstWritten.countDown();
                        await(releaseFirst, "release first expiry-race writer");
                    }));
            assertThat(firstWritten.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> secondResult = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        secondStarted.countDown();
                        second.run();
                    }));
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondResult.isDone())
                    .as("the second writer must wait behind the first writer's learner lock")
                    .isFalse();
            releaseFirst.countDown();
            firstResult.get(10, TimeUnit.SECONDS);
            secondResult.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch, String purpose) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to " + purpose);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to " + purpose, interrupted);
        }
    }

    private void assertNoUnremediableAcceptedRelationship(UUID relationshipId) {
        Integer forbiddenRows = jdbcTemplate.queryForObject("""
                select count(*)
                  from linked_learner_relationships r
                  join users u on u.id = r.learner_user_id
                  join linked_learner_provisional_birth_years p on p.relationship_id = r.id
                 where r.id = ?
                   and r.status = 'ACCEPTED'
                   and u.birth_year is null
                """, Integer.class, relationshipId);
        assertThat(forbiddenRows)
                .as("no ACCEPTED relationship may retain provisional data while account year is null")
                .isZero();
    }

    /**
     * ⚠️ The ONLY test that proves a revoked, redeemed or expired link is actually unusable.
     *
     * <p>Added at the item-2 audit. `LinkedLearnerInvitationLinkServiceTest`'s
     * {@code unknownRevokedExpiredAndRedeemedAllUseOneNotFoundContract} stubs
     * {@code findUsableByToken} to return empty for ALL FOUR token strings, so the four cases are
     * one case — it proves the exception is constructed identically, which was never in doubt, and
     * proves nothing about the query predicate. Deleting {@code revokedAt is null} from
     * {@code findUsableByToken} left the whole 1,775-test suite green.
     *
     * <p>Indistinguishability is a property of the PREDICATE, so only a real row can establish it.
     */
    @Test
    void revokedRedeemedAndExpiredInvitationLinksAreAllUnusable() {
        UUID creator = seedUser("link-creator");
        UUID redeemer = seedUser("link-redeemer");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        seedInvitationLink("LiveTokenAAAAAAAAAAAAA", creator);
        assertThat(invitationLinkRepository.findUsableByToken("LiveTokenAAAAAAAAAAAAA", now))
                .as("a live, unexpired link is usable — the control for the three cases below")
                .isPresent();

        UUID revokedId = UUID.randomUUID();
        seedInvitationLink(revokedId, "RevokedTokenAAAAAAAAAA", creator);
        jdbcTemplate.update(
                "update linked_learner_invitation_links set revoked_at = now() where id = ?", revokedId);

        UUID redeemedId = UUID.randomUUID();
        seedInvitationLink(redeemedId, "RedeemedTokenAAAAAAAAA", creator);
        jdbcTemplate.update(
                "update linked_learner_invitation_links"
                        + " set redeemed_at = now(), redeemed_by_user_id = ? where id = ?",
                redeemer, redeemedId);

        UUID expiredId = UUID.randomUUID();
        seedInvitationLink(expiredId, "ExpiredTokenAAAAAAAAAA", creator);
        jdbcTemplate.update(
                "update linked_learner_invitation_links"
                        + " set expires_at = now() - interval '1 day' where id = ?", expiredId);

        assertThat(invitationLinkRepository.findUsableByToken("RevokedTokenAAAAAAAAAA", now))
                .as("revoked").isEmpty();
        assertThat(invitationLinkRepository.findUsableByToken("RedeemedTokenAAAAAAAAA", now))
                .as("redeemed").isEmpty();
        assertThat(invitationLinkRepository.findUsableByToken("ExpiredTokenAAAAAAAAAA", now))
                .as("expired").isEmpty();
        assertThat(invitationLinkRepository.findUsableByToken("UnknownTokenAAAAAAAAAA", now))
                .as("unknown").isEmpty();

        assertThat(invitationLinkRepository.markRedeemedIfUsable("RevokedTokenAAAAAAAAAA", redeemer, now))
                .as("a revoked link cannot be claimed").isZero();
        assertThat(invitationLinkRepository.markRedeemedIfUsable("ExpiredTokenAAAAAAAAAA", redeemer, now))
                .as("an expired link cannot be claimed").isZero();
    }

    /**
     * ⚠️ Covers the TWO link queries the earlier real-row test never reached.
     *
     * <p>A cold-agent pressure test neutralised `findLiveByCreator`'s creator predicate, its
     * `revokedAt is null` filter, and `markRevokedIfUsable`'s creator predicate — **all 1,786 tests
     * stayed green each time**. That matters because `list()` returns the token AND the full
     * invitation URL, so an unscoped creator filter would hand one person another's live links.
     *
     * <p>This is the THIRD recurrence of one class in this repository: a mocked repository cannot
     * test a predicate. `LinkedLearnerInvitationLinkRepository` has four queries; the release that
     * diagnosed the class added real-row coverage for two of them.
     */
    @Test
    void invitationLinkQueriesAreScopedToTheirCreatorAndToLiveRows() {
        UUID creator = seedUser("link-owner");
        UUID otherCreator = seedUser("link-other");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UUID mine = UUID.randomUUID();
        seedInvitationLink(mine, "OwnerLiveTokenAAAAAAAA", creator);
        seedInvitationLink("OtherLiveTokenAAAAAAAA", otherCreator);
        UUID revoked = UUID.randomUUID();
        seedInvitationLink(revoked, "OwnerRevokedTokenAAAAA", creator);
        jdbcTemplate.update(
                "update linked_learner_invitation_links set revoked_at = now() where id = ?", revoked);

        assertThat(invitationLinkRepository.findLiveByCreator(creator, now))
                .as("only this creator's live links — the list exposes tokens and full URLs")
                .extracting(LinkedLearnerInvitationLinkEntity::getId)
                .containsExactly(mine);

        assertThat(invitationLinkRepository.markRevokedIfUsable(mine, otherCreator, now))
                .as("a non-creator cannot revoke someone else's link")
                .isZero();
        assertThat(invitationLinkRepository.markRevokedIfUsable(mine, creator, now))
                .as("the creator can").isEqualTo(1);
        assertThat(invitationLinkRepository.markRevokedIfUsable(mine, creator, now))
                .as("revoking twice is not a second revocation").isZero();

        UUID claimable = UUID.randomUUID();
        seedInvitationLink(claimable, "OwnerClaimTokenAAAAAAA", creator);
        UUID redeemer = seedUser("link-redeemer2");
        assertThat(invitationLinkRepository.markRedeemedIfUsable("OwnerClaimTokenAAAAAAA", redeemer, now))
                .isEqualTo(1);
        assertThat(invitationLinkRepository.markRedeemedIfUsable("OwnerClaimTokenAAAAAAA", redeemer, now))
                .as("single-threaded second claim on an already-redeemed row").isZero();
    }

    /**
     * Headline acceptance test: consent remains reachable while the learner's account has no year,
     * then real acceptance promotes and erases the relationship-scoped declaration atomically.
     */
    @Test
    void supporterCanConsentAndAcceptALinkRedeemedMinorWithOnlyAProvisionalBirthYear() {
        UUID supporter = seedUser("provisional-supporter");
        UUID learner = seedUser("provisional-minor");
        UUID relationshipId = seedRelationship(
                supporter, learner, "PENDING", LinkedLearnerSide.LEARNER.name());
        int minorYear = Year.now().getValue() - 10;

        assertThat(provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                relationshipId, learner, minorYear, OffsetDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
        assertThat(accountBirthYear(learner)).isNull();

        LinkedLearnerService service = linkedLearnerService();
        assertThat(service.recordGuardianConsent(relationshipId, supporter).guardianConsentRecorded()).isTrue();
        assertThat(accountBirthYear(learner))
                .as("guardian consent must not promote before creator confirmation").isNull();

        var accepted = service.accept(
                relationshipId, supporter, new AcceptLinkedLearnerRequest(null, false));

        assertThat(accepted.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(accountBirthYear(learner)).isEqualTo(minorYear);
        assertThat(provisionalRows(relationshipId)).isZero();
        // ⚠️ Assert the RETURNED DTO, not just the columns. toResponse reads the effective year
        // AFTER promotion has written users.birth_year and cleanup has removed the provisional row,
        // so this is the one moment the projection could report a momentarily-absent year and tell
        // the supporter the learner's age is unknown on the very response that accepted them.
        assertThat(accepted.birthYearRequired())
                .as("promotion must be visible to the acceptance response").isFalse();
        assertThat(accepted.guardianConsentRequired()).isTrue();
        assertThat(accepted.guardianConsentRecorded()).isTrue();
    }

    /** Killing test for deleting provisional-row cleanup from LinkedLearnerService.revoke. */
    @Test
    void revokingAnUnconfirmedRedemptionDeletesItsProvisionalYearWithoutTouchingTheAccount() {
        UUID supporter = seedUser("revoke-provisional-supporter");
        UUID learner = seedUser("revoke-provisional-learner");
        UUID relationshipId = seedRelationship(
                supporter, learner, "PENDING", LinkedLearnerSide.LEARNER.name());
        provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                relationshipId, learner, Year.now().getValue() - 10, OffsetDateTime.now(ZoneOffset.UTC));

        linkedLearnerService().revoke(relationshipId, learner);

        assertThat(provisionalRows(relationshipId)).isZero();
        assertThat(accountBirthYear(learner)).isNull();
        assertThat(relationshipStatus(relationshipId)).isEqualTo("REVOKED");
    }

    /**
     * Kills both precedence inversion and a missing relationship-id predicate in effective lookup.
     * The unrelated provisional row is deliberately present when the selected relationship has one.
     */
    @Test
    void effectiveBirthYearPrefersTheAccountAndScopesTheFallbackToTheRelationship() {
        UUID supporter = seedUser("effective-supporter");
        UUID learner = seedUser("effective-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");
        UUID otherRelationshipId = seedRelationship(
                seedUser("effective-other-supporter"), learner, "PENDING");
        jdbcTemplate.update("update users set birth_year = 1998 where id = ?", learner);
        seedProvisional(relationshipId, 2011);
        seedProvisional(otherRelationshipId, 2013);

        assertThat(provisionalBirthYearRepository.findEffectiveBirthYear(relationshipId, learner))
                .contains(1998);

        jdbcTemplate.update("update users set birth_year = null where id = ?", learner);
        assertThat(provisionalBirthYearRepository.findEffectiveBirthYear(relationshipId, learner))
                .contains(2011);
        assertThat(provisionalBirthYearRepository.findEffectiveBirthYear(UUID.randomUUID(), learner))
                .as("an unrelated provisional declaration must not satisfy this relationship")
                .isEmpty();

        // ⚠️ A MISMATCHED PAIR must not resolve. Before the relationship join, the provisional row
        // was reached by relationship_id alone while the account row came from a separately-passed
        // learner id, with nothing tying them together — so a caller passing a real relationship
        // belonging to SOMEONE ELSE would coalesce that stranger's declared year onto this user's
        // consent decision. Every caller passes a matched pair today, which is precisely why such a
        // mismatch would never announce itself.
        UUID strangerLearner = seedUser("effective-stranger");
        UUID strangerRelationshipId = seedRelationship(
                seedUser("effective-stranger-supporter"), strangerLearner, "PENDING");
        seedProvisional(strangerRelationshipId, 2009);
        assertThat(provisionalBirthYearRepository
                .findEffectiveBirthYear(strangerRelationshipId, learner))
                .as("a relationship that does not belong to this learner must not supply their year")
                .isEmpty();
    }

    /** Real rows pin the write predicate, promotion guard/write-once rule, and scoped cleanup. */
    @Test
    void provisionalWritePromotionAndDeletionPredicatesAreRelationshipScopedAndWriteOnce() {
        UUID supporter = seedUser("predicate-supporter");
        UUID learner = seedUser("predicate-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");
        UUID otherRelationshipId = seedRelationship(
                seedUser("predicate-other-supporter"), seedUser("predicate-other-learner"), "PENDING");
        int declaredYear = Year.now().getValue() - 12;

        assertThat(provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                relationshipId, learner, declaredYear, OffsetDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
        seedProvisional(otherRelationshipId, Year.now().getValue() - 13);
        assertThat(provisionalBirthYearRepository.promoteIfAccountBirthYearMissing(
                relationshipId, learner, OffsetDateTime.now(ZoneOffset.UTC)))
                .as("PENDING is not real acceptance").isZero();

        jdbcTemplate.update(
                "update linked_learner_relationships set status = 'ACCEPTED' where id = ?", relationshipId);
        assertThat(provisionalBirthYearRepository.promoteIfAccountBirthYearMissing(
                relationshipId, learner, OffsetDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
        assertThat(accountBirthYear(learner)).isEqualTo(declaredYear);
        assertThat(provisionalBirthYearRepository.promoteIfAccountBirthYearMissing(
                relationshipId, learner, OffsetDateTime.now(ZoneOffset.UTC)))
                .as("a second promotion cannot overwrite the account-global value").isZero();

        assertThat(provisionalBirthYearRepository.deleteForRelationship(relationshipId)).isEqualTo(1);
        assertThat(provisionalRows(relationshipId)).isZero();
        assertThat(provisionalRows(otherRelationshipId))
                .as("cleanup must retain a different relationship's declaration").isOne();
        assertThat(provisionalBirthYearRepository.deleteForRelationship(relationshipId))
                .as("cleanup is idempotent").isZero();
    }

    @Test
    void existingAccountBirthYearPreventsAProvisionalWriteAndWinsAConcurrentPromotion() {
        UUID supporter = seedUser("existing-year-supporter");
        UUID learner = seedUser("existing-year-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");
        jdbcTemplate.update("update users set birth_year = 1997 where id = ?", learner);

        assertThat(provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                relationshipId, learner, 2012, OffsetDateTime.now(ZoneOffset.UTC))).isZero();
        assertThat(provisionalRows(relationshipId)).isZero();

        // Model a year becoming non-null after an older provisional row existed but before promote.
        seedProvisional(relationshipId, 2012);
        jdbcTemplate.update(
                "update linked_learner_relationships set status = 'ACCEPTED' where id = ?", relationshipId);
        assertThat(provisionalBirthYearRepository.promoteIfAccountBirthYearMissing(
                relationshipId, learner, OffsetDateTime.now(ZoneOffset.UTC))).isZero();
        assertThat(accountBirthYear(learner)).isEqualTo(1997);
        assertThat(provisionalBirthYearRepository.deleteForRelationship(relationshipId)).isOne();
    }

    private void seedInvitationLink(String token, UUID creatorUserId) {
        seedInvitationLink(UUID.randomUUID(), token, creatorUserId);
    }

    private void seedInvitationLink(UUID id, String token, UUID creatorUserId) {
        jdbcTemplate.update(
                "insert into linked_learner_invitation_links"
                        + " (id, token, creator_user_id, creator_role, created_at, expires_at)"
                        + " values (?, ?, ?, 'SUPPORTER', now(), now() + interval '1 day')",
                id, token, creatorUserId);
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private int insertGrant(UUID relationshipId, UUID fromUserId, UUID toUserId) {
        return grantRepository.insertLiveIfAbsent(
                UUID.randomUUID(),
                relationshipId,
                fromUserId,
                toUserId,
                LinkedLearnerGrantScope.PROGRESS.name(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private int liveGrants(UUID relationshipId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from linked_learner_grants where relationship_id = ? and revoked_at is null",
                Integer.class,
                relationshipId
        );
        return count == null ? 0 : count;
    }

    @Test
    /**
     * ⚠️ Renamed in v0.100.0, closing a v0.99.0 Known limitation. It was
     * {@code consentPausedRelationshipIsNeverExpiredBecauseAPauseIsNotATermination}, which claimed more
     * than it shows: {@code seedRelationship} never writes {@code expires_at}, so the row reaches the
     * sweep with a NULL deadline by OMISSION, and this test cannot tell "the worker respects a consent
     * pause" apart from "there was no deadline to act on."
     *
     * <p>What it does prove is still worth having, and is the reachable production shape: the worker is
     * handed this id — selection and execution are separate transactions by design — and leaves the row
     * PENDING rather than expiring it.
     *
     * <p>⚠️ The stronger property, that {@code pauseAcceptedForConsent} LEAVES the deadline NULL, is
     * covered by {@link #aPreMigrationConsentPausedRowIsNeverExpirableAtAnyFutureInstant()}, which nulls
     * the column explicitly and asserts the intermediate state. Do not merge the two: a name that
     * overstates its test is how a guard looks present and does nothing.
     */
    void expiryWorkerLeavesAConsentPausedRelationshipPendingWhenHandedItsId() {
        UUID supporter = seedUser("paused-not-expired-supporter");
        UUID learner = seedUser("paused-not-expired-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);

        // A v0.89.1 correction into the minor range pauses ACCEPTED -> PENDING.
        linkedLearnerService().correctBirthYear(learner, java.time.Year.now().getValue() - 10);
        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");

        // The sweep reaches this id (selection and execution are separate transactions by design).
        requestExpiryWorker.expire(relationshipId, OffsetDateTime.now(ZoneOffset.UTC));

        // A pause is not a termination: this connection was confirmed once.
        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");
    }

    /**
     * ⚠️ The privacy predicate for the account data export. This table has no user column — it is
     * keyed by relationship — so the join through {@code linked_learner_relationships} on
     * {@code learner_user_id} is the only thing standing between a caller and a stranger's declared
     * birth year. A mocked repository cannot test that, which is why this runs against real rows.
     */
    @Test
    void provisionalExportReturnsOnlyDeclarationsWhereTheCallerIsTheLearner() {
        UUID learner = seedUser("export-mine-learner");
        UUID stranger = seedUser("export-stranger-learner");
        UUID mine = seedRelationship(seedUser("export-mine-supporter"), learner, "PENDING");
        UUID theirs = seedRelationship(seedUser("export-stranger-supporter"), stranger, "PENDING");
        seedProvisional(mine, 2011);
        seedProvisional(theirs, 2012);

        assertThat(provisionalBirthYearRepository.findAllDeclaredByLearner(learner))
                .extracting(row -> row.getRelationshipId(), row -> row.getBirthYear())
                .containsExactly(tuple(mine, 2011));

        // The supporter on the caller's own relationship is not the learner and must see nothing.
        assertThat(provisionalBirthYearRepository.findAllDeclaredByLearner(
                relationshipSupporter(mine))).isEmpty();
    }

    /**
     * ⚠️ Proves the export field must be a LIST. The primary key is relationship_id, and the insert
     * is guarded only on the relationship being PENDING and the account year being absent, so a
     * learner who redeems two links before either creator confirms holds two declarations. A single
     * scalar would silently drop one from a compliance surface.
     */
    @Test
    void aLearnerWithTwoUnconfirmedRedemptionsExportsBothDeclarations() {
        UUID learner = seedUser("export-two-learner");
        UUID first = seedRelationship(seedUser("export-two-supporter-a"), learner, "PENDING");
        UUID second = seedRelationship(seedUser("export-two-supporter-b"), learner, "PENDING");
        seedProvisional(first, 2010);
        seedProvisional(second, 2011);

        assertThat(provisionalBirthYearRepository.findAllDeclaredByLearner(learner))
                .extracting(row -> row.getBirthYear())
                .containsExactlyInAnyOrder(2010, 2011);
    }

    /**
     * ⚠️ THE LOAD-BEARING HALF OF ITEM 4. A consent pause is NOT a termination, so it must leave
     * every grant row live. v0.93.0 made the row survive the ACCEPTED -> PENDING pause BY DESIGN:
     * {@code *SharedByMe} reflects the ROW, so it reports the caller's own standing act of sharing
     * and what resumes on re-acceptance. Cutting here would make a learner's own toggle read OFF
     * while they never touched it, and sharing would silently fail to resume.
     */
    /**
     * ⚠️ Item 2: bounded retention, against real rows. A terminal row stays visible only while recent,
     * and the clock is the ROW'S OWN terminal timestamp — which is exactly why v0.97.0 refused to
     * overwrite {@code expires_at} with the sweep time.
     */
    /**
     * ⚠️ THE DEFECT v0.99.0 ITEM 1 EXISTS TO FIX, reproduced directly. Retention used to key on the
     * DEADLINE, so a request swept long after it — which v0.98.0's batch bound and pause hook made
     * possible — arrived already outside the window and vanished without ever being shown as expired.
     */
    @Test
    void aLateSweptRequestIsStillShownAsExpiredRatherThanVanishing() {
        UUID supporter = seedUser("late-sweep-supporter");
        UUID learner = seedUser("late-sweep-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");
        // Deadline 90 days ago: the sweep was paused, and is only running now.
        setRelationshipExpiry(relationshipId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(90));

        assertThat(requestExpiryWorker.expire(relationshipId, OffsetDateTime.now(ZoneOffset.UTC)))
                .isTrue();

        List<UUID> visible = relationshipRepository
                .findVisibleForUser(learner, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)).stream()
                .map(LinkedLearnerRelationshipEntity::getId).toList();
        assertThat(visible)
                .as("it expired just now, so both parties must still see that it expired")
                .contains(relationshipId);
        // ⚠️ AND the transition must actually RECORD when it expired. Without this the test passes
        // for the wrong reason: an unwritten expired_at stays NULL, the safe-retain branch keeps the
        // row visible, and a mutation removing the write survives — which it did, until this line.
        // The recorded moment must be NOW, not the 90-day-old deadline; that difference IS the fix.
        Map<String, Object> stamps = jdbcTemplate.queryForMap(
                "select expired_at, expires_at from linked_learner_relationships where id = ?",
                relationshipId);
        assertThat(stamps.get("expired_at")).as("the sweep must record when it expired").isNotNull();
        assertThat(((java.sql.Timestamp) stamps.get("expired_at")).toInstant())
                .as("recorded at sweep time, not at the long-past deadline")
                .isAfter(((java.sql.Timestamp) stamps.get("expires_at")).toInstant().plusSeconds(86400));
    }

    /**
     * ⚠️ THE SAFE-RETAIN BRANCH, which is the one that goes untested and then breaks. A terminal row
     * with no terminal timestamp — expired between deploy and backfill, or any data oddity — must be
     * RETAINED, never hidden. v0.98.0 shipped a mutation that survived precisely because a null
     * branch was quietly doing the work an assertion claimed a different clause was doing.
     */
    @Test
    void aTerminalRowWithNoTerminalTimestampIsRetainedNotHidden() {
        UUID supporter = seedUser("no-stamp-supporter");
        UUID learner = seedUser("no-stamp-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "EXPIRED");
        jdbcTemplate.update(
                "update linked_learner_relationships set expired_at = null, revoked_at = null,"
                        + " expires_at = now() - interval '90 days' where id = ?", relationshipId);

        List<UUID> visible = relationshipRepository
                .findVisibleForUser(learner, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)).stream()
                .map(LinkedLearnerRelationshipEntity::getId).toList();

        assertThat(visible)
                .as("no terminal timestamp must fail SAFE — retain, never hide")
                .contains(relationshipId);
    }

    @Test
    void terminalRelationshipsFallOutOfTheListOnceTheyAreOlderThanTheRetentionWindow() {
        UUID supporter = seedUser("retention-supporter");
        UUID learner = seedUser("retention-learner");
        UUID recentlyRevoked = seedRelationship(supporter, learner, "REVOKED");
        UUID longRevoked = seedRelationship(seedUser("old-supporter"), learner, "REVOKED");
        UUID stillPending = seedRelationship(seedUser("live-supporter"), learner, "PENDING");
        jdbcTemplate.update("update linked_learner_relationships set revoked_at = now() - interval '2 days' where id = ?", recentlyRevoked);
        jdbcTemplate.update("update linked_learner_relationships set revoked_at = now() - interval '90 days' where id = ?", longRevoked);

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        List<UUID> visible = relationshipRepository.findVisibleForUser(learner, cutoff).stream()
                .map(LinkedLearnerRelationshipEntity::getId).toList();

        assertThat(visible).contains(recentlyRevoked, stillPending);
        assertThat(visible)
                .as("a relationship revoked 90 days ago must stop cluttering the list")
                .doesNotContain(longRevoked);
    }

    /**
     * ⚠️ THE CASE THAT MAKES THE STATUS ALLOWLIST LOAD-BEARING, and the one the first version of these
     * tests missed. An ACCEPTED row has both terminal timestamps null, so it stays visible through the
     * null branch alone — the allowlist is redundant for it, and a mutation dropping the allowlist
     * survived.
     *
     * <p>A PENDING row is different: it carries a real {@code expires_at}. The sweep runs daily, so for
     * up to a day a request can be PAST its deadline and not yet swept. Without the status allowlist
     * that row falls outside the retention window and DISAPPEARS FROM THE OWNER'S LIST BEFORE IT HAS
     * ACTUALLY EXPIRED — a live request vanishing while it is still confirmable.
     */
    @Test
    void aDueButUnsweptPendingRequestIsStillVisible() {
        UUID supporter = seedUser("unswept-supporter");
        UUID learner = seedUser("unswept-learner");
        UUID due = seedRelationship(supporter, learner, "PENDING");
        // Past its deadline by more than the retention window, and the sweep has not run yet.
        setRelationshipExpiry(due, OffsetDateTime.now(ZoneOffset.UTC).minusDays(45));

        List<UUID> visible = relationshipRepository
                .findVisibleForUser(learner, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)).stream()
                .map(LinkedLearnerRelationshipEntity::getId).toList();

        assertThat(visible)
                .as("a PENDING request must never be hidden by retention; only the sweep ends it")
                .contains(due);
    }

    /**
     * ⚠️ A LIVE row is never hidden, whatever its timestamps say. Retention bounds terminal clutter;
     * it must never remove a connection someone still has.
     */
    @Test
    void anAcceptedRelationshipIsNeverHiddenByTheRetentionWindow() {
        UUID supporter = seedUser("never-hidden-supporter");
        UUID learner = seedUser("never-hidden-learner");
        UUID accepted = seedRelationship(supporter, learner, "ACCEPTED");
        jdbcTemplate.update("update linked_learner_relationships set created_at = now() - interval '400 days' where id = ?", accepted);

        List<UUID> visible = relationshipRepository
                .findVisibleForUser(learner, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)).stream()
                .map(LinkedLearnerRelationshipEntity::getId).toList();

        assertThat(visible).contains(accepted);
    }

    /**
     * ⚠️ THE EXECUTABLE COUNTERPART TO V129, and the assertion that matters is the NEGATIVE one.
     *
     * <p>Healing grants on terminated relationships is the easy half. The half that would be a real
     * defect is sweeping a CONSENT PAUSE: a v0.89.1 birth-year correction returns an ACCEPTED
     * relationship to PENDING, and v0.93.0 made the grant row survive that BY DESIGN. A migration that
     * caught PENDING would turn a learner's own sharing toggle OFF without them touching it, and
     * sharing would not resume on re-acceptance.
     *
     * <p>The migration has already run against this container, so its statement is exercised directly
     * here against rows seeded afterwards — the same technique the V128 backfill test uses.
     */
    /**
     * ⚠️ Item 6, and the assertion that matters is that the SIBLING row goes too. A learner can hold
     * more than one declaration; deleting only the promoted relationship's row retains a declared
     * value after the account-global column exists, which v0.89.1 forbids.
     *
     * <p>Also pins the load-bearing ORDER: the sweep-all statement is guarded on the account year
     * being present, so it cannot delete a declaration that is still deciding consent.
     */
    @Test
    void promotingOneDeclarationClearsEveryOtherOneThatLearnerHolds() {
        UUID learner = seedUser("two-decl-learner");
        UUID first = seedRelationship(seedUser("two-decl-supporter-a"), learner, "PENDING",
                LinkedLearnerSide.LEARNER.name());
        UUID second = seedRelationship(seedUser("two-decl-supporter-b"), learner, "PENDING",
                LinkedLearnerSide.LEARNER.name());
        int minorYear = Year.now().getValue() - 10;
        assertThat(provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                first, learner, minorYear, OffsetDateTime.now(ZoneOffset.UTC))).isOne();
        assertThat(provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                second, learner, minorYear, OffsetDateTime.now(ZoneOffset.UTC))).isOne();
        assertThat(accountBirthYear(learner)).isNull();

        // ⚠️ Before promotion the sweep must be a NO-OP: the declarations are still load-bearing.
        assertThat(provisionalBirthYearRepository.deleteAllForLearnerOncePromoted(learner))
                .as("a declaration may not be discarded while it is still deciding consent")
                .isZero();
        assertThat(provisionalRows(first)).isOne();

        LinkedLearnerService service = linkedLearnerService();
        service.recordGuardianConsent(first, relationshipSupporter(first));
        service.accept(first, relationshipSupporter(first),
                new AcceptLinkedLearnerRequest(null, false));

        assertThat(accountBirthYear(learner)).isEqualTo(minorYear);
        assertThat(provisionalRows(first)).as("the promoted relationship's row").isZero();
        assertThat(provisionalRows(second))
                .as("⚠️ the SIBLING too — v0.89.1 does not retain a declared-value history")
                .isZero();
    }

    @Test
    void theTerminalGrantHealNeverTouchesAConsentPause() {
        UUID learner = seedUser("heal-learner");
        // ⚠️ EVERY relationship is seeded ACCEPTED and granted FIRST, then moved to its terminal
        // status. insertLiveIfAbsent is conditional on ACCEPTED (v0.93.0), so seeding a REVOKED row
        // and calling insertGrant inserts NOTHING — and the zero-grant assertions below would then
        // pass without the heal doing any work at all. Found by the idempotency test returning 0.
        UUID revokedRel = seedRelationship(seedUser("heal-revoked-supporter"), learner, "ACCEPTED");
        UUID expiredRel = seedRelationship(seedUser("heal-expired-supporter"), learner, "ACCEPTED");
        UUID acceptedRel = seedRelationship(seedUser("heal-accepted-supporter"), learner, "ACCEPTED");
        // A paused relationship: ACCEPTED, granted, then returned to PENDING by a correction.
        UUID pausedRel = seedRelationship(seedUser("heal-paused-supporter"), learner, "ACCEPTED");
        for (UUID id : List.of(revokedRel, expiredRel, acceptedRel, pausedRel)) {
            assertThat(insertGrant(id, learner, relationshipSupporter(id)))
                    .as("the fixture must actually create a grant, or the heal has nothing to cut")
                    .isOne();
        }
        jdbcTemplate.update("update linked_learner_relationships set status = 'REVOKED', revoked_at = now() where id = ?", revokedRel);
        jdbcTemplate.update("update linked_learner_relationships set status = 'EXPIRED', expired_at = now() where id = ?", expiredRel);
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);
        linkedLearnerService().correctBirthYear(learner, Year.now().getValue() - 10);
        assertThat(relationshipStatus(pausedRel)).isEqualTo("PENDING");
        assertThat(liveGrants(pausedRel)).as("v0.93.0: the pause leaves the row live").isOne();

        // ⚠️ The migration's ACTUAL SQL, read from the file, not a hand-copied string. Flyway has
        // already run V129 against this container, so it is replayed here — but replaying a COPY
        // means the test and the migration can drift apart silently, which is the one thing this
        // test exists to prevent.
        jdbcTemplate.update(migrationSql("V129__revoke_grants_on_terminal_relationships.sql"));

        assertThat(liveGrants(revokedRel)).as("a revoked relationship shares nothing").isZero();
        assertThat(liveGrants(expiredRel)).as("an expired relationship shares nothing").isZero();
        assertThat(liveGrants(pausedRel))
                .as("⚠️ A PAUSE IS NOT A TERMINATION — sharing must resume on re-acceptance")
                .isOne();
        assertThat(liveGrants(acceptedRel)).as("a live connection is untouched").isOne();
    }

    /** Re-running the heal must change nothing, so a repeated deploy cannot re-stamp revoked_at. */
    @Test
    void theTerminalGrantHealIsIdempotent() {
        UUID learner = seedUser("heal-idem-learner");
        UUID revokedRel = seedRelationship(seedUser("heal-idem-supporter"), learner, "ACCEPTED");
        assertThat(insertGrant(revokedRel, learner, relationshipSupporter(revokedRel))).isOne();
        jdbcTemplate.update("update linked_learner_relationships set status = 'REVOKED', revoked_at = now() where id = ?", revokedRel);
        String heal = migrationSql("V129__revoke_grants_on_terminal_relationships.sql");

        assertThat(jdbcTemplate.update(heal)).isOne();
        assertThat(jdbcTemplate.update(heal)).as("second run is a no-op").isZero();
    }

    @Test
    void aConsentPauseLeavesEveryGrantLiveBecauseAPauseIsNotATermination() {
        UUID supporter = seedUser("pause-grant-supporter");
        UUID learner = seedUser("pause-grant-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);
        insertGrant(relationshipId, learner, supporter);
        assertThat(liveGrants(relationshipId)).isOne();

        linkedLearnerService().correctBirthYear(learner, Year.now().getValue() - 10);

        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");
        assertThat(liveGrants(relationshipId))
                .as("the pause must leave sharing intact so it resumes on re-acceptance")
                .isOne();
    }

    /**
     * ⚠️ THE EXECUTABLE COUNTERPART to the V128 backfill fix, and the test whose ABSENCE let the
     * defect through. Every other pause test rests on {@code expires_at} being NULL — and
     * {@code seedRelationship} never sets it, so the one thing capable of removing that NULL, the
     * migration backfill, was never exercised against a paused row.
     *
     * <p>This reproduces the inherited shape directly: a relationship that was ACCEPTED, then
     * consent-paused back to PENDING, then handed a deadline by V128's backfill statement. Under
     * the naive {@code created_at + interval '30 days'} the deadline lands in the past and the
     * sweep terminates a confirmed connection. Under {@code greatest(created_at, now())} it does
     * not.
     */
    @Test
    void aPreMigrationConsentPausedRowIsNeverExpirableAtAnyFutureInstant() {
        UUID supporter = seedUser("v128-pause-supporter");
        UUID learner = seedUser("v128-pause-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        // Aged well beyond the TTL, exactly like a relationship formed before this release.
        jdbcTemplate.update(
                "update linked_learner_relationships set created_at = now() - interval '90 days'"
                        + " where id = ?", relationshipId);
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);
        insertGrant(relationshipId, learner, supporter);

        // A v0.89.1 correction pauses it. Pre-V128 this left expires_at NULL because the column
        // did not exist; the seed above reproduces that by never setting it.
        linkedLearnerService().correctBirthYear(learner, Year.now().getValue() - 10);
        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");
        jdbcTemplate.update(
                "update linked_learner_relationships set expires_at = null where id = ?",
                relationshipId);

        // V128 writes NOTHING here, so the NULL survives — and that is the whole protection.
        // ⚠️ Asserted at a FAR-FUTURE instant, not just "now". An earlier fix backfilled
        // greatest(created_at, now()) + 30 days, which passes a check at migration time and expires
        // the row a month later; only a future-dated sweep distinguishes a fix from a delay.
        OffsetDateTime longAfterAnyDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusYears(5);
        assertThat(relationshipRepository.findDuePendingIds(longAfterAnyDeadline, 500))
                .as("an inherited consent pause must never become due, at any future instant")
                .doesNotContain(relationshipId);
        assertThat(requestExpiryWorker.expire(relationshipId, longAfterAnyDeadline))
                .isFalse();
        assertThat(relationshipStatus(relationshipId)).isEqualTo("PENDING");
        assertThat(liveGrants(relationshipId))
                .as("a pause is not a termination, at migration time either").isOne();
    }

    @Test
    void revokingARelationshipCutsEveryLiveGrantOnItInBothDirections() {
        UUID supporter = seedUser("revoke-grant-supporter");
        UUID learner = seedUser("revoke-grant-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        jdbcTemplate.update("update users set birth_year = 2000 where id = ?", learner);
        insertGrant(relationshipId, learner, supporter);
        insertGrant(relationshipId, supporter, learner);
        assertThat(liveGrants(relationshipId)).isEqualTo(2);

        linkedLearnerService().revoke(relationshipId, supporter);

        assertThat(relationshipStatus(relationshipId)).isEqualTo("REVOKED");
        assertThat(liveGrants(relationshipId)).isZero();
    }

    /**
     * ⚠️ WHY EXPIRY'S GRANT CUT IS UNREACHABLE TODAY, established here rather than assumed. v0.93.0
     * made {@code insertLiveIfAbsent} conditional on the relationship being ACCEPTED at write time,
     * so a PENDING relationship can never receive a grant. Combined with the fact that the only
     * route from ACCEPTED back to PENDING is the consent pause — which leaves {@code expires_at}
     * NULL and is therefore unexpirable — the sweep can never MEET a live grant. The cut stays in
     * the worker as one rule shared with revoke; this records that it is defence in depth, so
     * nobody later reads its zero-row count as a defect.
     */
    @Test
    void aPendingRelationshipCannotReceiveAGrantWhichIsWhyExpiryNeverMeetsOne() {
        UUID supporter = seedUser("pending-grant-supporter");
        UUID learner = seedUser("pending-grant-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "PENDING");

        assertThat(insertGrant(relationshipId, learner, supporter))
                .as("v0.93.0 conditions the live-grant insert on ACCEPTED")
                .isZero();
        assertThat(liveGrants(relationshipId)).isZero();
    }

    /**
     * Defence in depth for the rule above: if any future path ever leaves a live grant on an
     * expirable PENDING row, the sweep must cut it exactly as revoke does. The state is forced
     * directly, because no supported path can produce it today.
     */
    @Test
    void expiryStillCutsAGrantIfAPendingRelationshipEverHoldsOne() {
        UUID supporter = seedUser("expire-grant-supporter");
        UUID learner = seedUser("expire-grant-learner");
        UUID relationshipId = seedRelationship(supporter, learner, "ACCEPTED");
        insertGrant(relationshipId, learner, supporter);
        assertThat(liveGrants(relationshipId)).isOne();
        jdbcTemplate.update(
                "update linked_learner_relationships set status = 'PENDING' where id = ?",
                relationshipId);
        setRelationshipExpiry(relationshipId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        assertThat(requestExpiryWorker.expire(relationshipId, OffsetDateTime.now(ZoneOffset.UTC)))
                .isTrue();

        assertThat(relationshipStatus(relationshipId)).isEqualTo("EXPIRED");
        assertThat(liveGrants(relationshipId)).isZero();
    }

    private LinkedLearnerService linkedLearnerService() {
        StudySnapProperties properties = new StudySnapProperties();
        return new LinkedLearnerService(
                relationshipRepository,
                invitationRepository,
                consentRepository,
                grantRepository,
                provisionalBirthYearRepository,
                userRepository,
                mock(OnboardingGuardService.class),
                mock(AuthService.class),
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                properties,
                new GuardianConsentPolicy(properties),
                mock(InvitationRateLimitService.class));
    }

    private Integer accountBirthYear(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select birth_year from users where id = ?", Integer.class, userId);
    }

    private int provisionalRows(UUID relationshipId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from linked_learner_provisional_birth_years where relationship_id = ?",
                Integer.class,
                relationshipId);
        return count == null ? 0 : count;
    }

    /** Read a migration's real SQL, so a test replaying it cannot drift from what actually ships. */
    private String migrationSql(String fileName) {
        try {
            return new ClassPathResource("db/migration/" + fileName)
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("migration not readable: " + fileName, exception);
        }
    }

    private UUID relationshipSupporter(UUID relationshipId) {
        return jdbcTemplate.queryForObject(
                "select supporter_user_id from linked_learner_relationships where id = ?",
                UUID.class,
                relationshipId);
    }

    private String relationshipStatus(UUID relationshipId) {
        return jdbcTemplate.queryForObject(
                "select status from linked_learner_relationships where id = ?", String.class, relationshipId);
    }

    private void setRelationshipExpiry(UUID relationshipId, OffsetDateTime expiresAt) {
        jdbcTemplate.update(
                "update linked_learner_relationships set expires_at = ? where id = ?",
                expiresAt, relationshipId);
    }

    private void seedProvisional(UUID relationshipId, int birthYear) {
        jdbcTemplate.update(
                "insert into linked_learner_provisional_birth_years"
                        + " (relationship_id, birth_year, declared_at) values (?, ?, now())",
                relationshipId,
                birthYear);
    }


    /**
     * Killing test for reverting the Long Exam refund clamp to {@code > 0}, which underflows for any
     * count above one. A mocked repository cannot test a predicate, and {@code long_exam_used_this_month}
     * carries NO non-negative CHECK constraint — unlike several sibling columns — so this clamp is the
     * only thing standing between a double refund and a negative allowance that reads as free quota.
     */
    @Test
    void longExamRefundClampsAtZeroInsteadOfUnderflowing() {
        UUID userId = seedUser("refund-clamp");
        OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        seedLongExamUsage(userId, periodStart, 1);

        // Refunding 2 against a stored 1 must land on 0, never -1.
        userUsageRepository.decrementLongExamUsageNotBelowZero(userId, periodStart, 2);

        assertThat(readLongExamUsage(userId)).isZero();

        // And a normal single refund still decrements rather than zeroing everything.
        seedLongExamUsage(userId, periodStart.plusMonths(1), 3);
        userUsageRepository.decrementLongExamUsageNotBelowZero(userId, periodStart.plusMonths(1), 1);
        assertThat(readLongExamUsage(userId, periodStart.plusMonths(1))).isEqualTo(2);
    }

    /**
     * Killing test for the v0.106.0 two-meter Board Exam reversal, which no mocked repository can reach:
     * the clamp and the second counter both live inside the JPQL {@code set} list.
     *
     * <p>The two columns fail DIFFERENTLY, which is exactly why both must be asserted.
     * {@code challenge_quiz_generations} carries a {@code >= 0} CHECK from V20, so an unclamped underflow
     * THROWS and takes the refund transaction with it; {@code board_exam_used_this_month} (V57) has no such
     * constraint, so an unclamped underflow silently stores a negative and reads back as free allowance.
     * Reversing only one of the two leaves the other permanently overcharged.
     */
    @Test
    void boardExamRefundReversesBothMetersAndClampsEachAtZero() {
        UUID userId = seedUser("board-refund");
        OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        seedBoardExamUsage(userId, periodStart, 1, 1);
        // ⚠️ A BYSTANDER PERIOD HOLDING NON-ZERO VALUES. The scoping assertion at the end of this test used
        // to read the REFUNDED period's own column — which the test had already driven to 0 — so deleting
        // the `periodStart` predicate from the reversal clamped 0 → 0 and the assertion still passed. A
        // cross-period write can only be detected against a row that must SURVIVE with a non-zero value.
        OffsetDateTime bystanderPeriod = periodStart.plusMonths(2);
        seedBoardExamUsage(userId, bystanderPeriod, 5, 4);

        // Refunding 2 against a stored 1 must land on 0 on BOTH columns, never -1 and never an exception.
        userUsageRepository.decrementBoardExamUsageNotBelowZero(userId, periodStart, 2);

        assertThat(readUsageColumn(userId, periodStart, "challenge_quiz_generations")).isZero();
        assertThat(readUsageColumn(userId, periodStart, "board_exam_used_this_month")).isZero();

        // A normal single refund decrements BOTH meters rather than zeroing them or touching only one.
        OffsetDateTime nextPeriod = periodStart.plusMonths(1);
        seedBoardExamUsage(userId, nextPeriod, 4, 3);
        userUsageRepository.decrementBoardExamUsageNotBelowZero(userId, nextPeriod, 1);

        assertThat(readUsageColumn(userId, nextPeriod, "challenge_quiz_generations")).isEqualTo(3);
        assertThat(readUsageColumn(userId, nextPeriod, "board_exam_used_this_month")).isEqualTo(2);

        // ⚠️ THE REAL SCOPING ASSERTION: the bystander period is untouched by EITHER refund, on BOTH
        // columns. Without the periodStart predicate these would read 3 and 2.
        assertThat(readUsageColumn(userId, bystanderPeriod, "challenge_quiz_generations")).isEqualTo(5);
        assertThat(readUsageColumn(userId, bystanderPeriod, "board_exam_used_this_month")).isEqualTo(4);
    }

    private void seedBoardExamUsage(UUID userId, OffsetDateTime periodStart, int challengeUsed, int boardUsed) {
        jdbcTemplate.update(
                "insert into user_usage (id, user_id, month, year, period_start, period_end,"
                        + " challenge_quiz_generations, board_exam_used_this_month, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), userId, periodStart.getMonthValue(), periodStart.getYear(),
                periodStart, periodStart.plusMonths(1), challengeUsed, boardUsed
        );
    }

    private int readUsageColumn(UUID userId, OffsetDateTime periodStart, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from user_usage where user_id = ? and period_start = ?",
                Integer.class, userId, periodStart);
    }

    private void seedLongExamUsage(UUID userId, OffsetDateTime periodStart, int used) {
        jdbcTemplate.update(
                "insert into user_usage (id, user_id, month, year, period_start, period_end,"
                        + " long_exam_used_this_month, created_at) values (?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), userId, periodStart.getMonthValue(), periodStart.getYear(),
                periodStart, periodStart.plusMonths(1), used
        );
    }

    private int readLongExamUsage(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select long_exam_used_this_month from user_usage where user_id = ? order by period_start limit 1",
                Integer.class, userId);
    }

    private int readLongExamUsage(UUID userId, OffsetDateTime periodStart) {
        return jdbcTemplate.queryForObject(
                "select long_exam_used_this_month from user_usage where user_id = ? and period_start = ?",
                Integer.class, userId, periodStart);
    }

    private UUID seedUser(String handle) {
        UUID id = UUID.randomUUID();
        String usernamePrefix = handle.substring(0, Math.min(handle.length(), 18));
        jdbcTemplate.update(
                "insert into users (id, email, username, password_hash, role, first_name, last_name,"
                        + " created_at, updated_at)"
                        + " values (?, ?, ?, 'x', 'USER', 'Test', 'User', now(), now())",
                id, handle + "-" + id + "@example.test",
                usernamePrefix + "-" + id.toString().substring(0, 8)
        );
        return id;
    }

    /**
     * A curator account: ADMIN role AND completed onboarding, which is what
     * {@code CuratorAuthoringPredicate.isCurator} actually requires. Bulk regeneration fixtures use
     * this rather than {@link #seedUser(String)} because the plain seed is a non-onboarded USER — and
     * a fixture that could not pass the gate would make every one of these guards pass vacuously.
     */
    private UUID seedCuratorUser(String handle) {
        UUID id = seedUser(handle);
        jdbcTemplate.update(
                "update users set role = 'ADMIN', onboarding_completed_at = now() where id = ?", id);
        return id;
    }

    private UUID seedRelationship(UUID supporterUserId, UUID learnerUserId, String status) {
        return seedRelationship(supporterUserId, learnerUserId, status, LinkedLearnerSide.SUPPORTER.name());
    }

    private UUID seedRelationship(
            UUID supporterUserId,
            UUID learnerUserId,
            String status,
            String initiatedBy
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into linked_learner_relationships"
                        + " (id, supporter_user_id, learner_user_id, status, initiated_by, created_at)"
                        + " values (?, ?, ?, ?, ?, now())",
                id, supporterUserId, learnerUserId, status, initiatedBy
        );
        return id;
    }

    @Test
    void postgresLibraryBranchesExecuteEveryFilter() {
        UUID ownerUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        List<String> tags = List.of("cardiology", "review");

        for (NoteLibraryReadiness readiness : NoteLibraryReadiness.values()) {
            NoteLibraryFilterCriteria criteria = new NoteLibraryFilterCriteria(
                    ownerUserId,
                    SEARCH_PATTERN,
                    readiness,
                    "Nursing",
                    tags,
                    NoteVisibility.PRIVATE,
                    // Non-null so the collection-membership EXISTS branch is PREPAREd too; the
                    // allOwned criteria below leaves it null so the other branch is covered as well.
                    UUID.randomUUID()
            );
            noteLibraryRepository.findLibraryPage(criteria, NoteLibrarySort.RECENTLY_UPDATED, 0, 10);
            noteLibraryRepository.countLibraryMatches(criteria);
            noteLibraryRepository.findLibraryCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectIdCandidates(criteria);
            noteLibraryRepository.findLibraryMatchingIds(criteria, 10);
        }
        NoteLibraryFilterCriteria allOwned = new NoteLibraryFilterCriteria(
                ownerUserId, null, NoteLibraryReadiness.ALL, null, List.of(), null, null
        );
        for (NoteLibrarySort sort : NoteLibrarySort.values()) {
            if (sort != NoteLibrarySort.RECENTLY_REVIEWED) {
                noteLibraryRepository.findLibraryPage(allOwned, sort, 0, 10);
            }
        }
        noteLibraryRepository.findListItemProjectionsByOwnerUserId(ownerUserId, 10);
        noteLibraryRepository.findLibraryListItemProjectionsByOwnerUserIdAndIdIn(ownerUserId, List.of(otherUserId));
        noteLibraryRepository.findAllLibrarySubjectCandidates(ownerUserId);
        noteLibraryRepository.countLibraryCoursePrograms(ownerUserId);
        noteLibraryRepository.countLibraryTags(ownerUserId);
        noteLibraryRepository.existsOwnedNoteWithQuizQuestions(ownerUserId);
        noteLibraryRepository.findMostRecentlyUpdatedStudyPackReadyNoteId(ownerUserId);

        PublicLibraryFilterCriteria publicCriteria = new PublicLibraryFilterCriteria(
                ownerUserId,
                otherUserId,
                SEARCH_PATTERN,
                "cardiology",
                tags,
                "nursing",
                "supporter",
                LearnerLevel.JUNIOR_HIGH,
                true,
                List.of(PublicLibrarySource.BY_YOU, PublicLibrarySource.OFFICIAL, PublicLibrarySource.COMMUNITY)
        );
        publicLibraryRepository.findDistinctPublicTags();
        publicLibraryRepository.findPublicLibraryPage(publicCriteria, PublicLibrarySort.RECENT, 0, 10);
        publicLibraryRepository.findPublicLibraryPage(publicCriteria, PublicLibrarySort.TITLE, 0, 10);
        publicLibraryRepository.countPublicLibraryMatches(publicCriteria);
        publicLibraryRepository.findPublicLibraryCandidates(publicCriteria);
        publicLibraryRepository.findPublicLibraryListItemProjectionsByIdIn(List.of(ownerUserId));
    }

    /**
     * ⚠️ Assertions on the PostgreSQL-only tag filter, which is unreachable from the H2 suite.
     *
     * <p>{@code postgresLibraryBranchesExecuteEveryFilter} above is a SMOKE test: it proves the PG
     * branches parse and run, and asserts nothing about what they return. A cold-agent pressure test
     * showed that gap concretely — flipping {@code " in ("} to {@code " not in ("} in
     * {@code PublicLibraryRepositoryImpl.appendTagFilter}'s PostgreSQL branch inverts tag filtering on
     * the anonymous Explore surface, and the whole suite stayed green. This test seeds real rows so
     * that inversion fails.
     */
    @Test
    void postgresTagFilterSelectsMatchingPublicNotesOnly() {
        UUID author = seedUser("tag-author");
        UUID matching = seedPublicNote(author, "Cardiology basics", new String[] {"cardiology", "review"});
        seedPublicNote(author, "Unrelated pharmacology", new String[] {"pharmacology"});

        List<NoteListItemProjection> cardiology = publicLibraryRepository.findPublicLibraryPage(
                publicTagCriteria(List.of("cardiology")), PublicLibrarySort.RECENT, 0, 10);

        assertThat(cardiology).extracting(NoteListItemProjection::getId).containsExactly(matching);
        assertThat(publicLibraryRepository.countPublicLibraryMatches(publicTagCriteria(List.of("cardiology"))))
                .isEqualTo(1);
        assertThat(publicLibraryRepository.findPublicLibraryPage(
                publicTagCriteria(List.of("nephrology")), PublicLibrarySort.RECENT, 0, 10))
                .as("a tag no public note carries must match nothing")
                .isEmpty();
    }

    /**
     * Real-row boundary test for adding a database-level save constraint, removing the generation
     * predicate, or changing its exact two-program boundary to three. Service-level tests separately pin
     * that both save APIs accept this state. PostgreSQL must persist it, while generation readiness rejects
     * it without changing the persisted note status.
     */
    @Test
    void curatorMultiProgramNoteWithoutDomainContextSavesButGenerationRejectsWithoutStatusChange() {
        UUID curatorId = seedUser("domain-ready-curator");
        jdbcTemplate.update(
                "update users set role = 'ADMIN', profile_type = 'STUDENT', onboarding_completed_at = now() where id = ?",
                curatorId
        );
        UUID noteId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " target_profile_type, domain_context, status, created_at, updated_at)"
                        + " values (?, ?, 'Shared engineering note', 'body', 'PRIVATE', '{}',"
                        + " 'STUDENT', null, 'DRAFT', now(), now())",
                noteId,
                curatorId
        );
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?), (?, ?, ?)",
                UUID.randomUUID(), noteId, UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.randomUUID(), noteId, UUID.fromString("20000000-0000-0000-0000-000000000005")
        );

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_course_program where note_id = ?", Integer.class, noteId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select domain_context from notes where id = ?", String.class, noteId
        )).isNull();

        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setStatus(NoteStatus.DRAFT);
        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                null,
                null,
                new NoteCourseProgramRepository(jdbcTemplate),
                null
        );

        assertThatThrownBy(() -> resolver.assertGenerationReady(note))
                .isInstanceOf(MultiProgramDomainContextRequiredException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select status from notes where id = ?", String.class, noteId
        )).isEqualTo("DRAFT");
    }

    /**
     * V132's snapshot property, against the database engine: a combined quiz has no notes FK, so deleting
     * its source note cannot cascade either the snapshot or its active share link. The public-service test
     * separately proves that the remaining link is rendered and graded from the copied JSON.
     */
    @Test
    void deletingASourceNoteLeavesTheCombinedQuizAndItsShareLinkIntact() {
        UUID owner = seedUser("combined-snapshot-owner");
        UUID sourceNote = UUID.randomUUID();
        UUID combinedQuiz = UUID.randomUUID();
        UUID shareLink = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type, status, created_at, updated_at)"
                        + " values (?, ?, 'Deleted source', 'body', 'PRIVATE', '{}', 'STUDENT', 'DRAFT', now(), now())",
                sourceNote, owner);
        jdbcTemplate.update(
                "insert into combined_quizzes (id, owner_user_id, title, sections, created_at) values (?, ?, 'Unit snapshot', ?::jsonb, now())",
                combinedQuiz, owner, "[{\"title\":\"Deleted source\",\"questions\":[]}]");
        jdbcTemplate.update(
                "insert into quiz_share_links (id, combined_quiz_id, owner_user_id, token, is_active, created_at)"
                        + " values (?, ?, ?, 'combined-snapshot-token', true, now())",
                shareLink, combinedQuiz, owner);

        jdbcTemplate.update("delete from notes where id = ?", sourceNote);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from combined_quizzes where id = ?", Integer.class, combinedQuiz)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from quiz_share_links where id = ? and is_active", Integer.class, shareLink)).isOne();
    }

    /**
     * Killing test for weakening V132's exclusive arc: both targets set must be rejected.
     *
     * <p>⚠️ The two arc violations are SEPARATE tests on purpose. PostgreSQL aborts the whole transaction on
     * a constraint violation, so a second insert in the same test fails with SQLSTATE 25P02
     * ("current transaction is aborted") rather than the CHECK — it asserts nothing about the arc while
     * still throwing. That is exactly how a test passes for the wrong reason.
     */
    @Test
    void quizShareLinkExclusiveArcRejectsBothTargets() {
        UUID owner = seedUser("combined-arc-both-owner");
        UUID generatedQuiz = seedArcGeneratedQuiz(owner, "Arc source both");
        UUID combinedQuiz = seedArcCombinedQuiz(owner, "Arc snapshot both");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into quiz_share_links (id, generated_quiz_id, combined_quiz_id, owner_user_id, token, is_active, created_at)"
                        + " values (?, ?, ?, ?, 'both-arc-targets', true, now())",
                UUID.randomUUID(), generatedQuiz, combinedQuiz, owner))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_quiz_share_links_exactly_one_quiz");
    }

    /** Killing test for weakening V132's exclusive arc: neither target set must be rejected. */
    @Test
    void quizShareLinkExclusiveArcRejectsNoTarget() {
        UUID owner = seedUser("combined-arc-none-owner");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into quiz_share_links (id, owner_user_id, token, is_active, created_at) values (?, ?, 'no-arc-target', true, now())",
                UUID.randomUUID(), owner))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_quiz_share_links_exactly_one_quiz");
    }

    private UUID seedArcGeneratedQuiz(UUID owner, String noteTitle) {
        UUID note = UUID.randomUUID();
        UUID generatedQuiz = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'body', 'PRIVATE', '{}', 'STUDENT', 'DRAFT', now(), now())",
                note, owner, noteTitle);
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", generatedQuiz, owner, note);
        return generatedQuiz;
    }

    private UUID seedArcCombinedQuiz(UUID owner, String title) {
        UUID combinedQuiz = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into combined_quizzes (id, owner_user_id, title, sections, created_at) values (?, ?, ?, '[]'::jsonb, now())",
                combinedQuiz, owner, title);
        return combinedQuiz;
    }

    /**
     * ⚠️ THE OWNER SCOPE ON THE COMBINED-QUIZ LIST, AGAINST THE REAL DATABASE.
     *
     * <p>{@code CombinedQuizServiceTest} MOCKS this repository, so the derived query never executes there —
     * dropping {@code OwnerUserId} from the method name passes that whole suite, and the service's
     * defensive Java filter would mask it too. This is the `v0.93.0` failure mode the project recorded:
     * every repository reference in the test tree being a Mockito mock, so a deleted predicate ships green.
     * Only a real query against real rows can pin it.
     */
    @Test
    void combinedQuizListReturnsOnlyTheOwnersRowsNewestFirst() {
        UUID owner = seedUser("combined-list-owner");
        UUID otherOwner = seedUser("combined-list-other");
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into combined_quizzes (id, owner_user_id, title, sections, created_at)"
                        + " values (?, ?, 'Older', '[]'::jsonb, now() - interval '1 day')", older, owner);
        jdbcTemplate.update(
                "insert into combined_quizzes (id, owner_user_id, title, sections, created_at)"
                        + " values (?, ?, 'Newer', '[]'::jsonb, now())", newer, owner);
        jdbcTemplate.update(
                "insert into combined_quizzes (id, owner_user_id, title, sections, created_at)"
                        + " values (?, ?, 'Someone else', '[]'::jsonb, now())", foreign, otherOwner);

        List<CombinedQuizEntity> rows = combinedQuizRepository
                .findByOwnerUserIdOrderByCreatedAtDesc(owner, PageRequest.of(0, 50));

        assertThat(rows).extracting(CombinedQuizEntity::getId).containsExactly(newer, older);
        assertThat(rows).extracting(CombinedQuizEntity::getOwnerUserId).containsOnly(owner);
    }

    private PublicLibraryFilterCriteria publicTagCriteria(List<String> tagSlugs) {
        return new PublicLibraryFilterCriteria(
                null, null, null, null, tagSlugs, null, null, null, false,
                List.of(PublicLibrarySource.BY_YOU, PublicLibrarySource.OFFICIAL, PublicLibrarySource.COMMUNITY)
        );
    }

    private UUID seedPublicNote(UUID ownerUserId, String title, String[] tags) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " target_profile_type, created_at, updated_at)"
                        + " values (?, ?, ?, 'body', ?, ?, 'STUDENT', now(), now())",
                id, ownerUserId, title, NoteVisibility.PUBLIC.name(), tags
        );
        return id;
    }

    private UUID seedStudyPack(UUID ownerUserId, UUID noteId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into study_packs"
                        + " (id, owner_user_id, note_id, input_type, title, summary, key_concepts, quiz,"
                        + " model_used, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'TEXT', ?, 'summary', '[]'::jsonb, '[]'::jsonb,"
                        + " 'test-model', 'DONE', now(), now())",
                id, ownerUserId, noteId, title
        );
        return id;
    }

    private UUID seedCollection(UUID ownerUserId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into note_collections (id, owner_user_id, title, created_at, updated_at)"
                        + " values (?, ?, ?, now(), now())",
                id, ownerUserId, title
        );
        return id;
    }

    private void seedCollectionItem(UUID collectionId, UUID noteId) {
        jdbcTemplate.update(
                "insert into note_collection_items (id, collection_id, note_id, position, created_at)"
                        + " values (?, ?, ?, 0, now())",
                UUID.randomUUID(), collectionId, noteId
        );
    }

    private UUID seedQuizSession(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            UUID sourceCollectionId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into quick_review_sessions"
                        + " (id, user_id, study_pack_id, note_id, source_collection_id, session_mode, status,"
                        + " current_question_index, current_round, total_questions, created_at)"
                        + " values (?, ?, ?, ?, ?, 'ADAPTIVE', 'IN_PROGRESS', 0, 'INITIAL', 1, now())",
                id, userId, studyPackId, noteId, sourceCollectionId
        );
        return id;
    }

    private UUID seedQuizSession(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            UUID sourceCollectionId,
            String status
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into quick_review_sessions"
                        + " (id, user_id, study_pack_id, note_id, source_collection_id, session_mode, status,"
                        + " current_question_index, current_round, total_questions, created_at, completed_at)"
                        + " values (?, ?, ?, ?, ?, 'ADAPTIVE', ?, 0, 'INITIAL', 1, now(), now())",
                id, userId, studyPackId, noteId, sourceCollectionId, status
        );
        return id;
    }

    private void prepare(NativeQueryMethod query, int index) {
        String statementName = "notelib_native_query_" + index;
        String sql = positionalParameters(query.sql());
        try {
            jdbcTemplate.execute("PREPARE " + statementName + " AS " + sql);
            jdbcTemplate.execute("DEALLOCATE " + statementName);
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "PostgreSQL could not PREPARE " + query.repository() + "." + query.method()
                            + ": " + rootMessage(failure),
                    failure
            );
        }
    }

    private List<NativeQueryMethod> findNativeQueries() throws IOException, ClassNotFoundException {
        List<NativeQueryMethod> queries = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources(REPOSITORY_CLASSES)) {
            String className = className(resource);
            if (className.contains("$") || !className.startsWith("com.studysnap.backend.repository.")) {
                continue;
            }
            Class<?> repositoryType = Class.forName(className);
            for (Method method : repositoryType.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null && query.nativeQuery()) {
                    queries.add(new NativeQueryMethod(
                            repositoryType.getSimpleName(), method.getName(), query.value()
                    ));
                }
            }
        }
        queries.sort(Comparator.comparing(NativeQueryMethod::repository).thenComparing(NativeQueryMethod::method));
        return queries;
    }

    private String className(Resource resource) throws IOException {
        URI uri = resource.getURI();
        String path = uri.toString();
        int packageStart = path.lastIndexOf("com/studysnap/backend/repository/");
        if (packageStart < 0 || !path.endsWith(CLASS_SUFFIX)) {
            throw new IOException("Cannot resolve repository class name from " + path);
        }
        return path.substring(packageStart, path.length() - CLASS_SUFFIX.length()).replace('/', '.');
    }

    private String positionalParameters(String sql) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        int occurrence = 0;
        StringBuilder translated = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (inLineComment) {
                translated.append(current);
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                translated.append(current);
                if (current == '*' && next == '/') {
                    translated.append(next);
                    index++;
                    inBlockComment = false;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == '-' && next == '-') {
                translated.append(current).append(next);
                index++;
                inLineComment = true;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == '/' && next == '*') {
                translated.append(current).append(next);
                index++;
                inBlockComment = true;
                continue;
            }
            if (!inDoubleQuote && current == '\'') {
                translated.append(current);
                if (inSingleQuote && next == '\'') {
                    translated.append(next);
                    index++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (!inSingleQuote && current == '"') {
                translated.append(current);
                if (inDoubleQuote && next == '"') {
                    translated.append(next);
                    index++;
                } else {
                    inDoubleQuote = !inDoubleQuote;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == ':' && next != ':'
                    && (index == 0 || sql.charAt(index - 1) != ':') && Character.isJavaIdentifierStart(next)) {
                int end = index + 2;
                while (end < sql.length() && Character.isJavaIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                String name = sql.substring(index + 1, end);
                // ⚠️ ONE PLACEHOLDER PER OCCURRENCE, not per distinct name — this must mirror what
                // Hibernate actually emits, or the harness is weaker than the shape it is checking.
                // Verified against PostgreSQL 16: `... where c < $1 or $1 is null` PREPAREs, while the
                // production shape `... where c < $1 or $2 is null` fails with `could not determine data
                // type of parameter $2`. Collapsing repeats to a single `$n` therefore made an uncast
                // `(col < :p or :p is null)` pass here and 500 in production — the exact defect class
                // this harness exists to close. `NoteShareRepository.findSharedWithMe` repeats
                // `:cursorCreatedAt` three times and `LinkedLearnerGrantRepository.insertLiveIfAbsent`
                // repeats `:relationshipId`, so this is live, not theoretical.
                positions.merge(name, 1, Integer::sum);
                translated.append('$').append(++occurrence);
                index = end - 1;
                continue;
            }
            translated.append(current);
        }
        return translated.toString();
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private record NativeQueryMethod(String repository, String method, String sql) {
    }

    static final class DockerRequiredUnlessOptedOut implements ExecutionCondition {
        private static final ConditionEvaluationResult ENABLED =
                ConditionEvaluationResult.enabled("PostgreSQL native-query verification is enabled");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (Boolean.getBoolean(SKIP_PROPERTY)) {
                System.err.printf(
                        "WARNING: %s was supplied; PostgreSQL 16 native queries and the full Flyway schema "
                                + "were NOT verified.%n",
                        SKIP_FLAG
                );
                return ConditionEvaluationResult.disabled("Explicitly opted out with " + SKIP_FLAG);
            }
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new ExtensionConfigurationException(
                        "Docker is required for PostgreSQL 16 native-query verification. Start Docker or "
                                + "explicitly opt out with " + SKIP_FLAG + "."
                );
            }
            return ENABLED;
        }
    }

    /**
     * ⚠️ Deleting a Study Plan must ORPHAN a learner's completed plan-scoped sessions, never destroy
     * them. The v0.113.0 pressure test proved the original ON DELETE CASCADE hard-deleted COMPLETED
     * rows while the ConceptHealth rows they produced survived -- evidence without the history that
     * explains it. Reverting the FK to CASCADE fails this test.
     */
    @Test
    void deletingAPlanOrphansItsCompletedSessionsInsteadOfDeletingThem() {
        UUID userId = seedUser("plan-delete-history");
        UUID collectionId = seedCollection(userId, "CE Board Review");
        UUID completed = seedQuizSession(userId, null, null, collectionId, "COMPLETED");
        UUID forfeited = seedQuizSession(userId, null, null, collectionId, "FORFEITED");

        jdbcTemplate.update("delete from note_collections where id = ?", collectionId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from quick_review_sessions where id in (?, ?)",
                Integer.class, completed, forfeited)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from quick_review_sessions where id = ? and source_collection_id is null",
                Integer.class, completed)).isEqualTo(1);
    }

    /**
     * The other half of the same constraint: an ACTIVE session may never be anchorless, because
     * nothing could reach it. This is why NoteCollectionService.delete clears non-terminal
     * plan-scoped sessions BEFORE deleting the plan -- without that sweep this SET NULL would violate
     * the anchor CHECK and the delete would fail outright.
     */
    @Test
    void anchorlessActiveSessionIsRejected() {
        UUID userId = seedUser("anchorless-active");

        assertThatThrownBy(() -> seedQuizSession(userId, null, null, null, "IN_PROGRESS"))
                .hasMessageContaining("chk_quick_review_sessions_anchor");
    }

    /**
     * Separate test on purpose: the rejection above aborts its transaction, so a terminal insert in
     * the same method would fail for the wrong reason (25P02) and prove nothing.
     */
    @Test
    void anchorlessTerminalSessionIsAllowedBecauseItIsHistory() {
        UUID userId = seedUser("anchorless-terminal");

        UUID terminal = seedQuizSession(userId, null, null, null, "COMPLETED");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from quick_review_sessions where id = ?",
                Integer.class, terminal)).isEqualTo(1);
    }

    /** Killing test for removing V134's per-placement non-negative source-position constraint. */
    @Test
    void reviewSetPlacementSourcePositionAtSyncRejectsNegativeValues() {
        UUID userId = seedUser("review-sync-position");
        UUID noteId = seedPublicNote(userId, "Source position", new String[] {});
        UUID collectionId = seedCollection(userId, "Adopted Review Set");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into note_collection_items"
                        + " (id, collection_id, note_id, position, source_position_at_sync, created_at)"
                        + " values (?, ?, ?, 0, -1, now())",
                UUID.randomUUID(), collectionId, noteId
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_note_collection_items_source_position_at_sync_non_negative");
    }

    /** Killing test for adding a source FK or removing the adopted-collection cascade on V134 tombstones. */
    @Test
    void reviewSetRemovalTombstoneSurvivesSourceDeletionAndCascadesWithAdoption() {
        UUID curatorId = seedUser("review-sync-curator");
        UUID learnerId = seedUser("review-sync-learner");
        UUID sourceNoteId = seedPublicNote(curatorId, "Removed upstream topic", new String[] {});
        UUID sourcePlanId = seedCollection(curatorId, "Official Review Set");
        UUID adoptedPlanId = seedCollection(learnerId, "My Review Set");
        jdbcTemplate.update(
                "insert into note_collection_item_removals"
                        + " (adopted_collection_id, source_plan_id, source_note_id, removed_at)"
                        + " values (?, ?, ?, now())",
                adoptedPlanId, sourcePlanId, sourceNoteId
        );

        jdbcTemplate.update("delete from note_collections where id = ?", sourcePlanId);
        jdbcTemplate.update("delete from notes where id = ?", sourceNoteId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_collection_item_removals where adopted_collection_id = ?",
                Integer.class,
                adoptedPlanId
        )).isOne();

        jdbcTemplate.update("delete from note_collections where id = ?", adoptedPlanId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_collection_item_removals where adopted_collection_id = ?",
                Integer.class,
                adoptedPlanId
        )).isZero();
    }

    // ------------------------------------------------------------------------------------------------
    // v0.118.0 — Note + Study Pack regeneration. Guards 1, 2, 3 and 11 assert PERSISTED state.
    //
    // ⚠️ WHY THESE LIVE HERE AND NOT IN StudyPackServiceTest. Guard 2 says "both meters unchanged after a
    // failure". With MockitoExtension the only way to write that is verify(userUsageService, never()),
    // which asserts a CALL and passes under a version that charges through some other path. These read the
    // counter columns back out of PostgreSQL instead.
    //
    // ⚠️ EVERY ONE OF THEM IS @Transactional(propagation = NOT_SUPPORTED), AND THAT IS LOAD-BEARING, NOT
    // STYLE. Under @DataJpaTest's default rollback transaction, dispatchAfterCommit registers an
    // afterCommit synchronization that NEVER FIRES, so the async worker would never run and a guard
    // asserting "content unchanged / meters unchanged" would pass because NOTHING HAPPENED AT ALL.
    // ------------------------------------------------------------------------------------------------

    /**
     * GUARD 1 + GUARD 2 (pre-declared). The pairing invariant and quota-on-failure, in one run.
     *
     * <p>The fixture is the one that discriminates: <strong>note content generation SUCCEEDS and Study Pack
     * generation then FAILS</strong> — row 4 of the failure matrix. A fixture whose generation succeeds
     * passes under both the defect and the fix and proves nothing.
     *
     * <p>Asserted: the note's original {@code content} and the original {@code study_packs} row (id, title,
     * summary, key_concepts, quiz) survive, and BOTH persisted meters are still zero.
     *
     * <p>⚠️ Deliberately NOT asserted: {@code notes.status} and {@code notes.updated_at}. The request thread
     * legitimately writes GENERATING/enqueued/updated before dispatch and the failure path legitimately
     * writes FAILED — that is already true of today's Study Pack regeneration, and asserting otherwise
     * would invite "fixing" production to match the test.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedStudyPackGenerationLeavesTheOriginalNoteContentPackAndBothMetersUntouched() {
        UUID owner = seedUser("regen-pairing");
        UUID noteId = seedRegenerationNote(owner, "Shear and Moment Diagrams", "ORIGINAL BODY, hand written.");
        UUID packId = seedStudyPack(owner, noteId, "Original pack title");

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Regenerated body that must never be persisted.";
        harness.studyPackFailure = new IllegalStateException("study pack generation exploded");

        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(readNoteColumn(noteId, "content"))
                .as("the note keeps its ORIGINAL content when the Study Pack half fails")
                .isEqualTo("ORIGINAL BODY, hand written.");
        assertThat(jdbcTemplate.queryForMap("select id, title, summary, key_concepts::text as key_concepts,"
                + " quiz::text as quiz from study_packs where id = ?", packId))
                .as("the original Study Pack row is untouched")
                .containsEntry("id", packId)
                .containsEntry("title", "Original pack title")
                .containsEntry("summary", "summary")
                .containsEntry("key_concepts", "[]")
                .containsEntry("quiz", "[]");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from study_packs where note_id = ?", Integer.class, noteId))
                .as("no second Study Pack row was minted")
                .isOne();

        assertThat(persistedUsage(owner, "note_generations"))
                .as("GUARD 2: the note-generation meter is not charged for a run that failed")
                .isZero();
        assertThat(persistedUsage(owner, "study_pack_generations"))
                .as("GUARD 2: the Study Pack meter is not charged for a run that failed")
                .isZero();
    }

    /**
     * GUARD 3 (pre-declared) — identity preservation on a SUCCESSFUL run, plus the positive half of
     * guard 2 without which guard 2 could pass vacuously (a build that never charges anything at all).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void successfulRegenerationPreservesNoteAndPackIdentityAndChargesBothMetersExactlyOnce() {
        UUID owner = seedUser("regen-identity");
        UUID noteId = seedRegenerationNote(owner, "Reinforced Concrete Design", "Old body.");
        UUID packId = seedStudyPack(owner, noteId, "Old pack title");
        UUID collectionId = seedCollection(owner, "Structural plan");
        jdbcTemplate.update(
                "insert into note_collection_items (id, collection_id, note_id, position, label, created_at)"
                        + " values (?, ?, ?, 7, 'Structural Analysis', now())",
                UUID.randomUUID(), collectionId, noteId);
        OffsetDateTime createdAtBefore = jdbcTemplate.queryForObject(
                "select created_at from notes where id = ?", OffsetDateTime.class, noteId);

        // ⚠️ MULTI-LINE ON PURPOSE, AND IT IS WHAT MAKES THIS ASSERTION DISCRIMINATING. A generated note
        // body is a structured document (title, blank line, section headers, bullet lines), and the note
        // body is persisted trimmed-only while the Study Pack's source text is whitespace-collapsed. A
        // single-line fixture is byte-identical under both, so it passes whether or not the note is
        // flattened — which is exactly how the flattening reached this test suite green in the first place.
        String generatedBody = String.join("\n",
                "Development Length of Reinforced Bars",
                "",
                "📘 Overview",
                "Development length is the embedment needed to yield a bar.",
                "",
                "⚔️ Core Details",
                "- Bond stress governs the transfer.",
                "- Hooks reduce the required straight length.");

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = generatedBody;
        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(readNoteColumn(noteId, "content"))
                .as("the note's content really was replaced, with its line structure intact")
                .isEqualTo(generatedBody);
        assertThat(readNoteColumn(noteId, "content"))
                .as("the body is NOT whitespace-collapsed the way the Study Pack's source text is")
                .contains("\n\n📘 Overview\n");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notes where id = ?", Integer.class, noteId))
                .as("notes.id survives — regeneration mutates columns, never identity")
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select created_at from notes where id = ?", OffsetDateTime.class, noteId))
                .as("notes.created_at is never rewritten")
                .isEqualTo(createdAtBefore);
        assertThat(jdbcTemplate.queryForObject(
                "select id from study_packs where note_id = ?", UUID.class, noteId))
                .as("study_packs.id survives — saveStudyPack mutates the row in place")
                .isEqualTo(packId);
        assertThat(jdbcTemplate.queryForObject(
                "select title from study_packs where id = ?", String.class, packId))
                .as("the pack really was regenerated")
                .isEqualTo("Regenerated pack");

        Map<String, Object> placement = jdbcTemplate.queryForMap(
                "select position, label from note_collection_items where note_id = ?", noteId);
        assertThat(placement)
                .as("the note keeps its place in the Study Plan, with position AND label")
                .containsEntry("position", 7)
                .containsEntry("label", "Structural Analysis");

        assertThat(persistedUsage(owner, "note_generations"))
                .as("the note-generation meter IS charged, exactly once, on success")
                .isOne();
        assertThat(persistedUsage(owner, "study_pack_generations"))
                .as("the Study Pack meter IS charged, exactly once, on success")
                .isOne();
    }

    /**
     * GUARD 11 (pre-declared). Every previously-active share link for the note is DEACTIVATED and every
     * row still EXISTS. Deleting would force the owner to mint a new link and spend share-link quota —
     * punishing them for our fix.
     *
     * <p>Two active links, not one: {@code createShareLink} mints a new row over an inactive one and
     * {@code findActiveLink} accepts ANY active token, so a version that deactivated only the newest would
     * leave the defect reachable through the other.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void successfulRegenerationDeactivatesEveryLiveShareLinkAndDeletesNone() {
        UUID owner = seedUser("regen-sharelinks");
        UUID noteId = seedRegenerationNote(owner, "Fluid Mechanics", "Old body.");
        seedStudyPack(owner, noteId, "Old pack title");
        UUID generatedQuizId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", generatedQuizId, owner, noteId);
        UUID activeOne = seedQuizShareLink(generatedQuizId, owner, "regen-link-active-one", true);
        UUID activeTwo = seedQuizShareLink(generatedQuizId, owner, "regen-link-active-two", true);
        UUID alreadyOff = seedQuizShareLink(generatedQuizId, owner, "regen-link-already-off", false);

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Freshly generated body.";
        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from quiz_share_links where generated_quiz_id = ?", Integer.class, generatedQuizId))
                .as("DEACTIVATED, never deleted — all three rows survive")
                .isEqualTo(3);
        assertThat(readShareLinkActive(activeOne)).as("first live link is off").isFalse();
        assertThat(readShareLinkActive(activeTwo)).as("second live link is off too, not just the newest").isFalse();
        assertThat(readShareLinkActive(alreadyOff)).as("an already-inactive link stays inactive").isFalse();
    }

    /** GUARD 11, second half: a note with a quiz but no live links is a clean no-op, not an error. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void regenerationWithNoLiveShareLinksIsACleanNoOp() {
        UUID owner = seedUser("regen-nolinks");
        UUID noteId = seedRegenerationNote(owner, "Hydraulics", "Old body.");
        seedStudyPack(owner, noteId, "Old pack title");
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", UUID.randomUUID(), owner, noteId);

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Freshly generated body.";
        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(readNoteColumn(noteId, "content")).isEqualTo("Freshly generated body.");
        assertThat(readNoteColumn(noteId, "status")).isEqualTo("GENERATED");
    }

    /**
     * GUARD 4. Existing learner copies are byte-identical after the canonical note is regenerated. There is
     * no propagation mechanism and there must never be one — this pins the absence.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void regeneratingACanonicalNoteLeavesEveryLearnerCopyByteIdentical() {
        UUID owner = seedUser("regen-copy-owner");
        UUID copier = seedUser("regen-copy-learner");
        UUID noteId = seedRegenerationNote(owner, "Steel Design", "Canonical body.");
        seedStudyPack(owner, noteId, "Old pack title");
        UUID copyId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type,"
                        + " status, copied_from_note_id, created_at, updated_at)"
                        + " values (?, ?, 'Steel Design', 'Canonical body.', 'PRIVATE', '{}', 'STUDENT',"
                        + " 'DRAFT', ?, now(), now())",
                copyId, copier, noteId);
        Map<String, Object> copyBefore = jdbcTemplate.queryForMap(
                "select title, content, subject, status, updated_at from notes where id = ?", copyId);

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Regenerated canonical body.";
        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(readNoteColumn(noteId, "content"))
                .as("the canonical note DID change — otherwise the copy assertion is vacuous")
                .isEqualTo("Regenerated canonical body.");
        assertThat(jdbcTemplate.queryForMap(
                "select title, content, subject, status, updated_at from notes where id = ?", copyId))
                .as("the learner's copy is byte-identical: no sync, no inheritance, no live fork")
                .isEqualTo(copyBefore);
    }

    /**
     * GUARD 5. ConceptHealth honesty. A regenerated pack that DROPS one key concept and KEEPS another must
     * still surface the kept concept's history and surface nothing for the dropped one — and neither row may
     * be deleted or rewritten.
     *
     * <p>The kept concept carries a recent {@code last_correct_at}, so "history is still read" is
     * observable: if regeneration had wiped the row the concept would come back as DUE.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void regenerationLeavesConceptHealthRowsIntactAndSurfacesOnlySurvivingConcepts() {
        UUID owner = seedUser("regen-concepts");
        UUID noteId = seedRegenerationNote(owner, "Structural Analysis", "Old body.");
        UUID packId = seedStudyPack(owner, noteId, "Old pack title");
        seedConceptHealth(owner, packId, "Shear Force");
        seedConceptHealth(owner, packId, "Moment Distribution");

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Regenerated body.";
        // The regenerated pack keeps Shear Force and drops Moment Distribution.
        harness.keyConcepts = List.of("Shear Force", "Influence Lines");
        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from concept_health where study_pack_id = ?", Integer.class, packId))
                .as("no concept_health row is deleted — a dropped concept's row is inert, not cleaned up")
                .isEqualTo(2);

        ConceptHealthService conceptHealthService = new ConceptHealthService(
                conceptHealthRepository, studyPackRepository, mock(SubscriptionService.class),
                mock(FeatureGateService.class), mock(NoteShareService.class));
        List<String> due = conceptHealthService.getDueConceptsByStudyPackIds(
                owner,
                Map.of(packId, List.of("Shear Force", "Influence Lines")),
                OffsetDateTime.now(ZoneOffset.UTC)
        ).get(packId);

        assertThat(due)
                .as("the KEPT concept still carries its history, so it is not due; the DROPPED one is never"
                        + " read at all; a brand-new concept starts with no history and is due")
                .containsExactly("Influence Lines");
    }

    /**
     * GUARD 7. Applicable Programs isolation. Two joined catalog programs plus a set Domain Context must
     * resolve a context whose {@code courseProgram} is neither program concatenated — and must not throw.
     * A single-program fixture passes under a concatenation bug and proves nothing.
     */
    @Test
    void twoApplicableProgramsNeverConcatenateIntoTheGenerationCourseProgram() {
        UUID owner = seedUser("regen-programs");
        UUID noteId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags, target_profile_type,"
                        + " status, subject, course_program, domain_context, created_at, updated_at)"
                        + " values (?, ?, 'Building Utilities', 'body', 'PRIVATE', '{}', 'STUDENT', 'DRAFT',"
                        + " 'Utilities', 'Architectural Engineering', 'ENGINEERING_SCIENCES', now(), now())",
                noteId, owner);
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?), (?, ?, ?)",
                UUID.randomUUID(), noteId, UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.randomUUID(), noteId, UUID.fromString("20000000-0000-0000-0000-000000000005"));

        NoteEntity note = noteRepository.findById(noteId).orElseThrow();
        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, new NoteCourseProgramRepository(jdbcTemplate), null);

        resolver.assertGenerationReady(note);
        StudyPackGenerationContext context = resolver.resolve(owner, note);

        assertThat(context.courseProgram())
                .as("a LIST may never reach a prompt as the domain; with 2+ joined programs the resolver"
                        + " falls back to the note's own single string")
                .isEqualTo("Architectural Engineering")
                .doesNotContain("Architecture")
                .doesNotContain("Civil Engineering");
    }

    // --- regeneration fixture helpers -----------------------------------------------------------------

    /**
     * ⚠️ THE INTERLOCK'S ORDERING, WHICH NOTHING PINNED. A cold falsification pass moved the content
     * write ABOVE the status re-check, and skipped the interlock for combined runs, and all 236 tests
     * stayed green -- the ordering was asserted only in a comment.
     *
     * <p>It is reachable, not theoretical: {@code resolveSourceNoteForGeneration} reads {@code status}
     * WITHOUT a lock, so two concurrent regenerations can both pass and both set {@code GENERATING}.
     * Once the first commits, the second's worker finds the note {@code GENERATED}, and the interlock is
     * the only thing standing between that worker and a second content overwrite plus a SECOND charge on
     * BOTH meters.
     *
     * <p>The fixture drives a combined run whose note is flipped out of {@code GENERATING} before the
     * worker's transaction opens, and asserts nothing was written and nothing was charged.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aLateCombinedWorkerPersistsNothingAndChargesNothingWhenTheNoteIsNoLongerGenerating() {
        UUID owner = seedUser("regen-interlock");
        UUID noteId = seedRegenerationNote(owner, "Shear and Moment", "ORIGINAL BODY.");
        UUID packId = seedStudyPack(owner, noteId, "Original pack title");

        RegenerationHarness harness = new RegenerationHarness();
        harness.noteContent = "Body from a worker that lost the race.";
        // Fires between the two LLM calls and the commit transaction -- exactly the window a recovery
        // sweep, a mid-generation delete, or a second concurrent worker occupies.
        harness.beforeCommit = () -> jdbcTemplate.update(
                "update notes set status = 'GENERATED' where id = ?", noteId);

        harness.service().startAsyncNoteAndStudyPackRegeneration(noteId.toString(), owner);

        assertThat(readNoteColumn(noteId, "content"))
                .as("a declined worker must not overwrite the note")
                .isEqualTo("ORIGINAL BODY.");
        assertThat(jdbcTemplate.queryForObject(
                "select title from study_packs where id = ?", String.class, packId))
                .as("a declined worker must not replace the pack")
                .isEqualTo("Original pack title");
        assertThat(persistedUsage(owner, "note_generations"))
                .as("a declined worker must not charge the note-generation meter")
                .isZero();
        assertThat(persistedUsage(owner, "study_pack_generations"))
                .as("a declined worker must not charge the Study Pack meter")
                .isZero();
    }

    /**
     * ⚠️ REAL-ROW GUARD FOR THE REGENERATION CLOCK, and it is the discriminating one. Quiz mastery is
     * DERIVED, never stored, and regeneration preserves {@code study_packs.id} on purpose -- so without
     * the {@code generation_enqueued_at} clause the session that mastered the OLD quiz keeps matching the
     * NEW one (equal sizes by construction, the prompt asks for {@code exactly {QUIZ_COUNT}}), leaving the
     * Quiz tab unlocked WITH ITS ANSWER KEY on questions the learner has never seen. Combined Note +
     * Study Pack regeneration widened that: the answer key would belong to content the learner never read.
     *
     * <p>Three legs, because a one-legged fixture proves nothing here: mastery holds before the clock
     * moves, is revoked once it moves, and is re-earned by a perfect score afterwards.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void quizMasteryDoesNotSurviveARegenerationOfTheQuizItWasEarnedOn() {
        UUID owner = seedUser("regen-mastery");
        UUID noteId = seedRegenerationNote(owner, "Development Length", "Body.");
        UUID packId = seedStudyPack(owner, noteId, "Pack");
        OffsetDateTime enqueuedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        jdbcTemplate.update("update notes set generation_enqueued_at = ? where id = ?",
                enqueuedAt.minusHours(5), noteId);

        OffsetDateTime firstMastery = enqueuedAt.minusHours(4);
        seedCompletedQuickReview(owner, packId, noteId, firstMastery, 5);

        assertThat(quickReviewSessionRepository.findQuizMasteredAt(owner, packId, 5, noteId))
                .as("mastered against the quiz that was current when the session ran")
                .isNotNull();

        // Regeneration: the clock moves, the pack id does not, the quiz size is unchanged.
        jdbcTemplate.update("update notes set generation_enqueued_at = ? where id = ?", enqueuedAt, noteId);

        assertThat(quickReviewSessionRepository.findQuizMasteredAt(owner, packId, 5, noteId))
                .as("the old perfect score must NOT unlock the regenerated quiz")
                .isNull();

        seedCompletedQuickReview(owner, packId, noteId, OffsetDateTime.now(ZoneOffset.UTC), 5);

        assertThat(quickReviewSessionRepository.findQuizMasteredAt(owner, packId, 5, noteId))
                .as("mastery is re-earned by a perfect score on the NEW quiz")
                .isNotNull();
    }

    /**
     * A note generated before {@code V118} added the column carries a NULL clock. Revoking mastery for
     * that entire population would be a silent regression for every existing learner, so the rule is
     * deliberately null-tolerant. Without this test the fix could ship as a mass re-lock.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void quizMasterySurvivesWhenTheNoteCarriesNoGenerationClock() {
        UUID owner = seedUser("regen-mastery-legacy");
        UUID noteId = seedRegenerationNote(owner, "Legacy note", "Body.");
        UUID packId = seedStudyPack(owner, noteId, "Pack");
        seedCompletedQuickReview(owner, packId, noteId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3), 5);

        assertThat(readNoteColumn(noteId, "generation_enqueued_at"))
                .as("the fixture really is the legacy shape this guard is about")
                .isNull();
        assertThat(quickReviewSessionRepository.findQuizMasteredAt(owner, packId, 5, noteId))
                .as("a legacy note keeps the mastery the learner already earned")
                .isNotNull();
    }

    private void seedCompletedQuickReview(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            OffsetDateTime completedAt,
            int verifiedCorrectAnswers
    ) {
        jdbcTemplate.update(
                "insert into quick_review_sessions (id, user_id, study_pack_id, note_id, session_mode,"
                        + " status, current_round, current_question_index, total_questions,"
                        + " quota_exempt, verified_correct_answers, created_at, completed_at)"
                        + " values (?, ?, ?, ?, 'QUICK_REVIEW', 'COMPLETED', 'INITIAL', 0, ?, false,"
                        + " ?, ?, ?)",
                UUID.randomUUID(), userId, studyPackId, noteId, verifiedCorrectAnswers,
                verifiedCorrectAnswers, completedAt, completedAt
        );
    }

    /**
     * B3's collection filter, against real rows. The PREPARE sweep proves the SQL parses; only this
     * proves it FILTERS.
     *
     * <p>⚠️ THE FIXTURE PUTS ONE NOTE IN TWO COLLECTIONS ON PURPOSE. A note can belong to several
     * Review Sets, so a JOIN would emit it once per membership row and inflate both the page and the
     * count — a fixture where every note sits in at most one collection passes under that defect and
     * proves nothing.
     */
    @Test
    void theCollectionFilterSelectsMembersOnlyAndNeverDuplicatesAMultiCollectionNote() {
        UUID owner = seedUser("library-collection-filter");
        UUID inBoth = seedRegenerationNote(owner, "Shear and Moment", "Body.");
        UUID inTarget = seedRegenerationNote(owner, "Development Length", "Body.");
        UUID outside = seedRegenerationNote(owner, "Fluid Mechanics", "Body.");
        UUID target = seedCollection(owner, "CE Board Review");
        UUID other = seedCollection(owner, "Structural Depth");
        seedCollectionItem(target, inBoth);
        seedCollectionItem(other, inBoth);
        seedCollectionItem(target, inTarget);

        NoteLibraryFilterCriteria filtered = new NoteLibraryFilterCriteria(
                owner, null, NoteLibraryReadiness.ALL, null, List.of(), null, target
        );

        assertThat(noteLibraryRepository.findLibraryMatchingIds(filtered, 100))
                .as("only the target collection's members, and the multi-collection note exactly once")
                .containsExactlyInAnyOrder(inBoth, inTarget);
        assertThat(noteLibraryRepository.countLibraryMatches(filtered))
                .as("the count matches the page -- a join would double-count the multi-collection note")
                .isEqualTo(2);
        assertThat(noteLibraryRepository.findLibraryMatchingIds(filtered, 100))
                .as("the note outside the collection is excluded")
                .doesNotContain(outside);

        NoteLibraryFilterCriteria unfiltered = new NoteLibraryFilterCriteria(
                owner, null, NoteLibraryReadiness.ALL, null, List.of(), null, null
        );
        assertThat(noteLibraryRepository.findLibraryMatchingIds(unfiltered, 100))
                .as("a null collectionId means NO filter -- never 'notes in no collection'")
                .containsExactlyInAnyOrder(inBoth, inTarget, outside);
    }

    /**
     * The curator gate, on BOTH surfaces.
     *
     * <p>⚠️ The endpoints' {@code @PreAuthorize} is {@code hasAnyRole('USER','ADMIN')}, which every
     * authenticated account satisfies — so without this gate bulk regeneration is reachable by every
     * learner, which is wider than the capability was scoped to. A fixture using a curator passes
     * whether or not the gate exists, which is why the subject here is a plain non-onboarded USER.
     *
     * <p>⚠️ PREFLIGHT IS ASSERTED TOO. Gating only the batch would leave a disclosure surface wider
     * than the capability, handing a non-curator per-Note readiness and quota figures for a batch they
     * could never run.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkRegenerationRefusesANonCuratorOnBothTheBatchAndThePreflight() {
        UUID learner = seedUser("bulk-regen-non-curator");
        UUID note = seedRegenerationNote(learner, "Fluid Mechanics", "Body.");
        seedStudyPack(learner, note, "Pack");

        BulkRegenerationHarness harness = new BulkRegenerationHarness();

        assertThatThrownBy(() -> harness.run(learner, List.of(note), true))
                .as("a learner cannot run a batch")
                .isInstanceOf(BulkRegenerationNotPermittedException.class);
        assertThatThrownBy(() -> harness.preflight(learner, List.of(note), NoteRegenerationScope.NOTE_AND_STUDY_PACK, true))
                .as("and cannot read the preflight either")
                .isInstanceOf(BulkRegenerationNotPermittedException.class);

        assertThat(readNoteColumn(note, "content"))
                .as("nothing was regenerated")
                .isEqualTo("Body.");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_bulk_regeneration_item where owner_user_id = ?",
                Integer.class, learner))
                .as("and no batch row was written")
                .isZero();

        // The SAME selection succeeds once the account is a curator -- so the refusal is the gate,
        // not some unrelated defect in the fixture.
        jdbcTemplate.update("update users set role = 'ADMIN', onboarding_completed_at = now() where id = ?", learner);
        assertThat(harness.preflight(learner, List.of(note), NoteRegenerationScope.NOTE_AND_STUDY_PACK, true).readyCount())
                .as("a curator reads the same selection fine")
                .isEqualTo(1);
    }

    /**
     * Quota exhaustion that arrives DURING a batch is reported as quota, never as a bare failure.
     *
     * <p>⚠️ THE FIXTURE EXHAUSTS QUOTA AFTER THE BATCH IS QUEUED, not before. A batch that is over
     * quota at queue time is refused by the 422 and never reaches the driver at all, so it would pass
     * under both the defect and the fix and prove nothing.
     *
     * <p>⚠️ WHAT THIS PINS, STATED HONESTLY: the SYNCHRONOUS leg. The primitive's own
     * {@code assertQuotaAvailable} throws on the calling thread and the driver's existing catch records
     * BLOCKED — behaviour that already worked, pinned here so it cannot regress into a bare FAILED. A
     * per-item pre-check was written for this and REMOVED after mutation showed it changed nothing
     * observable.
     *
     * <p>⚠️ WHAT THIS DOES NOT COVER: the ASYNC leg. Production showed the same exception arriving on
     * the generation thread inside {@code generateFromTopic}, where
     * {@code generateStudyPackFromExistingNoteAsync} swallows it and marks the note FAILED. Reaching
     * that deterministically needs quota to vanish between the primitive's synchronous check and the
     * worker's second assert — a window inside one item that this harness cannot open. The driver
     * carries a defensive re-check for it; it is NOT proven by this test, and the finding doc says so
     * rather than implying coverage that does not exist.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void quotaExhaustedDuringABatchIsReportedAsQuotaRatherThanAsABareFailure() {
        UUID owner = seedCuratorUser("bulk-regen-midbatch-quota");
        UUID first = seedRegenerationNote(owner, "Site Grading", "First body.");
        UUID second = seedRegenerationNote(owner, "Site Drainage", "Second body.");
        seedStudyPack(owner, first, "Pack one");
        seedStudyPack(owner, second, "Pack two");
        // FREE allows 10; leave exactly 2 so the batch is accepted at queue time.
        seedNoteGenerationUsage(owner, 8);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        // Burn the remaining allowance WHILE item 1 is still running, which is what a concurrent
        // regeneration on another surface does. By the time the driver reaches item 2 the meter is
        // gone, and item 2 never had a chance to see that at queue time.
        harness.beforeStudyPackForTitle = "Site Grading";
        harness.beforeStudyPack = () -> seedNoteGenerationUsage(owner, 2);
        UUID batchId = harness.run(owner, List.of(first, second), true);

        assertThat(itemState(batchId, first))
                .as("the item that fitted still regenerates")
                .isEqualTo("REGENERATED");
        assertThat(itemState(batchId, second))
                .as("the item that ran out is BLOCKED, not FAILED -- retry must not re-run it blindly")
                .isEqualTo("BLOCKED");
        assertThat(jdbcTemplate.queryForObject(
                "select reason_code from note_bulk_regeneration_item where batch_id = ? and note_id = ?",
                String.class, batchId, second))
                .as("and it says WHY, rather than a generic regeneration failure")
                .isEqualTo("NOTE_GENERATION_LIMIT_REACHED");
        assertThat(readNoteColumn(second, "content"))
                .as("nothing was written for the blocked item")
                .isEqualTo("Second body.");
    }

    private UUID seedRegenerationNote(UUID ownerUserId, String title, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " target_profile_type, status, subject, created_at, updated_at)"
                        + " values (?, ?, ?, ?, 'PRIVATE', '{}', 'STUDENT', 'GENERATED', 'Engineering',"
                        + " now(), now())",
                id, ownerUserId, title, content
        );
        return id;
    }

    private UUID seedQuizShareLink(UUID generatedQuizId, UUID ownerUserId, String token, boolean active) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into quiz_share_links (id, generated_quiz_id, owner_user_id, token, is_active, created_at)"
                        + " values (?, ?, ?, ?, ?, now())",
                id, generatedQuizId, ownerUserId, token, active
        );
        return id;
    }

    private void seedConceptHealth(UUID userId, UUID studyPackId, String concept) {
        jdbcTemplate.update(
                "insert into concept_health (id, user_id, study_pack_id, concept, incorrect_streak,"
                        + " last_correct_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, 0, now(), now(), now())",
                UUID.randomUUID(), userId, studyPackId, concept
        );
    }

    private boolean readShareLinkActive(UUID shareLinkId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_active from quiz_share_links where id = ?", Boolean.class, shareLinkId));
    }

    private String readNoteColumn(UUID noteId, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from notes where id = ?", String.class, noteId);
    }

    /**
     * Sums across every usage period rather than reading one row, so the assertion cannot be quietly
     * satisfied by a charge that landed in a period the test did not predict.
     */
    private int persistedUsage(UUID userId, String column) {
        Integer value = jdbcTemplate.queryForObject(
                "select coalesce(sum(" + column + "), 0) from user_usage where user_id = ?",
                Integer.class, userId);
        return value == null ? 0 : value;
    }

    /**
     * ⚠️ REPRODUCES PRODUCTION'S TRANSACTION SEMANTICS, AND THIS IS NOT COSMETIC — WITHOUT IT GUARD 2
     * PASSES FOR THE WRONG REASON.
     *
     * <p>A hand-built {@link UserUsageService} is not a Spring proxy, so its {@code @Transactional} is
     * inert. Its increments are {@code @Modifying} native upserts, which throw
     * {@code TransactionRequiredException} when no transaction is active — so a mutant that charges the
     * note-generation meter OUTSIDE the commit transaction would blow up harmlessly here and guard 2
     * would stay green while the defect was live in production, where the proxy opens a transaction and
     * the charge really lands. Verified by mutation: with the plain constructor the "charge eagerly"
     * mutant SURVIVED; with this wrapper it is killed.
     *
     * <p>Default REQUIRED propagation, exactly like the annotation: it joins the commit transaction when
     * one is active and opens its own when one is not.
     */
    private UserUsageService transactionalUserUsageService() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return new UserUsageService(
                userUsageRepository,
                new BillingUsagePeriodService(subscriptionRepository, userRepository)) {
            @Override
            public void incrementNoteGeneration(UUID userId, OffsetDateTime occurredAt) {
                template.execute(status -> {
                    super.incrementNoteGeneration(userId, occurredAt);
                    return null;
                });
            }

            @Override
            public void incrementStudyPackGeneration(UUID userId, OffsetDateTime occurredAt) {
                template.execute(status -> {
                    super.incrementStudyPackGeneration(userId, occurredAt);
                    return null;
                });
            }
        };
    }

    /**
     * Hand-builds a real {@link StudyPackService} over the real database, following the
     * {@code LinkedLearnerService} precedent in this class. Repositories, {@link UserUsageService},
     * {@link NoteGenerationUsageProtectionService} and {@link NoteGenerationService} are REAL, because the
     * guards assert persisted counters; only the LLM and the fire-and-forget collaborators are mocked.
     *
     * <p>The task dispatcher is synchronous so the async worker actually runs inside the test.
     */
    private final class RegenerationHarness {
        private String noteContent = "Regenerated body.";
        private List<String> keyConcepts = List.of("Regenerated concept");
        private RuntimeException studyPackFailure;
        /** Runs after the second LLM call returns and before the commit transaction opens. */
        private Runnable beforeCommit;

        private StudyPackService service() {
            LlmStudyPackService llm = mock(LlmStudyPackService.class);
            lenient().when(llm.generateNoteFromTopic(anyString(), any())).thenAnswer(invocation -> noteContent);
            if (studyPackFailure == null) {
                lenient().when(llm.generateStudyPack(anyString(), any())).thenAnswer(invocation -> {
                        if (beforeCommit != null) {
                            beforeCommit.run();
                        }
                        return new GeneratedStudyPackContent(
                                "Regenerated pack", "Regenerated summary", "Engineering",
                                List.of("regenerated"), keyConcepts, List.of(),
                                "test-model", 1, 1, 0, BigDecimal.ZERO);
                });
            } else {
                lenient().when(llm.generateStudyPack(anyString(), any())).thenThrow(studyPackFailure);
            }

            SubscriptionService subscriptionService = mock(SubscriptionService.class);
            lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);

            StudySnapProperties properties = new StudySnapProperties();
            UserUsageService userUsageService = transactionalUserUsageService();
            StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                    userRepository, noteRepository, new NoteCourseProgramRepository(jdbcTemplate),
                    new CourseProgramCatalogRepository(jdbcTemplate));
            NoteGenerationUsageProtectionService noteGenerationUsage =
                    new NoteGenerationUsageProtectionService(properties, userUsageService);
            NoteGenerationService noteGenerationService = new NoteGenerationService(
                    userRepository, subscriptionService, noteGenerationUsage, llm,
                    mock(ContentModerationService.class), mock(OnboardingGuardService.class),
                    resolver, new CourseProgramCatalogRepository(jdbcTemplate));
            GeneratedQuizService generatedQuizService = new GeneratedQuizService(
                    noteRepository, generatedQuizRepository, mock(QuizGenerationService.class),
                    subscriptionService, properties, userUsageService, mock(AuthService.class),
                    mock(AiRateLimitService.class), resolver, userRepository,
                    mock(QuizDocxExportService.class), mock(ExportUsageProtectionService.class),
                    quizShareLinkRepository);

            return new StudyPackService(
                    studyPackRepository,
                    mock(StudyPackDraftRepository.class),
                    noteRepository,
                    userRepository,
                    mock(OcrService.class),
                    llm,
                    properties,
                    mock(ActivityTrackingService.class),
                    mock(AnalyticsService.class),
                    subscriptionService,
                    userUsageService,
                    new StudyPackUsageService(userUsageService, studyPackRepository),
                    mock(OcrRateLimitService.class),
                    mock(OcrUsageProtectionService.class),
                    mock(AiRateLimitService.class),
                    resolver,
                    new TransactionTemplate(transactionManager),
                    new StudyPackGenerationTaskDispatcher(Runnable::run),
                    mock(ContentModerationService.class),
                    mock(ExamQuestionPoolService.class),
                    mock(OfficialChallengeQuizTemplateService.class),
                    mock(OnboardingGuardService.class),
                    mock(StudyPackQuizMasteryService.class),
                    noteGenerationService,
                    noteGenerationUsage,
                    generatedQuizService
            );
        }
    }

    // ================================================================================================
    // CURATOR BULK REGENERATION (v0.119.0, slices B1 + B2) -- REAL-ROW GUARDS.
    //
    // ⚠️ THEY LIVE HERE AND NOT IN A MOCKED TEST FOR A MEASURED REASON. The headline guard asserts
    // PERSISTED usage counters, and a hand-built UserUsageService is not a Spring proxy, so its
    // @Transactional is inert -- a "nothing was charged" assertion would pass for the wrong reason.
    // BulkRegenerationHarness therefore reuses transactionalUserUsageService() exactly as
    // RegenerationHarness does.
    //
    // ⚠️ EVERY ONE IS @Transactional(propagation = NOT_SUPPORTED), for the same reason the single-Note
    // regeneration guards above are: under @DataJpaTest's rollback transaction dispatchAfterCommit
    // registers a synchronization that never fires, so the worker would never run and every assertion
    // would pass because nothing happened at all.
    // ================================================================================================

    /**
     * GUARD 1 (quota) and GUARD 6 (continue-on-failure), in one run.
     *
     * <p>The fixture is the discriminating one: item 2 of 3 FAILS. A batch in which every item succeeds
     * passes under a driver that charges per selected note rather than per regenerated note, and under
     * one that stops at the first failure — it proves neither thing.
     *
     * <p>Asserted on PERSISTED counters: two units on each meter for two regenerated notes, and the
     * failed note's unit is not spent. Asserted on rows: item 3 still ran.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFailedItemCostsNoUnitsAndDoesNotStopTheItemsAfterIt() {
        UUID owner = seedCuratorUser("bulk-regen-quota");
        UUID first = seedRegenerationNote(owner, "Shear Force", "First body.");
        UUID second = seedRegenerationNote(owner, "Moment Distribution", "Second body.");
        UUID third = seedRegenerationNote(owner, "Influence Lines", "Third body.");
        seedStudyPack(owner, first, "First pack");
        seedStudyPack(owner, second, "Second pack");
        seedStudyPack(owner, third, "Third pack");

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.failStudyPackForTitle = "Moment Distribution";

        UUID batchId = harness.run(owner, List.of(first, second, third), true);

        assertThat(itemState(batchId, first)).as("item 1 regenerated").isEqualTo("REGENERATED");
        assertThat(itemState(batchId, second)).as("item 2 failed").isEqualTo("FAILED");
        assertThat(itemState(batchId, third))
                .as("GUARD 6: one failing item must not prevent later items from running")
                .isEqualTo("REGENERATED");
        assertThat(readNoteColumn(second, "content"))
                .as("the failed item's note keeps its original content -- nothing partial is written")
                .isEqualTo("Second body.");

        assertThat(persistedUsage(owner, "note_generations"))
                .as("GUARD 1: exactly one note-generation unit per REGENERATED item, none for the failure")
                .isEqualTo(2);
        assertThat(persistedUsage(owner, "study_pack_generations"))
                .as("GUARD 1: exactly one Study Pack unit per REGENERATED item, none for the failure")
                .isEqualTo(2);
    }

    /**
     * GUARD 2. A TEACHER curator's batch is metered; an ADMIN's is not.
     *
     * <p>⚠️ THIS IS THE GUARD AGAINST THE BYPASS BEING WIDENED, and it needs BOTH legs. An ADMIN-only
     * fixture bypasses quota entirely and proves nothing about the metered path the owner's decision
     * opened; a TEACHER-only fixture cannot tell "metered" from "the meter is broken for everyone".
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aTeacherBatchIsMeteredAndAnAdminBatchIsNot() {
        UUID teacher = seedCuratorUser("bulk-regen-teacher");
        UUID teacherNoteA = seedRegenerationNote(teacher, "Steel Design", "Body A.");
        UUID teacherNoteB = seedRegenerationNote(teacher, "Timber Design", "Body B.");
        seedStudyPack(teacher, teacherNoteA, "Pack A");
        seedStudyPack(teacher, teacherNoteB, "Pack B");

        UUID admin = seedCuratorUser("bulk-regen-admin");
        UUID adminNoteA = seedRegenerationNote(admin, "Hydraulics", "Body A.");
        UUID adminNoteB = seedRegenerationNote(admin, "Hydrology", "Body B.");
        seedStudyPack(admin, adminNoteA, "Pack A");
        seedStudyPack(admin, adminNoteB, "Pack B");

        // enforceLimits == true is exactly `user.role() != UserRole.ADMIN` for a TEACHER curator.
        new BulkRegenerationHarness().run(teacher, List.of(teacherNoteA, teacherNoteB), true);
        new BulkRegenerationHarness().run(admin, List.of(adminNoteA, adminNoteB), false);

        assertThat(readNoteColumn(teacherNoteA, "content"))
                .as("the TEACHER's batch really regenerated -- otherwise the meter assertion is vacuous")
                .isEqualTo("Regenerated body.");
        assertThat(readNoteColumn(adminNoteA, "content"))
                .as("the ADMIN's batch really regenerated too")
                .isEqualTo("Regenerated body.");

        assertThat(persistedUsage(teacher, "note_generations"))
                .as("a TEACHER curator is metered normally -- the ADMIN bypass is NOT widened to TEACHER")
                .isEqualTo(2);
        assertThat(persistedUsage(teacher, "study_pack_generations"))
                .as("both meters charge a TEACHER")
                .isEqualTo(2);
        assertThat(persistedUsage(admin, "note_generations"))
                .as("the pre-existing ADMIN bypass still applies to bulk regeneration")
                .isZero();
        assertThat(persistedUsage(admin, "study_pack_generations"))
                .as("the ADMIN bypass covers BOTH meters, as it does on every other generation path")
                .isZero();
    }

    /**
     * GUARD 3. An over-quota selection is rejected with 422 BEFORE dispatch, carrying how many notes to
     * remove — and nothing is generated and no item row is written.
     *
     * <p>⚠️ Asserted on persisted counters AND on the absence of rows. A test that only asserts the
     * exception passes under a driver that throws after already dispatching half the batch.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void anOverQuotaSelectionIsRejectedBeforeAnythingIsGeneratedOrRecorded() {
        UUID owner = seedCuratorUser("bulk-regen-overquota");
        UUID first = seedRegenerationNote(owner, "Fluid Mechanics", "First body.");
        UUID second = seedRegenerationNote(owner, "Thermodynamics", "Second body.");
        UUID third = seedRegenerationNote(owner, "Statics", "Third body.");
        seedStudyPack(owner, first, "Pack one");
        seedStudyPack(owner, second, "Pack two");
        seedStudyPack(owner, third, "Pack three");
        // FREE allows 10 note generations a month; burn 8 so only 2 of the 3 selected notes fit.
        seedNoteGenerationUsage(owner, 8);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();

        assertThatThrownBy(() -> harness.run(owner, List.of(first, second, third), true))
                .isInstanceOf(BulkNoteRegenerationQuotaExceededException.class)
                .hasMessageContaining("Remove 1 note to continue");

        assertThat(readNoteColumn(first, "content"))
                .as("nothing is generated when the selection is rejected")
                .isEqualTo("First body.");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_bulk_regeneration_item where owner_user_id = ?",
                Integer.class, owner))
                .as("no item row is written -- the rejection precedes the batch entirely")
                .isZero();
        assertThat(persistedUsage(owner, "note_generations"))
                .as("the pre-existing usage is untouched by a rejected selection")
                .isEqualTo(8);
    }

    /**
     * GUARD 4 — the outer-catch regression, and the one this whole table exists for.
     *
     * <p>{@code NoteBulkGenerationService.processBatch}'s outer catch clears its partial lists and marks
     * EVERY topic failed while {@code created_count} keeps its partial value. Reproduced here, a curator
     * whose batch was interrupted by a routine deploy would be told to regenerate notes that had already
     * succeeded — spending quota and replacing good content.
     *
     * <p>⚠️ THE FIXTURE MUST COMPLETE AT LEAST ONE ITEM BEFORE THE INTERRUPT. A batch interrupted before
     * anything finished passes under the defect and proves nothing, which is why this waits for item 1
     * to reach {@code REGENERATED} before interrupting the driver thread.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void anInterruptedBatchLeavesAlreadyCompletedItemsRecordedAsRegenerated() throws Exception {
        UUID owner = seedCuratorUser("bulk-regen-interrupt");
        UUID first = seedRegenerationNote(owner, "Development Length", "First body.");
        UUID second = seedRegenerationNote(owner, "Bar Cut-off", "Second body.");
        seedStudyPack(owner, first, "Pack one");
        seedStudyPack(owner, second, "Pack two");

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        // A wide throttle so the interrupt reliably lands in the pause BETWEEN items rather than inside
        // a JDBC call, which is where a real deploy's interrupt lands too.
        harness.throttleDelayMs = 4_000;
        UUID batchId = harness.start(owner, List.of(first, second), true);

        awaitItemState(batchId, first, "REGENERATED");
        harness.driverThread.get().interrupt();
        harness.driverThread.get().join(30_000);

        assertThat(itemState(batchId, first))
                .as("GUARD 4: an item that COMPLETED before the interrupt stays REGENERATED -- it is"
                        + " never rewritten as failed by a terminal catch")
                .isEqualTo("REGENERATED");
        assertThat(readNoteColumn(first, "content"))
                .as("and the completed item's new content really is persisted")
                .isEqualTo("Regenerated body.");
        assertThat(itemState(batchId, second))
                .as("the item the batch never reached stays PENDING -- not FAILED, and not REGENERATED")
                .isEqualTo("PENDING");
        assertThat(readNoteColumn(second, "content"))
                .as("an unreached note is untouched")
                .isEqualTo("Second body.");
    }

    /**
     * GUARD 5. The per-Note guards RE-RUN at item start, so a Note that passed preflight and then went
     * into {@code GENERATING} lands {@code BLOCKED} with its reason.
     *
     * <p>The race is driven deterministically: note 2 is flipped to {@code GENERATING} while note 1 is
     * still generating, which is exactly the window a single-Note regeneration occupies.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aNoteThatBecomesGeneratingBeforeItsTurnIsBlockedRatherThanForcedOrSkipped() {
        UUID owner = seedCuratorUser("bulk-regen-reguard");
        UUID first = seedRegenerationNote(owner, "Slope Stability", "First body.");
        UUID second = seedRegenerationNote(owner, "Retaining Walls", "Second body.");
        seedStudyPack(owner, first, "Pack one");
        seedStudyPack(owner, second, "Pack two");

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.throttleDelayMs = 0;
        harness.beforeStudyPackForTitle = "Slope Stability";
        harness.beforeStudyPack = () -> jdbcTemplate.update(
                "update notes set status = 'GENERATING' where id = ?", second);

        UUID batchId = harness.run(owner, List.of(first, second), true);

        assertThat(itemState(batchId, second))
                .as("GUARD 5: a Note blocked at its turn is BLOCKED -- never silently skipped, and"
                        + " never counted as REGENERATED")
                .isEqualTo("BLOCKED");
        assertThat(itemReasonCode(batchId, second))
                .as("and the curator is told WHY, so they can act on it")
                .isEqualTo("NOTE_GENERATION_IN_PROGRESS");
        assertThat(readNoteColumn(second, "content"))
                .as("a blocked Note's content is not touched")
                .isEqualTo("Second body.");
        assertThat(persistedUsage(owner, "note_generations"))
                .as("a blocked Note spends nothing -- only item 1 was charged")
                .isEqualTo(1);
    }

    /**
     * GUARD 7. Preflight and the driver return the SAME verdict for every blocked shape.
     *
     * <p>⚠️ ONE HAPPY NOTE PROVES NOTHING. Every deterministic blocked shape the driver can produce is
     * run through both paths: if they can ever disagree, the curator confirms one batch and receives a
     * different one.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void preflightAndTheDriverAgreeOnEveryBlockedShape() {
        UUID owner = seedCuratorUser("bulk-regen-agreement");

        UUID ready = seedRegenerationNote(owner, "Ready note", "Body.");
        seedStudyPack(owner, ready, "Pack");

        UUID generating = seedRegenerationNote(owner, "Already generating", "Body.");
        seedStudyPack(owner, generating, "Pack");
        jdbcTemplate.update("update notes set status = 'GENERATING' where id = ?", generating);

        UUID noPack = seedRegenerationNote(owner, "No Study Pack yet", "Body.");

        UUID noTitle = seedRegenerationNote(owner, "Placeholder", "Body.");
        seedStudyPack(owner, noTitle, "Pack");
        jdbcTemplate.update("update notes set title = '   ' where id = ?", noTitle);

        UUID multiProgram = seedRegenerationNote(owner, "Two programs, no Domain Context", "Body.");
        seedStudyPack(owner, multiProgram, "Pack");
        jdbcTemplate.update(
                "insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?), (?, ?, ?)",
                UUID.randomUUID(), multiProgram, UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.randomUUID(), multiProgram, UUID.fromString("20000000-0000-0000-0000-000000000005"));

        UUID notMine = seedRegenerationNote(seedCuratorUser("bulk-regen-someone-else"), "Not yours", "Body.");

        List<UUID> selection = List.of(ready, generating, noPack, noTitle, multiProgram, notMine);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.throttleDelayMs = 0;
        NoteRegenerationPreflightResponse preflight =
                harness.preflight(owner, selection, NoteRegenerationScope.NOTE_AND_STUDY_PACK, true);
        UUID batchId = harness.run(owner, selection, true);

        Map<UUID, String> preflightVerdicts = new LinkedHashMap<>();
        preflight.items().forEach(item -> preflightVerdicts.put(item.noteId(), item.readiness()));

        assertThat(preflightVerdicts)
                .as("preflight names every deterministic blocked shape, and nothing else")
                .containsEntry(ready, "READY")
                .containsEntry(generating, "BLOCKED")
                .containsEntry(noPack, "BLOCKED")
                .containsEntry(noTitle, "BLOCKED")
                .containsEntry(multiProgram, "BLOCKED")
                .containsEntry(notMine, "NOT_ELIGIBLE");

        assertThat(itemState(batchId, ready)).as("driver agrees: ready").isEqualTo("REGENERATED");
        assertThat(itemState(batchId, generating)).as("driver agrees: generating").isEqualTo("BLOCKED");
        assertThat(itemState(batchId, noPack)).as("driver agrees: no Study Pack").isEqualTo("BLOCKED");
        assertThat(itemState(batchId, noTitle)).as("driver agrees: no title").isEqualTo("BLOCKED");
        assertThat(itemState(batchId, multiProgram))
                .as("driver agrees: multi-program with no Domain Context").isEqualTo("BLOCKED");
        assertThat(itemState(batchId, notMine))
                .as("a Note that is not the caller's is NOT_RUN at item time, matching preflight's"
                        + " NOT_ELIGIBLE -- the same underlying miss, named for the moment it is seen")
                .isEqualTo("NOT_RUN");

        assertThat(preflight.readyCount()).as("only the ready note is counted as ready").isEqualTo(1);
        assertThat(preflight.noteGenerationUnitsRequired())
                .as("units are counted over the DISPATCHABLE set, not the raw selection -- otherwise a"
                        + " mostly-blocked selection is refused for units it would never spend")
                .isEqualTo(1);
        assertThat(persistedUsage(owner, "note_generations"))
                .as("and exactly that many units are actually spent")
                .isEqualTo(1);
        assertThat(readNoteColumn(noPack, "content"))
                .as("every blocked Note keeps its content")
                .isEqualTo("Body.");
    }

    /**
     * ⚠️ THE §A BLOCKER, AND THE SINGLE MOST DESTRUCTIVE THING THIS FEATURE COULD DO.
     * {@code NoteBulkGenerationService.processItem} always passes a non-null {@code preservedSubject},
     * which routes into {@code applyBulkGeneratedMetadataToNote} and UNCONDITIONALLY overwrites the
     * note's title and tags with LLM output. On a curator-authored canonical Note that destroys exactly
     * the titles the canonical-title doctrine protects.
     *
     * <p>⚠️ A fixture asserting only that CONTENT changed passes while titles are being destroyed. This
     * asserts title and tags are BYTE-IDENTICAL, on a Note whose regenerated pack carries a different
     * title and different tags — so the fixture can actually tell the two apart.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkRegenerationNeverOverwritesAnAuthoredTitleOrTags() {
        UUID owner = seedCuratorUser("bulk-regen-metadata");
        UUID noteId = seedRegenerationNote(owner, "Structural Applications of Differential Equations",
                "Authored body.");
        seedStudyPack(owner, noteId, "Old pack");
        jdbcTemplate.update("update notes set tags = '{\"curator-tag\",\"second-tag\"}' where id = ?", noteId);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.run(owner, List.of(noteId), true);

        assertThat(readNoteColumn(noteId, "content"))
                .as("the Note really was regenerated -- otherwise the metadata assertions are vacuous")
                .isEqualTo("Regenerated body.");
        assertThat(readNoteColumn(noteId, "title"))
                .as("the curator's authored title survives byte-identical; the regenerated pack's title"
                        + " ('Regenerated pack') must never be written onto the Note")
                .isEqualTo("Structural Applications of Differential Equations");
        assertThat(jdbcTemplate.queryForObject(
                "select array_to_string(tags, ',') from notes where id = ?", String.class, noteId))
                .as("and so do the curator's tags -- the LLM's tags never reach the Note")
                .isEqualTo("curator-tag,second-tag");
    }

    /**
     * ⚠️ THE SECOND §A BLOCKER. {@code NoteBulkGenerationService.processBatch} resolves ONE
     * {@code StudyPackGenerationContext} for the whole batch and passes it as an override, which makes
     * the primitive SKIP per-note resolution. Correct for "N topics under one authoring decision";
     * structurally wrong here, where each Note owns its Subject, Domain Context, Depth and program.
     *
     * <p>⚠️ TWO NOTES WITH DIFFERENT METADATA, DELIBERATELY. A single-Note batch, or two Notes sharing
     * metadata, passes under the batch-context defect and proves nothing.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void eachNoteInABatchGeneratesAgainstItsOwnContext() {
        UUID owner = seedCuratorUser("bulk-regen-context");
        UUID civil = seedRegenerationNote(owner, "Reinforced Concrete", "Body.");
        UUID nursing = seedRegenerationNote(owner, "Pharmacology", "Body.");
        seedStudyPack(owner, civil, "Pack one");
        seedStudyPack(owner, nursing, "Pack two");
        jdbcTemplate.update(
                "update notes set subject = 'Structural Engineering', domain_context = 'CIVIL_ENGINEERING',"
                        + " course_program = 'Civil Engineering' where id = ?", civil);
        jdbcTemplate.update(
                "update notes set subject = 'Pharmacology', domain_context = 'NURSING',"
                        + " course_program = 'Nursing' where id = ?", nursing);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.throttleDelayMs = 0;
        harness.run(owner, List.of(civil, nursing), true);

        assertThat(harness.contextsByTopic.get("Reinforced Concrete"))
                .as("the civil Note generates against ITS OWN domain")
                .extracting(StudyPackGenerationContext::courseProgram, StudyPackGenerationContext::subject)
                .containsExactly("Civil Engineering", "Structural Engineering");
        assertThat(harness.contextsByTopic.get("Pharmacology"))
                .as("and the nursing Note against ITS OWN -- not the first note's, and not one"
                        + " batch-wide context")
                .extracting(StudyPackGenerationContext::courseProgram, StudyPackGenerationContext::subject)
                .containsExactly("Nursing", "Pharmacology");
    }

    /**
     * ⚠️ BOTH §A BLOCKERS ON THE SCOPE WHERE THEY ARE REACHABLE, AND THAT CHOICE IS THE POINT.
     * {@code NoteBulkGenerationService.processItem} reaches the destructive branch by passing a non-null
     * {@code preservedSubject} and a batch-wide {@code generationContextOverride} into
     * {@code startAsyncGenerationFromNote} — which is exactly the call the Study-Pack-only scope makes.
     * A combined-scope fixture cannot express either defect, so it would pass under both and prove
     * nothing.
     *
     * <p>Blocker 1: {@code preservedSubject != null} routes into
     * {@code applyBulkGeneratedMetadataToNote}, which UNCONDITIONALLY overwrites title and tags with LLM
     * output — here, "Regenerated pack" and "llm-tag". Blocker 2: a non-null context override makes the
     * primitive SKIP per-note resolution, so both notes would generate against one domain.
     *
     * <p>⚠️ TWO NOTES WITH DIFFERENT SUBJECTS AND DOMAINS, deliberately: two notes sharing metadata pass
     * under the batch-context defect.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void studyPackScopePreservesAuthoredMetadataAndResolvesEachNotesOwnContext() {
        UUID owner = seedCuratorUser("bulk-regen-blockers");
        UUID civil = seedRegenerationNote(owner, "Structural Applications of Shear",
                "Civil body, hand written.");
        UUID nursing = seedRegenerationNote(owner, "Pharmacokinetics in Practice",
                "Nursing body, hand written.");
        seedStudyPack(owner, civil, "Old pack one");
        seedStudyPack(owner, nursing, "Old pack two");
        jdbcTemplate.update(
                "update notes set subject = 'Structural Engineering', domain_context = 'CIVIL_ENGINEERING',"
                        + " course_program = 'Civil Engineering', tags = '{\"authored-civil\"}' where id = ?",
                civil);
        jdbcTemplate.update(
                "update notes set subject = 'Pharmacology', domain_context = 'NURSING',"
                        + " course_program = 'Nursing', tags = '{\"authored-nursing\"}' where id = ?",
                nursing);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.throttleDelayMs = 0;
        UUID batchId = harness.run(owner, List.of(civil, nursing), NoteRegenerationScope.STUDY_PACK, true);

        assertThat(itemState(batchId, civil)).isEqualTo("REGENERATED");
        assertThat(jdbcTemplate.queryForObject(
                "select title from study_packs where note_id = ?", String.class, civil))
                .as("the packs really were regenerated -- otherwise every assertion below is vacuous")
                .isEqualTo("Regenerated pack");

        assertThat(readNoteColumn(civil, "title"))
                .as("BLOCKER 1: the curator's authored title survives; the pack's LLM title never"
                        + " reaches the Note")
                .isEqualTo("Structural Applications of Shear");
        assertThat(readNoteColumn(nursing, "title"))
                .as("BLOCKER 1: and so does the second note's")
                .isEqualTo("Pharmacokinetics in Practice");
        assertThat(jdbcTemplate.queryForObject(
                "select array_to_string(tags, ',') from notes where id = ?", String.class, civil))
                .as("BLOCKER 1: authored tags survive; the LLM's 'llm-tag' never reaches the Note")
                .isEqualTo("authored-civil");
        assertThat(jdbcTemplate.queryForObject(
                "select array_to_string(tags, ',') from notes where id = ?", String.class, nursing))
                .as("BLOCKER 1: and so do the second note's")
                .isEqualTo("authored-nursing");

        assertThat(harness.contextsByTopic.get("Civil body, hand written."))
                .as("BLOCKER 2: the civil Note generates against ITS OWN domain and subject")
                .extracting(StudyPackGenerationContext::courseProgram, StudyPackGenerationContext::subject)
                .containsExactly("Civil Engineering", "Structural Engineering");
        assertThat(harness.contextsByTopic.get("Nursing body, hand written."))
                .as("BLOCKER 2: and the nursing Note against ITS OWN -- not the first note's, and not"
                        + " one batch-wide context")
                .extracting(StudyPackGenerationContext::courseProgram, StudyPackGenerationContext::subject)
                .containsExactly("Nursing", "Pharmacology");
    }

    /**
     * The Study-Pack-only scope spends NO note-generation units and deactivates NO share links, matching
     * the single-Note primitive exactly.
     *
     * <p>⚠️ Without this, a scope-blind unit count would 422 a Study-Pack-only batch on a note-generation
     * allowance it never touches, and the preflight would promise the curator a share-link consequence
     * that never happens.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void studyPackOnlyScopeSpendsNoNoteGenerationUnitsAndDeactivatesNoShareLinks() {
        UUID owner = seedCuratorUser("bulk-regen-scope");
        UUID noteId = seedRegenerationNote(owner, "Surveying", "Original body.");
        seedStudyPack(owner, noteId, "Old pack");
        UUID generatedQuizId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", generatedQuizId, owner, noteId);
        UUID liveLink = seedQuizShareLink(generatedQuizId, owner, "bulk-regen-scope-link", true);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        NoteRegenerationPreflightResponse preflight =
                harness.preflight(owner, List.of(noteId), NoteRegenerationScope.STUDY_PACK, true);
        assertThat(preflight.noteGenerationUnitsRequired())
                .as("Study-Pack-only regeneration asserts and charges only the Study Pack meter")
                .isZero();
        assertThat(preflight.sharedQuizzesToDeactivate())
                .as("and it does not replace the Note content the shared quiz was built from")
                .isZero();

        UUID batchId = harness.run(owner, List.of(noteId), NoteRegenerationScope.STUDY_PACK, true);

        assertThat(itemState(batchId, noteId)).isEqualTo("REGENERATED");
        assertThat(readNoteColumn(noteId, "content"))
                .as("Study-Pack-only regeneration leaves the Note body alone")
                .isEqualTo("Original body.");
        assertThat(persistedUsage(owner, "note_generations"))
                .as("no note-generation unit is spent by the Study-Pack-only scope")
                .isZero();
        assertThat(persistedUsage(owner, "study_pack_generations")).isEqualTo(1);
        assertThat(readShareLinkActive(liveLink))
                .as("and the live share link stays live")
                .isTrue();
    }

    /**
     * The combined scope's consequence counts are EXACT and are recorded on the item that caused them.
     * Two notes, only one of which carries a live share link, so a fixture where every note has one
     * cannot pass by accident.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void combinedScopeCountsAndRecordsExactlyTheShareLinksItTurnsOff() {
        UUID owner = seedCuratorUser("bulk-regen-consequences");
        UUID shared = seedRegenerationNote(owner, "Shared note", "Body.");
        UUID unshared = seedRegenerationNote(owner, "Unshared note", "Body.");
        seedStudyPack(owner, shared, "Pack one");
        seedStudyPack(owner, unshared, "Pack two");
        jdbcTemplate.update("update notes set visibility = 'PUBLIC' where id = ?", shared);
        UUID sharedQuizId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", sharedQuizId, owner, shared);
        UUID liveLink = seedQuizShareLink(sharedQuizId, owner, "bulk-regen-consequence-link", true);
        UUID unsharedQuizId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into generated_quizzes (id, owner_user_id, note_id, questions, generated_at, updated_at)"
                        + " values (?, ?, ?, '[]'::jsonb, now(), now())", unsharedQuizId, owner, unshared);
        seedQuizShareLink(unsharedQuizId, owner, "bulk-regen-consequence-inactive", false);

        BulkRegenerationHarness harness = new BulkRegenerationHarness();
        harness.throttleDelayMs = 0;
        NoteRegenerationPreflightResponse preflight = harness.preflight(
                owner, List.of(shared, unshared), NoteRegenerationScope.NOTE_AND_STUDY_PACK, true);

        assertThat(preflight.publicNotesAffected())
                .as("one of the two selected notes is public")
                .isEqualTo(1);
        assertThat(preflight.sharedQuizzesToDeactivate())
                .as("EXACT, not an estimate: only the note with a LIVE link is counted")
                .isEqualTo(1);

        UUID batchId = harness.run(owner, List.of(shared, unshared), true);

        assertThat(readShareLinkActive(liveLink))
                .as("the live link really is turned off by the combined scope")
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select share_link_deactivated from note_bulk_regeneration_item"
                        + " where batch_id = ? and note_id = ?", Boolean.class, batchId, shared))
                .as("and the receipt records WHICH item did it")
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select share_link_deactivated from note_bulk_regeneration_item"
                        + " where batch_id = ? and note_id = ?", Boolean.class, batchId, unshared))
                .as("the item with no live link records none")
                .isFalse();
    }

    /**
     * The receipt expires on the BATCH's clock, so a batch disappears whole.
     *
     * <p>⚠️ THE FIXTURE MIXES CLOCKS ON PURPOSE. Sweeping on each row's own {@code updated_at} would
     * keep the late item of an expired batch — a receipt with a hole in it — and a fixture whose rows
     * share both timestamps passes under either implementation.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void anExpiredBatchIsSweptWholeRatherThanRowByRow() {
        UUID owner = seedCuratorUser("bulk-regen-ttl");
        UUID batchId = UUID.randomUUID();
        OffsetDateTime batchCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(30);
        seedBulkRegenerationItem(batchId, owner, UUID.randomUUID(), batchCreatedAt, batchCreatedAt);
        // Same batch, resolved 29 hours later than it started: fresh updated_at, expired batch clock.
        seedBulkRegenerationItem(batchId, owner, UUID.randomUUID(), batchCreatedAt,
                OffsetDateTime.now(ZoneOffset.UTC));
        UUID freshBatchId = UUID.randomUUID();
        seedBulkRegenerationItem(freshBatchId, owner, UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));

        // ⚠️ Driven through a TransactionTemplate for the same measured reason transactionalUserUsageService
        // exists: a hand-built service is not a Spring proxy, so its @Transactional is INERT here and the
        // bulk delete would throw instead of running. In production the proxy opens this transaction.
        NoteBulkRegenerationReceiptService receiptService =
                new NoteBulkRegenerationReceiptService(bulkRegenerationItemRepository, noteRepository);
        Long deleted = new TransactionTemplate(transactionManager).execute(status ->
                receiptService.deleteExpiredItems(OffsetDateTime.now(ZoneOffset.UTC)));

        assertThat(deleted).as("both rows of the expired batch go, including the freshly updated one")
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_bulk_regeneration_item where batch_id = ?",
                Integer.class, batchId))
                .as("no half-swept receipt survives")
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from note_bulk_regeneration_item where batch_id = ?",
                Integer.class, freshBatchId))
                .as("a batch inside the TTL is untouched")
                .isEqualTo(1);
    }

    // --- bulk regeneration fixture helpers ------------------------------------------------------------

    private void seedBulkRegenerationItem(
            UUID batchId,
            UUID ownerUserId,
            UUID noteId,
            OffsetDateTime batchCreatedAt,
            OffsetDateTime updatedAt
    ) {
        jdbcTemplate.update(
                "insert into note_bulk_regeneration_item (id, batch_id, owner_user_id, note_id, scope,"
                        + " state, share_link_deactivated, batch_created_at, updated_at)"
                        + " values (?, ?, ?, ?, 'NOTE_AND_STUDY_PACK', 'REGENERATED', false, ?, ?)",
                UUID.randomUUID(), batchId, ownerUserId, noteId, batchCreatedAt, updatedAt);
    }

    /**
     * ⚠️ Charged through the REAL usage service rather than an INSERT, so the seeded row carries exactly
     * the shape and billing period production writes. A hand-built row that drifts from the real period
     * resolution would leave the over-quota guard asserting against usage the service cannot see.
     */
    private void seedNoteGenerationUsage(UUID userId, int noteGenerations) {
        UserUsageService usageService = transactionalUserUsageService();
        for (int index = 0; index < noteGenerations; index++) {
            usageService.incrementNoteGeneration(userId, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private String itemState(UUID batchId, UUID noteId) {
        return jdbcTemplate.queryForObject(
                "select state from note_bulk_regeneration_item where batch_id = ? and note_id = ?",
                String.class, batchId, noteId);
    }

    private String itemReasonCode(UUID batchId, UUID noteId) {
        return jdbcTemplate.queryForObject(
                "select reason_code from note_bulk_regeneration_item where batch_id = ? and note_id = ?",
                String.class, batchId, noteId);
    }

    private void awaitItemState(UUID batchId, UUID noteId, String expectedState) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (expectedState.equals(itemState(batchId, noteId))) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        throw new IllegalStateException(
                "item " + noteId + " never reached " + expectedState + " (was " + itemState(batchId, noteId) + ")");
    }

    /**
     * Builds a real {@link NoteBulkRegenerationService} over the real database and a real
     * {@link StudyPackService}, following {@link RegenerationHarness} exactly. Only the LLM, the rate
     * limiter and the fire-and-forget collaborators are mocked; every repository, both usage services
     * and the readiness/consequence guards are real, because these guards assert persisted rows and
     * persisted counters.
     *
     * <p>⚠️ {@code AiRateLimitService} is mocked deliberately. With a synchronous dispatcher a batch's
     * items run back to back, so the real limiter's 5-per-minute FREE bucket would fail items for a
     * reason production — where each item is two real LLM calls — never hits.
     */
    private final class BulkRegenerationHarness {
        private final Map<String, StudyPackGenerationContext> contextsByTopic = new ConcurrentHashMap<>();
        private final AtomicReference<Thread> driverThread = new AtomicReference<>();
        private String failStudyPackForTitle;
        private String beforeStudyPackForTitle;
        private Runnable beforeStudyPack;
        private int throttleDelayMs = 500;

        private UUID run(UUID ownerUserId, List<UUID> noteIds, boolean enforceLimits) {
            return run(ownerUserId, noteIds, NoteRegenerationScope.NOTE_AND_STUDY_PACK, enforceLimits);
        }

        private UUID run(
                UUID ownerUserId,
                List<UUID> noteIds,
                NoteRegenerationScope scope,
                boolean enforceLimits
        ) {
            UUID batchId = start(ownerUserId, noteIds, scope, enforceLimits);
            try {
                driverThread.get().join(120_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return batchId;
        }

        private UUID start(UUID ownerUserId, List<UUID> noteIds, boolean enforceLimits) {
            return start(ownerUserId, noteIds, NoteRegenerationScope.NOTE_AND_STUDY_PACK, enforceLimits);
        }

        private UUID start(
                UUID ownerUserId,
                List<UUID> noteIds,
                NoteRegenerationScope scope,
                boolean enforceLimits
        ) {
            return service().queueBatch(
                    new BulkRegenerateNotesRequest(noteIds, scope.name()), ownerUserId, enforceLimits
            ).batchId();
        }

        private NoteRegenerationPreflightResponse preflight(
                UUID ownerUserId,
                List<UUID> noteIds,
                NoteRegenerationScope scope,
                boolean enforceLimits
        ) {
            NoteRegenerationReadinessService readiness = readinessService();
            NoteRegenerationConsequenceService consequences = consequenceService();
            return new NoteRegenerationPreflightService(
                    noteRepository,
                    readiness,
                    consequences,
                    driver(readiness, consequences),
                    mePlanService(),
                    mock(OnboardingGuardService.class),
                    // ⚠️ REAL, not mocked -- a mocked gate would let every one of these guards pass
                    // against a non-curator and prove nothing about who may run a batch.
                    new BulkRegenerationAccessGuard(userRepository)
            ).preflight(new NoteRegenerationPreflightRequest(noteIds, scope.name()),
                    ownerUserId, enforceLimits);
        }

        private NoteRegenerationReadinessService readinessService() {
            return new NoteRegenerationReadinessService(
                    noteRepository, studyPackRepository, new NoteCourseProgramRepository(jdbcTemplate));
        }

        private NoteRegenerationConsequenceService consequenceService() {
            return new NoteRegenerationConsequenceService(generatedQuizRepository, quizShareLinkRepository);
        }

        private MePlanService mePlanService() {
            SubscriptionService subscriptionService = mock(SubscriptionService.class);
            lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);
            UserUsageService userUsageService = transactionalUserUsageService();
            return new MePlanService(
                    subscriptionService,
                    userUsageService,
                    new StudyPackUsageService(userUsageService, studyPackRepository),
                    userRepository,
                    mock(FeatureGateService.class),
                    new StudySnapProperties()
            );
        }

        private NoteBulkRegenerationService driver(
                NoteRegenerationReadinessService readiness,
                NoteRegenerationConsequenceService consequences
        ) {
            return new NoteBulkRegenerationService(
                    noteRepository,
                    bulkRegenerationItemRepository,
                    readiness,
                    consequences,
                    studyPackService(),
                    mePlanService(),
                    mock(OnboardingGuardService.class),
                    new BulkGenerationFailureReasonNormalizer(),
                    new NoteBulkRegenerationTaskDispatcher(task -> {
                        Thread thread = new Thread(task, "test-bulk-regeneration-driver");
                        driverThread.set(thread);
                        thread.start();
                    }),
                    new BulkRegenerationAccessGuard(userRepository),
                    50,
                    throttleDelayMs,
                    10,
                    60_000L
            );
        }

        private NoteBulkRegenerationService service() {
            NoteRegenerationReadinessService readiness = readinessService();
            return driver(readiness, consequenceService());
        }

        /**
         * The same wiring as {@link RegenerationHarness}, with the LLM keyed by TOPIC so a batch can make
         * one item fail while its neighbours succeed, and recording the context each topic was generated
         * against so the per-note-context guard has something to assert on.
         */
        private StudyPackService studyPackService() {
            LlmStudyPackService llm = mock(LlmStudyPackService.class);
            lenient().when(llm.generateNoteFromTopic(anyString(), any())).thenAnswer(invocation -> {
                String topic = invocation.getArgument(0);
                contextsByTopic.put(topic, invocation.getArgument(1));
                if (topic.equals(beforeStudyPackForTitle) && beforeStudyPack != null) {
                    beforeStudyPack.run();
                }
                if (topic.equals(failStudyPackForTitle)) {
                    throw new IllegalStateException("generation exploded for " + topic);
                }
                return "Regenerated body.";
            });
            lenient().when(llm.generateStudyPack(anyString(), any())).thenAnswer(invocation -> {
                // Keyed by SOURCE TEXT as well as topic, so the per-note-context guard also covers the
                // Study-Pack-only scope -- which is the scope where a batch-wide context override is
                // actually passable, and therefore where the defect is reachable.
                contextsByTopic.put(invocation.getArgument(0), invocation.getArgument(1));
                return new GeneratedStudyPackContent(
                        "Regenerated pack", "Regenerated summary", "Engineering",
                        List.of("llm-tag"), List.of("Regenerated concept"), List.of(),
                        "test-model", 1, 1, 0, BigDecimal.ZERO);
            });

            SubscriptionService subscriptionService = mock(SubscriptionService.class);
            lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);

            StudySnapProperties properties = new StudySnapProperties();
            UserUsageService userUsageService = transactionalUserUsageService();
            StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                    userRepository, noteRepository, new NoteCourseProgramRepository(jdbcTemplate),
                    new CourseProgramCatalogRepository(jdbcTemplate));
            NoteGenerationUsageProtectionService noteGenerationUsage =
                    new NoteGenerationUsageProtectionService(properties, userUsageService);
            NoteGenerationService noteGenerationService = new NoteGenerationService(
                    userRepository, subscriptionService, noteGenerationUsage, llm,
                    mock(ContentModerationService.class), mock(OnboardingGuardService.class),
                    resolver, new CourseProgramCatalogRepository(jdbcTemplate));
            GeneratedQuizService generatedQuizService = new GeneratedQuizService(
                    noteRepository, generatedQuizRepository, mock(QuizGenerationService.class),
                    subscriptionService, properties, userUsageService, mock(AuthService.class),
                    mock(AiRateLimitService.class), resolver, userRepository,
                    mock(QuizDocxExportService.class), mock(ExportUsageProtectionService.class),
                    quizShareLinkRepository);

            return new StudyPackService(
                    studyPackRepository,
                    mock(StudyPackDraftRepository.class),
                    noteRepository,
                    userRepository,
                    mock(OcrService.class),
                    llm,
                    properties,
                    mock(ActivityTrackingService.class),
                    mock(AnalyticsService.class),
                    subscriptionService,
                    userUsageService,
                    new StudyPackUsageService(userUsageService, studyPackRepository),
                    mock(OcrRateLimitService.class),
                    mock(OcrUsageProtectionService.class),
                    mock(AiRateLimitService.class),
                    resolver,
                    new TransactionTemplate(transactionManager),
                    new StudyPackGenerationTaskDispatcher(Runnable::run),
                    mock(ContentModerationService.class),
                    mock(ExamQuestionPoolService.class),
                    mock(OfficialChallengeQuizTemplateService.class),
                    mock(OnboardingGuardService.class),
                    mock(StudyPackQuizMasteryService.class),
                    noteGenerationService,
                    noteGenerationUsage,
                    generatedQuizService
            );
        }
    }
}
