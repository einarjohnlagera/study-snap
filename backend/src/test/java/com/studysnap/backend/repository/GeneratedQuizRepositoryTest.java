package com.studysnap.backend.repository;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GeneratedQuizRepositoryTest {

    @Autowired
    private GeneratedQuizRepository generatedQuizRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists generated_quizzes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    note_id uuid not null,
                    target_learner_level varchar(32),
                    questions json not null,
                    generated_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("delete from generated_quizzes");
    }

    @Test
    void findLatestTargetLearnerLevelByNoteId_returnsMostRecentNonNullValue() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        saveQuiz(ownerUserId, noteId, LearnerLevel.COLLEGE, OffsetDateTime.parse("2026-05-01T10:00:00Z"));
        saveQuiz(ownerUserId, noteId, null, OffsetDateTime.parse("2026-05-02T10:00:00Z"));
        saveQuiz(ownerUserId, noteId, LearnerLevel.JUNIOR_HIGH, OffsetDateTime.parse("2026-05-03T10:00:00Z"));

        assertThat(generatedQuizRepository.findLatestTargetLearnerLevelByNoteId(noteId))
                .contains(LearnerLevel.JUNIOR_HIGH);
    }

    private void saveQuiz(
            UUID ownerUserId,
            UUID noteId,
            LearnerLevel learnerLevel,
            OffsetDateTime generatedAt
    ) {
        GeneratedQuizEntity entity = new GeneratedQuizEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setNoteId(noteId);
        entity.setTargetLearnerLevel(learnerLevel);
        entity.setQuestions(List.of(new QuizItem(
                "Question?",
                List.of("A", "B", "C", "D"),
                0,
                "Concept",
                "Explanation"
        )));
        entity.setGeneratedAt(generatedAt);
        entity.setUpdatedAt(generatedAt);
        generatedQuizRepository.save(entity);
    }
}
