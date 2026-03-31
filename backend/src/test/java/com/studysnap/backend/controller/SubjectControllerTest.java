package com.studysnap.backend.controller;

import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @Mock
    private NoteService noteService;

    private SubjectController subjectController;

    @BeforeEach
    void setUp() {
        subjectController = new SubjectController(noteService);
    }

    @Test
    void listSubjects_returnsMineSubjectsForAuthenticatedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listMineSubjects(userId)).thenReturn(List.of("Biology", "Chemistry"));

        List<String> response = subjectController.listSubjects("mine", user);

        assertThat(response).containsExactly("Biology", "Chemistry");
        verify(noteService).listMineSubjects(userId);
    }

    @Test
    void listSubjects_returnsPublicSubjectsForAnonymousUsers() {
        when(noteService.listPublicSubjects()).thenReturn(List.of("Biology", "Physics"));

        List<String> response = subjectController.listSubjects("public", null);

        assertThat(response).containsExactly("Biology", "Physics");
        verify(noteService).listPublicSubjects();
    }

    @Test
    void listSubjects_rejectsMineScopeWithoutAuthentication() {
        assertThatThrownBy(() -> subjectController.listSubjects("mine", null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
