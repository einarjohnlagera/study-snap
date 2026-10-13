package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ExamGoalResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UserExamGoalHttpTest {
    @Test
    void addActivateAndRemoveBindRealHttpRequests() throws Exception {
        AuthService service = mock(AuthService.class);
        UUID userId = UUID.randomUUID();
        List<ExamGoalResponse> goals = List.of(new ExamGoalResponse("ale", true));
        when(service.addExamGoal(userId, "ale")).thenReturn(goals);
        when(service.activateExamGoal(userId, "ale")).thenReturn(goals);
        when(service.removeExamGoal(userId, "ale")).thenReturn(List.of());
        MockMvc mvc = mvc(service, userId);

        mvc.perform(post("/users/profile/exam-goals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"ale\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("ale"));
        mvc.perform(put("/users/profile/exam-goals/ale/active").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].isActive").value(true));
        mvc.perform(delete("/users/profile/exam-goals/ale").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        verify(service).addExamGoal(userId, "ale");
        verify(service).activateExamGoal(userId, "ale");
        verify(service).removeExamGoal(userId, "ale");
    }

    private MockMvc mvc(AuthService service, UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        return standaloneSetup(new UserProfileController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) {
                        return principal;
                    }
                }).build();
    }
}
