package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CampaignFeedbackResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampaignFeedbackResponseRepository extends JpaRepository<CampaignFeedbackResponseEntity, UUID> {
    Optional<CampaignFeedbackResponseEntity> findByUserIdAndCampaignId(UUID userId, String campaignId);

    void deleteByUserId(UUID userId);
}
