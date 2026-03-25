package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.PremiumWaitlistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumWaitlistControllerTest {

    @Mock
    private PremiumWaitlistService premiumWaitlistService;

    @Test
    void controller_requiresAuthenticatedUserRole() throws NoSuchMethodException {
        PreAuthorize annotation = PremiumWaitlistController.class.getMethod("joinWaitlist", AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('USER','ADMIN')");
    }

    @Test
    void joinWaitlist_returnsSuccessMessage() {
        PremiumWaitlistController controller = new PremiumWaitlistController(premiumWaitlistService);
        UUID userId = UUID.randomUUID();
        when(premiumWaitlistService.joinWaitlist(userId))
                .thenReturn("You're on the list! We'll notify you when Premium launches.");

        SimpleMessageResponse response = controller.joinWaitlist(new AuthenticatedUser(
                userId,
                UserRole.USER,
                true,
                0
        ));

        assertThat(response.message()).isEqualTo("You're on the list! We'll notify you when Premium launches.");
        verify(premiumWaitlistService).joinWaitlist(userId);
    }
}
