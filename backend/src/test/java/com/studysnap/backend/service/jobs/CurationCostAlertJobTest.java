package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CurationCostAlertJobTest {
    private static final String STANDARD_MODEL = "gpt-4.1";
    private static final String OCTOBER_KEY = "curation-cost-alert:2026-10:10.00";
    private static final String NOVEMBER_KEY = "curation-cost-alert:2026-11:10.00";
    private final StudyPackRepository packs = mock(StudyPackRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StudySnapProperties properties = new StudySnapProperties();
    private final CurationCostAlertJob job = new CurationCostAlertJob(packs, users, notifications, properties);
    private final OffsetDateTime october = OffsetDateTime.parse("2026-10-19T12:00:00Z");

    @BeforeEach
    void setUp() {
        properties.getCurationCostAlert().setThresholdUsd(new BigDecimal("10.00"));
    }

    @Test
    void pricingSplitsCachedInputAndDoesNotAddReasoningToOutput() {
        properties.getCurationCostAlert().getModelPrices().get(STANDARD_MODEL)
                .setCachedInput(new BigDecimal("0.50"));
        assertThat(job.estimate(List.of(tokens(STANDARD_MODEL, 2_000_000, 500_000, 1_000_000))))
                .isEqualByComparingTo("11.25");
    }

    @Test
    void missingCachedRateFallsBackToInputRate() {
        assertThat(job.estimate(List.of(tokens(STANDARD_MODEL, 1_000_000, 500_000, 0))))
                .isEqualByComparingTo("2.00");
    }

    @Test
    void unknownModelUsesHighestConfiguredRates() {
        assertThat(job.estimate(List.of(tokens("unknown-model", 1_000_000, 250_000, 1_000_000))))
                .isEqualByComparingTo("12.00");
    }

    @Test
    void longestModelPrefixWinsForMini() {
        assertThat(job.estimate(List.of(tokens("gpt-4.1-mini-2026-10", 1_000_000, 0, 1_000_000))))
                .isEqualByComparingTo("2.00");
    }

    @Test
    void disabledDoesNotQueryOrNotify() {
        properties.getCurationCostAlert().setEnabled(false);
        job.runAt(october);
        verifyNoInteractions(packs, users, notifications);
    }

    @Test
    void queryFailureIsSwallowedWithoutNotifying() {
        when(packs.sumAdminGenerationTokensByModel(any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        job.runAt(october);
        verifyNoInteractions(users, notifications);
    }

    @Test
    void belowThresholdDoesNotResolveAdminsOrNotify() {
        var row = tokens(STANDARD_MODEL, 0, 0, 1_000_000);
        when(packs.sumAdminGenerationTokensByModel(any(), any())).thenReturn(List.of(row));
        job.runAt(october);
        verifyNoInteractions(users, notifications);
    }

    @Test
    void atThresholdDeliversToEveryAdminWithPartialScopeAndStableMonthlyKey() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var row = tokens("gpt-6.1-sol", 0, 0, 1_000_000);
        when(packs.sumAdminGenerationTokensByModel(any(), any())).thenReturn(List.of(row));
        when(users.findAdminUserIds()).thenReturn(List.of(first, second));

        job.runAt(october);
        job.runAt(october.plusDays(1));
        job.runAt(october.plusMonths(1));

        ArgumentCaptor<NotificationService.NotificationDelivery> deliveries =
                ArgumentCaptor.forClass(NotificationService.NotificationDelivery.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(6)).deliver(deliveries.capture(), keys.capture());
        assertThat(keys.getAllValues()).containsExactly(
                OCTOBER_KEY, OCTOBER_KEY, OCTOBER_KEY, OCTOBER_KEY, NOVEMBER_KEY, NOVEMBER_KEY);
        assertThat(deliveries.getAllValues()).extracting(NotificationService.NotificationDelivery::recipientUserId)
                .containsExactly(first, second, first, second, first, second);
        assertThat(deliveries.getAllValues()).allSatisfy(delivery -> {
            assertThat(delivery.type()).isEqualTo(NotificationType.CURATION_COST_ALERT);
            assertThat(delivery.body()).contains("This estimate covers Study Pack generation only.")
                    .contains("It excludes note generation from topics, template seeding, summary regeneration and quiz repair.")
                    .contains("Check the OpenAI billing dashboard for total spend.")
                    .doesNotContain("gpt-6.1-sol");
        });
    }

    private static StudyPackRepository.MonthlyCurationTokens tokens(String model, long input, long cached, long output) {
        var row = mock(StudyPackRepository.MonthlyCurationTokens.class);
        when(row.getModelUsed()).thenReturn(model);
        when(row.getInputTokens()).thenReturn(input);
        when(row.getCachedInputTokens()).thenReturn(cached);
        when(row.getOutputTokens()).thenReturn(output);
        return row;
    }
}
