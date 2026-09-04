package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NoteCollectionItemRepositoryTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 8, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private NoteCollectionItemRepository itemRepository;
    @Autowired
    private NoteCollectionRepository collectionRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private QuickReviewSessionRepository sessionRepository;
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
                    domain_context varchar(64),
                    learner_level varchar(32),
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
                    updated_at timestamp with time zone not null,
                    generation_enqueued_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_collections (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title varchar(150) not null,
                    description text,
                    visibility varchar(16) not null,
                    course_program varchar(120),
                    learner_level varchar(50),
                    estimated_study_hours integer,
                    target_completion_date date,
                    companion json,
                    companion_structure_snapshot json,
                    source_plan_id uuid,
                    parent_collection_id uuid,
                    sibling_position integer,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists note_collection_items (
                    id uuid primary key,
                    collection_id uuid not null,
                    note_id uuid not null,
                    label varchar(120),
                    position integer not null,
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid,
                    note_id uuid,
                    source_collection_id uuid,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    verified_correct_answers integer,
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
        jdbcTemplate.execute("delete from note_collection_items");
        jdbcTemplate.execute("delete from quick_review_sessions");
        jdbcTemplate.execute("delete from note_collections");
        jdbcTemplate.execute("delete from notes");
    }

    @Test
    void containingCollections_areOwnerScopedAndOrderedByMostRecentUpdate() {
        UUID userId = UUID.randomUUID();
        UUID noteId = saveNote(userId, NoteVisibility.PRIVATE).getId();
        NoteCollectionEntity older = saveCollection(userId, NOW.minusDays(2).toInstant());
        NoteCollectionEntity newest = saveCollection(userId, NOW.minusHours(1).toInstant());
        NoteCollectionEntity otherOwner = saveCollection(UUID.randomUUID(), NOW.toInstant());
        saveItem(older.getId(), noteId, 0);
        saveItem(newest.getId(), noteId, 0);
        saveItem(otherOwner.getId(), noteId, 0);
        entityManager.flush();

        List<UUID> containingCollectionIds = itemRepository
                .findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(noteId, userId);

        assertThat(containingCollectionIds).containsExactly(newest.getId(), older.getId());
    }

    @Test
    void readableCandidates_keepPositionOrderAndExcludeOnlyTheCompletedNote() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, NOW.toInstant());
        NoteEntity practiced = saveNote(userId, NoteVisibility.PRIVATE);
        NoteEntity justCompleted = saveNote(userId, NoteVisibility.PRIVATE);
        NoteEntity firstUnpracticed = saveNote(userId, NoteVisibility.PRIVATE);
        NoteEntity laterUnpracticed = saveNote(userId, NoteVisibility.PRIVATE);
        saveItem(collection.getId(), practiced.getId(), 0);
        saveItem(collection.getId(), justCompleted.getId(), 1);
        saveItem(collection.getId(), firstUnpracticed.getId(), 2);
        saveItem(collection.getId(), laterUnpracticed.getId(), 3);
        saveCompletedSession(userId, practiced.getId());
        entityManager.flush();

        List<UUID> candidates = itemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collection.getId(),
                userId,
                justCompleted.getId()
        );

        // The already-practiced note is deliberately still returned: practice state has exactly one
        // definition and it lives in QuizSessionHistoryService, which also counts multi-note sessions
        // that no session.noteId predicate here could see. This query only orders and excludes.
        assertThat(candidates).containsExactly(
                practiced.getId(),
                firstUnpracticed.getId(),
                laterUnpracticed.getId()
        );
    }

    @Test
    void readableCandidates_skipMissingAndUnreadableNotes() {
        UUID userId = UUID.randomUUID();
        NoteCollectionEntity collection = saveCollection(userId, NOW.toInstant());
        NoteEntity completed = saveNote(userId, NoteVisibility.PRIVATE);
        NoteEntity unreadable = saveNote(UUID.randomUUID(), NoteVisibility.PRIVATE);
        NoteEntity publicNote = saveNote(UUID.randomUUID(), NoteVisibility.PUBLIC);
        saveItem(collection.getId(), completed.getId(), 0);
        saveItem(collection.getId(), UUID.randomUUID(), 1);
        saveItem(collection.getId(), unreadable.getId(), 2);
        saveItem(collection.getId(), publicNote.getId(), 3);
        entityManager.flush();

        List<UUID> candidates = itemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collection.getId(),
                userId,
                completed.getId()
        );

        assertThat(candidates).containsExactly(publicNote.getId());
    }

    private NoteEntity saveNote(UUID ownerUserId, NoteVisibility visibility) {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Note");
        note.setTags(new String[0]);
        note.setContent("Content");
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCreatedAt(NOW);
        note.setUpdatedAt(NOW);
        return noteRepository.save(note);
    }

    private NoteCollectionEntity saveCollection(UUID ownerUserId, Instant updatedAt) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(UUID.randomUUID());
        collection.setOwnerUserId(ownerUserId);
        collection.setTitle("Plan");
        collection.setVisibility(CollectionVisibility.PRIVATE);
        collection.setCreatedAt(updatedAt.minusSeconds(60));
        collection.setUpdatedAt(updatedAt);
        return collectionRepository.save(collection);
    }

    private void saveItem(UUID collectionId, UUID noteId, int position) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setPosition(position);
        item.setCreatedAt(NOW.toInstant());
        itemRepository.save(item);
    }

    private void saveCompletedSession(UUID userId, UUID noteId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCreatedAt(NOW.minusMinutes(5));
        session.setCompletedAt(NOW);
        sessionRepository.save(session);
    }
}
