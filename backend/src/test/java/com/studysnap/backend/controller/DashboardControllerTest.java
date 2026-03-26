package com.studysnap.backend.controller;

import com.studysnap.backend.dto.DashboardFocusAreasResponse;
import com.studysnap.backend.dto.DashboardOverviewResponse;
import com.studysnap.backend.dto.DashboardPerformanceSummaryResponse;
import com.studysnap.backend.dto.DashboardWeeklyActivityResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        dashboardController = new DashboardController(dashboardService);
    }

    @Test
    void getOverview_returnsDashboardOverviewPayload() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        DashboardOverviewResponse expected = new DashboardOverviewResponse(
                new DashboardPerformanceSummaryResponse(null, 0, 0, null, null),
                new DashboardFocusAreasResponse(List.of(), null, false),
                new DashboardWeeklyActivityResponse(0, 0, 0, 0)
        );
        when(dashboardService.getOverview(userId)).thenReturn(expected);

        DashboardOverviewResponse response = dashboardController.getOverview(user);

        assertThat(response).isEqualTo(expected);
        verify(dashboardService).getOverview(userId);
    }
}
