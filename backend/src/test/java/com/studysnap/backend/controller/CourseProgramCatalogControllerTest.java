package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.ProgramFamilyResponse;
import com.studysnap.backend.service.CourseProgramCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourseProgramCatalogControllerTest {

    private final CourseProgramCatalogService service = mock(CourseProgramCatalogService.class);

    @Test
    void createAndSimilarEndpointsAreAdminOnly() throws NoSuchMethodException {
        Method create = CourseProgramCatalogController.class.getMethod("create", CreateCourseProgramCatalogRequest.class);
        Method similar = CourseProgramCatalogController.class.getMethod("similar", String.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(similar.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(new CourseProgramCatalogController(mock(CourseProgramCatalogService.class))).isNotNull();
    }

    @Test
    void programFamilyEndpointsAreAdminOnly() throws NoSuchMethodException {
        Method create = CourseProgramCatalogController.class.getMethod("createProgramFamily", CreateProgramFamilyRequest.class);
        Method list = CourseProgramCatalogController.class.getMethod("listProgramFamilies");

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(list.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }

    /**
     * ⚠️ THE REAL-REQUEST GUARD, AND THE REASON IT EXISTS IS MEASURED, NOT THEORETICAL.
     *
     * <p>Every other test in this class reflects on annotations or calls the handler as a plain method.
     * That is exactly the shape that let {@code v0.119.0} ship a feature whose JSON POSTs carried no
     * {@code Content-Type}: Spring rejected every request with {@code HttpMediaTypeNotSupportedException}
     * BEFORE the controller was entered, while the whole suite stayed green. A direct method call cannot
     * fail that way, so it is not a substitute.
     *
     * <p>This issues a real request through MockMvc, so content negotiation, JSON binding into the
     * record, and the response shape are all actually executed.
     */
    @Test
    void createProgramFamilyBindsARealJsonPostAndReturnsTheCreatedFamily() throws Exception {
        UUID familyId = UUID.randomUUID();
        when(service.createProgramFamily(any())).thenReturn(new ProgramFamilyResponse(familyId, "Health Sciences"));

        mockMvc().perform(post("/course-program-catalog/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Health Sciences\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId.toString()))
                .andExpect(jsonPath("$.name").value("Health Sciences"));
    }

    /**
     * ⚠️ THE VALIDATION MUST REJECT BEFORE THE SERVICE IS REACHED. Asserting only the 400 would pass
     * under a controller that called the service first and happened to throw, so the never() is the
     * half that pins "no write was attempted".
     */
    @Test
    void createProgramFamilyRejectsABlankNameWithoutReachingTheService() throws Exception {
        mockMvc().perform(post("/course-program-catalog/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).createProgramFamily(any());
    }

    /**
     * The families READ is not optional garnish: a family is created with no members, and the admin
     * form derives its options from the catalog. Without this endpoint a newly created family would
     * disappear from the picker on refresh until a program was assigned to it.
     */
    @Test
    void listProgramFamiliesReturnsFamiliesThatHaveNoMembersYet() throws Exception {
        UUID emptyFamilyId = UUID.randomUUID();
        when(service.listProgramFamilies()).thenReturn(List.of(new ProgramFamilyResponse(emptyFamilyId, "Health Sciences")));

        mockMvc().perform(get("/course-program-catalog/families"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(emptyFamilyId.toString()))
                .andExpect(jsonPath("$[0].name").value("Health Sciences"));
    }

    private MockMvc mockMvc() {
        return standaloneSetup(new CourseProgramCatalogController(service)).build();
    }
}
