package com.studysnap.backend.repository;

import com.studysnap.backend.entity.GoalAdoptionJobEntity;
import com.studysnap.backend.entity.GoalAdoptionJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalAdoptionJobRepository extends JpaRepository<GoalAdoptionJobEntity, UUID> {
    Optional<GoalAdoptionJobEntity> findByGoalIdAndOwnerUserId(UUID goalId, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GoalAdoptionJobEntity job where job.id = :id")
    Optional<GoalAdoptionJobEntity> findByIdForUpdate(UUID id);

    @Query("select job.id from GoalAdoptionJobEntity job where (job.status in :retryStates or (job.status = :running and job.updatedAt < :staleBefore)) order by job.updatedAt")
    List<UUID> findRecoverableIds(List<GoalAdoptionJobStatus> retryStates,
                                  GoalAdoptionJobStatus running, OffsetDateTime staleBefore, Pageable page);
}
