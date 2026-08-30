package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.studysnap.backend.dto.DataExportResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.entity.LinkedLearnerProvisionalBirthYearEntity;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDataExportServiceTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-06-02T10:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;

    @Test
    void exportForUser_includesOwnedContentAndPracticeSummaryOnly() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID publicNoteId = UUID.randomUUID();
        UUID privateNoteId = UUID.randomUUID();
        UUID foreignNoteId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UserEntity user = user(userId);
        NoteEntity publicNote = note(publicNoteId, userId, "Public note", NoteVisibility.PUBLIC);
        NoteEntity privateNote = note(privateNoteId, userId, "Private note", NoteVisibility.PRIVATE);
        StudyPackEntity studyPack = studyPack(userId, publicNoteId);
        NoteCollectionEntity collection = collection(collectionId, userId);
        QuickReviewSessionEntity quickReview = completedSession(userId, QuickReviewSessionMode.QUICK_REVIEW, "2026-06-10T10:00:00Z");
        QuickReviewSessionEntity challenge = completedSession(userId, QuickReviewSessionMode.CHALLENGE, "2026-06-09T10:00:00Z");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(publicNote, privateNote));
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(noteCollectionRepository.findByOwnerUserId(userId)).thenReturn(List.of(collection));
        when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(collectionId)).thenReturn(List.of(
                collectionItem(collectionId, publicNoteId, 0),
                collectionItem(collectionId, foreignNoteId, 1),
                collectionItem(collectionId, privateNoteId, 2)
        ));
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(quickReview, challenge));

        DataExportResponse export = service().exportForUser(userId);

        verify(noteRepository).findByOwnerUserIdOrderByUpdatedAtDesc(userId);
        verify(studyPackRepository).findByOwnerUserId(userId);
        verify(noteCollectionRepository).findByOwnerUserId(userId);
        verify(quickReviewSessionRepository).findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        );
        // 1.2 adds Account.provisionalBirthYears. The version is pinned deliberately: this payload
        // is a compliance surface a person can download and keep, so a shape change is versioned.
        assertThat(export.meta().schemaVersion()).isEqualTo("1.2");
        assertThat(export.account().email()).isEqualTo("note@example.com");
        assertThat(export.account().birthYear()).isEqualTo(2001);
        assertThat(export.notes()).extracting(DataExportResponse.Note::id)
                .containsExactly(publicNoteId, privateNoteId);
        assertThat(export.notes()).extracting(DataExportResponse.Note::visibility)
                .containsExactly(NoteVisibility.PUBLIC, NoteVisibility.PRIVATE);
        assertThat(export.notes()).extracting(DataExportResponse.Note::domainContext)
                .containsExactly(DomainContext.GENERAL_EDUCATION, DomainContext.GENERAL_EDUCATION);
        assertThat(export.notes()).extracting(DataExportResponse.Note::learnerLevel)
                .containsExactly(LearnerLevel.COLLEGE, LearnerLevel.COLLEGE);
        assertThat(export.studyPacks()).hasSize(1);
        assertThat(export.studyPacks().getFirst().noteId()).isEqualTo(publicNoteId);
        assertThat(export.collections()).hasSize(1);
        assertThat(export.collections().getFirst().name()).isEqualTo("Study plan");
        assertThat(export.collections().getFirst().notes())
                .extracting(DataExportResponse.CollectionNoteReference::id)
                .containsExactly(publicNoteId, privateNoteId);
        assertThat(export.practiceSummary().totalCompletedSessions()).isEqualTo(2);
        assertThat(export.practiceSummary().completedSessionsByMode())
                .containsEntry(QuickReviewSessionMode.QUICK_REVIEW, 1L)
                .containsEntry(QuickReviewSessionMode.CHALLENGE, 1L);
        assertThat(export.practiceSummary().lastSessionCompletedAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-10T10:00:00Z"));

        String json = objectMapper().writeValueAsString(export);
        assertThat(json)
                .doesNotContain("passwordHash")
                .doesNotContain("tokenVersion")
                .doesNotContain("failedLoginAttempts")
                .doesNotContain("lockedUntil")
                .doesNotContain("payment")
                .doesNotContain(foreignNoteId.toString());
    }

    @Test
    void exportForUser_returnsEmptyArraysForEmptyAccount() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of());
        when(noteCollectionRepository.findByOwnerUserId(userId)).thenReturn(List.of());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());

        DataExportResponse export = service().exportForUser(userId);

        assertThat(export.notes()).isEmpty();
        assertThat(export.studyPacks()).isEmpty();
        assertThat(export.collections()).isEmpty();
        assertThat(export.practiceSummary().totalCompletedSessions()).isZero();
        assertThat(export.practiceSummary().completedSessionsByMode()).isEmpty();
        assertThat(export.practiceSummary().lastSessionCompletedAt()).isNull();
    }

    /**
     * ⚠️ A provisional declaration must appear in the export AND must stay separate from
     * {@code birthYear}. {@code users.birth_year} is account-global and write-once; a provisional
     * year is neither, and only becomes the account year if the link's creator confirms. Merging
     * them would make the one surface that exists to state what is held accurately assert an
     * account-global value that was never written.
     */
    @Test
    void exportForUser_reportsProvisionalBirthYearsSeparatelyFromTheAccountYear() {
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UserEntity user = user(userId);
        user.setBirthYear(null);
        LinkedLearnerProvisionalBirthYearEntity declaration = new LinkedLearnerProvisionalBirthYearEntity();
        declaration.setRelationshipId(relationshipId);
        declaration.setBirthYear(2011);
        declaration.setDeclaredAt(OffsetDateTime.parse("2026-08-29T10:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(provisionalBirthYearRepository.findAllDeclaredByLearner(userId))
                .thenReturn(List.of(declaration));

        DataExportResponse export = service().exportForUser(userId);

        assertThat(export.account().birthYear())
                .as("the account-global write-once column is still unwritten")
                .isNull();
        assertThat(export.account().provisionalBirthYears())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.relationshipId()).isEqualTo(relationshipId);
                    assertThat(row.birthYear()).isEqualTo(2011);
                    assertThat(row.declaredAt()).isEqualTo(OffsetDateTime.parse("2026-08-29T10:00:00Z"));
                });
    }

    private AccountDataExportService service() {
        return new AccountDataExportService(
                userRepository,
                noteRepository,
                studyPackRepository,
                noteCollectionRepository,
                noteCollectionItemRepository,
                quickReviewSessionRepository,
                provisionalBirthYearRepository
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private UserEntity user(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("note@example.com");
        user.setPasswordHash("secret");
        user.setFirstName("Note");
        user.setLastName("Taker");
        user.setDisplayName("Note Taker");
        user.setUsername("notetaker");
        user.setProfileType(ProfileType.STUDENT);
        user.setCourseProgram("Nursing");
        user.setStudyGoal("Pass PNLE");
        user.setFocusSubjects(new String[]{"Anatomy"});
        user.setBirthYear(2001);
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setThemePreference(ThemePreference.SYSTEM);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(4);
        user.setFailedLoginAttempts(2);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        user.setWeeklySummaryRemindersEnabled(false);
        user.setMarketingEmailsEnabled(false);
        user.setPublicProfileVisible(false);
        user.setCreatedAt(CREATED_AT);
        user.setUpdatedAt(UPDATED_AT);
        return user;
    }

    private NoteEntity note(UUID noteId, UUID userId, String title, NoteVisibility visibility) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setTitle(title);
        note.setSubject("Biology");
        note.setDomainContext(DomainContext.GENERAL_EDUCATION);
        note.setLearnerLevel(LearnerLevel.COLLEGE);
        note.setContent("Cell structure notes");
        note.setStatus(NoteStatus.GENERATED);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setCopiedFromTitle("Original note");
        note.setCreatedAt(CREATED_AT);
        note.setUpdatedAt(UPDATED_AT);
        note.setTags(new String[]{"cells"});
        return note;
    }

    private StudyPackEntity studyPack(UUID userId, UUID noteId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Biology pack");
        studyPack.setSummary("A summary");
        studyPack.setKeyConcepts(List.of("Cells"));
        studyPack.setQuiz(List.of(new QuizItem(
                "What is the cell membrane?",
                List.of("Barrier", "Nucleus"),
                0,
                "Cells",
                "It controls movement."
        )));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("test");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(CREATED_AT);
        studyPack.setUpdatedAt(UPDATED_AT);
        studyPack.setTags(new String[]{"cells"});
        return studyPack;
    }

    private NoteCollectionEntity collection(UUID collectionId, UUID userId) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(userId);
        collection.setTitle("Study plan");
        collection.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        collection.setUpdatedAt(Instant.parse("2026-06-02T10:00:00Z"));
        return collection;
    }

    private NoteCollectionItemEntity collectionItem(UUID collectionId, UUID noteId, int position) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setPosition(position);
        item.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        return item;
    }

    private QuickReviewSessionEntity completedSession(
            UUID userId,
            QuickReviewSessionMode mode,
            String completedAt
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(UUID.randomUUID());
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(mode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCreatedAt(CREATED_AT);
        session.setCompletedAt(OffsetDateTime.parse(completedAt));
        return session;
    }

}
