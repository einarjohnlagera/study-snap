package com.studysnap.backend.controller;

import com.studysnap.backend.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminJobControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminJobController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void expireSubscription_callsServiceAndReturnsOk() {
        AdminJobController controller = new AdminJobController(subscriptionService);
        UUID subscriptionId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.expireSubscription(subscriptionId);

        verify(subscriptionService).expireSubscriptionAndDowngradeToFree(subscriptionId);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
