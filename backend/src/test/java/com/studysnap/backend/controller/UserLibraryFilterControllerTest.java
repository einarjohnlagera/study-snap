package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateSavedLibraryFilterRequest;
import com.studysnap.backend.dto.SavedLibraryFilterResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.UserLibraryFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLibraryFilterControllerTest {

    private static final String PREAUTHORIZE_ROLES = "hasAnyRole('USER','ADMIN')";
    private static final String FILTER_ID = "e2163cd7-6bf7-45e9-8a01-14002a8fd8f6";
    private static final String FILTER_NAME = "PNLE Notes";
    private static final String COURSE_PROGRAM_KEY = "courseProgram";
    private static final String COURSE_PROGRAM = "PNLE";

    @Mock
    private UserLibraryFilterService service;

    @Test
    void endpoints_requireAuthenticatedUserRole() throws NoSuchMethodException {
        assertThat(UserLibraryFilterController.class
                .getMethod("list", AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(UserLibraryFilterController.class
                .getMethod("create", CreateSavedLibraryFilterRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(UserLibraryFilterController.class
                .getMethod("delete", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
    }

    @Test
    void list_returnsSavedFiltersForAuthenticatedUser() {
        UserLibraryFilterController controller = new UserLibraryFilterController(service);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 0);
        SavedLibraryFilterResponse response = savedFilterResponse();
        when(service.list(userId)).thenReturn(List.of(response));

        List<SavedLibraryFilterResponse> result = controller.list(user);

        assertThat(result).containsExactly(response);
        verify(service).list(userId);
    }

    @Test
    void create_returnsCreatedSavedFilter() {
        UserLibraryFilterController controller = new UserLibraryFilterController(service);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 0);
        CreateSavedLibraryFilterRequest request = new CreateSavedLibraryFilterRequest(
                FILTER_NAME,
                Map.of(COURSE_PROGRAM_KEY, COURSE_PROGRAM)
        );
        SavedLibraryFilterResponse response = savedFilterResponse();
        when(service.create(userId, request)).thenReturn(response);

        ResponseEntity<SavedLibraryFilterResponse> result = controller.create(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).create(userId, request);
    }

    @Test
    void delete_returnsNoContent() {
        UserLibraryFilterController controller = new UserLibraryFilterController(service);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 0);

        ResponseEntity<Void> result = controller.delete(FILTER_ID, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(UUID.fromString(FILTER_ID), userId);
    }

    private static SavedLibraryFilterResponse savedFilterResponse() {
        return new SavedLibraryFilterResponse(
                UUID.fromString(FILTER_ID),
                FILTER_NAME,
                Map.of(COURSE_PROGRAM_KEY, COURSE_PROGRAM),
                Instant.parse("2026-03-24T00:00:00Z")
        );
    }
}
