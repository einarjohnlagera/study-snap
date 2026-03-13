package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.UserActivityEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<UserActivityEventEntity, UUID> {
    List<UserActivityEventEntity> findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
            UUID userId,
            ActivityType activityType,
            Pageable pageable
    );

    Optional<UserActivityEventEntity> findTopByUserIdAndStudyPackIdAndActivityTypeOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            ActivityType activityType
    );

    List<UserActivityEventEntity> findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
            UUID userId,
            Collection<ActivityType> activityTypes,
            OffsetDateTime createdAt
    );
}
