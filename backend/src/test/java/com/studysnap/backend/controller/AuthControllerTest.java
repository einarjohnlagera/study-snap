package com.studysnap.backend.controller;

import com.studysnap.backend.dto.DataExportResponse;
import com.studysnap.backend.dto.DeleteAccountRequest;
import com.studysnap.backend.dto.ReactivateAccountRequest;
import com.studysnap.backend.dto.UpdateEmailPreferencesRequest;
import com.studysnap.backend.dto.UpdateMobileTabBarPreferenceRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AccountDataExportService;
import com.studysnap.backend.service.AuthService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
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
    @Mock
    private AccountDataExportService accountDataExportService;

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
        AuthController controller = controller();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateEmailPreferencesRequest request = new UpdateEmailPreferencesRequest(true, true, true, true, true, true);

        controller.updateEmailPreferences(user, request);

        verify(authService).updateEmailPreferences(userId, request);
    }

    @Test
    void updateMobileTabBarPreference_usesDedicatedRouteAndDelegatesToAuthService() throws NoSuchMethodException {
        Method method = AuthController.class.getDeclaredMethod(
                "updateMobileTabBarPreference",
                AuthenticatedUser.class,
                UpdateMobileTabBarPreferenceRequest.class
        );
        PostMapping annotation = method.getAnnotation(PostMapping.class);
        AuthController controller = controller();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        UpdateMobileTabBarPreferenceRequest request = new UpdateMobileTabBarPreferenceRequest(false);

        controller.updateMobileTabBarPreference(user, request);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/preferences/mobile-tab-bar");
        verify(authService).updateMobileTabBarPreference(userId, request);
    }

    @Test
    void updateMobileTabBarPreferenceRequest_rejectsMissingValue() {
        UpdateMobileTabBarPreferenceRequest request = new UpdateMobileTabBarPreferenceRequest(null);

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isNotEmpty();
        }
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
        AuthController controller = controller();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        DeleteAccountRequest request = new DeleteAccountRequest("DELETE");

        controller.deleteAccount(user, request);

        verify(authService).requestAccountDeletion(userId, request);
    }

    @Test
    void exportAccountData_usesAccountExportRouteAndRequiresAuthentication() throws NoSuchMethodException {
        Method method = AuthController.class.getDeclaredMethod(
                "exportAccountData",
                AuthenticatedUser.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/account/export");
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void exportAccountData_rateLimitsAndReturnsJsonAttachment() throws Exception {
        AuthController controller = controller();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        DataExportResponse export = new DataExportResponse(
                new DataExportResponse.Meta(OffsetDateTime.parse("2026-06-23T10:00:00Z"), "1.1"),
                new DataExportResponse.Account(
                        "note@example.com",
                        "Note",
                        null,
                        "Note",
                        "notelib",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        OffsetDateTime.parse("2026-06-01T10:00:00Z")
                ),
                List.of(),
                List.of(),
                List.of(),
                new DataExportResponse.PracticeSummary(0, java.util.Map.of(), null)
        );
        when(accountDataExportService.exportForUser(userId)).thenReturn(export);

        ResponseEntity<DataExportResponse> response = controller.exportAccountData(user);

        verify(authRateLimitService).assertAllowed("data-export", userId.toString());
        verify(accountDataExportService).exportForUser(userId);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("notelib-export-")
                .contains(".json");
        assertThat(response.getBody()).isEqualTo(export);
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
        AuthController controller = controller();
        HttpServletRequest servletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        ReactivateAccountRequest request = new ReactivateAccountRequest("Note@Example.com", "password123", null, true);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        controller.reactivateAccount(request, servletRequest);

        verify(authRateLimitService).assertAllowed("reactivate", "127.0.0.1:note@example.com");
        verify(authService).reactivateAccount(request, "127.0.0.1", "JUnit");
    }

    private AuthController controller() {
        return new AuthController(authService, authRateLimitService, accountDataExportService);
    }
}
