package com.studysnap.backend.service;

import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.repository.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingGuardServiceTest {
    @Mock
    private UserRepository userRepository;

    private OnboardingGuardService onboardingGuardService;

    @BeforeEach
    void setUp() {
        onboardingGuardService = new OnboardingGuardService(userRepository);
    }

    @Test
    void assertProfileComplete_throwsOnboardingRequiredForCompletedButNullProfileType() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setOnboardingCompletedAt(OffsetDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> onboardingGuardService.assertProfileComplete(userId))
                .isInstanceOf(ProfileSetupRequiredException.class)
                .satisfies(error -> {
                    ProfileSetupRequiredException exception = (ProfileSetupRequiredException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("ONBOARDING_REQUIRED");
                    assertThat(exception.getAction()).isEqualTo("COMPLETE_PROFILE_TYPE");
                });
    }

    @Test
    void assertProfileComplete_exemptsFreshPracticeFirstLearnersStillMidOnboarding() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        // null profileType + null onboardingCompletedAt = mid-onboarding or copy-on-signup; must not block.
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatCode(() -> onboardingGuardService.assertProfileComplete(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void assertProfileComplete_allowsUsersWithProfileType() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setProfileType(ProfileType.STUDENT);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatCode(() -> onboardingGuardService.assertProfileComplete(userId))
                .doesNotThrowAnyException();
    }
}
