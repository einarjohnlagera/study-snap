package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SubmitCampaignFeedbackRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CampaignFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class CampaignFeedbackControllerTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private CampaignFeedbackService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, UserRole.USER, true, 0);
        mockMvc = standaloneSetup(new CampaignFeedbackController(service))
                .setControllerAdvice(new GlobalExceptionHandler(org.springframework.util.unit.DataSize.ofMegabytes(10)))
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
                        return user;
                    }
                })
                .build();
    }

    @Test
    void postCampaignFeedback_deserializesARealJsonRequest() throws Exception {
        String body = """
                {
                  "primaryBlockers": ["QUIZ_QUALITY"],
                  "quizIssues": ["ANSWERS_SEEM_INCORRECT"],
                  "planIssue": null,
                  "missingFeatureText": null,
                  "contentSubjectText": null,
                  "freeText": "Please improve explanations."
                }
                """;

        mockMvc.perform(post("/feedback/campaign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(service).submit(eq(USER_ID), any(SubmitCampaignFeedbackRequest.class));
    }

    @Test
    void postCampaignFeedback_unknownEnumNamesTheField() throws Exception {
        mockMvc.perform(post("/feedback/campaign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryBlockers\":[\"NOT_REAL\"],\"freeText\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Invalid value for primaryBlockers."));
    }
}
