package com.studysnap.backend.service;

import com.studysnap.backend.entity.PremiumWaitlistEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.PremiumWaitlistRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumWaitlistServiceTest {

    @Mock
    private PremiumWaitlistRepository premiumWaitlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;

    private PremiumWaitlistService premiumWaitlistService;

    @BeforeEach
    void setUp() {
        premiumWaitlistService = new PremiumWaitlistService(
                premiumWaitlistRepository,
                userRepository,
                emailTemplateService,
                emailService
        );
    }

    @Test
    void joinWaitlist_savesNewEntryAndSendsConfirmationEmail() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "[email protected]", "Note");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(premiumWaitlistRepository.existsByUserId(userId)).thenReturn(false);
        when(emailTemplateService.render(eq("premium-waitlist-confirmation"), any(Map.class)))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("subject", "<p>body</p>", "body"));

        String message = premiumWaitlistService.joinWaitlist(userId);

        assertThat(message).isEqualTo("You're on the list! We'll notify you when Premium launches.");
        ArgumentCaptor<PremiumWaitlistEntity> captor = ArgumentCaptor.forClass(PremiumWaitlistEntity.class);
        verify(premiumWaitlistRepository).save(captor.capture());
        PremiumWaitlistEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("[email protected]");
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(emailService).sendEmail(any(EmailMessage.class));
    }

    @Test
    void joinWaitlist_isIdempotentForExistingUser() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "[email protected]", "Note");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(premiumWaitlistRepository.existsByUserId(userId)).thenReturn(true);

        String message = premiumWaitlistService.joinWaitlist(userId);

        assertThat(message).isEqualTo("You're on the list! We'll notify you when Premium launches.");
        verify(premiumWaitlistRepository, never()).save(any(PremiumWaitlistEntity.class));
        verify(emailTemplateService, never()).render(any(), any(Map.class));
        verify(emailService, never()).sendEmail(any(EmailMessage.class));
    }

    @Test
    void joinWaitlist_throwsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> premiumWaitlistService.joinWaitlist(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    private UserEntity buildUser(UUID userId, String email, String firstName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
