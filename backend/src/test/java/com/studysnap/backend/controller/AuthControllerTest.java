package com.studysnap.backend.controller;

import com.studysnap.backend.dto.UpdateEmailPreferencesRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthRateLimitService authRateLimitService;

    @Test
    void updateEmailPreferences_usesEmailPreferencesRoute() throws NoSuchMethodException {
        Method method = AuthController.class.getDeclaredMethod(
                "updateEmailPreferences",
                AuthenticatedUser.class,
                UpdateEmailPreferencesRequest.class
        );
        PostMapping annotation = method.getAnnotation(PostMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/preferences/email-preferences");
    }

    @Test
    void updateEmailPreferences_delegatesToAuthService() {
        AuthController controller = new AuthController(authService, authRateLimitService);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateEmailPreferencesRequest request = new UpdateEmailPreferencesRequest(true, true, true, true);

        controller.updateEmailPreferences(user, request);

        verify(authService).updateEmailPreferences(userId, request);
    }
}
