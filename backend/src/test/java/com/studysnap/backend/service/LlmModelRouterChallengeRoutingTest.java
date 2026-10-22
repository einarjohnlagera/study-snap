package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.LlmRouting;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmModelRouterChallengeRoutingTest {
    @ParameterizedTest
    @CsvSource({
            "FREE, USER, free-route",
            "PLUS, USER, premium-route",
            "PRO, USER, premium-route",
            "FREE, ADMIN, premium-route",
            "PLUS, ADMIN, premium-route",
            "PRO, ADMIN, premium-route"
    })
    void learnerTieredRouteSelectsConfiguredModelWithoutFreeFallback(
            PlanType plan, UserRole role, String expectedModel) {
        UUID learner = UUID.randomUUID();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getSettings().setModelFree("free-route");
        properties.getSettings().setModelPremium("premium-route");
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        UserRepository users = mock(UserRepository.class);
        UserEntity user = mock(UserEntity.class);
        when(user.getRole()).thenReturn(role);
        when(users.findById(learner)).thenReturn(Optional.of(user));
        when(subscriptions.getPlanSnapshot(learner)).thenReturn(
                new SubscriptionService.PlanSnapshot(plan, false, null, null));
        LlmRouting routePassedByOrdinaryChallenge = LlmRouting.tiered(learner);
        assertThat(new LlmModelRouter(properties, subscriptions, users)
                .resolve(routePassedByOrdinaryChallenge).model()).isEqualTo(expectedModel);
    }
}
