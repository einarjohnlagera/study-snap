package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.service.AdminFunnelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFunnelControllerTest {

    @Mock
    private AdminFunnelService adminFunnelService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminFunnelController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getMetrics_returnsFunnelMetrics() {
        AdminFunnelController controller = new AdminFunnelController(adminFunnelService);
        AdminFunnelMetricsResponse expected = new AdminFunnelMetricsResponse(
                new AdminFunnelMetricsResponse.ActivationMetrics(10, 4, 40.0, 2.5),
                new AdminFunnelMetricsResponse.StuckUsersMetrics(3),
                new AdminFunnelMetricsResponse.QuotaHitMetrics(2, 5, 40.0),
                new AdminFunnelMetricsResponse.PaywallConversionMetrics(6, 2, 33.3),
                new AdminFunnelMetricsResponse.ValueLoopMetrics(4, 3, 75.0)
        );
        when(adminFunnelService.getMetrics()).thenReturn(expected);

        AdminFunnelMetricsResponse response = controller.getMetrics();

        assertThat(response).isEqualTo(expected);
        verify(adminFunnelService).getMetrics();
    }
}
