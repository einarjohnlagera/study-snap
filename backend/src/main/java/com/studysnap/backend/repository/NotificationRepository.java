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

    /**
     * The badge count. ⚠️ ITS VISIBILITY PREDICATE MUST MATCH {@link #findVisibleInbox} EXACTLY, minus
     * the read and actionable-type legs that are the badge's own.
     *
     * <p>⚠️ THIS QUERY ORIGINALLY FILTERED ON {@code readAt} ALONE, AND THAT WAS A DEFECT — found by two
     * independent pressure-test agents in {@code v0.130.0}. A learner who dismissed an actionable row
     * without reading it removed it from the inbox while it kept incrementing the bell, leaving a badge
     * pointing at nothing openable and no way to clear it. THE {@code dismissedAt} LEG IS THAT FIX.
     *
     * <p>The lifecycle subquery is MIRRORING, not a live bug fix: announcements are non-actionable, so
     * {@code type in :actionableTypes} already excludes every row that has an announcement today. It is
     * here so the two predicates cannot drift the first time an actionable type carries an
     * {@code announcement_id} — a change to either query is a change to BOTH. A badge that can name a
     * number the inbox cannot show is worse than no badge: it trains people to ignore the one signal a
     * pending request has.
     */
    @Query("""
            select count(notification)
            from NotificationEntity notification
            where notification.recipientUserId = :recipientUserId
              and notification.readAt is null
              and notification.dismissedAt is null
              and notification.type in :actionableTypes
              and not exists (
                  select 1
                  from AnnouncementEntity announcement
                  where announcement.id = notification.announcementId
                    and (
                        announcement.status = com.studysnap.backend.entity.AnnouncementStatus.ENDED
                        or (announcement.expiresAt is not null and announcement.expiresAt <= :now)
                    )
              )
            """)
    long countActionableUnread(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("actionableTypes") Collection<NotificationType> actionableTypes,
            @Param("now") OffsetDateTime now
    );

    /**
     * Episode suppression for genuinely different Review Set publication events. This is deliberately
     * not an existence check for delivery identity: the permanent recipient/dedup unique index remains
     * the sole identity mechanism, and each revision has a distinct key. This query only withholds a
     * newer revision while the learner already holds an undismissed signal for the same source. A race
     * can therefore produce one extra notification about a real newer revision; it can never duplicate
     * awareness for one event or suppress that event through a pre-insert identity check.
     */
    @Query("""
            select notification.recipientUserId
            from NotificationEntity notification
            where notification.type = :type
              and notification.dismissedAt is null
              and notification.recipientUserId in :recipientUserIds
              and notification.dedupKey like :dedupKeyPrefix
            """)
    List<UUID> findRecipientsWithUndismissedEpisode(
            @Param("type") NotificationType type,
            @Param("recipientUserIds") Collection<UUID> recipientUserIds,
            @Param("dedupKeyPrefix") String dedupKeyPrefix
    );

    @Modifying
    @Query("""
            delete from NotificationEntity notification
            where notification.createdAt < :threshold
              and (notification.readAt is not null
                   or notification.dismissedAt is not null
                   or notification.type in :expirableTypes)
            """)
    int deleteExpiredBefore(
            @Param("threshold") OffsetDateTime threshold,
            @Param("expirableTypes") Collection<NotificationType> expirableTypes
    );

    /**
     * ⚠️ ACCOUNT ERASURE, NOT RETENTION. {@link #deleteExpiredBefore} deliberately keeps unread
     * actionable rows forever; a purge must take them anyway, because after a purge there is no learner
     * left to act on them. Called from {@code AccountPurgeService.deletePersonalRows}.
     */
    int deleteByRecipientUserId(UUID recipientUserId);
}
