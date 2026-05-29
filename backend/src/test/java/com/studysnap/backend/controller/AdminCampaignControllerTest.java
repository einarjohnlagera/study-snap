package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CampaignStatusResponse;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.service.ReEngagementCampaignService;
import com.studysnap.backend.service.ReEngagementCampaignService.ReEngagementSendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCampaignControllerTest {

    @Mock private ReEngagementCampaignService reEngagementCampaignService;
    @Mock private EmailLogRepository emailLogRepository;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminCampaignController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getStatus_returnsEligibleCountTotalSentAndLastSentAt() {
        OffsetDateTime lastSent = OffsetDateTime.of(2026, 6, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        EmailLogEntity logEntry = new EmailLogEntity();
        logEntry.setSentAt(lastSent);

        when(reEngagementCampaignService.countEligible()).thenReturn(42);
        when(emailLogRepository.countByEmailType(RetentionEmailType.RE_ENGAGEMENT_2025)).thenReturn(87L);
        when(emailLogRepository.findTopByEmailTypeOrderBySentAtDesc(RetentionEmailType.RE_ENGAGEMENT_2025))
                .thenReturn(Optional.of(logEntry));

        AdminCampaignController controller = new AdminCampaignController(reEngagementCampaignService, emailLogRepository);
        ResponseEntity<CampaignStatusResponse> response = controller.getReEngagementStatus();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().eligibleCount()).isEqualTo(42);
        assertThat(response.getBody().totalSent()).isEqualTo(87L);
        assertThat(response.getBody().lastSentAt()).isEqualTo(lastSent);
    }

    @Test
    void getStatus_returnsNullLastSentAtWhenNeverSent() {
        when(reEngagementCampaignService.countEligible()).thenReturn(10);
        when(emailLogRepository.countByEmailType(RetentionEmailType.RE_ENGAGEMENT_2025)).thenReturn(0L);
        when(emailLogRepository.findTopByEmailTypeOrderBySentAtDesc(RetentionEmailType.RE_ENGAGEMENT_2025))
                .thenReturn(Optional.empty());

        AdminCampaignController controller = new AdminCampaignController(reEngagementCampaignService, emailLogRepository);
        ResponseEntity<CampaignStatusResponse> response = controller.getReEngagementStatus();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().lastSentAt()).isNull();
        assertThat(response.getBody().totalSent()).isEqualTo(0L);
    }

    @Test
    void send_callsServiceAndReturnsResult() {
        ReEngagementSendResult result = new ReEngagementSendResult(75, 2);
        when(reEngagementCampaignService.send()).thenReturn(result);

        AdminCampaignController controller = new AdminCampaignController(reEngagementCampaignService, emailLogRepository);
        ResponseEntity<ReEngagementSendResult> response = controller.sendReEngagement();

        verify(reEngagementCampaignService).send();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sent()).isEqualTo(75);
        assertThat(response.getBody().skipped()).isEqualTo(2);
    }
}
