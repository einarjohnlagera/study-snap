package com.studysnap.backend.controller;

import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.MePlanService;
import com.studysnap.backend.service.ProgressReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private MePlanService mePlanService;

    @Mock
    private ProgressReportService progressReportService;

    @Mock
    private UserRepository userRepository;

    private MeController meController;

    @BeforeEach
    void setUp() {
        meController = new MeController(mePlanService, progressReportService, userRepository);
    }

    @Test
    void getPlan_returnsBackendDrivenPlanSummary() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MePlanResponse expected = new MePlanResponse(
                PlanType.FREE,
                new MePlanResponse.UsageCycle(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z")
                ),
                new MePlanResponse.Limits(10, 5, 0, 20, 5, 2),
                new MePlanResponse.Usage(3, 2, 0, 5, 1, 0),
                new MePlanResponse.Remaining(7, 3, 0, 15, 4, 2),
                new MePlanResponse.Features(false, true, true, true)
        );
        when(mePlanService.getPlan(userId)).thenReturn(expected);

        MePlanResponse response = meController.getPlan(user);

        assertThat(response).isEqualTo(expected);
        verify(mePlanService).getPlan(userId);
    }

    @Test
    void getProgress_returnsProgressReportForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setStudyGoal("ale");
        userEntity.setProfileType(ProfileType.STUDENT);
        ProgressReportResponse expected = new ProgressReportResponse(List.of(
                new SubjectProgressEntry("Biology", 4, 2, 1, 1, 50)
        ), null, List.of("Biology"), null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(progressReportService.getProgressReport(eq(userId), eq("ale"), eq(List.of()), any(OffsetDateTime.class))).thenReturn(expected);

        ProgressReportResponse response = meController.getProgress(user);

        assertThat(response).isEqualTo(new ProgressReportResponse(
                expected.subjects(),
                expected.goalSummary(),
                expected.userCoursePrograms(),
                "STUDENT"
        ));
        verify(progressReportService).getProgressReport(eq(userId), eq("ale"), eq(List.of()), any(OffsetDateTime.class));
    }

    @Test
    void getGoal_returnsGoalSummaryWhenStudyGoalIsSet() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setStudyGoal("pnle");
        GoalNudgeResponse expected = new GoalNudgeResponse(
                "pnle",
                "EXAM",
                "PNLE",
                "Philippine Nurse Licensure Examination",
                42,
                8,
                null
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(progressReportService.buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class))).thenReturn(expected);

        GoalNudgeResponse response = meController.getGoal(user);

        assertThat(response).isEqualTo(expected);
        verify(progressReportService).buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class));
    }

    @Test
    void getGoal_returnsNullWhenStudyGoalIsMissing() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setStudyGoal(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        GoalNudgeResponse response = meController.getGoal(user);

        assertThat(response).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getGoal_returnsNullWhenGoalSummaryCannotBeComputed() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setStudyGoal("Biochemistry");
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(progressReportService.buildGoalNudge(eq(userId), eq("Biochemistry"), any(OffsetDateTime.class)))
                .thenThrow(new IllegalStateException("summary unavailable"));

        GoalNudgeResponse response = meController.getGoal(user);

        assertThat(response).isNull();
        verify(progressReportService).buildGoalNudge(eq(userId), eq("Biochemistry"), any(OffsetDateTime.class));
    }
}
