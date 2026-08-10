package com.studysnap.backend.controller;

import com.studysnap.backend.service.NoteApplicableProgramsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminNoteApplicableProgramsControllerTest {
    @Test
    void controllerIsAdminOnly() {
        PreAuthorize annotation = AdminNoteApplicableProgramsController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(new AdminNoteApplicableProgramsController(mock(NoteApplicableProgramsService.class))).isNotNull();
    }
}
