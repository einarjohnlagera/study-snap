package com.studysnap.backend.repository;

import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLogEntity, UUID> {
    boolean existsByUserIdAndEmailTypeAndSentAtAfter(UUID userId, RetentionEmailType emailType, OffsetDateTime sentAt);

    boolean existsByUserIdAndEmailType(UUID userId, RetentionEmailType emailType);

    Optional<EmailLogEntity> findTopByUserIdAndEmailTypeOrderBySentAtDesc(UUID userId, RetentionEmailType emailType);

    long countByEmailType(RetentionEmailType emailType);

    Optional<EmailLogEntity> findTopByEmailTypeOrderBySentAtDesc(RetentionEmailType emailType);

    long countBySentAtGreaterThanEqual(OffsetDateTime sentAt);

    void deleteByUserId(UUID userId);
}
