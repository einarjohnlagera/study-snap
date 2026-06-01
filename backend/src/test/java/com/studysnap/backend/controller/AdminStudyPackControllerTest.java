package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.service.AdminStudyPackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStudyPackControllerTest {

    @Mock
    private AdminStudyPackService adminStudyPackService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminStudyPackController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void regenerateSummaries_returnsQueuedAndSkippedCounts() {
        AdminStudyPackController controller = new AdminStudyPackController(adminStudyPackService);
        AdminRegenerateSummariesResponse expected = new AdminRegenerateSummariesResponse(3, 7);
        when(adminStudyPackService.regenerateOfficialSummaries()).thenReturn(expected);

        AdminRegenerateSummariesResponse response = controller.regenerateSummaries();

        assertThat(response).isEqualTo(expected);
        verify(adminStudyPackService).regenerateOfficialSummaries();
    }
}
