package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminDashboardRecentEventsResponse;
import com.studysnap.backend.dto.AdminDashboardSummaryResponse;
import com.studysnap.backend.dto.AdminDashboardTopContentResponse;
import com.studysnap.backend.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminDashboardController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getSummary_returnsAdminDashboardSummary() {
        AdminDashboardController controller = new AdminDashboardController(adminDashboardService);
        AdminDashboardSummaryResponse expected = new AdminDashboardSummaryResponse(
                new AdminDashboardSummaryResponse.Overview(120, 90, 18, 41, 420, 275, 44, 830, 67, 18),
                new AdminDashboardSummaryResponse.Billing(18, 12, 6, 2, 3, new BigDecimal("199.50"), new BigDecimal("1499.00")),
                new AdminDashboardSummaryResponse.Engagement(38, 240, 81, 44, 130, 27, 52, 31)
        );
        when(adminDashboardService.getSummary()).thenReturn(expected);

        AdminDashboardSummaryResponse response = controller.getSummary();

        assertThat(response).isEqualTo(expected);
        verify(adminDashboardService).getSummary();
    }

    @Test
    void getTopContent_returnsAdminTopContent() {
        AdminDashboardController controller = new AdminDashboardController(adminDashboardService);
        AdminDashboardTopContentResponse expected = new AdminDashboardTopContentResponse(List.of(), List.of(), List.of());
        when(adminDashboardService.getTopContent()).thenReturn(expected);

        AdminDashboardTopContentResponse response = controller.getTopContent();

        assertThat(response).isEqualTo(expected);
        verify(adminDashboardService).getTopContent();
    }

    @Test
    void getRecentEvents_returnsRecentEventsPayload() {
        AdminDashboardController controller = new AdminDashboardController(adminDashboardService);
        AdminDashboardRecentEventsResponse expected = new AdminDashboardRecentEventsResponse(List.of(), List.of(), List.of());
        when(adminDashboardService.getRecentEvents()).thenReturn(expected);

        AdminDashboardRecentEventsResponse response = controller.getRecentEvents();

        assertThat(response).isEqualTo(expected);
        verify(adminDashboardService).getRecentEvents();
    }
}
