package com.studysnap.backend.service.event;

import java.util.UUID;

public record ChallengeQuizReadyEvent(UUID userId, UUID noteId, UUID sessionId) {}
