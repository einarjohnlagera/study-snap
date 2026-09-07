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

    List<NotificationEntity> findByRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(
            UUID recipientUserId,
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
