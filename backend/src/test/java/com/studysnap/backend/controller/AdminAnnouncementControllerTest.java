package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AnnouncementPublishResponse;
import com.studysnap.backend.dto.AnnouncementResponse;
import com.studysnap.backend.dto.UpsertAnnouncementRequest;
import com.studysnap.backend.entity.AnnouncementStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AnnouncementNotEditableException;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.exception.InvalidAnnouncementRequestException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnnouncementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * ⚠️ EVERY ENDPOINT IS EXERCISED AS A REAL REQUEST, WITH {@code Content-Type: application/json} AND A
 * BODY. A direct controller-method call is NOT a substitute and passes under the defect by
 * construction: {@code v0.119.0} shipped a feature whose two JSON POSTs sent no content type, so
 * Spring rejected every request with {@code HttpMediaTypeNotSupportedException} BEFORE the controller
 * was entered, while 2,182 tests passed. Three POSTs and a PUT here are exactly that shape.
 */
@ExtendWith(MockitoExtension.class)
class AdminAnnouncementControllerTest {
    private static final String ADMIN_ROLE_GATE = "hasRole('ADMIN')";
    private static final String CREATE_BODY = """
            {
              "title": "Board Exam Mode is here",
              "body": "Practice with a full timed board exam.",
              "ctaLabel": "Try it",
              "ctaPath": "/dashboard?tab=exams",
              "audience": "EVERYONE",
              "audienceValue": null,
              "expiresAt": null
            }
            """;

    @Mock
    private AnnouncementService announcementService;

    @Test
    void everyEndpointAcceptsARealRequestWithJsonContentType() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        AnnouncementResponse draft = announcement(announcementId, AnnouncementStatus.DRAFT);
        AnnouncementResponse published = announcement(announcementId, AnnouncementStatus.PUBLISHED);
        AnnouncementResponse ended = announcement(announcementId, AnnouncementStatus.ENDED);

        when(announcementService.list()).thenReturn(List.of(draft));
        when(announcementService.create(any(), eq(adminUserId))).thenReturn(draft);
        when(announcementService.update(eq(announcementId), any())).thenReturn(draft);
        when(announcementService.publish(announcementId))
                .thenReturn(new AnnouncementPublishResponse(published, 12, 12));
        when(announcementService.end(announcementId)).thenReturn(ended);

        MockMvc mockMvc = buildMockMvc(adminUserId);

        mockMvc.perform(get("/admin/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(announcementId.toString()))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        mockMvc.perform(post("/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(announcementId.toString()));

        mockMvc.perform(put("/admin/announcements/" + announcementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(announcementId.toString()));

        mockMvc.perform(post("/admin/announcements/" + announcementId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.recipientCount").value(12))
                .andExpect(jsonPath("$.queued").value(12))
                .andExpect(jsonPath("$.delivered").doesNotExist())
                .andExpect(jsonPath("$.skipped").doesNotExist());

        mockMvc.perform(post("/admin/announcements/" + announcementId + "/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        verify(announcementService).list();
        verify(announcementService).publish(announcementId);
        verify(announcementService).end(announcementId);
    }

    /**
     * The request body must actually arrive as typed fields, not merely be accepted. A create that
     * dropped {@code ctaPath} on the floor would still return 200 above.
     */
    @Test
    void createBindsEveryFieldFromTheJsonBody() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        AnnouncementResponse draft = announcement(UUID.randomUUID(), AnnouncementStatus.DRAFT);
        when(announcementService.create(any(), eq(adminUserId))).thenReturn(draft);

        buildMockMvc(adminUserId).perform(post("/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<UpsertAnnouncementRequest> captor = ArgumentCaptor.forClass(UpsertAnnouncementRequest.class);
        verify(announcementService).create(captor.capture(), eq(adminUserId));
        assertThat(captor.getValue().title()).isEqualTo("Board Exam Mode is here");
        assertThat(captor.getValue().body()).isEqualTo("Practice with a full timed board exam.");
        assertThat(captor.getValue().ctaLabel()).isEqualTo("Try it");
        assertThat(captor.getValue().ctaPath()).isEqualTo("/dashboard?tab=exams");
        assertThat(captor.getValue().audience()).isEqualTo("EVERYONE");
    }

    /**
     * ⚠️ AN EDIT AFTER PUBLISH IS REFUSED, WITH A MESSAGE NAMING THE REMEDY — never a silent no-op, and
     * never a 200 that quietly changed nothing.
     */
    @Test
    void editAfterPublishIsRefusedOverHttpWithTheRemedyNamed() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        when(announcementService.update(eq(announcementId), any()))
                .thenThrow(new AnnouncementNotEditableException(AnnouncementStatus.PUBLISHED));

        buildMockMvc(adminUserId).perform(put("/admin/announcements/" + announcementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ANNOUNCEMENT_NOT_EDITABLE"))
                .andExpect(jsonPath("$.error.action").value("End this announcement and publish a replacement."));
    }

    @Test
    void anInvalidCtaPathIsRejectedOverHttpAndNamesTheField() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        when(announcementService.create(any(), eq(adminUserId)))
                .thenThrow(new InvalidAnnouncementRequestException("ctaPath", "must be a relative path."));

        buildMockMvc(adminUserId).perform(post("/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Phishy",
                                  "body": "Click here.",
                                  "ctaLabel": "Click",
                                  "ctaPath": "https://evil.example",
                                  "audience": "EVERYONE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ANNOUNCEMENT_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("ctaPath: must be a relative path."));
    }

    @Test
    void aBlankTitleFailsValidationBeforeReachingTheService() throws Exception {
        buildMockMvc(UUID.randomUUID()).perform(post("/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","body":"Something","audience":"EVERYONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * ⚠️ THIS IS A REFLECTIVE PROOF THAT NO HANDLER WEAKENS THE CLASS GATE — not a live 403 assertion.
     * {@code standaloneSetup} does not run the security filter chain, so a request-level assertion here
     * would prove nothing; what CAN be proven mechanically is that the {@code hasRole('ADMIN')} gate
     * covers every mapped method and that none overrides it with something weaker.
     */
    @Test
    void everyMappedMethodIsCoveredByTheAdminGateAndNoneOverridesIt() {
        PreAuthorize classGate = AdminAnnouncementController.class.getAnnotation(PreAuthorize.class);
        assertThat(classGate).isNotNull();
        assertThat(classGate.value()).isEqualTo(ADMIN_ROLE_GATE);

        List<Method> mappedMethods = Arrays.stream(AdminAnnouncementController.class.getDeclaredMethods())
                .filter(method -> Arrays.stream(method.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType().getAnnotation(RequestMapping.class) != null
                                || annotation.annotationType() == RequestMapping.class))
                .toList();

        assertThat(mappedMethods).hasSize(5);
        assertThat(mappedMethods)
                .allSatisfy(method -> assertThat(method.getAnnotation(PreAuthorize.class)).isNull());
    }

    private MockMvc buildMockMvc(UUID adminUserId) {
        AuthenticatedUser routeUser = new AuthenticatedUser(adminUserId, UserRole.ADMIN, true, 1);
        return standaloneSetup(new AdminAnnouncementController(announcementService))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
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
                        return routeUser;
                    }
                })
                .build();
    }

    private AnnouncementResponse announcement(UUID id, AnnouncementStatus status) {
        return new AnnouncementResponse(
                id,
                "Board Exam Mode is here",
                "Practice with a full timed board exam.",
                "Try it",
                "/dashboard?tab=exams",
                "EVERYONE",
                null,
                status.name(),
                status == AnnouncementStatus.DRAFT ? null : OffsetDateTime.parse("2026-09-07T12:00:00Z"),
                null,
                OffsetDateTime.parse("2026-09-07T11:00:00Z"),
                OffsetDateTime.parse("2026-09-07T12:00:00Z"),
                status == AnnouncementStatus.DRAFT,
                false
        );
    }
}
