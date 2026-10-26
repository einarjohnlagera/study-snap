package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.service.NotificationService.NotificationDelivery;

import java.util.UUID;

final class ChallengeQuizReadyNotifications {
    private ChallengeQuizReadyNotifications() {}

    static NotificationDelivery delivery(UUID userId, UUID noteId, UUID sessionId) {
        return new NotificationDelivery(userId, NotificationType.CHALLENGE_QUIZ_READY,
                sessionId.toString(), "Your quiz is ready!", "Pick up where you left off.",
                "Open your prepared quiz", "/notes/" + noteId + "/challenge-quiz?session=" + sessionId,
                null);
    }
}
