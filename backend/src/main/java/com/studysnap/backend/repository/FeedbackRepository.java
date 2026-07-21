package com.studysnap.backend.repository;

import com.studysnap.backend.entity.FeedbackEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {
    List<FeedbackEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<FeedbackEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteByUserId(UUID userId);
}
