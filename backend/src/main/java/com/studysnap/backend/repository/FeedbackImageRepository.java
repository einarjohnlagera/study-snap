package com.studysnap.backend.repository;

import com.studysnap.backend.entity.FeedbackImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedbackImageRepository extends JpaRepository<FeedbackImageEntity, UUID> {
    @Query("select image.feedbackId from FeedbackImageEntity image where image.feedbackId in :feedbackIds")
    List<UUID> findExistingFeedbackIds(@Param("feedbackIds") Collection<UUID> feedbackIds);
}
