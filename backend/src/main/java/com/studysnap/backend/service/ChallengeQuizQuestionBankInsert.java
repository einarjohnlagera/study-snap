package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

/** Serializes the QuizItem value before the native jsonb insert shared by both bank writers. */
final class ChallengeQuizQuestionBankInsert {
    // Hibernate's default SqlTypes.JSON writer for this entity. The real-PostgreSQL entity
    // round-trip pins its QuizItem field mapping, including optional fields and labelled choices.
    private static final JacksonJsonFormatMapper JSON = new JacksonJsonFormatMapper();

    private ChallengeQuizQuestionBankInsert() {}

    static int insert(ChallengeQuizQuestionBankRepository repository, ChallengeQuizQuestionBankEntity entry) {
        return repository.insertIfAbsent(entry.getId(), entry.getUserId(), entry.getStudyPackId(),
                entry.getGenerationStamp(), entry.getOriginSessionId(), entry.getQuestionKey(),
                JSON.toString(entry.getQuestion(), QuizItem.class), entry.getLearnerLevel(),
                entry.getLastKnownOutcome(), entry.getClaimedSessionId(), entry.getGeneratedAt());
    }
}
