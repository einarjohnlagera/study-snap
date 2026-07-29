package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NoteRepositoryCreatorImpactIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-28T08:00:00Z");

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists notes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title text,
                    subject varchar(64),
                    course_program varchar(120),
                    tags varchar array not null,
                    content text not null,
                    status varchar(16) not null,
                    visibility varchar(16) not null,
                    target_profile_type varchar(16) not null,
                    source_note_id uuid,
                    copied_from_note_id uuid,
                    copied_from_user_id uuid,
                    copied_from_title varchar(255),
                    copied_from_public boolean,
                    copied_at timestamp with time zone,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid not null,
                    note_id uuid not null,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    score_percentage numeric(5,2),
                    retry_count integer,
                    duration_seconds integer,
                    confidence_level varchar(16),
                    session_metadata json,
                    session_state json,
                    quota_exempt boolean not null default false,
                    model_used varchar(64),
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
        jdbcTemplate.execute("delete from notes");
    }

    @Test
    void impactQueriesRequireCompletedSessionsAndKeepCreatorAttributionDistinct() {
        UUID firstCreatorId = UUID.randomUUID();
        UUID secondCreatorId = UUID.randomUUID();
        UUID sharedLearnerId = UUID.randomUUID();
        UUID incompleteLearnerId = UUID.randomUUID();
        UUID otherCreatorLearnerId = UUID.randomUUID();
        NoteEntity firstSource = saveNote(firstCreatorId, "First source", NoteVisibility.PUBLIC, null, false);
        NoteEntity secondSource = saveNote(firstCreatorId, "Second source", NoteVisibility.PUBLIC, null, false);
        NoteEntity otherCreatorSource = saveNote(secondCreatorId, "Other source", NoteVisibility.PUBLIC, null, false);
        NoteEntity firstCompletedCopy = saveNote(
                sharedLearnerId,
                "First copy",
                NoteVisibility.PRIVATE,
                firstSource,
                true
        );
        NoteEntity secondCompletedCopy = saveNote(
                sharedLearnerId,
                "Second copy",
                NoteVisibility.PRIVATE,
                secondSource,
                true
        );
        NoteEntity incompleteCopy = saveNote(
                incompleteLearnerId,
                "Incomplete copy",
                NoteVisibility.PRIVATE,
                firstSource,
                true
        );
        NoteEntity otherCreatorCopy = saveNote(
                otherCreatorLearnerId,
                "Other creator copy",
                NoteVisibility.PRIVATE,
                otherCreatorSource,
                true
        );
        saveSession(sharedLearnerId, firstCompletedCopy.getId(), true);
        saveSession(sharedLearnerId, secondCompletedCopy.getId(), true);
        saveSession(incompleteLearnerId, incompleteCopy.getId(), false);
        saveSession(otherCreatorLearnerId, otherCreatorCopy.getId(), true);
        entityManager.flush();
        entityManager.clear();

        List<NoteLearnersHelpedProjection> perNote = noteRepository.countDistinctLearnersHelpedBySourceNoteIds(
                List.of(firstSource.getId(), secondSource.getId())
        );
        Map<UUID, Long> countsByNoteId = perNote.stream().collect(Collectors.toMap(
                NoteLearnersHelpedProjection::getNoteId,
                NoteLearnersHelpedProjection::getLearnerCount
        ));

        assertThat(countsByNoteId).containsExactlyInAnyOrderEntriesOf(Map.of(
                firstSource.getId(), 1L,
                secondSource.getId(), 1L
        ));
        assertThat(noteRepository.countDistinctLearnersHelpedByCreatorUserId(firstCreatorId)).isEqualTo(1);
        assertThat(noteRepository.countDistinctLearnersHelpedByCreatorUserId(secondCreatorId)).isEqualTo(1);
    }

    @Test
    void windowedCreatorImpactQueryExcludesOldSessionsAndIncludesRecentSessions() {
        UUID creatorId = UUID.randomUUID();
        UUID oldLearnerId = UUID.randomUUID();
        UUID recentLearnerId = UUID.randomUUID();
        NoteEntity source = saveNote(creatorId, "Windowed source", NoteVisibility.PUBLIC, null, false);
        NoteEntity oldCopy = saveNote(oldLearnerId, "Old copy", NoteVisibility.PRIVATE, source, true);
        NoteEntity recentCopy = saveNote(recentLearnerId, "Recent copy", NoteVisibility.PRIVATE, source, true);
        OffsetDateTime now = BASE_TIME.plusDays(40);
        OffsetDateTime since = now.minusDays(30);
        saveSession(oldLearnerId, oldCopy.getId(), since.minusMinutes(1));
        saveSession(recentLearnerId, recentCopy.getId(), since.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        long learnersHelped = noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(creatorId, since);

        assertThat(learnersHelped).isEqualTo(1);
    }

    @Test
    void windowedCreatorImpactQueryExcludesAnIncompleteSession() {
        UUID creatorId = UUID.randomUUID();
        UUID incompleteLearnerId = UUID.randomUUID();
        NoteEntity source = saveNote(creatorId, "Windowed source", NoteVisibility.PUBLIC, null, false);
        NoteEntity incompleteCopy = saveNote(incompleteLearnerId, "Incomplete copy", NoteVisibility.PRIVATE, source, true);
        OffsetDateTime now = BASE_TIME.plusDays(40);
        OffsetDateTime since = now.minusDays(30);
        saveSession(incompleteLearnerId, incompleteCopy.getId(), false);
        entityManager.flush();
        entityManager.clear();

        long learnersHelped = noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(creatorId, since);

        assertThat(learnersHelped).isZero();
    }

    private NoteEntity saveNote(
            UUID ownerUserId,
            String title,
            NoteVisibility visibility,
            NoteEntity source,
            boolean copiedFromPublic
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setTags(new String[0]);
        note.setContent("content");
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCopiedFromNoteId(source == null ? null : source.getId());
        note.setCopiedFromUserId(source == null ? null : source.getOwnerUserId());
        note.setCopiedFromPublic(copiedFromPublic);
        note.setCreatedAt(BASE_TIME);
        note.setUpdatedAt(BASE_TIME);
        return noteRepository.save(note);
    }

    private void saveSession(UUID learnerUserId, UUID noteId, boolean completed) {
        saveSession(learnerUserId, noteId, completed ? BASE_TIME.plusMinutes(5) : null);
    }

    private void saveSession(UUID learnerUserId, UUID noteId, OffsetDateTime completedAt) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(learnerUserId);
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(completedAt != null ? QuickReviewSessionStatus.COMPLETED : QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(5);
        session.setRetryCount(0);
        session.setCreatedAt(completedAt == null ? BASE_TIME : completedAt.minusMinutes(5));
        session.setCompletedAt(completedAt);
        quickReviewSessionRepository.save(session);
    }
}
