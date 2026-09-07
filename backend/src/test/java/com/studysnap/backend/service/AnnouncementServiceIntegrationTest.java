package com.studysnap.backend.service;

import com.studysnap.backend.dto.AnnouncementPublishResponse;
import com.studysnap.backend.dto.AnnouncementResponse;
import com.studysnap.backend.dto.NotificationResponse;
import com.studysnap.backend.dto.UpsertAnnouncementRequest;
import com.studysnap.backend.entity.AnnouncementEntity;
import com.studysnap.backend.entity.AnnouncementStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.AnnouncementNotEditableException;
import com.studysnap.backend.exception.AnnouncementNotPublishableException;
import com.studysnap.backend.exception.InvalidAnnouncementRequestException;
import com.studysnap.backend.repository.AnnouncementRepository;
import com.studysnap.backend.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Announcement fan-out against a REAL DATABASE, because the whole idempotency guarantee is a unique
 * index and a mocked repository cannot express one.
 *
 * <p>⚠️ EVERY ROW-COUNT ASSERTION READS THE DATABASE, never a service return value. A fan-out that
 * reported "delivered = 3" while inserting six rows would pass an assertion on its own return.
 */
@SpringBootTest
class AnnouncementServiceIntegrationTest {
    private static final String COUNT_ROWS_FOR_ANNOUNCEMENT =
            "select count(*) from notifications where announcement_id = ?";
    private static final String TITLE = "Board Exam Mode is here";
    private static final String BODY = "Practice with a full timed board exam.";
    private static final String CTA_LABEL = "Try it";
    private static final String CTA_PATH = "/dashboard?tab=exams";

    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private AnnouncementAudienceResolver audienceResolver;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AnnouncementRepository announcementRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID adminUserId;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("drop table if exists notifications");
        jdbcTemplate.execute("""
                create table notifications (
                    id uuid primary key,
                    recipient_user_id uuid not null,
                    type varchar(64) not null,
                    dedup_key varchar(255) not null,
                    title varchar(255) not null,
                    body varchar(1000),
                    cta_label varchar(64),
                    cta_path varchar(512),
                    announcement_id uuid,
                    created_at timestamp with time zone not null,
                    read_at timestamp with time zone,
                    dismissed_at timestamp with time zone
                )
                """);
        // ⚠️ THE UNIQUE INDEX IS THE SUBJECT OF THIS TEST CLASS. Without it every assertion below still
        // compiles and most still pass, so it is created here explicitly rather than assumed.
        jdbcTemplate.execute(
                "create unique index idx_notifications_recipient_dedup on notifications(recipient_user_id, dedup_key)");

        jdbcTemplate.execute("drop table if exists announcements");
        jdbcTemplate.execute("""
                create table announcements (
                    id uuid primary key,
                    title varchar(255) not null,
                    body varchar(1000) not null,
                    cta_label varchar(64),
                    cta_path varchar(512),
                    audience varchar(32) not null,
                    audience_value varchar(64),
                    status varchar(16) not null,
                    published_at timestamp with time zone,
                    expires_at timestamp with time zone,
                    created_by_user_id uuid not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);

        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("""
                create table users (
                    id uuid primary key,
                    status varchar(32) not null,
                    profile_type varchar(32)
                )
                """);
        jdbcTemplate.execute("drop table if exists subscriptions");
        jdbcTemplate.execute("""
                create table subscriptions (
                    id uuid primary key,
                    user_id uuid not null,
                    plan_type varchar(32) not null,
                    status varchar(32) not null,
                    start_at timestamp with time zone,
                    end_at timestamp with time zone
                )
                """);

        adminUserId = UUID.randomUUID();
    }

    /**
     * ⚠️ THIS CLASS MUST LEAVE NO {@code users} TABLE BEHIND. The suite shares one in-memory H2 with
     * {@code DB_CLOSE_DELAY=-1}, and other classes create {@code users} with THEIR own narrow shape —
     * some with {@code if not exists}, at least one with a bare {@code create table} that fails
     * outright if the table is already there. Leaving this class's three-column version in place would
     * break them by test ordering alone, which is the worst kind of failure to diagnose.
     */
    @AfterEach
    void dropSharedTables() {
        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("drop table if exists subscriptions");
    }

    // ------------------------------------------------------------------ idempotency

    /**
     * ⚠️ THE DISCRIMINATING FORM: fan out TWICE for the same announcement and count the DATABASE. A
     * test that publishes once passes whether or not the dedup key exists at all.
     */
    @Test
    void reRunningFanOutForTheSameAnnouncementInsertsZeroNewRows() {
        List<UUID> recipients = List.of(activeUser(), activeUser(), activeUser());
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);

        AnnouncementPublishResponse firstPublish = announcementService.publish(draft.id());
        assertThat(rowsFor(draft.id())).isEqualTo(3);
        assertThat(firstPublish.delivered()).isEqualTo(3);

        AnnouncementPublishResponse retry = announcementService.publish(draft.id());

        assertThat(rowsFor(draft.id()))
                .as("a retried fan-out must insert zero duplicates")
                .isEqualTo(3);
        assertThat(retry.recipientCount()).isEqualTo(recipients.size());
        assertThat(publishedAt(draft.id()))
                .as("published_at is stamped once and never re-stamped by a retry")
                .isEqualTo(publishedAtOf(firstPublish));
    }

    /**
     * ⚠️ THE OTHER HALF OF THE DEDUP CONTRACT, AND THE ONE THE TWICE-TEST CANNOT SEE. If fan-out passed
     * a constant (or null) as the delivery's entity id, every announcement would collapse onto one
     * dedup key and each user would receive only the FIRST announcement ever published — forever,
     * silently, while the idempotency test above still passed.
     */
    @Test
    void twoAnnouncementsToTheSameUserGetDistinctDedupKeysAndBothArrive() {
        UUID recipientId = activeUser();
        AnnouncementResponse first = createDraft("EVERYONE", null, null);
        AnnouncementResponse second = createDraft("EVERYONE", null, null);

        announcementService.publish(first.id());
        announcementService.publish(second.id());

        List<String> dedupKeys = jdbcTemplate.queryForList(
                "select dedup_key from notifications where recipient_user_id = ? order by dedup_key",
                String.class,
                recipientId
        );
        assertThat(dedupKeys).containsExactlyInAnyOrder(
                "ANNOUNCEMENT:" + first.id(),
                "ANNOUNCEMENT:" + second.id()
        );
    }

    // ------------------------------------------------------------------ immutability

    /**
     * ⚠️ BOTH HALVES. The edit is REFUSED, and the rows people already received are byte-identical
     * afterwards — changing the announcement definition must not retro-change delivered copy.
     */
    @Test
    void editAfterPublishIsRefusedAndDeliveredRowsAreUnchanged() {
        activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        announcementService.publish(draft.id());
        List<Map<String, Object>> before = deliveredCopy(draft.id());
        UUID announcementId = draft.id();
        UpsertAnnouncementRequest rewrite = request("Rewritten", "New body", null, null, "EVERYONE", null, null);

        assertThatThrownBy(() -> announcementService.update(announcementId, rewrite))
                .isInstanceOf(AnnouncementNotEditableException.class);

        assertThat(deliveredCopy(draft.id())).isEqualTo(before);
        AnnouncementEntity unchanged = announcementRepository.findById(draft.id()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo(TITLE);
    }

    @Test
    void anEndedAnnouncementCannotBeEditedOrPublishedAgain() {
        activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        announcementService.publish(draft.id());
        announcementService.end(draft.id());
        UUID announcementId = draft.id();
        UpsertAnnouncementRequest rewrite = request("Rewritten", BODY, null, null, "EVERYONE", null, null);

        assertThatThrownBy(() -> announcementService.update(announcementId, rewrite))
                .isInstanceOf(AnnouncementNotEditableException.class);
        assertThatThrownBy(() -> announcementService.publish(announcementId))
                .isInstanceOf(AnnouncementNotPublishableException.class);
    }

    // ------------------------------------------------------------------ lifecycle on read

    /**
     * ⚠️ IMMEDIATELY, WITH NO JOB IN BETWEEN. Ending is decided on read, so the inbox stops presenting
     * the row on the very next call — and the row itself survives, because a user who saw it cannot
     * un-see it and retention, not the lifecycle, decides when it is deleted.
     */
    @Test
    void endingAnAnnouncementHidesItFromTheInboxImmediatelyWhileTheRowSurvives() {
        UUID recipientId = activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        announcementService.publish(draft.id());
        assertThat(notificationService.listInbox(recipientId, 50)).hasSize(1);

        announcementService.end(draft.id());

        assertThat(notificationService.listInbox(recipientId, 50)).isEmpty();
        assertThat(rowsFor(draft.id()))
                .as("the delivered row is hidden, not deleted")
                .isEqualTo(1);
    }

    @Test
    void anExpiredAnnouncementStopsPresentingWithoutAnyCleanupJobRunning() {
        UUID recipientId = activeUser();
        OffsetDateTime alreadyPast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        AnnouncementResponse draft = createDraft("EVERYONE", null, alreadyPast);

        announcementService.publish(draft.id());

        assertThat(rowsFor(draft.id())).isEqualTo(1);
        assertThat(notificationService.listInbox(recipientId, 50)).isEmpty();
    }

    @Test
    void anAnnouncementWithAFutureExpiryStillPresents() {
        UUID recipientId = activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));

        announcementService.publish(draft.id());

        assertThat(notificationService.listInbox(recipientId, 50)).hasSize(1);
    }

    /**
     * A notification outlives the thing it points at, by design, and its copy is self-contained — so a
     * vanished announcement row must leave the delivered row VISIBLE rather than silently eating it.
     */
    @Test
    void aDeletedAnnouncementRowLeavesItsDeliveredNotificationsVisible() {
        UUID recipientId = activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        announcementService.publish(draft.id());

        jdbcTemplate.update("delete from announcements where id = ?", draft.id());

        assertThat(notificationService.listInbox(recipientId, 50)).hasSize(1);
    }

    // ------------------------------------------------------------------ targeting

    @Test
    void profileTypeAudienceTargetsOnlyMatchingUsersAndChangesNothingAboutTheOthers() {
        UUID teacherId = activeUser(ProfileType.TEACHER);
        UUID studentId = activeUser(ProfileType.STUDENT);
        List<Map<String, Object>> usersBefore = allUserRows();
        List<Map<String, Object>> subscriptionsBefore = allSubscriptionRows();

        AnnouncementResponse draft = createDraft("PROFILE_TYPE", "TEACHER", null);
        announcementService.publish(draft.id());

        assertThat(recipientsOf(draft.id())).containsExactly(teacherId);
        assertThat(notificationService.listInbox(studentId, 50)).isEmpty();
        // ⚠️ Targeting is EDITORIAL, never authorization: the untargeted account's own rows — including
        // everything its entitlements are derived from — are byte-identical after a publish.
        assertThat(allUserRows()).isEqualTo(usersBefore);
        assertThat(allSubscriptionRows()).isEqualTo(subscriptionsBefore);
    }

    /**
     * ⚠️ THE PLAN LEG MUST AGREE WITH {@code SubscriptionService.resolvePlan}, PRECEDENCE INCLUDED. The
     * PLUS+PRO user resolves to PRO there, so they belong to the PRO audience and NOT the PLUS one.
     */
    @Test
    void planTypeAudienceMirrorsResolvePlanIncludingItsPrecedence() {
        UUID freeUserId = activeUser();
        UUID plusUserId = activeUser();
        UUID proUserId = activeUser();
        UUID plusAndProUserId = activeUser();
        UUID expiredPlusUserId = activeUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        subscription(plusUserId, PlanType.PLUS, SubscriptionStatus.ACTIVE, now.minusDays(1), now.plusDays(30));
        subscription(proUserId, PlanType.PRO, SubscriptionStatus.ACTIVE, now.minusDays(1), null);
        subscription(plusAndProUserId, PlanType.PLUS, SubscriptionStatus.ACTIVE, now.minusDays(1), now.plusDays(30));
        subscription(plusAndProUserId, PlanType.PRO, SubscriptionStatus.ACTIVE, now.minusDays(1), now.plusDays(30));
        subscription(expiredPlusUserId, PlanType.PLUS, SubscriptionStatus.ACTIVE, now.minusDays(60), now.minusDays(1));

        assertThat(audienceFor("PLAN_TYPE", "PRO")).containsExactlyInAnyOrder(proUserId, plusAndProUserId);
        assertThat(audienceFor("PLAN_TYPE", "PLUS")).containsExactly(plusUserId);
        assertThat(audienceFor("PLAN_TYPE", "FREE"))
                .containsExactlyInAnyOrder(freeUserId, expiredPlusUserId);
    }

    @Test
    void everyoneAudienceExcludesAccountsThatAreNotActive() {
        UUID activeId = activeUser();
        UUID suspendedId = UUID.randomUUID();
        jdbcTemplate.update("insert into users values (?, ?, ?)", suspendedId, UserStatus.SUSPENDED.name(), null);

        assertThat(audienceFor("EVERYONE", null)).containsExactly(activeId);
        assertThat(audienceFor("EVERYONE", null)).doesNotContain(suspendedId);
    }

    @Test
    void anAudienceResolvingToZeroUsersPublishesSuccessfullyAndDeliversNothing() {
        activeUser(ProfileType.STUDENT);
        AnnouncementResponse draft = createDraft("PROFILE_TYPE", "PARENT", null);

        AnnouncementPublishResponse published = announcementService.publish(draft.id());

        assertThat(published.announcement().status()).isEqualTo(AnnouncementStatus.PUBLISHED.name());
        assertThat(published.recipientCount()).isZero();
        assertThat(published.delivered()).isZero();
        assertThat(rowsFor(draft.id())).isZero();
    }

    @Test
    void anUnknownAudienceValueIsRejectedAtWriteTimeRatherThanDuringFanOut() {
        assertThatThrownBy(() -> createDraft("PROFILE_TYPE", "ASTRONAUT", null))
                .isInstanceOf(InvalidAnnouncementRequestException.class);
        assertThatThrownBy(() -> createDraft("PLAN_TYPE", null, null))
                .isInstanceOf(InvalidAnnouncementRequestException.class);
        assertThat(announcementRepository.count()).isZero();
    }

    /**
     * ⚠️ EDITORIAL, NEVER AUTHORIZATION — asserted structurally as well as behaviourally. The resolver
     * cannot consult, and therefore cannot become, the entitlement path.
     */
    @Test
    void neitherTheResolverNorTheServiceHoldsAReferenceToTheFeatureGate() {
        assertThat(fieldTypeNames(AnnouncementAudienceResolver.class)).doesNotContain("FeatureGateService");
        assertThat(fieldTypeNames(AnnouncementService.class)).doesNotContain("FeatureGateService");
    }

    // ------------------------------------------------------------------ partial failure

    /**
     * ⚠️ ONE RECIPIENT'S FAILURE MUST NOT ABANDON THE REST, and a retry must pick up exactly the ones
     * that were missed while inserting zero duplicates for the ones that were not. Deliveries already
     * made are never rolled back — a user who saw it cannot un-see it.
     */
    @Test
    void aFailedDeliveryIsCountedAndSteppedOverAndTheRetryPicksUpOnlyTheMissedRecipient() {
        UUID firstId = activeUser();
        UUID poisonedId = activeUser();
        UUID lastId = activeUser();
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        AnnouncementEntity announcement = announcementRepository.findById(draft.id()).orElseThrow();

        AnnouncementService failingOnce = new AnnouncementService(
                announcementRepository,
                new NotificationService(notificationRepository) {
                    @Override
                    public NotificationResponse deliver(NotificationDelivery delivery) {
                        if (delivery.recipientUserId().equals(poisonedId)) {
                            throw new IllegalStateException("simulated delivery failure");
                        }
                        return super.deliver(delivery);
                    }
                },
                audienceResolver
        );

        AnnouncementService.AnnouncementFanOutResult partial =
                failingOnce.fanOut(announcement, List.of(firstId, poisonedId, lastId));

        assertThat(partial.delivered()).isEqualTo(2);
        assertThat(partial.skipped()).isEqualTo(1);
        assertThat(recipientsOf(draft.id())).containsExactlyInAnyOrder(firstId, lastId);

        AnnouncementService.AnnouncementFanOutResult retry =
                announcementService.fanOut(announcement, List.of(firstId, poisonedId, lastId));

        assertThat(retry.skipped()).isZero();
        assertThat(rowsFor(draft.id())).isEqualTo(3);
        assertThat(recipientsOf(draft.id())).containsExactlyInAnyOrder(firstId, poisonedId, lastId);
    }

    /**
     * ⚠️ THE GUARD MUST REACH ITS SUBJECT THE WAY PRODUCTION DOES, AND PRODUCTION RUNS THIS UNDER
     * OPEN-SESSION-IN-VIEW. {@code spring.jpa.open-in-view} is ON, so a real publish request has ONE
     * EntityManager bound to the thread for its whole duration, and every duplicate insert's
     * constraint violation lands on that shared persistence context rather than a private one.
     *
     * <p>Every other test in this class runs with no bound EntityManager, so all of them would pass
     * even if the retry path were unusable in production. This one binds the holder exactly as
     * {@code OpenEntityManagerInViewInterceptor} does, then fans out twice — the case where EVERY
     * recipient is a duplicate, which is the shape a retried publish actually takes.
     */
    @Test
    void aRetriedFanOutStillWorksWithAnOpenSessionInViewEntityManagerBoundToTheThread() {
        List<UUID> recipients = List.of(activeUser(), activeUser(), activeUser());
        AnnouncementResponse draft = createDraft("EVERYONE", null, null);
        AnnouncementEntity announcement = announcementRepository.findById(draft.id()).orElseThrow();
        announcementService.fanOut(announcement, recipients);
        assertThat(rowsFor(draft.id())).isEqualTo(3);

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
        AnnouncementService.AnnouncementFanOutResult retry;
        long readableAfterTheRetry;
        try {
            assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory))
                    .as("the holder must actually be bound, or this test is indistinguishable from the unbound case")
                    .isTrue();
            retry = announcementService.fanOut(announcement, recipients);
            // ⚠️ THE LOAD-BEARING ASSERTION. A real publish request keeps going after fan-out returns, so
            // the failure this test exists to catch is a persistence context left UNUSABLE by three
            // caught constraint violations — which fan-out's own return value cannot show. This read runs
            // on the same bound EntityManager, after the damage would have been done.
            readableAfterTheRetry = notificationRepository.count();
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            EntityManagerFactoryUtils.closeEntityManager(entityManager);
        }

        assertThat(retry.skipped())
                .as("every recipient is a duplicate here; none of them may be reported as a failure")
                .isZero();
        assertThat(retry.delivered()).isEqualTo(3);
        assertThat(readableAfterTheRetry).isEqualTo(3);
        assertThat(rowsFor(draft.id())).isEqualTo(3);
    }

    // ------------------------------------------------------------------ helpers

    private AnnouncementResponse createDraft(String audience, String audienceValue, OffsetDateTime expiresAt) {
        return announcementService.create(
                request(TITLE, BODY, CTA_LABEL, CTA_PATH, audience, audienceValue, expiresAt),
                adminUserId
        );
    }

    private UpsertAnnouncementRequest request(
            String title,
            String body,
            String ctaLabel,
            String ctaPath,
            String audience,
            String audienceValue,
            OffsetDateTime expiresAt
    ) {
        return new UpsertAnnouncementRequest(title, body, ctaLabel, ctaPath, audience, audienceValue, expiresAt);
    }

    private UUID activeUser() {
        return activeUser(ProfileType.STUDENT);
    }

    private UUID activeUser(ProfileType profileType) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users values (?, ?, ?)",
                userId,
                UserStatus.ACTIVE.name(),
                profileType.name()
        );
        return userId;
    }

    private void subscription(
            UUID userId,
            PlanType planType,
            SubscriptionStatus status,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        jdbcTemplate.update(
                "insert into subscriptions values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                planType.name(),
                status.name(),
                startAt,
                endAt
        );
    }

    private List<UUID> audienceFor(String audience, String audienceValue) {
        AnnouncementResponse draft = createDraft(audience, audienceValue, null);
        return audienceResolver.resolve(announcementRepository.findById(draft.id()).orElseThrow());
    }

    private int rowsFor(UUID announcementId) {
        Integer rows = jdbcTemplate.queryForObject(COUNT_ROWS_FOR_ANNOUNCEMENT, Integer.class, announcementId);
        return rows == null ? 0 : rows;
    }

    private List<UUID> recipientsOf(UUID announcementId) {
        return jdbcTemplate.queryForList(
                "select recipient_user_id from notifications where announcement_id = ?",
                UUID.class,
                announcementId
        );
    }

    private List<Map<String, Object>> deliveredCopy(UUID announcementId) {
        return jdbcTemplate.queryForList(
                "select recipient_user_id, dedup_key, title, body, cta_label, cta_path"
                        + " from notifications where announcement_id = ? order by recipient_user_id",
                announcementId
        );
    }

    private List<Map<String, Object>> allUserRows() {
        return jdbcTemplate.queryForList("select id, status, profile_type from users order by id");
    }

    private List<Map<String, Object>> allSubscriptionRows() {
        return jdbcTemplate.queryForList(
                "select id, user_id, plan_type, status, start_at, end_at from subscriptions order by id");
    }

    private OffsetDateTime publishedAt(UUID announcementId) {
        return announcementRepository.findById(announcementId).orElseThrow().getPublishedAt();
    }

    private OffsetDateTime publishedAtOf(AnnouncementPublishResponse response) {
        return response.announcement().publishedAt();
    }

    private List<String> fieldTypeNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getType).map(Class::getSimpleName).toList();
    }
}
