package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SubscriptionPlanStatusResponse;
import com.studysnap.backend.dto.UpdateExamDateRequest;
import com.studysnap.backend.dto.UpdateStudyDaysPerWeekRequest;
import com.studysnap.backend.dto.UpdateStudyGoalRequest;
import com.studysnap.backend.dto.UpdatePublicProfileVisibilityRequest;
import com.studysnap.backend.dto.UpdateUserProfileRequest;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private AuthService authService;

    private UserProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new UserProfileController(authService);
    }

    @Test
    void updateProfile_delegatesIdentitySaveToAuthService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Nursing",
                "NoteLib Academy",
                "[email protected]"
        );
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                "[email protected]",
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Nursing",
                null,
                java.util.List.of(),
                "NoteLib Academy",
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateUserProfile(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateProfile(user, request);

        assertThat(response).isEqualTo(expected);
        verify(authService).updateUserProfile(userId, request);
    }

    @Test
    void updateProfileRequest_rejectsSchoolNameOverMaxLength() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Note",
                "User",
                "Study Note",
                "studynote",
                null,
                LearnerLevel.COLLEGE,
                "Nursing",
                "x".repeat(121),
                "[email protected]"
        );

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("School name must be 120 characters or less.");
        }
    }


    @Test
    void updatePublicProfileVisibility_delegatesToAuthService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdatePublicProfileVisibilityRequest request = new UpdatePublicProfileVisibilityRequest(false);
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Nursing",
                null,
                java.util.List.of(),
                null,
                false,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updatePublicProfileVisibility(userId, request)).thenReturn(expected);

        MeResponse response = controller.updatePublicProfileVisibility(user, request);

        assertThat(response).isEqualTo(expected);
        verify(authService).updatePublicProfileVisibility(userId, request);
    }

    @Test
    void updateExamDate_delegatesToAuthService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateExamDateRequest request = new UpdateExamDateRequest(LocalDate.parse("2026-10-15"));
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "pnle",
                java.util.List.of(),
                null,
                true,
                null,
                ProfileType.BOARD_EXAM,
                LocalDate.parse("2026-10-15"),
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateExamDate(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateExamDate(user, request);

        assertThat(response).isEqualTo(expected);
        verify(authService).updateExamDate(userId, request);
    }

    @Test
    void updateStudyGoal_delegatesValidGoalToAuthService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateStudyGoalRequest request = new UpdateStudyGoalRequest("ale");
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Architecture",
                "ale",
                java.util.List.of(),
                null,
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateStudyGoal(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateStudyGoal(user, request);

        assertThat(response).isEqualTo(expected);
        verify(authService).updateStudyGoal(userId, request);
    }

    @Test
    void updateStudyGoal_allowsNullGoalToClearGoal() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateStudyGoalRequest request = new UpdateStudyGoalRequest(null);
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Architecture",
                null,
                java.util.List.of(),
                null,
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateStudyGoal(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateStudyGoal(user, request);

        assertThat(response.studyGoal()).isNull();
        verify(authService).updateStudyGoal(userId, request);
    }

    @Test
    void updateStudyGoal_allowsCourseProgramGoal() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateStudyGoalRequest request = new UpdateStudyGoalRequest("Mathematics");
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Mathematics",
                "Mathematics",
                java.util.List.of(),
                null,
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                null,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateStudyGoal(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateStudyGoal(user, request);

        assertThat(response.studyGoal()).isEqualTo("Mathematics");
        verify(authService).updateStudyGoal(userId, request);
    }

    @Test
    void updateStudyDaysPerWeek_delegatesToAuthService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateStudyDaysPerWeekRequest request = new UpdateStudyDaysPerWeekRequest(5);
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                null,
                "Note",
                "User",
                "Study Note",
                "studynote",
                "Reviewing pathology one note at a time.",
                LearnerLevel.COLLEGE,
                "Nursing",
                null,
                java.util.List.of(),
                null,
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                null,
                null,
                5,
                4,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                new SubscriptionPlanStatusResponse(false, null, null)
        );
        when(authService.updateStudyDaysPerWeek(userId, request)).thenReturn(expected);

        MeResponse response = controller.updateStudyDaysPerWeek(user, request);

        assertThat(response.studyDaysPerWeek()).isEqualTo(5);
        verify(authService).updateStudyDaysPerWeek(userId, request);
    }

    @Test
    void updateStudyDaysPerWeekRequest_rejectsValueOutsideOneToSevenRange() {
        UpdateStudyDaysPerWeekRequest tooLow = new UpdateStudyDaysPerWeekRequest(0);
        UpdateStudyDaysPerWeekRequest tooHigh = new UpdateStudyDaysPerWeekRequest(8);

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(tooLow))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("Study days per week must be between 1 and 7.");
            assertThat(validatorFactory.getValidator().validate(tooHigh))
                    .extracting(ConstraintViolation::getMessage)
                    .contains("Study days per week must be between 1 and 7.");
        }
    }

    @Test
    void updateStudyDaysPerWeekRequest_allowsNullToClear() {
        UpdateStudyDaysPerWeekRequest request = new UpdateStudyDaysPerWeekRequest(null);

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isEmpty();
        }
    }
}
