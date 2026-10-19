package com.studysnap.backend.service.model;

import java.util.UUID;

public record LlmRouting(LlmRoute route, UUID tierUserId) {
    public static LlmRouting tiered(UUID userId) {
        return new LlmRouting(LlmRoute.TIERED, userId);
    }

    public static LlmRouting interactiveQuiz() {
        return new LlmRouting(LlmRoute.INTERACTIVE_QUIZ, null);
    }

    public static LlmRouting paidInteractive() {
        return new LlmRouting(LlmRoute.PAID_INTERACTIVE, null);
    }
}
