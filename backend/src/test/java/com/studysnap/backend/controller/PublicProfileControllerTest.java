package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
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
                "STUDENT",
                false,
                1,
                3,
                List.of(new PublicProfileNoteResponse(
                        "note-1",
                        "Cell Structure",
                        "Biology",
                        List.of("cells"),
                        3,
                        "cell-structure"
                ))
        );
        when(publicProfileService.getByUserId("user-1")).thenReturn(expected);

        PublicProfileResponse response = controller.getByUserId("user-1");

        assertThat(response).isEqualTo(expected);
        verify(publicProfileService).getByUserId("user-1");
    }
}
