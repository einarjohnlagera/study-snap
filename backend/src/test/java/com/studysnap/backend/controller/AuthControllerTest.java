package com.studysnap.backend.controller;

import com.studysnap.backend.dto.DeleteAccountRequest;
import com.studysnap.backend.dto.ReactivateAccountRequest;
import com.studysnap.backend.dto.UpdateEmailPreferencesRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
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

    @Test
    void deleteAccount_usesAccountDeleteRoute() throws NoSuchMethodException {
        Method method = AuthController.class.getDeclaredMethod(
                "deleteAccount",
                AuthenticatedUser.class,
                DeleteAccountRequest.class
        );
        PostMapping annotation = method.getAnnotation(PostMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/account/delete");
    }

    @Test
    void deleteAccount_delegatesToAuthService() {
        AuthController controller = new AuthController(authService, authRateLimitService);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        DeleteAccountRequest request = new DeleteAccountRequest("DELETE");

        controller.deleteAccount(user, request);

        verify(authService).requestAccountDeletion(userId, request);
    }

    @Test
    void reactivateAccount_usesAccountReactivateRoute() throws NoSuchMethodException {
        Method method = AuthController.class.getDeclaredMethod(
                "reactivateAccount",
                ReactivateAccountRequest.class,
                HttpServletRequest.class
        );
        PostMapping annotation = method.getAnnotation(PostMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/account/reactivate");
    }

    @Test
    void reactivateAccount_rateLimitsAndDelegatesToAuthService() {
        AuthController controller = new AuthController(authService, authRateLimitService);
        HttpServletRequest servletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        ReactivateAccountRequest request = new ReactivateAccountRequest("Note@Example.com", "password123", null, true);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        controller.reactivateAccount(request, servletRequest);

        verify(authRateLimitService).assertAllowed("reactivate", "127.0.0.1:note@example.com");
        verify(authService).reactivateAccount(request, "127.0.0.1", "JUnit");
    }
}
