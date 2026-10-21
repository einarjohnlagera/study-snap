package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeQuizReadyNotificationsTest {
    @Test
    void exactSessionCtaPassesTheSameOriginValidator() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var delivery = ChallengeQuizReadyNotifications.delivery(userId, noteId, sessionId);

        assertThat(delivery.type()).isEqualTo(NotificationType.CHALLENGE_QUIZ_READY);
        assertThat(delivery.dedupDiscriminator()).isEqualTo(sessionId.toString());
        assertThat(delivery.ctaPath()).isEqualTo(
                "/notes/" + noteId + "/challenge-quiz?session=" + sessionId);
        assertThat(AnnouncementCtaPathValidator.validate(delivery.ctaPath()))
                .isEqualTo(delivery.ctaPath());
    }
}
