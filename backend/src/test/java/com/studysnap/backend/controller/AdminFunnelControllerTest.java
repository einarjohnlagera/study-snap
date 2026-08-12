package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.service.AdminFunnelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFunnelControllerTest {
    private static final String LEGACY_STEP_NAME = "legacy";
    private static final String LEGACY_STEP_LABEL = "Legacy";

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
                null,
                null,
                new AdminFunnelMetricsResponse.OnboardingMetrics(
                        10,
                        6,
                        60.0,
                        List.of(),
                        new AdminFunnelMetricsResponse.OnboardingStepMetrics(
                                LEGACY_STEP_NAME,
                                LEGACY_STEP_LABEL,
                                0,
                                null
                        )
                ),
                new AdminFunnelMetricsResponse.ActivationMetrics(10, 4, 40.0, 2.5),
                new AdminFunnelMetricsResponse.StuckUsersMetrics(3),
                new AdminFunnelMetricsResponse.QuotaHitMetrics(2, 5, 40.0, List.of()),
                new AdminFunnelMetricsResponse.PaywallConversionMetrics(6, 2, 33.3),
                new AdminFunnelMetricsResponse.ValueLoopMetrics(4, 3, 75.0),
                new AdminFunnelMetricsResponse.RetentionCohortMetrics(
                        8,
                        3,
                        37.5,
                        new AdminFunnelMetricsResponse.WideRetentionMetrics(6, 3, 50.0, 4, 66.7, 5, 83.3),
                        List.of()
                ),
                new AdminFunnelMetricsResponse.CheckoutConversionMetrics(10, 4, 1, 40.0, 25.0, 10.0)
        );
        when(adminFunnelService.getMetrics(null)).thenReturn(expected);

        AdminFunnelMetricsResponse response = controller.getMetrics(null);

        assertThat(response).isEqualTo(expected);
        verify(adminFunnelService).getMetrics(null);
    }

    @Test
    void getMetrics_passesWindowDaysToService() {
        AdminFunnelController controller = new AdminFunnelController(adminFunnelService);
        AdminFunnelMetricsResponse expected = new AdminFunnelMetricsResponse(
                30,
                java.time.OffsetDateTime.now(),
                new AdminFunnelMetricsResponse.OnboardingMetrics(
                        0,
                        0,
                        0.0,
                        List.of(),
                        new AdminFunnelMetricsResponse.OnboardingStepMetrics(
                                LEGACY_STEP_NAME,
                                LEGACY_STEP_LABEL,
                                0,
                                null
                        )
                ),
                new AdminFunnelMetricsResponse.ActivationMetrics(0, 0, 0.0, null),
                new AdminFunnelMetricsResponse.StuckUsersMetrics(0),
                new AdminFunnelMetricsResponse.QuotaHitMetrics(0, 0, 0.0, List.of()),
                new AdminFunnelMetricsResponse.PaywallConversionMetrics(0, 0, 0.0),
                new AdminFunnelMetricsResponse.ValueLoopMetrics(0, 0, 0.0),
                new AdminFunnelMetricsResponse.RetentionCohortMetrics(
                        0,
                        0,
                        0.0,
                        new AdminFunnelMetricsResponse.WideRetentionMetrics(0, 0, 0.0, 0, 0.0, 0, 0.0),
                        List.of()
                ),
                new AdminFunnelMetricsResponse.CheckoutConversionMetrics(0, 0, 0, 0.0, 0.0, 0.0)
        );
        when(adminFunnelService.getMetrics(30)).thenReturn(expected);

        AdminFunnelMetricsResponse response = controller.getMetrics(30);

        assertThat(response).isEqualTo(expected);
        verify(adminFunnelService).getMetrics(30);
    }
}
