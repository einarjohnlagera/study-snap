package com.studysnap.backend.service;

import com.studysnap.backend.exception.InvalidUnsubscribeTokenException;
import com.studysnap.backend.security.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokenServiceTest {
    @Test
    void signAndVerify_roundTripsUserAndCategory() {
        UUID userId = UUID.randomUUID();
        UnsubscribeTokenService service = serviceWithSecret("0123456789abcdef0123456789abcdef");

        String token = service.sign(userId, UnsubscribeCategory.MARKETING);

        UnsubscribeTokenService.VerifiedUnsubscribeToken verified = service.verify(token);
        assertThat(verified.userId()).isEqualTo(userId);
        assertThat(verified.category()).isEqualTo(UnsubscribeCategory.MARKETING);
    }

    @Test
    void verify_rejectsTamperedToken() {
        UnsubscribeTokenService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        String token = service.sign(UUID.randomUUID(), UnsubscribeCategory.WEEKLY_SUMMARY);
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(InvalidUnsubscribeTokenException.class);
    }

    @Test
    void verify_rejectsWrongSecret() {
        UUID userId = UUID.randomUUID();
        String token = serviceWithSecret("0123456789abcdef0123456789abcdef")
                .sign(userId, UnsubscribeCategory.STUDY_REMINDERS);
        UnsubscribeTokenService wrongSecretService = serviceWithSecret("abcdef0123456789abcdef0123456789");

        assertThatThrownBy(() -> wrongSecretService.verify(token))
                .isInstanceOf(InvalidUnsubscribeTokenException.class);
    }

    private UnsubscribeTokenService serviceWithSecret(String secret) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(secret);
        return new UnsubscribeTokenService(properties);
    }
}
