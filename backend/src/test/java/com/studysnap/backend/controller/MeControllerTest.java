package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.MePlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private MePlanService mePlanService;

    private MeController meController;

    @BeforeEach
    void setUp() {
        meController = new MeController(mePlanService);
    }

    @Test
    void getPlan_returnsBackendDrivenPlanSummary() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MePlanResponse expected = new MePlanResponse(
                PlanType.FREE,
                new MePlanResponse.Limits(10, 5, 0, 20),
                new MePlanResponse.Usage(3, 2, 0, 5),
                new MePlanResponse.Remaining(7, 3, 0, 15),
                new MePlanResponse.Features(false, false, true, true)
        );
        when(mePlanService.getPlan(userId)).thenReturn(expected);

        MePlanResponse response = meController.getPlan(user);

        assertThat(response).isEqualTo(expected);
        verify(mePlanService).getPlan(userId);
    }
}
