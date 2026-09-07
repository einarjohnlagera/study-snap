package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NotificationEntity;
import com.studysnap.backend.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Optional<NotificationEntity> findByRecipientUserIdAndDedupKey(UUID recipientUserId, String dedupKey);

    Optional<NotificationEntity> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    /**
     * The inbox read, with announcement lifecycle applied ON READ.
     *
     * <p>⚠️ AN ENDED OR EXPIRED ANNOUNCEMENT MUST STOP READING AS NEW IMMEDIATELY, not at the next
     * cleanup sweep — so the decision is made here, against {@code status}/{@code expires_at}, and
     * there is no scheduled job involved. The delivered row may linger until retention deletes it;
     * it just stops presenting.
     *
     * <p>⚠️ The predicate is NOT-EXISTS-ended rather than EXISTS-live on purpose. A notification
     * outlives the thing it points at by design and its copy is self-contained, so a missing
     * announcement row leaves the delivered row VISIBLE. An EXISTS-live form would make a vanished
     * announcement silently eat inboxes.
     *
     * <p>⚠️ ONE QUERY, INDEPENDENT OF ROW COUNT. The lifecycle check is a correlated subquery, not a
     * per-row lookup in service code.
     */
    @Query("""
            select notification
            from NotificationEntity notification
            where notification.recipientUserId = :recipientUserId
              and notification.dismissedAt is null
              and not exists (
                  select 1
                  from AnnouncementEntity announcement
                  where announcement.id = notification.announcementId
                    and (
                        announcement.status = com.studysnap.backend.entity.AnnouncementStatus.ENDED
                        or (announcement.expiresAt is not null and announcement.expiresAt <= :now)
                    )
              )
            order by notification.createdAt desc
            """)
    List<NotificationEntity> findVisibleInbox(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Query("""
            select count(notification)
            from NotificationEntity notification
            where notification.recipientUserId = :recipientUserId
              and notification.readAt is null
              and notification.type in :actionableTypes
            """)
    long countActionableUnread(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("actionableTypes") Collection<NotificationType> actionableTypes
    );

    @Modifying
    @Query("""
            delete from NotificationEntity notification
            where notification.createdAt < :threshold
              and (notification.readAt is not null or notification.dismissedAt is not null)
            """)
    int deleteReadOrDismissedBefore(@Param("threshold") OffsetDateTime threshold);
}
