package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailUnsubscribeServiceTest {
    @Mock
    private UnsubscribeTokenService unsubscribeTokenService;
    @Mock
    private UserRepository userRepository;

    @Test
    void unsubscribe_disablesMarketingEmails() {
        UserEntity user = optedInUser();
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.MARKETING, user);

        EmailUnsubscribeService.UnsubscribeResult result = service().unsubscribe("token");

        assertThat(result.category()).isEqualTo(UnsubscribeCategory.MARKETING);
        assertThat(user.getMarketingEmailsEnabled()).isFalse();
        assertThat(user.getInactivityRemindersEnabled()).isTrue();
        assertThat(user.getWeakConceptRemindersEnabled()).isTrue();
        assertThat(user.getWeeklySummaryRemindersEnabled()).isTrue();
    }

    @Test
    void unsubscribe_disablesWeeklySummaryEmails() {
        UserEntity user = optedInUser();
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.WEEKLY_SUMMARY, user);

        service().unsubscribe("token");

        assertThat(user.getWeeklySummaryRemindersEnabled()).isFalse();
    }

    @Test
    void unsubscribe_disablesStudyReminders() {
        UserEntity user = optedInUser();
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.STUDY_REMINDERS, user);

        service().unsubscribe("token");

        assertThat(user.getInactivityRemindersEnabled()).isFalse();
    }

    @Test
    void unsubscribe_disablesWeakConceptEmails() {
        UserEntity user = optedInUser();
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.WEAK_CONCEPT, user);

        service().unsubscribe("token");

        assertThat(user.getWeakConceptRemindersEnabled()).isFalse();
    }

    @Test
    void unsubscribe_disablesDueConceptsDigestWithoutChangingStudyReminders() {
        UserEntity user = optedInUser();
        user.setDueConceptsDigestRemindersEnabled(true);
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.DUE_CONCEPTS_DIGEST, user);

        service().unsubscribe("token");

        assertThat(user.getDueConceptsDigestRemindersEnabled()).isFalse();
        assertThat(user.getInactivityRemindersEnabled()).isTrue();
    }

    @Test
    void unsubscribe_isIdempotentWhenAlreadyDisabled() {
        UserEntity user = optedInUser();
        user.setMarketingEmailsEnabled(false);
        stubVerifiedToken("token", user.getId(), UnsubscribeCategory.MARKETING, user);

        EmailUnsubscribeService.UnsubscribeResult result = service().unsubscribe("token");

        assertThat(result.category()).isEqualTo(UnsubscribeCategory.MARKETING);
        assertThat(user.getMarketingEmailsEnabled()).isFalse();
    }

    @Test
    void unsubscribe_unknownUserIsSafeNoOp() {
        UUID userId = UUID.randomUUID();
        when(unsubscribeTokenService.verify("token"))
                .thenReturn(new UnsubscribeTokenService.VerifiedUnsubscribeToken(userId, UnsubscribeCategory.MARKETING));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EmailUnsubscribeService.UnsubscribeResult result = service().unsubscribe("token");

        assertThat(result.category()).isEqualTo(UnsubscribeCategory.MARKETING);
        verify(userRepository).findById(userId);
    }

    private EmailUnsubscribeService service() {
        return new EmailUnsubscribeService(unsubscribeTokenService, userRepository);
    }

    private void stubVerifiedToken(String token, UUID userId, UnsubscribeCategory category, UserEntity user) {
        when(unsubscribeTokenService.verify(token))
                .thenReturn(new UnsubscribeTokenService.VerifiedUnsubscribeToken(userId, category));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private UserEntity optedInUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setMarketingEmailsEnabled(true);
        user.setWeeklySummaryRemindersEnabled(true);
        user.setInactivityRemindersEnabled(true);
        user.setWeakConceptRemindersEnabled(true);
        return user;
    }
}
