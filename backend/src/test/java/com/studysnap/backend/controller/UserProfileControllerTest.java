package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SubscriptionPlanStatusResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                true,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
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
                false,
                null,
                ProfileType.STUDENT,
                null,
                EngagementMode.FOCUSED,
                false,
                false,
                ThemePreference.SYSTEM,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
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
}
