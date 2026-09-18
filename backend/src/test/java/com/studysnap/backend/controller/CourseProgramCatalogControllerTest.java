package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.ProgramFamilyResponse;
import com.studysnap.backend.dto.UpdateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.UpdateProgramFamilyRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.CourseProgramNotFoundException;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.exception.UnknownProgramFamilyException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CourseProgramCatalogService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.util.unit.DataSize;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void updateEndpointIsAdminOnly() throws NoSuchMethodException {
        Method update = CourseProgramCatalogController.class.getMethod(
                "update",
                String.class,
                UpdateCourseProgramCatalogRequest.class
        );

        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void updateBindsARealJsonPatchAndReturnsTheFreshCatalogItem() throws Exception {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse updated = new CourseProgramCatalogItemResponse(
                programId,
                "Nursing",
                familyId,
                "Health Sciences",
                true
        );
        when(service.updateProgramFamilies(any(), any())).thenReturn(updated);

        mockMvc().perform(patch("/course-program-catalog/{id}", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[\"" + familyId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(programId.toString()))
                .andExpect(jsonPath("$.name").value("Nursing"))
                .andExpect(jsonPath("$.programFamilyId").value(familyId.toString()))
                .andExpect(jsonPath("$.programFamilyName").value("Health Sciences"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(service).updateProgramFamilies(
                programId,
                new UpdateCourseProgramCatalogRequest(List.of(familyId))
        );
    }

    @Test
    void updateBindsAnEmptyListAsAuthoritativeClear() throws Exception {
        UUID programId = UUID.randomUUID();
        CourseProgramCatalogItemResponse updated = new CourseProgramCatalogItemResponse(
                programId, "Nursing", null, null, true);
        when(service.updateProgramFamilies(any(), any())).thenReturn(updated);

        mockMvc().perform(patch("/course-program-catalog/{id}", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programFamilies").isEmpty())
                .andExpect(jsonPath("$.programFamilyId").doesNotExist());

        verify(service).updateProgramFamilies(programId, new UpdateCourseProgramCatalogRequest(List.of()));
    }

    @Test
    void updateBindsIsActiveFromARealJsonPatchAndReturnsTheInactiveItem() throws Exception {
        UUID programId = UUID.randomUUID();
        String url = "jdbc:h2:mem:course-program-controller-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("create table course_programs (id uuid primary key, name varchar(120) not null, is_active boolean not null default true)");
            statement.execute("create table program_families (id uuid primary key, name varchar(120) not null)");
            statement.execute("create table course_program_family (course_program_id uuid not null, program_family_id uuid not null)");
            statement.executeUpdate("insert into course_programs (id, name) values ('" + programId + "', 'Nursing')");
            CourseProgramCatalogRepository repository = new CourseProgramCatalogRepository(
                    new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            CourseProgramCatalogController controller = new CourseProgramCatalogController(
                    new CourseProgramCatalogService(repository));
            MockMvc databaseBackedMockMvc = standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                    .build();

            databaseBackedMockMvc.perform(patch("/course-program-catalog/{id}", programId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isActive\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));

            databaseBackedMockMvc.perform(get("/course-program-catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(programId.toString()))
                    .andExpect(jsonPath("$[0].isActive").value(false));
        }
    }

    @Test
    void updateMapsMalformedAndMissingProgramsToTheSameNotFoundResponse() throws Exception {
        UUID missingProgramId = UUID.randomUUID();
        when(service.updateProgramFamilies(org.mockito.ArgumentMatchers.eq(missingProgramId), any()))
                .thenThrow(new CourseProgramNotFoundException());

        mockMvc().perform(patch("/course-program-catalog/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_PROGRAM_NOT_FOUND"));
        mockMvc().perform(patch("/course-program-catalog/{id}", missingProgramId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_PROGRAM_NOT_FOUND"));
    }

    @Test
    void updateMapsAnUnknownFamilyToBadRequest() throws Exception {
        UUID programId = UUID.randomUUID();
        when(service.updateProgramFamilies(org.mockito.ArgumentMatchers.eq(programId), any()))
                .thenThrow(new UnknownProgramFamilyException());

        mockMvc().perform(patch("/course-program-catalog/{id}", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_PROGRAM_FAMILY"));
    }

    @Test
    void updateRejectsARealNonAdminRequestWithForbidden() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);

        securedMockMvc(user).perform(patch("/course-program-catalog/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programFamilyIds\":[]}"))
                .andExpect(status().isForbidden());

        verify(service, never()).updateProgramFamilies(any(), any());
    }

    @Test
    void programFamilyEndpointsAreAdminOnly() throws NoSuchMethodException {
        Method create = CourseProgramCatalogController.class.getMethod("createProgramFamily", CreateProgramFamilyRequest.class);
        Method list = CourseProgramCatalogController.class.getMethod("listProgramFamilies");
        Method update = CourseProgramCatalogController.class.getMethod(
                "updateProgramFamily", String.class, UpdateProgramFamilyRequest.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(list.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
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

    @Test
    void createProgramFamilyBindsInitialProgramIdsFromARealRequest() throws Exception {
        UUID familyId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(service.createProgramFamily(any())).thenReturn(new ProgramFamilyResponse(familyId, "Health Sciences"));

        mockMvc().perform(post("/course-program-catalog/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Health Sciences\",\"programIds\":[\"" + programId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId.toString()));

        verify(service).createProgramFamily(new CreateProgramFamilyRequest("Health Sciences", List.of(programId)));
    }

    @Test
    void updateProgramFamilyBindsNameAndMembersFromARealRequest() throws Exception {
        UUID familyId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(service.updateProgramFamily(any(), any()))
                .thenReturn(new ProgramFamilyResponse(familyId, "Built Environment"));

        mockMvc().perform(patch("/course-program-catalog/families/{id}", familyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Built Environment\",\"programIds\":[\"" + programId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId.toString()))
                .andExpect(jsonPath("$.name").value("Built Environment"));

        verify(service).updateProgramFamily(
                familyId, new UpdateProgramFamilyRequest("Built Environment", List.of(programId)));
    }

    @Test
    void updateProgramFamilyWithOmittedProgramIdsLeavesThemNull() throws Exception {
        UUID familyId = UUID.randomUUID();
        when(service.updateProgramFamily(any(), any()))
                .thenReturn(new ProgramFamilyResponse(familyId, "Health sciences"));

        mockMvc().perform(patch("/course-program-catalog/families/{id}", familyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Health sciences\"}"))
                .andExpect(status().isOk());

        verify(service).updateProgramFamily(
                familyId, new UpdateProgramFamilyRequest("Health sciences", null));
    }

    @Test
    void updateProgramFamilyRejectsARealNonAdminRequest() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);

        securedMockMvc(user).perform(patch("/course-program-catalog/families/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Health Sciences\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).updateProgramFamily(any(), any());
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
        return standaloneSetup(new CourseProgramCatalogController(service))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .build();
    }

    private MockMvc securedMockMvc(AuthenticatedUser routeUser) {
        ProxyFactory proxyFactory = new ProxyFactory(new CourseProgramCatalogController(service));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        CourseProgramCatalogController securedController =
                (CourseProgramCatalogController) proxyFactory.getProxy();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                routeUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + routeUser.role().name()))
        );
        Filter authenticationFilter = (request, response, chain) -> {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        ExceptionTranslationFilter exceptionTranslationFilter =
                new ExceptionTranslationFilter(new Http403ForbiddenEntryPoint());
        exceptionTranslationFilter.setAccessDeniedHandler(new AccessDeniedHandlerImpl());

        return standaloneSetup(securedController)
                .addFilters(authenticationFilter, exceptionTranslationFilter)
                .build();
    }
}
