package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CampaignStatusResponse;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.service.ReEngagementCampaignService;
import com.studysnap.backend.service.ReEngagementCampaignService.ReEngagementSendResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/admin/campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCampaignController {

    private final ReEngagementCampaignService reEngagementCampaignService;
    private final EmailLogRepository emailLogRepository;

    @GetMapping("/re-engagement/status")
    public ResponseEntity<CampaignStatusResponse> getReEngagementStatus() {
        int eligibleCount = reEngagementCampaignService.countEligible();
        long totalSent = emailLogRepository.countByEmailType(RetentionEmailType.RE_ENGAGEMENT_2025);
        OffsetDateTime lastSentAt = emailLogRepository
                .findTopByEmailTypeOrderBySentAtDesc(RetentionEmailType.RE_ENGAGEMENT_2025)
                .map(log -> log.getSentAt())
                .orElse(null);
        return ResponseEntity.ok(new CampaignStatusResponse(eligibleCount, totalSent, lastSentAt));
    }

    @PostMapping("/re-engagement/send")
    public ResponseEntity<ReEngagementSendResult> sendReEngagement() {
        ReEngagementSendResult result = reEngagementCampaignService.send();
        return ResponseEntity.ok(result);
    }
}
