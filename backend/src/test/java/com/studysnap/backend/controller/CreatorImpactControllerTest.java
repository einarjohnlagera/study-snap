package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreatorImpactResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CreatorImpactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
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
class CreatorImpactControllerTest {
    @Mock
    private CreatorImpactService creatorImpactService;

    private CreatorImpactController creatorImpactController;

    @BeforeEach
    void setUp() {
        creatorImpactController = new CreatorImpactController(creatorImpactService);
    }

    @Test
    void getMine_usesOnlyTheAuthenticatedCreatorsIdentity() {
        UUID creatorUserId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(creatorUserId, UserRole.USER, true, 1);
        CreatorImpactResponse expected = new CreatorImpactResponse(2, List.of());
        when(creatorImpactService.getMine(creatorUserId)).thenReturn(expected);

        CreatorImpactResponse response = creatorImpactController.getMine(authenticatedUser);

        assertThat(response).isEqualTo(expected);
        verify(creatorImpactService).getMine(creatorUserId);
    }

    @Test
    void getMine_endpointIgnoresAnyOtherUserIdentifier() throws Exception {
        UUID creatorUserId = UUID.randomUUID();
        UUID ignoredUserId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(creatorUserId, UserRole.USER, true, 1);
        CreatorImpactResponse expected = new CreatorImpactResponse(2, List.of());
        when(creatorImpactService.getMine(creatorUserId)).thenReturn(expected);
        MockMvc mockMvc = buildMockMvc(authenticatedUser);

        mockMvc.perform(get("/creator-impact/me").queryParam("userId", ignoredUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distinctLearnersHelped").value(2));

        verify(creatorImpactService).getMine(creatorUserId);
    }

    private MockMvc buildMockMvc(AuthenticatedUser routeUser) {
        return standaloneSetup(creatorImpactController)
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
}
