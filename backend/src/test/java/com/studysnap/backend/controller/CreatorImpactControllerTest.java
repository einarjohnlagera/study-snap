package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreatorImpactPageResponse;
import com.studysnap.backend.dto.CreatorImpactSummaryResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CreatorImpactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.Arrays;
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
class CreatorImpactControllerTest {
    @Mock
    private CreatorImpactService creatorImpactService;

    private CreatorImpactController creatorImpactController;
    private UUID creatorUserId;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        creatorImpactController = new CreatorImpactController(creatorImpactService);
        creatorUserId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(creatorUserId, UserRole.USER, true, 1);
        mockMvc = standaloneSetup(creatorImpactController)
                .setCustomArgumentResolvers(authenticatedUserResolver(authenticatedUser))
                .build();
    }

    @Test
    void paginatedImpactIsServedThroughARealRequest() throws Exception {
        CreatorImpactPageResponse expected = new CreatorImpactPageResponse(
                List.of(new CreatorImpactPageResponse.NoteImpact("note-1", "Biology", 2, 9, 4)),
                1,
                10,
                21,
                30
        );
        when(creatorImpactService.getMine(creatorUserId, true, 1, 10)).thenReturn(expected);

        mockMvc.perform(get("/creator-impact/me")
                        .queryParam("impacted", "true")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].noteId").value("note-1"))
                .andExpect(jsonPath("$.totalImpacted").value(21))
                .andExpect(jsonPath("$.totalZeroImpact").value(30))
                .andExpect(jsonPath("$.distinctLearnersHelped").doesNotExist());

        verify(creatorImpactService).getMine(creatorUserId, true, 1, 10);
    }

    @Test
    void summaryIsServedThroughARealRequest() throws Exception {
        when(creatorImpactService.getSummary(creatorUserId))
                .thenReturn(new CreatorImpactSummaryResponse(7, 12));

        mockMvc.perform(get("/creator-impact/me/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distinctLearnersHelped").value(7))
                .andExpect(jsonPath("$.publicNoteCount").value(12));

        verify(creatorImpactService).getSummary(creatorUserId);
    }

    @Test
    void missingImpactedParameterIsRejectedInsteadOfReachingAnUnboundedDefault() throws Exception {
        mockMvc.perform(get("/creator-impact/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void controllerMethodsExposeNoUserIdentifierInput() throws NoSuchMethodException {
        Method pageMethod = CreatorImpactController.class.getDeclaredMethod(
                "getMine",
                boolean.class,
                int.class,
                int.class,
                AuthenticatedUser.class
        );
        Method summaryMethod = CreatorImpactController.class.getDeclaredMethod(
                "getSummary",
                AuthenticatedUser.class
        );

        assertThat(Arrays.stream(pageMethod.getParameterTypes()).map(Class::getName).toList())
                .doesNotContain(UUID.class.getName(), String.class.getName());
        assertThat(Arrays.stream(summaryMethod.getParameterTypes()).map(Class::getName).toList())
                .containsExactly(AuthenticatedUser.class.getName());
        assertThat(Arrays.stream(pageMethod.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(java.util.Objects::nonNull)
                .map(RequestParam::name))
                .noneMatch("userId"::equals);
    }

    private HandlerMethodArgumentResolver authenticatedUserResolver(AuthenticatedUser routeUser) {
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
                return routeUser;
            }
        };
    }
}
