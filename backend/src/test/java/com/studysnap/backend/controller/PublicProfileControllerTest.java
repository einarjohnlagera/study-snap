package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.dto.PublicProfileSummaryResponse;
import com.studysnap.backend.dto.PublicProfileFocusResponse;
import com.studysnap.backend.dto.SubjectCount;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.exception.PublicProfileNotFoundException;
import com.studysnap.backend.exception.PublicProfilePrivateException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.PublicProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PublicProfileControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                        List.of("Nursing"),
                        null,
                        null,
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

    @Test
    void subjectCount_serializesWithPublicProfileJsonShape() throws Exception {
        SubjectCount subjectCount = new SubjectCount("Anatomy", 2);

        String json = objectMapper.writeValueAsString(subjectCount);

        assertThat(json).isEqualTo("{\"subject\":\"Anatomy\",\"count\":2}");
    }

    @Test
    void summaryRoutesReturnOnlyThreeFieldsAndKeepPrivateAndMissingErrors() throws Exception {
        MockMvc mvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binder) {
                        return null;
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10))).build();
        String id = java.util.UUID.randomUUID().toString();
        when(publicProfileService.getSummaryByUserId(id, null))
                .thenReturn(new PublicProfileSummaryResponse("Creator", "Biology notes", 4));
        when(publicProfileService.getSummaryByUsername("creator", null))
                .thenReturn(new PublicProfileSummaryResponse("Creator", "Biology notes", 4));
        mvc.perform(get("/public/profile/{userId}/summary", id).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Creator"))
                .andExpect(jsonPath("$.bio").value("Biology notes"))
                .andExpect(jsonPath("$.publicNotesCount").value(4))
                .andExpect(jsonPath("$.publicNotes").doesNotExist());
        mvc.perform(get("/public/creator/{username}/summary", "creator").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicNotesCount").value(4));

        when(publicProfileService.getFocusByUserId(id, null)).thenReturn(new PublicProfileFocusResponse(
                List.of(new PublicProfileFocusResponse.LabelCount("Math", 300)),
                List.of(new PublicProfileFocusResponse.LabelCount("Math", 300))));
        mvc.perform(get("/public/profile/{userId}/learning-focus", id).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[0].count").value(300));

        when(publicProfileService.getSummaryByUserId("private", null)).thenThrow(new PublicProfilePrivateException());
        when(publicProfileService.getSummaryByUsername("missing", null)).thenThrow(new PublicProfileNotFoundException());
        mvc.perform(get("/public/profile/private/summary").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PUBLIC_PROFILE_PRIVATE"));
        mvc.perform(get("/public/creator/missing/summary").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PUBLIC_PROFILE_NOT_FOUND"));
    }
}
