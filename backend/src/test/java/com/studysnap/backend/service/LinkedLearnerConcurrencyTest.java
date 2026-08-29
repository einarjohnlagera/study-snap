package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.InvitationRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * The concurrency interleavings that belong together in one two-transaction harness.
 *
 * <p>⚠️ WHY THIS EXISTS. Two release-blocking races were found in {@code v0.90.0} while every unit
 * test was green, because a mocked repository has no isolation, no locks and no second transaction.
 * These tests run TWO REAL TRANSACTIONS on TWO THREADS against a real database and a real
 * transaction manager, coordinated by latches rather than sleeps, and assert the PERSISTED row —
 * never a returned DTO or a method invocation.
 *
 * <p>⚠️ HARNESS LIMITATION, stated rather than glossed: the relationship repositories are wired to
 * JDBC instead of Hibernate, so the original five cases prove the pessimistic learner lock and
 * conditional status updates against real SQL semantics. The grant/revoke case coordinates two real
 * service transactions but models the native insert's zero-row conditional outcome through its
 * repository boundary; the exact insert syntax and ACCEPTED predicate are separately executed against
 * PostgreSQL. H2 was probed first and confirmed to BLOCK on {@code SELECT ... FOR UPDATE} contention
 * rather than throw, which is the production behaviour the relationship cases depend on.
 */
@SpringJUnitConfig(LinkedLearnerConcurrencyTest.TestConfiguration.class)
class LinkedLearnerConcurrencyTest {
    private static final int ADULT_YEAR = Year.now().getValue() - 30;
    private static final int MINOR_YEAR = Year.now().getValue() - 10;

    @Autowired private LinkedLearnerService service;
    @Autowired private LinkedLearnerGrantService grantService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private LinkedLearnerRelationshipRepository relationshipRepository;
    @Autowired private LinkedLearnerGuardianConsentRepository consentRepository;
    @Autowired private LinkedLearnerGrantRepository grantRepository;
    @Autowired private LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID learnerId;
    private UUID supporterId;
    private UUID relationshipId;
    private TransactionTemplate newTransaction;

    @BeforeEach
    void setUp() {
        learnerId = UUID.randomUUID();
        supporterId = UUID.randomUUID();
        relationshipId = UUID.randomUUID();
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        jdbcTemplate.execute("drop table if exists linked_learner_grants");
        jdbcTemplate.execute("drop table if exists linked_learner_relationships");
        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("""
                create table users (
                    id uuid primary key,
                    birth_year integer,
                    birth_year_updated_at timestamp with time zone,
                    updated_at timestamp with time zone not null
                )""");
        jdbcTemplate.execute("""
                create table linked_learner_relationships (
                    id uuid primary key,
                    supporter_user_id uuid not null,
                    learner_user_id uuid not null,
                    status varchar(16) not null,
                    accepted_at timestamp with time zone,
                    revoked_at timestamp with time zone,
                    expires_at timestamp with time zone
                )""");
        jdbcTemplate.execute("""
                create table linked_learner_grants (
                    id uuid primary key,
                    relationship_id uuid not null,
                    from_user_id uuid not null,
                    to_user_id uuid not null,
                    scope varchar(16) not null,
                    granted_at timestamp with time zone not null,
                    revoked_at timestamp with time zone
                )""");
        jdbcTemplate.update("insert into users values (?, ?, null, ?)",
                learnerId, ADULT_YEAR, OffsetDateTime.now());
        jdbcTemplate.update("insert into users values (?, ?, null, ?)",
                supporterId, ADULT_YEAR, OffsetDateTime.now());

        wireRepositories();
    }

    // ---------------------------------------------------------------- finding 1: consent bypass

    @Test
    void correctionIntoTheMinorRangeCommittingFirstForcesAcceptanceToRequireConsent() throws Exception {
        seedRelationship(LinkedLearnerStatus.PENDING);
        CountDownLatch correctionHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseCorrection = new CountDownLatch(1);

        // The correction pauses INSIDE its transaction while holding the learner's write lock.
        when(relationshipRepository.findByLearnerUserIdAndStatus(learnerId, LinkedLearnerStatus.ACCEPTED))
                .thenAnswer(invocation -> {
                    correctionHoldsLock.countDown();
                    releaseCorrection.await(5, TimeUnit.SECONDS);
                    return readRelationships(LinkedLearnerStatus.ACCEPTED);
                });

        AtomicReference<Throwable> correctionError = new AtomicReference<>();
        AtomicReference<Throwable> acceptError = new AtomicReference<>();

        Thread correction = run(correctionError,
                () -> newTransaction.executeWithoutResult(status -> service.correctBirthYear(learnerId, MINOR_YEAR)));
        correction.start();
        assertThat(correctionHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

        // Acceptance now blocks on the learner row until the correction commits.
        Thread acceptance = run(acceptError, () -> newTransaction.executeWithoutResult(status ->
                service.accept(relationshipId, supporterId, new AcceptLinkedLearnerRequest(null, false))));
        acceptance.start();
        // ⚠️ No yield, no sleep, and no race to win. The acceptance is not started until the
        // correction already holds the lock (the await above), so it can only reach the lock
        // afterwards — it either blocks until the correction commits, or arrives after it has.
        // Either way it reads the committed corrected year, which is why this does not flake.
        releaseCorrection.countDown();

        correction.join(10_000);
        acceptance.join(10_000);
        assertThat(correctionError.get()).isNull();
        assertThat(acceptError.get()).isNull();

        // ⚠️ THE ASSERTION THAT MATTERS: persisted state, not a DTO. A minor with no consent record
        // must not hold an ACCEPTED relationship.
        assertThat(persistedBirthYear()).isEqualTo(MINOR_YEAR);
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.PENDING.name());
        assertThat(persistedAcceptedAt()).isNull();
    }

    @Test
    void acceptanceCommittingFirstIsStillPausedByACorrectionIntoTheMinorRange() throws Exception {
        seedRelationship(LinkedLearnerStatus.PENDING);
        CountDownLatch acceptanceHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseAcceptance = new CountDownLatch(1);
        AtomicReference<Boolean> acceptanceWasReleased = new AtomicReference<>();

        // Acceptance pauses while holding the learner lock, having already read the ADULT year.
        Thread[] acceptanceThread = new Thread[1];
        when(consentRepository.findByRelationshipId(relationshipId)).thenAnswer(invocation -> {
            if (Thread.currentThread() == acceptanceThread[0]) {
                acceptanceHoldsLock.countDown();
                // Recorded and asserted below: a timeout here would let the test assert persisted
                // state mid-flight and pass without the interleaving ever happening.
                acceptanceWasReleased.set(releaseAcceptance.await(5, TimeUnit.SECONDS));
            }
            return Optional.empty();
        });

        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Throwable> correctionError = new AtomicReference<>();

        Thread acceptance = run(acceptError, () -> newTransaction.executeWithoutResult(status ->
                service.accept(relationshipId, supporterId, new AcceptLinkedLearnerRequest(null, false))));
        acceptanceThread[0] = acceptance;
        acceptance.start();
        assertThat(acceptanceHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

        Thread correction = run(correctionError,
                () -> newTransaction.executeWithoutResult(status -> service.correctBirthYear(learnerId, MINOR_YEAR)));
        correction.start();
        releaseAcceptance.countDown();

        acceptance.join(10_000);
        correction.join(10_000);
        assertThat(acceptanceWasReleased.get()).as("the acceptance must resume by release, not timeout").isTrue();
        assertThat(acceptError.get()).isNull();
        assertThat(correctionError.get()).isNull();

        // ⚠️ CORRECT IN EITHER ORDERING, deliberately — and that is what makes it deterministic
        // rather than a race. If the correction reaches the learner lock first it blocks until the
        // acceptance commits, then observes the ACCEPTED row and pauses it. If it arrives after the
        // acceptance has already committed, it observes the same row and pauses it just the same.
        // The LOCK is what removes the third possibility: without it the correction could read the
        // relationship list mid-flight, find nothing to pause, and leave a minor ACCEPTED — which
        // is exactly what happened while this harness was stubbed to skip the lock.
        assertThat(persistedBirthYear()).isEqualTo(MINOR_YEAR);
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.PENDING.name());
        assertThat(persistedAcceptedAt()).isNull();
    }

    // ------------------------------------------------------- finding 2: revocation overwritten


    @Test
    void revocationWaitsForCorrectionAndStillEndsRevoked() throws Exception {
        // Provisional cleanup makes revoke resolve the relationship's effective learner year. It
        // therefore takes the learner lock BEFORE its relationship update, the same order as
        // correction and acceptance. This prevents a learner-lock/relationship-lock cycle.
        seedRelationship(LinkedLearnerStatus.ACCEPTED);
        CountDownLatch correctionHasSelected = new CountDownLatch(1);
        CountDownLatch releaseCorrection = new CountDownLatch(1);

        when(relationshipRepository.findByLearnerUserIdAndStatus(learnerId, LinkedLearnerStatus.ACCEPTED))
                .thenAnswer(invocation -> {
                    List<LinkedLearnerRelationshipEntity> selected = readRelationships(LinkedLearnerStatus.ACCEPTED);
                    correctionHasSelected.countDown();
                    releaseCorrection.await(5, TimeUnit.SECONDS);
                    return selected;
                });

        AtomicReference<Throwable> correctionError = new AtomicReference<>();
        Thread correction = run(correctionError,
                () -> newTransaction.executeWithoutResult(status -> service.correctBirthYear(learnerId, MINOR_YEAR)));
        correction.start();
        assertThat(correctionHasSelected.await(5, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        Thread revocation = run(revokeError,
                () -> newTransaction.executeWithoutResult(status -> service.revoke(relationshipId, learnerId)));
        revocation.start();
        revocation.join(200);
        assertThat(revocation.isAlive()).as("revoke waits on the learner lock").isTrue();

        releaseCorrection.countDown();
        correction.join(10_000);
        revocation.join(10_000);

        assertThat(correctionError.get()).isNull();
        assertThat(revokeError.get()).isNull();
        assertThat(persistedBirthYear()).isEqualTo(MINOR_YEAR);
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.REVOKED.name());
    }

    @Test
    void revocationWaitsForAcceptanceAndThenCutsTheAcceptedConnection() throws Exception {
        seedRelationship(LinkedLearnerStatus.PENDING);
        CountDownLatch acceptanceHasReadPending = new CountDownLatch(1);
        CountDownLatch releaseAcceptance = new CountDownLatch(1);

        AtomicReference<Thread> pauseOn = new AtomicReference<>();
        when(consentRepository.findByRelationshipId(relationshipId)).thenAnswer(invocation -> {
            if (Thread.currentThread() == pauseOn.get()) {
                pauseOn.set(null);
                acceptanceHasReadPending.countDown();
                releaseAcceptance.await(5, TimeUnit.SECONDS);
            }
            return Optional.empty();
        });

        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        Thread acceptance = run(acceptError, () -> newTransaction.executeWithoutResult(status ->
                service.accept(relationshipId, supporterId, new AcceptLinkedLearnerRequest(null, false))));
        pauseOn.set(acceptance);
        acceptance.start();
        assertThat(acceptanceHasReadPending.await(5, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        Thread revocation = run(revokeError,
                () -> newTransaction.executeWithoutResult(status -> service.revoke(relationshipId, learnerId)));
        revocation.start();
        revocation.join(200);
        assertThat(revocation.isAlive()).as("revoke waits on acceptance's learner lock").isTrue();

        releaseAcceptance.countDown();
        acceptance.join(10_000);
        revocation.join(10_000);

        assertThat(acceptError.get()).isNull();
        assertThat(revokeError.get()).isNull();
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.REVOKED.name());
    }

    @Test
    void revocationStillWinsWhenAcceptanceCommittedFirst() {
        seedRelationship(LinkedLearnerStatus.PENDING);

        newTransaction.executeWithoutResult(status ->
                service.accept(relationshipId, supporterId, new AcceptLinkedLearnerRequest(null, false)));
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED.name());

        newTransaction.executeWithoutResult(status -> service.revoke(relationshipId, supporterId));

        // ⚠️ This is why the revoke guard covers ACCEPTED as well as PENDING. Guarding on PENDING
        // alone would leave an accepted connection un-revokable — "revocation cuts the read
        // immediately" would be false for exactly this ordering.
        assertThat(persistedStatus()).isEqualTo(LinkedLearnerStatus.REVOKED.name());
        assertThat(persistedRevokedAt()).isNotNull();
    }

    // ⚠️ REMOVED at the v0.93.0 pre-signoff pressure test: `progressGrantLosingTheRaceToRelationship
    // RevokeWritesNoLiveRow` was SELF-FULFILLING and is deliberately not replaced here. Its mock
    // hardcoded `return 0` — simulating the very outcome it existed to prove — and its closing
    // `count(*) ... where revoked_at is null` assertion was vacuously zero, because a mocked
    // repository never writes to that table. It added no guarantee over the existing unit test
    // `LinkedLearnerGrantServiceTest.zeroRowInsertWithoutALiveGrantReportsNotFound`.
    //
    // The conditional insert's ACCEPTED predicate is now covered where it can actually be executed:
    // `NativeQueryPostgresIntegrationTest.liveGrantInsertIsScopedToItsOwnRelationshipAndRequiresAccepted`
    // runs the real statement against real rows and kills both the `'ACCEPTED'`->`'PENDING'` and the
    // deleted-relationship-id mutants. Do not re-add a mocked "race" test here: this class mocks
    // `grantRepository` (see setUp), so it CANNOT exercise that statement, and a test that models its
    // own answer is worse than an absent one.

    // ---------------------------------------------------------------------------- infrastructure

    private Thread run(AtomicReference<Throwable> sink, Runnable body) {
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                sink.set(t);
            }
        });
        thread.setDaemon(true);
        return thread;
    }

    private void seedRelationship(LinkedLearnerStatus status) {
        jdbcTemplate.update(
                """
                        insert into linked_learner_relationships
                            (id, supporter_user_id, learner_user_id, status, accepted_at, revoked_at, expires_at)
                        values (?, ?, ?, ?, null, null, null)
                        """,
                relationshipId, supporterId, learnerId, status.name());
    }

    private int persistedBirthYear() {
        return jdbcTemplate.queryForObject(
                "select birth_year from users where id = ?", Integer.class, learnerId);
    }

    private String persistedStatus() {
        return jdbcTemplate.queryForObject(
                "select status from linked_learner_relationships where id = ?", String.class, relationshipId);
    }

    private OffsetDateTime persistedAcceptedAt() {
        return jdbcTemplate.queryForObject(
                "select accepted_at from linked_learner_relationships where id = ?", OffsetDateTime.class, relationshipId);
    }

    private OffsetDateTime persistedRevokedAt() {
        return jdbcTemplate.queryForObject(
                "select revoked_at from linked_learner_relationships where id = ?", OffsetDateTime.class, relationshipId);
    }

    private List<LinkedLearnerRelationshipEntity> readRelationships(LinkedLearnerStatus status) {
        return jdbcTemplate.query(
                "select * from linked_learner_relationships where learner_user_id = ? and status = ?",
                (rs, rowNum) -> toRelationship(rs.getObject("id", UUID.class)),
                learnerId, status.name());
    }

    private LinkedLearnerRelationshipEntity toRelationship(UUID id) {
        return jdbcTemplate.queryForObject("select * from linked_learner_relationships where id = ?",
                (rs, rowNum) -> {
                    LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
                    relationship.setId(rs.getObject("id", UUID.class));
                    relationship.setSupporterUserId(rs.getObject("supporter_user_id", UUID.class));
                    relationship.setLearnerUserId(rs.getObject("learner_user_id", UUID.class));
                    relationship.setStatus(LinkedLearnerStatus.valueOf(rs.getString("status")));
                    relationship.setInitiatedBy(LinkedLearnerSide.LEARNER);
                    relationship.setAcceptedAt(rs.getObject("accepted_at", OffsetDateTime.class));
                    relationship.setRevokedAt(rs.getObject("revoked_at", OffsetDateTime.class));
                    relationship.setCreatedAt(OffsetDateTime.now());
                    return relationship;
                }, id);
    }

    /**
     * ⚠️ Display-only reads are served from memory ON PURPOSE. H2 locks more coarsely than
     * Hibernate's identity map: this object never carries a birth year, so any code deciding
     * consent from the ENTITY reads null and fails, rather than passing by accident.
     *
     * <p>⚠️ This comment previously ALSO claimed that H2 blocks a plain SELECT behind a FOR UPDATE
     * and so manufactures a deadlock production does not have. That was measured and is FALSE:
     * against H2 2.4.240, a plain SELECT on a row held under FOR UPDATE returns in ~0 ms. A second
     * FOR UPDATE does block, which is the property the class javadoc relies on. The rationale above
     * is the real one and stands on its own; the deadlock story was never true.
     */
    /**
     * A birth-year-free stand-in for a managed entity. ⚠️ It does NOT cache: each call builds a new
     * instance, so Hibernate's instance identity is deliberately not modelled — only the property
     * that matters here, that the entity cannot supply a birth year.
     */
    private UserEntity blankManagedUser(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(id + "@example.com");
        return user;
    }

    /**
     * Model BOTH production properties of {@code findByIdForUpdate} at once, which is the thing the
     * previous two versions of this harness each got half right.
     *
     * <p>It must (a) genuinely take the row lock, because serialization is the mechanism under
     * test, and (b) return an entity WITHOUT a birth year, because Hibernate hands back the managed
     * instance rather than the state it just read — so any code deciding consent from the entity
     * must fail loudly here rather than accidentally reading the right answer.
     *
     * <p>⚠️ An earlier version issued the lock but returned a birth-year-carrying entity, hiding
     * the stale-read bug. Its replacement returned a blank entity but issued NO SQL, silently
     * removing the lock and leaving the release's central safety mechanism with zero coverage —
     * the tests then raced and failed about one run in three. Both properties, or neither test
     * means anything.
     */
    private UserEntity lockUser(UUID id) {
        jdbcTemplate.queryForObject("select id from users where id = ? for update", UUID.class, (Object) id);
        return blankManagedUser(id);
    }

    /**
     * Execute a repository method's real {@code @Query} SQL. Binding the tests to the production
     * statement — not a copy of it — is what makes a wrong guard in that statement detectable here.
     */
    private int runProductionQuery(String methodName, java.util.Map<String, Object> params) {
        String sql = java.util.Arrays.stream(LinkedLearnerRelationshipRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no such repository method: " + methodName))
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate)
                .update(sql, params);
    }

    private void wireRepositories() {
        // ⚠️ THIS MODELS THE PRODUCTION HAZARD, and the previous version did not — which is why
        // these tests passed against code that still had the bug. In production `findById` returns
        // a MANAGED entity and `findByIdForUpdate` hands that same stale instance back, so an
        // entity-based read after locking sees the PRE-LOCK value. Here `findById` deliberately
        // returns an object carrying NO birth year: any code that decides from the entity
        // gets null and fails loudly, instead of accidentally reading the right answer.
        when(userRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(blankManagedUser(invocation.getArgument(0))));
        when(userRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(lockUser(invocation.getArgument(0))));
        // ...while the SCALAR read goes to the database, which is the whole point of the fix.
        when(userRepository.findBirthYearById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(jdbcTemplate.queryForObject(
                        "select birth_year from users where id = ?",
                        Integer.class, (Object) invocation.getArgument(0))));
        when(provisionalBirthYearRepository.findEffectiveBirthYear(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(jdbcTemplate.queryForObject(
                        "select birth_year from users where id = ?",
                        Integer.class, (Object) invocation.getArgument(1))));
        when(userRepository.writeBirthYear(any(UUID.class), any(), any()))
                .thenAnswer(invocation -> jdbcTemplate.update(
                        "update users set birth_year = ?, birth_year_updated_at = ?, updated_at = ? where id = ?",
                        (Object) invocation.getArgument(1), (Object) invocation.getArgument(2),
                        (Object) invocation.getArgument(2), (Object) invocation.getArgument(0)));
        when(relationshipRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(toRelationship(invocation.getArgument(0))));
        when(relationshipRepository.findByLearnerUserIdAndStatus(any(UUID.class), any()))
                .thenAnswer(invocation -> readRelationships(invocation.getArgument(1)));
        // ⚠️ THE PRODUCTION SQL ITSELF, read off the repository's @Query annotation and executed
        // verbatim against real tables with the real names. A hand-copied paraphrase here would
        // make these tests blind to the thing most likely to be wrong — the guard in the actual
        // query — and a mutation of the real WHERE clause would sail past them.
        when(relationshipRepository.markAcceptedIfPending(any(UUID.class), any()))
                .thenAnswer(invocation -> runProductionQuery("markAcceptedIfPending",
                        java.util.Map.of("id", invocation.getArgument(0),
                                "acceptedAt", invocation.getArgument(1))));
        when(relationshipRepository.markRevokedIfLive(any(UUID.class), any()))
                .thenAnswer(invocation -> runProductionQuery("markRevokedIfLive",
                        java.util.Map.of("id", invocation.getArgument(0),
                                "revokedAt", invocation.getArgument(1))));
        when(relationshipRepository.pauseAcceptedForConsent(any(UUID.class)))
                .thenAnswer(invocation -> runProductionQuery("pauseAcceptedForConsent",
                        java.util.Map.of("id", invocation.getArgument(0))));
        when(consentRepository.findByRelationshipId(any(UUID.class))).thenReturn(Optional.empty());
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        LinkedLearnerRelationshipRepository relationshipRepository() {
            return mock(LinkedLearnerRelationshipRepository.class);
        }

        @Bean
        LinkedLearnerInvitationRepository invitationRepository() {
            return mock(LinkedLearnerInvitationRepository.class);
        }

        @Bean
        LinkedLearnerGuardianConsentRepository consentRepository() {
            return mock(LinkedLearnerGuardianConsentRepository.class);
        }

        @Bean
        LinkedLearnerGrantRepository grantRepository() {
            return mock(LinkedLearnerGrantRepository.class);
        }

        @Bean
        LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository() {
            return mock(LinkedLearnerProvisionalBirthYearRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        LinkedLearnerService linkedLearnerService(
                LinkedLearnerRelationshipRepository relationshipRepository,
                LinkedLearnerInvitationRepository invitationRepository,
                LinkedLearnerGuardianConsentRepository consentRepository,
                LinkedLearnerGrantRepository grantRepository,
                LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository,
                UserRepository userRepository
        ) {
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
                    new StudySnapProperties(),
                    new GuardianConsentPolicy(new StudySnapProperties()),
                    mock(InvitationRateLimitService.class));
        }

        @Bean
        LinkedLearnerGrantService linkedLearnerGrantService(
                LinkedLearnerRelationshipRepository relationshipRepository,
                LinkedLearnerGrantRepository grantRepository
        ) {
            return new LinkedLearnerGrantService(
                    relationshipRepository,
                    grantRepository,
                    mock(AuthService.class),
                    mock(AnalyticsService.class));
        }
    }
}
