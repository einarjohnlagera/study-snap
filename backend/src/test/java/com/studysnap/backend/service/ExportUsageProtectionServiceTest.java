package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.exception.MonthlyExportLimitReachedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportUsageProtectionServiceTest {
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;

    private ExportUsageProtectionService exportUsageProtectionService;

    @BeforeEach
    void setUp() {
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, new StudySnapProperties());
        exportUsageProtectionService = new ExportUsageProtectionService(featureGateService, userUsageService);
    }

    @Test
    void blocksTeacherFreeDocxAfterTeacherQuota() {
        UUID userId = UUID.randomUUID();
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(monthlyUsage(10, 0));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> exportUsageProtectionService.assertDocxQuotaAvailable(userId, PlanType.FREE, ProfileType.TEACHER)
        );

        assertThat(thrown).isInstanceOf(MonthlyExportLimitReachedException.class);
        assertThat(((MonthlyExportLimitReachedException) thrown).getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    void teacherPlusDocxHasNoQuotaCap() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> exportUsageProtectionService.assertDocxQuotaAvailable(
                userId,
                PlanType.PLUS,
                ProfileType.TEACHER
        )).doesNotThrowAnyException();
    }

    @Test
    void blocksNonTeacherPlusDocxAtStandardQuota() {
        UUID userId = UUID.randomUUID();
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(monthlyUsage(15, 0));

        assertThatThrownBy(() -> exportUsageProtectionService.assertDocxQuotaAvailable(
                userId,
                PlanType.PLUS,
                ProfileType.STUDENT
        )).isInstanceOf(MonthlyExportLimitReachedException.class);
    }

    @Test
    void teacherPlusPdfUsesStandardQuota() {
        UUID userId = UUID.randomUUID();
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(monthlyUsage(0, 15));

        assertThatThrownBy(() -> exportUsageProtectionService.assertPdfQuotaAvailable(userId, PlanType.PLUS))
                .isInstanceOf(MonthlyExportLimitReachedException.class);
    }

    private UserUsageService.MonthlyUsage monthlyUsage(int docxExports, int pdfExports) {
        return new UserUsageService.MonthlyUsage(
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                0,
                0,
                0,
                0,
                0,
                0,
                docxExports,
                pdfExports,
                0,
                0
        );
    }
}
