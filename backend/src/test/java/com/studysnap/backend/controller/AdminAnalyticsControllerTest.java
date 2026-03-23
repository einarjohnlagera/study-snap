package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminAnalyticsSummaryResponse;
import com.studysnap.backend.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Test
    void getSummary_returnsAnalyticsSnapshot() {
        AdminAnalyticsController controller = new AdminAnalyticsController(analyticsService);
        AdminAnalyticsSummaryResponse expected = new AdminAnalyticsSummaryResponse(
                10,
                4,
                22,
                5,
                6,
                3,
                2,
                40,
                80,
                25,
                12,
                9,
                3
        );
        when(analyticsService.getSummary()).thenReturn(expected);

        AdminAnalyticsSummaryResponse response = controller.getSummary();

        assertThat(response).isEqualTo(expected);
        verify(analyticsService).getSummary();
    }
}
