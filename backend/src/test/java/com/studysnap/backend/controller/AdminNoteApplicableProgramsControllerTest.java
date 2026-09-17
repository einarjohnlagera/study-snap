package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminNoteApplicableProgramsItemResponse;
import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteApplicableProgramsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AdminNoteApplicableProgramsControllerTest {
    @Mock
    private NoteApplicableProgramsService noteApplicableProgramsService;

    private UUID adminId;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(adminId, UserRole.ADMIN, true, 1);
        mockMvc = standaloneSetup(new AdminNoteApplicableProgramsController(noteApplicableProgramsService))
                .setCustomArgumentResolvers(authenticatedUserResolver(authenticatedUser))
                .build();
    }

    @Test
    void controllerIsAdminOnly() {
        PreAuthorize annotation = AdminNoteApplicableProgramsController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void omittedFilterReturnsTheFullOwnedSetAndSerializesPresentAndMissingDepths() throws Exception {
        UUID classifiedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(noteApplicableProgramsService.getAdminPage(0, 25, false, adminId)).thenReturn(
                new AdminNoteApplicableProgramsPageResponse(List.of(
                        item(classifiedId, "College Algebra", LearnerLevel.COLLEGE),
                        item(missingId, "Algebra Foundations", null)
                ), 0, 25, 2)
        );

        mockMvc.perform(get("/admin/notes/applicable-programs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].noteId").value(classifiedId.toString()))
                .andExpect(jsonPath("$.items[0].learnerLevel").value("COLLEGE"))
                .andExpect(jsonPath("$.items[1].noteId").value(missingId.toString()))
                .andExpect(jsonPath("$.items[1].learnerLevel").value((Object) null));

        verify(noteApplicableProgramsService).getAdminPage(0, 25, false, adminId);
    }

    @Test
    void missingDepthFilterBindsFromARealRequestAndReturnsOnlyNullDepthNotes() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(noteApplicableProgramsService.getAdminPage(1, 10, true, adminId)).thenReturn(
                new AdminNoteApplicableProgramsPageResponse(
                        List.of(item(missingId, "Algebra Foundations", null)),
                        1,
                        10,
                        1
                )
        );

        mockMvc.perform(get("/admin/notes/applicable-programs")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .queryParam("missingDepthOnly", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].noteId").value(missingId.toString()))
                .andExpect(jsonPath("$.items[0].learnerLevel").value((Object) null));

        verify(noteApplicableProgramsService).getAdminPage(1, 10, true, adminId);
    }

    private AdminNoteApplicableProgramsItemResponse item(UUID noteId, String title, LearnerLevel learnerLevel) {
        return new AdminNoteApplicableProgramsItemResponse(
                noteId,
                title,
                "Civil Engineering",
                "ENGINEERING_SCIENCES",
                learnerLevel,
                List.of()
        );
    }

    private HandlerMethodArgumentResolver authenticatedUserResolver(AuthenticatedUser authenticatedUser) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedUser.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return authenticatedUser;
            }
        };
    }
}
