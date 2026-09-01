package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerInvitationLinkEntity;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.EmailService;
import com.studysnap.backend.service.EmailTemplateService;
import com.studysnap.backend.service.GuardianConsentPolicy;
import com.studysnap.backend.service.LinkedLearnerService;
import com.studysnap.backend.service.OnboardingGuardService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NoteLibraryRepositoryImpl noteLibraryRepository;

    @Autowired
    private PublicLibraryRepositoryImpl publicLibraryRepository;

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
    private LinkedLearnerInvitationLinkRepository invitationLinkRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LinkedLearnerRequestExpiryWorker requestExpiryWorker;

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
                    NoteVisibility.PRIVATE
            );
            noteLibraryRepository.findLibraryPage(criteria, NoteLibrarySort.RECENTLY_UPDATED, 0, 10);
            noteLibraryRepository.countLibraryMatches(criteria);
            noteLibraryRepository.findLibraryCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectIdCandidates(criteria);
            noteLibraryRepository.findLibraryMatchingIds(criteria, 10);
        }
        NoteLibraryFilterCriteria allOwned = new NoteLibraryFilterCriteria(
                ownerUserId, null, NoteLibraryReadiness.ALL, null, List.of(), null
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
}
