package com.studysnap.backend.repository;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
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
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
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
        SqlCaptureStatementInspector.clear();
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

    @Test
    void findListItemProjectionsByOwnerUserIdOrderByCreatedAtDescIdDesc_projectsLeanFieldsInOrder() {
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        OffsetDateTime newestCreatedAt = OffsetDateTime.parse("2026-05-03T10:00:00Z");
        OffsetDateTime tiedCreatedAt = OffsetDateTime.parse("2026-05-02T10:00:00Z");
        StudyPackEntity newest = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                ownerUserId,
                "Newest",
                "Newest summary",
                "Biology",
                new String[]{"cells"},
                newestCreatedAt
        );
        StudyPackEntity tieLow = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ownerUserId,
                "Tie low",
                "Tie low summary",
                "Chemistry",
                new String[]{"bonds"},
                tiedCreatedAt
        );
        StudyPackEntity tieHigh = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                ownerUserId,
                "Tie high",
                "Tie high summary",
                "Physics",
                new String[]{"motion"},
                tiedCreatedAt
        );
        saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                otherOwnerUserId,
                "Other owner",
                "Other summary",
                "History",
                new String[]{"hidden"},
                OffsetDateTime.parse("2026-05-04T10:00:00Z")
        );
        SqlCaptureStatementInspector.clear();

        List<StudyPackListItemProjection> projections =
                studyPackRepository.findListItemProjectionsByOwnerUserIdOrderByCreatedAtDescIdDesc(
                        ownerUserId,
                        PageRequest.of(0, 3)
                );

        assertThat(projections)
                .extracting(StudyPackListItemProjection::id)
                .containsExactly(newest.getId(), tieHigh.getId(), tieLow.getId());
        assertThat(projections.get(0).title()).isEqualTo("Newest");
        assertThat(projections.get(0).summary()).isEqualTo("Newest summary");
        assertThat(projections.get(0).subject()).isEqualTo("Biology");
        assertThat(projections.get(0).tags()).containsExactly("cells");
        assertThat(projections.get(0).createdAt()).isEqualTo(newestCreatedAt);
        assertStudyPackListProjectionSelectsAvoidLargeColumns();
    }

    @Test
    void findListItemProjectionsByOwnerUserIdAndCursor_preservesCursorPredicateAndOrder() {
        UUID ownerUserId = UUID.randomUUID();
        OffsetDateTime newestCreatedAt = OffsetDateTime.parse("2026-05-03T10:00:00Z");
        OffsetDateTime tiedCreatedAt = OffsetDateTime.parse("2026-05-02T10:00:00Z");
        StudyPackEntity newest = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                ownerUserId,
                "Newest",
                "Newest summary",
                "Biology",
                new String[]{"cells"},
                newestCreatedAt
        );
        StudyPackEntity tieLow = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ownerUserId,
                "Tie low",
                "Tie low summary",
                "Chemistry",
                new String[]{"bonds"},
                tiedCreatedAt
        );
        StudyPackEntity tieHigh = saveStudyPack(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                ownerUserId,
                "Tie high",
                "Tie high summary",
                "Physics",
                new String[]{"motion"},
                tiedCreatedAt
        );
        SqlCaptureStatementInspector.clear();

        List<StudyPackListItemProjection> projections = studyPackRepository.findListItemProjectionsByOwnerUserIdAndCursor(
                ownerUserId,
                newest.getCreatedAt(),
                newest.getId(),
                PageRequest.of(0, 3)
        );

        assertThat(projections)
                .extracting(StudyPackListItemProjection::id)
                .containsExactly(tieHigh.getId(), tieLow.getId());
        assertStudyPackListProjectionSelectsAvoidLargeColumns();
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

    private StudyPackEntity saveStudyPack(
            UUID id,
            UUID ownerUserId,
            String title,
            String summary,
            String subject,
            String[] tags,
            OffsetDateTime createdAt
    ) {
        StudyPackEntity entity = new StudyPackEntity();
        entity.setId(id);
        entity.setOwnerUserId(ownerUserId);
        entity.setNoteId(UUID.randomUUID());
        entity.setInputType(InputType.TEXT);
        entity.setTitle(title);
        entity.setSummary(summary);
        entity.setSubject(subject);
        entity.setSourceText("Large source text that must not be selected by the library list projection");
        entity.setKeyConcepts(List.of("Concept"));
        entity.setQuiz(List.of(new QuizItem(
                "Question that should not be selected?",
                List.of("A", "B", "C", "D"),
                0,
                "Concept",
                "Explanation"
        )));
        entity.setModelTier(ModelTier.FREE);
        entity.setModelUsed("gpt-4.1-mini");
        entity.setStatus(StudyPackStatus.DONE);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        entity.setTags(tags);
        return studyPackRepository.save(entity);
    }

    private void assertStudyPackListProjectionSelectsAvoidLargeColumns() {
        List<String> studyPackSelects = SqlCaptureStatementInspector.statements().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select") && sql.contains(" from study_packs "))
                .toList();
        assertThat(studyPackSelects).isNotEmpty();
        assertThat(studyPackSelects).allSatisfy(sql -> {
            assertThat(sql).doesNotContain("source_text");
            assertThat(sql).doesNotContain("quiz");
        });
    }
}
