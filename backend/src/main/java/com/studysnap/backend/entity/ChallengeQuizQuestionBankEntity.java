package com.studysnap.backend.entity;

import com.studysnap.backend.dto.QuizItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "challenge_quiz_question_bank")
@Getter
@Setter
@NoArgsConstructor
public class ChallengeQuizQuestionBankEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "origin_session_id")
    private UUID originSessionId;

    @Column(name = "question_key", nullable = false, length = 512)
    private String questionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private QuizItem question;

    @Column(name = "learner_level", length = 50)
    private String learnerLevel;

    @Column(name = "last_known_outcome", nullable = false, length = 16)
    private String lastKnownOutcome;

    @Column(name = "claimed_session_id")
    private UUID claimedSessionId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;
}
