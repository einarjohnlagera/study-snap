package com.studysnap.backend.repository;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.model.StudyPackProgressProjection;
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

/**
 * Covers the interface-based JPQL projection added for the v0.37.3 Study Plan read-path memory
 * optimization. Unit tests with mocked repositories cannot catch a broken alias/type mapping in
 * the {@code @Query} projection (e.g. jsonb-to-List or enum coercion) — only a real Hibernate
 * session can, which is what this proves.
 */
@SpringBootTest
@Transactional
class StudyPackRepositoryTest {

    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists study_packs (
                    id uuid primary key,
                    owner_user_id uuid,
                    note_id uuid,
                    anon_id varchar(128),
                    input_type varchar(32) not null,
                    title varchar(255) not null,
                    summary varchar(2000) not null,
                    subject varchar(64),
                    source_text varchar(20000),
                    key_concepts json not null,
                    quiz json not null,
                    ocr_confidence double precision,
                    model_tier varchar(32) not null,
                    model_used varchar(64) not null,
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    estimated_cost numeric(12,6),
                    status varchar(32) not null,
                    error_code varchar(64),
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    share_token varchar(128) unique,
                    tags varchar array not null
                )
                """);
        jdbcTemplate.execute("delete from study_packs");
    }

    @Test
    void findProgressViewsByNoteIdIn_projectsOnlyTheReadPathFieldsFromARealHibernateSession() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity saved = saveStudyPack(ownerUserId, noteId, "Biology", List.of("Cells", "DNA"));

        List<StudyPackProgressProjection> projections = studyPackRepository.findProgressViewsByNoteIdIn(List.of(noteId));

        assertThat(projections).hasSize(1);
        StudyPackProgressProjection projection = projections.get(0);
        assertThat(projection.getId()).isEqualTo(saved.getId());
        assertThat(projection.getNoteId()).isEqualTo(noteId);
        assertThat(projection.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(projection.getSubject()).isEqualTo("Biology");
        assertThat(projection.getKeyConcepts()).containsExactly("Cells", "DNA");
        assertThat(projection.getStatus()).isEqualTo(StudyPackStatus.DONE);
    }

    @Test
    void findProgressViewsByOwnerUserId_returnsOnlyThatOwnersStudyPacks() {
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        saveStudyPack(ownerUserId, UUID.randomUUID(), "Chemistry", List.of("Bonds"));
        saveStudyPack(otherOwnerUserId, UUID.randomUUID(), "Physics", List.of("Motion"));

        List<StudyPackProgressProjection> projections = studyPackRepository.findProgressViewsByOwnerUserId(ownerUserId);

        assertThat(projections).hasSize(1);
        assertThat(projections.get(0).getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(projections.get(0).getSubject()).isEqualTo("Chemistry");
    }

    private StudyPackEntity saveStudyPack(UUID ownerUserId, UUID noteId, String subject, List<String> keyConcepts) {
        StudyPackEntity entity = new StudyPackEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setNoteId(noteId);
        entity.setInputType(InputType.TEXT);
        entity.setTitle("Title");
        entity.setSummary("Summary");
        entity.setSubject(subject);
        entity.setSourceText("Source text");
        entity.setKeyConcepts(keyConcepts);
        entity.setQuiz(List.of(new QuizItem(
                "Question?",
                List.of("A", "B", "C", "D"),
                0,
                "Concept",
                "Explanation"
        )));
        entity.setModelTier(ModelTier.FREE);
        entity.setModelUsed("gpt-4.1-mini");
        entity.setStatus(StudyPackStatus.DONE);
        entity.setCreatedAt(OffsetDateTime.parse("2026-05-01T10:00:00Z"));
        entity.setUpdatedAt(OffsetDateTime.parse("2026-05-01T10:00:00Z"));
        entity.setTags(new String[0]);
        return studyPackRepository.save(entity);
    }
}
