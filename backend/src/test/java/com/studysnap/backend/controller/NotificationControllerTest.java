package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NotificationResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock
    private NotificationService notificationService;

    @Test
    void notificationEndpointsAcceptRealRequestsWithJsonContentType() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NotificationResponse notification = notification(notificationId);
        when(notificationService.listInbox(userId, 50)).thenReturn(List.of(notification));
        when(notificationService.countActionableUnread(userId)).thenReturn(2);
        when(notificationService.markRead(userId, notificationId)).thenReturn(notification);
        when(notificationService.dismiss(userId, notificationId)).thenReturn(notification);

        MockMvc mockMvc = buildMockMvc(user);

        mockMvc.perform(get("/notifications")
                        .param("limit", "50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].actionable").value(true));
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(post("/notifications/" + notificationId + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()));
        mockMvc.perform(post("/notifications/" + notificationId + "/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()));

        verify(notificationService).listInbox(userId, 50);
        verify(notificationService).countActionableUnread(userId);
        verify(notificationService).markRead(userId, notificationId);
        verify(notificationService).dismiss(userId, notificationId);
    }

    private MockMvc buildMockMvc(AuthenticatedUser routeUser) {
        return standaloneSetup(new NotificationController(notificationService))
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

    private NotificationResponse notification(UUID id) {
        return new NotificationResponse(
                id,
                "ACTION_REQUIRED",
                true,
                "Action needed",
                "Review this item.",
                "Open",
                "/dashboard",
                OffsetDateTime.parse("2026-09-07T12:00:00Z"),
                null,
                null
        );
    }
}
