package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.dto.AdminRegenerationStatusResponse;
import com.studysnap.backend.service.AdminStudyPackService;
import com.studysnap.backend.service.RegenerationProgressTracker;
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
    @Mock
    private RegenerationProgressTracker progressTracker;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminStudyPackController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void regenerateSummaries_returnsQueuedAndSkippedCounts() {
        AdminStudyPackController controller = new AdminStudyPackController(adminStudyPackService, progressTracker);
        AdminRegenerateSummariesResponse expected = new AdminRegenerateSummariesResponse(3, 7);
        when(adminStudyPackService.regenerateOfficialSummaries()).thenReturn(expected);

        AdminRegenerateSummariesResponse response = controller.regenerateSummaries();

        assertThat(response).isEqualTo(expected);
        verify(adminStudyPackService).regenerateOfficialSummaries();
    }

    @Test
    void regenerationStatus_returnsProgressTrackerStatus() {
        AdminStudyPackController controller = new AdminStudyPackController(adminStudyPackService, progressTracker);
        AdminRegenerationStatusResponse expected = new AdminRegenerationStatusResponse(10, 4, 1, false);
        when(progressTracker.getStatus()).thenReturn(expected);

        AdminRegenerationStatusResponse response = controller.regenerationStatus();

        assertThat(response).isEqualTo(expected);
        verify(progressTracker).getStatus();
    }
}
