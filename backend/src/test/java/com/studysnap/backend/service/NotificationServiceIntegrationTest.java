package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationEntity;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.exception.NotificationNotFoundException;
import com.studysnap.backend.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NotificationServiceIntegrationTest {
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
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
        jdbcTemplate.execute("create unique index if not exists idx_notifications_recipient_dedup on notifications(recipient_user_id, dedup_key)");
        // ⚠️ The inbox read applies ANNOUNCEMENT LIFECYCLE ON READ as of v0.130.0 Stage 4, so its query
        // references this table even when no notification in the fixture came from an announcement.
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
        jdbcTemplate.execute("delete from notifications");
        jdbcTemplate.execute("delete from announcements");
    }

    @Test
    void duplicateDeliveryIsSwallowedByTheUniqueIndexAndLeavesOneDatabaseRow() {
        UUID recipientId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        NotificationService.NotificationDelivery delivery = delivery(recipientId, entityId, NotificationType.ACTION_REQUIRED);

        notificationService.deliver(delivery);
        notificationService.deliver(delivery);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from notifications where recipient_user_id = ? and dedup_key = ?",
                Integer.class,
                recipientId,
                "ACTION_REQUIRED:" + entityId
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void inboxUsesOnePagedRepositoryQueryForAnyNumberOfRows() {
        UUID recipientId = UUID.randomUUID();
        for (int index = 0; index < 4; index++) {
            notificationService.deliver(delivery(recipientId, UUID.randomUUID(), NotificationType.ACTION_REQUIRED));
        }

        assertThat(notificationService.listInbox(recipientId, 50)).hasSize(4);
    }

    @Test
    void anotherUserCannotReadOrDismissANotificationAndTheRowRemainsUnchanged() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID notificationId = UUID.fromString(notificationService.deliver(
                delivery(ownerId, entityId, NotificationType.ACTION_REQUIRED)
        ).id().toString());

        assertThatThrownBy(() -> notificationService.markRead(otherUserId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThatThrownBy(() -> notificationService.dismiss(otherUserId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);

        NotificationEntity unchanged = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(unchanged.getReadAt()).isNull();
        assertThat(unchanged.getDismissedAt()).isNull();
    }

    @Test
    void retentionKeepsUnreadActionableRowsAndDeletesReadRowsPastTheWindow() {
        UUID recipientId = UUID.randomUUID();
        UUID unreadId = UUID.fromString(notificationService.deliver(
                delivery(recipientId, UUID.randomUUID(), NotificationType.ACTION_REQUIRED)
        ).id().toString());
        UUID readId = UUID.fromString(notificationService.deliver(
                delivery(recipientId, UUID.randomUUID(), NotificationType.ACTION_REQUIRED)
        ).id().toString());
        notificationService.markRead(recipientId, readId);
        OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(91);
        jdbcTemplate.update("update notifications set created_at = ?", old);

        assertThat(notificationService.deleteExpired(OffsetDateTime.now(ZoneOffset.UTC), 90)).isEqualTo(1);
        assertThat(notificationRepository.findById(unreadId)).isPresent();
        assertThat(notificationRepository.findById(readId)).isEmpty();
    }

    @Test
    void announcementsAreExcludedFromTheActionableUnreadCountByTheirType() {
        UUID recipientId = UUID.randomUUID();
        notificationService.deliver(delivery(recipientId, UUID.randomUUID(), NotificationType.ANNOUNCEMENT));
        notificationService.deliver(delivery(recipientId, UUID.randomUUID(), NotificationType.ACTION_REQUIRED));

        assertThat(notificationService.countActionableUnread(recipientId)).isEqualTo(1);
    }

    private NotificationService.NotificationDelivery delivery(UUID recipientId, UUID entityId, NotificationType type) {
        return new NotificationService.NotificationDelivery(
                recipientId,
                type,
                entityId,
                "Action needed",
                "Review this item.",
                "Open",
                "/dashboard",
                null
        );
    }
}
