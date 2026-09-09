package com.studysnap.backend.service;

import com.studysnap.backend.dto.NotificationResponse;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.NotificationRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Exercises the real Spring proxy, transaction, AFTER_COMMIT listener, repositories and unique index.
 * A hand-built NoteCollectionService cannot prove R9 because its {@code @Transactional} annotation is inert.
 */
@SpringBootTest
class ReviewSetUpdateNotificationIntegrationTest {
    private static final String SOURCE_TITLE = "Official Biology Review Set";

    @Autowired
    private NoteCollectionService noteCollectionService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "notificationFanOutExecutor")
    private TaskExecutor notificationFanOutExecutor;
    @MockitoBean
    private UserRepository userRepository;

    private final ArrayDeque<Runnable> submittedTasks = new ArrayDeque<>();
    private final AtomicBoolean taskTransactionActive = new AtomicBoolean(true);

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists note_collections (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title varchar(150) not null,
                    description text,
                    visibility varchar(16) not null,
                    course_program varchar(120),
                    learner_level varchar(50),
                    estimated_study_hours integer,
                    target_completion_date date,
                    companion json,
                    companion_structure_snapshot json,
                    source_plan_id uuid,
                    source_title_at_sync varchar(150),
                    source_parent_id_at_sync uuid,
                    source_position_at_sync integer,
                    source_synced_at timestamp with time zone,
                    published_at timestamp with time zone,
                    last_update_published_at timestamp with time zone,
                    parent_collection_id uuid,
                    sibling_position integer,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_collection_items (
                    id uuid primary key,
                    collection_id uuid not null,
                    note_id uuid not null,
                    label varchar(120),
                    position integer not null,
                    source_label_at_sync varchar(120),
                    source_position_at_sync integer,
                    source_synced_at timestamp with time zone,
                    published_at timestamp with time zone,
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists notifications (
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
        jdbcTemplate.execute("""
                create table if not exists announcements (
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
        jdbcTemplate.execute("create unique index if not exists idx_notifications_recipient_dedup"
                + " on notifications(recipient_user_id, dedup_key)");
        jdbcTemplate.execute("delete from notifications");
        jdbcTemplate.execute("delete from announcements");
        jdbcTemplate.execute("delete from note_collection_items");
        jdbcTemplate.execute("delete from note_collections");
        submittedTasks.clear();
        taskTransactionActive.set(true);
        doAnswer(invocation -> {
            Runnable submitted = invocation.getArgument(0);
            submittedTasks.addLast(() -> {
                taskTransactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                submitted.run();
            });
            return null;
        }).when(notificationFanOutExecutor).execute(any(Runnable.class));
    }

    @Test
    void dismissingOneRevisionAllowsTheNextPublishedRevisionToCreateANewRow() {
        Fixture fixture = fixtureWithAdopters(1);
        addUnpublishedChange(fixture.sourceCollectionId(), 0);
        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());
        runNextTask();
        NotificationResponse first = notificationService.listInbox(fixture.learnerIds().getFirst(), 25).getFirst();
        Instant firstStamp = publishedStamp(fixture.sourceCollectionId());
        notificationService.dismiss(fixture.learnerIds().getFirst(), first.id());

        awaitNextEpochMilli(firstStamp);
        addUnpublishedChange(fixture.sourceCollectionId(), 1);
        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());
        runNextTask();

        List<String> keys = jdbcTemplate.queryForList(
                "select dedup_key from notifications where recipient_user_id = ? order by created_at",
                String.class,
                fixture.learnerIds().getFirst()
        );
        assertThat(keys).hasSize(2).allSatisfy(key -> assertThat(key)
                .startsWith("REVIEW_SET_UPDATE:" + fixture.sourceCollectionId() + ":"));
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void repressIsANoOpAndANewerRevisionIsSuppressedWhileTheEpisodeRemainsOpen() {
        Fixture fixture = fixtureWithAdopters(1);
        addUnpublishedChange(fixture.sourceCollectionId(), 0);
        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());
        runNextTask();
        Instant firstStamp = publishedStamp(fixture.sourceCollectionId());

        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());

        assertThat(submittedTasks).isEmpty();
        assertThat(publishedStamp(fixture.sourceCollectionId())).isEqualTo(firstStamp);

        awaitNextEpochMilli(firstStamp);
        addUnpublishedChange(fixture.sourceCollectionId(), 1);
        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());
        runNextTask();

        assertThat(publishedStamp(fixture.sourceCollectionId())).isAfter(firstStamp);
        assertThat(notificationRows(fixture.learnerIds().getFirst())).isOne();
    }

    @Test
    void realPublishCommitsBeforeDispatchAndDeliversFixedCopyToEachLearnersOwnCollection() {
        Fixture fixture = fixtureWithAdopters(2);
        addUnpublishedChange(fixture.sourceCollectionId(), 0);
        assertThat(jdbcTemplate.queryForObject("select count(*) from notifications", Integer.class)).isZero();

        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());

        assertThat(submittedTasks).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from notifications", Integer.class)).isZero();
        runNextTask();
        assertThat(taskTransactionActive.get())
                .as("deliver must run on the executor without the publishing transaction")
                .isFalse();

        Instant persistedStamp = publishedStamp(fixture.sourceCollectionId());
        for (int index = 0; index < fixture.learnerIds().size(); index++) {
            UUID learnerId = fixture.learnerIds().get(index);
            UUID adoptedCollectionId = fixture.adoptedCollectionIds().get(index);
            NotificationRow row = jdbcTemplate.queryForObject(
                    """
                    select dedup_key, title, body, cta_path
                    from notifications
                    where recipient_user_id = ?
                    """,
                    (resultSet, rowNum) -> new NotificationRow(
                            resultSet.getString("dedup_key"),
                            resultSet.getString("title"),
                            resultSet.getString("body"),
                            resultSet.getString("cta_path")
                    ),
                    learnerId
            );
            assertThat(row.dedupKey()).isEqualTo(
                    "REVIEW_SET_UPDATE:" + fixture.sourceCollectionId() + ":" + persistedStamp.toEpochMilli());
            assertThat(row.title()).isEqualTo(ReviewSetUpdateNotificationService.TITLE).doesNotContainPattern("\\d");
            assertThat(row.body()).isEqualTo(ReviewSetUpdateNotificationService.BODY).doesNotContainPattern("\\d");
            assertThat(row.ctaPath()).isEqualTo("/collections/" + adoptedCollectionId);
            assertThat(notificationService.countActionableUnread(learnerId)).isEqualTo(1);
        }
    }

    @Test
    void rejectedDispatchDoesNotEscapeOrRollBackThePublishedBoundary() {
        Fixture fixture = fixtureWithAdopters(1);
        addUnpublishedChange(fixture.sourceCollectionId(), 0);
        doThrow(new RejectedExecutionException("queue full"))
                .when(notificationFanOutExecutor).execute(any(Runnable.class));

        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());

        assertThat(publishedStamp(fixture.sourceCollectionId())).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select published_at is not null from note_collection_items where collection_id = ?",
                Boolean.class,
                fixture.sourceCollectionId()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from notifications", Integer.class)).isZero();
    }

    @Test
    void readingTheSignalDoesNotApplyTheReviewSetUpdate() {
        Fixture fixture = fixtureWithAdopters(1);
        UUID adoptedCollectionId = fixture.adoptedCollectionIds().getFirst();
        Instant syncedBefore = jdbcTemplate.queryForObject(
                "select source_synced_at from note_collections where id = ?",
                Instant.class,
                adoptedCollectionId
        );
        addUnpublishedChange(fixture.sourceCollectionId(), 0);
        noteCollectionService.publishReviewSetUpdate(fixture.sourceCollectionId(), fixture.curatorId());
        runNextTask();
        NotificationResponse notification = notificationService.listInbox(fixture.learnerIds().getFirst(), 25).getFirst();

        notificationService.markRead(fixture.learnerIds().getFirst(), notification.id());

        assertThat(notificationRepository.findById(notification.id()).orElseThrow().getReadAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select source_synced_at from note_collections where id = ?",
                Instant.class,
                adoptedCollectionId
        )).isEqualTo(syncedBefore);
        assertThat(publishedStamp(fixture.sourceCollectionId())).isAfter(syncedBefore);
    }

    /**
     * ⚠️ EDITING IS NOT PUBLISHING — the boundary `v0.132.0` shipped exists to forbid exactly this, and
     * it is the anti-drift line most easily lost by a producer that fires on a write rather than on the
     * publish call. Adding unpublished items must reach nobody, however many are added.
     */
    @Test
    void curatorEditsWithoutPublishingReachNoAdopter() {
        Fixture fixture = fixtureWithAdopters(3);

        addUnpublishedChange(fixture.sourceCollectionId(), 1);
        addUnpublishedChange(fixture.sourceCollectionId(), 2);
        addUnpublishedChange(fixture.sourceCollectionId(), 3);

        assertThat(submittedTasks)
                .as("an edit must not even dispatch a fan-out — nothing has been published yet")
                .isEmpty();
        for (UUID learnerId : fixture.learnerIds()) {
            assertThat(notificationRows(learnerId)).isZero();
        }
    }

    private Fixture fixtureWithAdopters(int adopterCount) {
        UUID curatorId = UUID.randomUUID();
        UUID sourceCollectionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-09T01:00:00Z");
        jdbcTemplate.update("""
                insert into note_collections (
                    id, owner_user_id, title, visibility, last_update_published_at, created_at, updated_at
                ) values (?, ?, ?, 'PUBLIC', ?, ?, ?)
                """, sourceCollectionId, curatorId, SOURCE_TITLE, now.minusSeconds(60), now, now);
        UserEntity curator = new UserEntity();
        curator.setId(curatorId);
        curator.setRole(UserRole.ADMIN);
        when(userRepository.findById(curatorId)).thenReturn(Optional.of(curator));

        List<UUID> learnerIds = java.util.stream.IntStream.range(0, adopterCount)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        List<UUID> adoptedCollectionIds = learnerIds.stream().map(ignored -> UUID.randomUUID()).toList();
        for (int index = 0; index < learnerIds.size(); index++) {
            jdbcTemplate.update("""
                    insert into note_collections (
                        id, owner_user_id, title, visibility, source_plan_id, source_synced_at, created_at, updated_at
                    ) values (?, ?, ?, 'PRIVATE', ?, ?, ?, ?)
                    """,
                    adoptedCollectionIds.get(index), learnerIds.get(index), "My Biology Review Set",
                    sourceCollectionId, now.minusSeconds(60), now, now
            );
        }
        return new Fixture(curatorId, sourceCollectionId, learnerIds, adoptedCollectionIds);
    }

    private void addUnpublishedChange(UUID sourceCollectionId, int position) {
        jdbcTemplate.update("""
                insert into note_collection_items (id, collection_id, note_id, position, created_at, published_at)
                values (?, ?, ?, ?, ?, null)
                """, UUID.randomUUID(), sourceCollectionId, UUID.randomUUID(), position, Instant.now());
    }

    private void runNextTask() {
        assertThat(submittedTasks).isNotEmpty();
        submittedTasks.removeFirst().run();
    }

    private int notificationRows(UUID learnerId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notifications where recipient_user_id = ?",
                Integer.class,
                learnerId
        );
    }

    private Instant publishedStamp(UUID sourceCollectionId) {
        return jdbcTemplate.queryForObject(
                "select last_update_published_at from note_collections where id = ?",
                Instant.class,
                sourceCollectionId
        );
    }

    private void awaitNextEpochMilli(Instant priorStamp) {
        while (System.currentTimeMillis() <= priorStamp.toEpochMilli()) {
            Thread.onSpinWait();
        }
    }

    private record Fixture(
            UUID curatorId,
            UUID sourceCollectionId,
            List<UUID> learnerIds,
            List<UUID> adoptedCollectionIds
    ) {
    }

    private record NotificationRow(String dedupKey, String title, String body, String ctaPath) {
    }
}
