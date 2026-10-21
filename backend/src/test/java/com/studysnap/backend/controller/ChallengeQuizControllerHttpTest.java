package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.exception.ChallengePreparationInProgressException;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ChallengeQuizService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ChallengeQuizControllerHttpTest {
    @Test
    void startQuizWhileGeneratingReturnsPreparationInProgressCode() throws Exception {
        ChallengeQuizService service = mock(ChallengeQuizService.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(service.startQuiz(sessionId.toString(), userId)).thenThrow(new ChallengePreparationInProgressException());
        var mvc = standaloneSetup(new ChallengeQuizController(service))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return user;
                    }
                }).build();

        mvc.perform(post("/challenge-quiz/sessions/" + sessionId + "/start-quiz"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREPARATION_IN_PROGRESS"));
    }

    @Test
    void startQuizRoutesRealPostAndSerializesRunningResponse() throws Exception {
        ChallengeQuizService service = mock(ChallengeQuizService.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(service.startQuiz(sessionId.toString(), userId)).thenReturn(new ChallengeQuizStartResponse(
                sessionId.toString(), QuickReviewSessionStatus.IN_PROGRESS, UUID.randomUUID().toString(),
                "Pack", 1, 90, 1, 20, 0, 10, "challenge", "medium", List.of(), 0,
                Map.of("timerStartedAtEpochSeconds", 42), List.of(), 3, false));
        var mvc = standaloneSetup(new ChallengeQuizController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return user;
                    }
                }).build();

        mvc.perform(post("/challenge-quiz/sessions/" + sessionId + "/start-quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.preparedNotStarted").value(false))
                .andExpect(jsonPath("$.sessionState.timerStartedAtEpochSeconds").value(42));
        verify(service).startQuiz(sessionId.toString(), userId);
    }
}
