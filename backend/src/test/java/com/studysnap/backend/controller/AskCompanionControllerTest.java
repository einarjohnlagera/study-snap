package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AskCompanionQuestionRequest;
import com.studysnap.backend.dto.AskCompanionSessionResponse;
import com.studysnap.backend.entity.AskCompanionSessionStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AskCompanionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskCompanionControllerTest {
    private static final String AUTHORIZED_ROLES = "hasAnyRole('USER','ADMIN')";

    @Mock
    private AskCompanionService service;

    @Test
    void endpointsRequireAuthenticatedUserRole() throws NoSuchMethodException {
        assertThat(AskCompanionController.class
                .getMethod("startOrResume", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(AUTHORIZED_ROLES);
        assertThat(AskCompanionController.class
                .getMethod("getActive", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(AUTHORIZED_ROLES);
        assertThat(AskCompanionController.class
                .getMethod(
                        "askQuestion",
                        String.class,
                        AskCompanionQuestionRequest.class,
                        AuthenticatedUser.class
                )
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(AUTHORIZED_ROLES);
    }

    @Test
    void getActiveReturnsNoContentWhenNoConversationExists() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 0);
        when(service.getActive(collectionId, userId)).thenReturn(Optional.empty());
        AskCompanionController controller = new AskCompanionController(service);

        ResponseEntity<AskCompanionSessionResponse> response = controller.getActive(collectionId.toString(), user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).getActive(collectionId, userId);
    }

    @Test
    void askQuestionDelegatesOnlyWithTheAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 0);
        AskCompanionQuestionRequest request = new AskCompanionQuestionRequest("What should I review?");
        AskCompanionSessionResponse expected = new AskCompanionSessionResponse(
                sessionId,
                collectionId,
                AskCompanionSessionStatus.ACTIVE,
                1,
                6,
                5,
                List.of(),
                1,
                20,
                OffsetDateTime.parse("2026-08-01T00:00:00Z")
        );
        when(service.askQuestion(sessionId, userId, request)).thenReturn(expected);
        AskCompanionController controller = new AskCompanionController(service);

        AskCompanionSessionResponse response = controller.askQuestion(sessionId.toString(), request, user);

        assertThat(response).isEqualTo(expected);
        verify(service).askQuestion(sessionId, userId, request);
    }
}
