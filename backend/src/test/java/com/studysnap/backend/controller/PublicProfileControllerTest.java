package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.dto.NoteStatsResponse.SubjectCount;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.PublicProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProfileControllerTest {

    @Mock
    private PublicProfileService publicProfileService;

    private PublicProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicProfileController(publicProfileService);
    }

    @Test
    void getByUserId_delegatesToService() {
        PublicProfileResponse expected = new PublicProfileResponse(
                "Study Buddy",
                "studybuddy",
                "Biology notes and board-review practice.",
                "BOARD_EXAM_REVIEW",
                "Biology",
                "STUDENT",
                false,
                true,
                false,
                "user-1",
                1,
                3,
                2,
                9,
                0,
                List.of(new SubjectCount("Biology", 1)),
                1,
                List.of(new PublicProfileNoteResponse(
                        "note-1",
                        "Cell Structure",
                        "Biology",
                        "Biology",
                        List.of("cells"),
                        "Note preview",
                        "Summary preview",
                        3,
                        2,
                        9,
                        "cell-structure"
                ))
        );
        AuthenticatedUser viewer = new AuthenticatedUser(java.util.UUID.randomUUID(), UserRole.USER, true, 1);
        when(publicProfileService.getByUserId("user-1", viewer.userId())).thenReturn(expected);

        PublicProfileResponse response = controller.getByUserId("user-1", viewer);

        assertThat(response).isEqualTo(expected);
        verify(publicProfileService).getByUserId("user-1", viewer.userId());
    }

    @Test
    void getByUsername_delegatesToService() {
        PublicProfileResponse expected = new PublicProfileResponse(
                "Study Buddy",
                "studybuddy",
                null,
                null,
                null,
                null,
                false,
                true,
                false,
                "user-2",
                0,
                0,
                0,
                0,
                0,
                List.of(),
                0,
                List.of()
        );
        AuthenticatedUser viewer = new AuthenticatedUser(java.util.UUID.randomUUID(), UserRole.USER, true, 1);
        when(publicProfileService.getByUsername("studybuddy", viewer.userId())).thenReturn(expected);

        PublicProfileResponse response = controller.getByUsername("studybuddy", viewer);

        assertThat(response).isEqualTo(expected);
        verify(publicProfileService).getByUsername("studybuddy", viewer.userId());
    }
}
