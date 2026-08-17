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

/**
 * Mirrors {@code SubjectControllerTest}. This controller had no unit coverage at all, which is worth
 * closing now that v0.83.2 permits its path anonymously — the scope gate enforced here is the only
 * thing separating a public facet read from a caller's own course/program list.
 */
@ExtendWith(MockitoExtension.class)
class CourseProgramControllerTest {

    @Mock
    private NoteService noteService;

    private CourseProgramController courseProgramController;

    @BeforeEach
    void setUp() {
        courseProgramController = new CourseProgramController(noteService);
    }

    @Test
    void listCoursePrograms_returnsMineCourseProgramsForAuthenticatedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listMineCoursePrograms(userId)).thenReturn(List.of("Nursing", "Accountancy"));

        List<String> response = courseProgramController.listCoursePrograms("mine", user);

        assertThat(response).containsExactly("Nursing", "Accountancy");
        verify(noteService).listMineCoursePrograms(userId);
    }

    @Test
    void listCoursePrograms_returnsPublicCourseProgramsForAnonymousUsers() {
        when(noteService.listPublicCoursePrograms()).thenReturn(List.of("Civil Engineering", "Nursing"));

        List<String> response = courseProgramController.listCoursePrograms("public", null);

        assertThat(response).containsExactly("Civil Engineering", "Nursing");
        verify(noteService).listPublicCoursePrograms();
    }

    @Test
    void listCoursePrograms_defaultsToPublicScopeForAnonymousUsers() {
        // The @RequestParam default is "public", so an omitted scope must not be treated as "mine".
        when(noteService.listPublicCoursePrograms()).thenReturn(List.of("Nursing"));

        assertThat(courseProgramController.listCoursePrograms("public", null)).containsExactly("Nursing");
    }

    @Test
    void listCoursePrograms_rejectsMineScopeWithoutAuthentication() {
        assertThatThrownBy(() -> courseProgramController.listCoursePrograms("mine", null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void listCoursePrograms_rejectsAnUnknownScope() {
        assertThatThrownBy(() -> courseProgramController.listCoursePrograms("everything", null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("INVALID_COURSE_PROGRAM_SCOPE");
    }
}
