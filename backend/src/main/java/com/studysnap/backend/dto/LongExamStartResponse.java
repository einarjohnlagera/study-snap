package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record LongExamStartResponse(
        UUID sessionId,
        String status,
        List<QuizItem> quiz,
        int totalQuestions,
        String difficulty,
        boolean canResume,
        int timeLimitSeconds,
        long timerStartedAtEpochSeconds,
        List<LongExamSourceNoteRef> sourceNoteRefs,
        int usedThisMonth,
        int monthlyLimit,
        /*
         * Most sources this learner may combine, INCLUDING the primary note.
         *
         * ⚠️ Server-derived on purpose. It is floor(questionCount / MIN_QUESTIONS_PER_SOURCE), and
         * questionCount comes from the learner's LEVEL, not their selection — so the real ceiling is
         * 6 / 8 / 10 and a College learner fails at 9. The frontend must render this value rather
         * than re-deriving the level mapping, which is backend config.
         */
        int maxSourceNotes
) {
}
