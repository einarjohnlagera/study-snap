package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.PublicNoteLikeEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteLikeCountProjection;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private PublicNoteLikeRepository publicNoteLikeRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private GeneratedQuizRepository generatedQuizRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private ExamQuestionPoolService examQuestionPoolService;

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(
                noteRepository,
                analyticsEventRepository,
                publicNoteLikeRepository,
                studyPackRepository,
                generatedQuizRepository,
                userRepository,
                subscriptionService,
                featureGateService,
                analyticsService,
                contentModerationService,
                examQuestionPoolService
        );
        lenient().when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.findAllSubjectValues()).thenReturn(List.of());
        lenient().when(noteRepository.findCourseProgramValuesByOwnerUserId(any())).thenReturn(List.of());
        lenient().when(noteRepository.findCourseProgramValuesByVisibility(any())).thenReturn(List.of());
        lenient().when(generatedQuizRepository.findByNoteId(any())).thenReturn(Optional.empty());
        lenient().when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findById(any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UserEntity user = buildUser(userId, "user@example.com");
            return Optional.of(user);
        });
        lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);
        lenient().when(featureGateService.hasFeatureAccess(any(PlanType.class), eq(Feature.ADAPTIVE_QUIZ))).thenReturn(false);
        lenient().when(featureGateService.hasFeatureAccess(any(PlanType.class), eq(Feature.DIFFICULTY_SELECTION))).thenReturn(false);
    }

    @Test
    void create_createsDraftPrivateNote() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setCourseProgram("Computer Science");
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "  Intro to React  ",
                "  Web Dev  ",
                null,
                List.of("react", "frontend"),
                null,
                "grade_school",
                "  hooks and state  "
        );

        NoteResponse created = noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(saved.getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(saved.getVisibility()).isEqualTo(NoteVisibility.PRIVATE);
        assertThat(saved.getCourseProgram()).isEqualTo("Computer Science");
        assertThat(saved.getLearnerLevel()).isEqualTo(LearnerLevel.GRADE_SCHOOL);
        assertThat(saved.getContent()).isEqualTo("hooks and state");
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
        assertThat(saved.getCopiedFromUserId()).isNull();
        assertThat(saved.getCopiedFromPublic()).isFalse();

        assertThat(created.studyPackStatus()).isEqualTo("DRAFT");
        assertThat(created.courseProgram()).isEqualTo("Computer Science");
        assertThat(created.learnerLevel()).isEqualTo("GRADE_SCHOOL");
        assertThat(created.targetProfileType()).isEqualTo("STUDENT");
        assertThat(created.copiedFromUserId()).isNull();
        assertThat(created.copiedFromPublic()).isFalse();
        verify(analyticsService).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.NOTE_CREATED), eq(saved.getId()), any());
    }

    @Test
    void create_boardTakerAutoAssignsBoardTakerTargetProfileType() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = buildUser(ownerUserId, "board@example.com");
        owner.setProfileType(ProfileType.BOARD_EXAM);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Board note",
                "Subject",
                null,
                List.of(),
                null,
                "content"
        );

        noteService.create(request, ownerUserId);

        verify(noteRepository).save(argThat(note -> note.getTargetProfileType() == NoteTargetProfileType.BOARD_TAKER));
    }

    @Test
    void create_teacherRequiresExplicitTargetProfileType() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = buildUser(ownerUserId, "teacher@example.com");
        owner.setProfileType(ProfileType.TEACHER);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Teacher note",
                "Subject",
                null,
                List.of(),
                null,
                "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_TARGET_PROFILE_TYPE_REQUIRED");

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void getById_mapsTransientGenerationStatuses() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatingNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATING, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(generatingNote));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());

        NoteResponse generating = noteService.getById(noteId.toString(), ownerUserId);

        assertThat(generating.studyPackStatus()).isEqualTo("GENERATING");

        NoteEntity failedNote = buildNote(noteId, ownerUserId, NoteStatus.FAILED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(failedNote));

        NoteResponse failed = noteService.getById(noteId.toString(), ownerUserId);

        assertThat(failed.studyPackStatus()).isEqualTo("FAILED");
    }

    @Test
    void create_reusesCanonicalSubjectFormattingWhenEquivalentSubjectAlreadyExists() {
        UUID ownerUserId = UUID.randomUUID();
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Cell note",
                " biology-cell division ",
                null,
                List.of(),
                null,
                "cell notes"
        );

        noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Biology – Cell Division");
    }

    @Test
    void create_normalizesProfileDefaultCourseProgram() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setCourseProgram("Senior High-STEM");
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Kinematics",
                "Physics",
                null,
                List.of(),
                null,
                "motion"
        );

        noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseProgram()).isEqualTo("Senior High – STEM");
    }

    @Test
    void update_draftNote_updatesContentAndMetadata() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "old content");
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(draftNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));

        UpsertNoteRequest request = new UpsertNoteRequest("New title", "biology- cell division", "Pre-Med", List.of("cells"), null, "new content");
        NoteResponse updated = noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(draftNote.getTitle()).isEqualTo("New title");
        assertThat(draftNote.getSubject()).isEqualTo("Biology – Cell Division");
        assertThat(draftNote.getCourseProgram()).isEqualTo("Pre – Med");
        assertThat(draftNote.getContent()).isEqualTo("new content");
        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.courseProgram()).isEqualTo("Pre – Med");
        assertThat(updated.content()).isEqualTo("new content");
    }

    @Test
    void update_refreshesExamPoolsWhenLearnerLevelChanges() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity draftNote = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "old content");
        draftNote.setLearnerLevel(LearnerLevel.GRADE_SCHOOL);
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        StudyPackEntity linkedStudyPack = new StudyPackEntity();
        linkedStudyPack.setId(studyPackId);
        linkedStudyPack.setNoteId(noteId);
        linkedStudyPack.setSubject("Biology");

        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(draftNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(linkedStudyPack));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "New title",
                "Biology",
                "Pre-Med",
                List.of("cells"),
                null,
                "JUNIOR_HIGH",
                "old content"
        );
        noteService.update(noteId.toString(), request, ownerUserId);

        verify(examQuestionPoolService).refreshPool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM);
        verify(examQuestionPoolService).refreshPool(studyPackId, ExamQuestionPoolService.MODE_BOARD_EXAM);
    }

    @Test
    void update_generatedNote_rejectsContentChange() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "locked content");
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(generatedNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest("Title", "Subject", "Nursing", List.of("tag"), null, "edited content");


        String id = noteId.toString();
        assertThatThrownBy(() -> noteService.update(id, request, ownerUserId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_CONTENT_LOCKED");

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void copyOwnNote_createsDraftWithoutAttribution() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "source content");
        source.setTitle("Source title");
        source.setSubject("Math");
        source.setCourseProgram("Engineering");
        source.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        source.setTags(new String[]{"algebra"});
        source.setTargetProfileType(NoteTargetProfileType.STUDENT);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(saved.getSourceNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCourseProgram()).isEqualTo("Engineering");
        assertThat(saved.getLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
        assertThat(saved.getCopiedFromUserId()).isNull();
        assertThat(saved.getCopiedFromPublic()).isFalse();
        assertThat(copied.copiedFromUserId()).isNull();
        assertThat(copied.copiedFromPublic()).isFalse();
    }

    @Test
    void copyPublicNote_setsAttributionFields() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        source.setTitle("Public source");
        source.setSubject("History");
        source.setCourseProgram("Humanities");
        source.setTags(new String[]{"ww2"});
        source.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getCopiedFromNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCopiedFromUserId()).isEqualTo(sourceOwnerUserId);
        assertThat(saved.getCopiedFromTitle()).isEqualTo("Public source");
        assertThat(saved.getCourseProgram()).isEqualTo("Humanities");
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER);
        assertThat(saved.getCopiedFromPublic()).isTrue();
        assertThat(saved.getCopiedAt()).isNotNull();

        assertThat(copied.copiedFromNoteId()).isEqualTo(sourceNoteId.toString());
        assertThat(copied.copiedFromUserId()).isEqualTo(sourceOwnerUserId.toString());
        assertThat(copied.copiedFromTitle()).isEqualTo("Public source");
        assertThat(copied.copiedFromPublic()).isTrue();
        assertThat(copied.copiedAt()).isNotNull();
        verify(analyticsService).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), eq(sourceNoteId), any());
    }

    @Test
    void copyPublicNote_returnsExistingCopyInsteadOfCreatingDuplicate() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        UUID existingCopyId = UUID.randomUUID();

        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        source.setTitle("Public source");
        NoteEntity existingCopy = buildNote(existingCopyId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "copied content");
        existingCopy.setCopiedFromNoteId(sourceNoteId);
        existingCopy.setCopiedFromUserId(sourceOwnerUserId);
        existingCopy.setCopiedFromTitle("Public source");
        existingCopy.setCopiedFromPublic(Boolean.TRUE);
        StudyPackEntity existingStudyPack = new StudyPackEntity();
        existingStudyPack.setId(UUID.randomUUID());
        existingStudyPack.setNoteId(existingCopyId);
        existingStudyPack.setSummary("Existing summary");

        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(noteRepository.findByOwnerUserIdAndCopiedFromNoteIdAndCopiedFromPublicTrue(ownerUserId, sourceNoteId))
                .thenReturn(Optional.of(existingCopy));
        when(studyPackRepository.findByNoteId(existingCopyId)).thenReturn(Optional.of(existingStudyPack));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        assertThat(copied.id()).isEqualTo(existingCopyId.toString());
        assertThat(copied.summary()).isEqualTo("Existing summary");
        verify(noteRepository, never()).save(any(NoteEntity.class));
        verify(analyticsService, never()).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), eq(sourceNoteId), any());
    }

    @Test
    void copyNote_normalizesCourseProgramFormatting() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, UUID.randomUUID(), NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        source.setCourseProgram("Senior High-STEM");
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseProgram()).isEqualTo("Senior High – STEM");
    }

    @Test
    void listMineCoursePrograms_returnsNormalizedDedupedSuggestionsIncludingProfileDefault() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setCourseProgram("Senior High-STEM");
        when(noteRepository.findCourseProgramValuesByOwnerUserId(ownerUserId))
                .thenReturn(List.of("  nursing  ", "Nursing", "Senior High – STEM"));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        List<String> coursePrograms = noteService.listMineCoursePrograms(ownerUserId);

        assertThat(coursePrograms).containsExactly("Nursing", "Senior High – STEM");
    }

    @Test
    void deleteById_deletesLinkedStudyPackAndNote() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setNoteId(noteId);

        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)).thenReturn(Optional.of(studyPack));

        noteService.deleteById(noteId.toString(), ownerUserId);

        verify(studyPackRepository).delete(studyPack);
        verify(noteRepository).delete(note);
    }

    @Test
    void getPublicBySeoPath_returnsPublicNoteWithAuthorAttribution() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity publicNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        publicNote.setTitle("World War 1 Causes");
        publicNote.setSubject("History");
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setSummary("Summary");
        studyPack.setKeyConcepts(List.of("Alliance systems"));
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setDisplayName("historyhero");
        owner.setFirstName("History");
        owner.setEmail("history@example.com");

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(publicNote));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(studyPack));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        var response = noteService.getPublicBySeoPath("history", "world-war-1-causes", null);

        assertThat(response.id()).isEqualTo(noteId.toString());
        assertThat(response.ownerUserId()).isNull();
        assertThat(response.authorDisplayName()).isEqualTo("historyhero");
        assertThat(response.isOfficialAuthor()).isFalse();
        assertThat(response.isCurrentUser()).isFalse();
        assertThat(response.content()).isEqualTo("content");
        assertThat(response.summary()).isEqualTo("Summary");
        assertThat(response.keyConcepts()).containsExactly("Alliance systems");
    }

    @Test
    void getPublicBySeoPath_matchesStructuredSubjectsBySlug() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity publicNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        publicNote.setTitle("Mitosis Overview");
        publicNote.setSubject("Biology – Cell Division");

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(publicNote));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.empty());

        var response = noteService.getPublicBySeoPath("biology-cell-division", "mitosis-overview", null);

        assertThat(response.id()).isEqualTo(noteId.toString());
        assertThat(response.subject()).isEqualTo("Biology – Cell Division");
    }

    @Test
    void getPublicBySeoPath_rejectsMissingOrPrivateMatch() {
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        assertThatThrownBy(() -> noteService.getPublicBySeoPath("science", "cell-structure", null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_NOT_FOUND");
    }

    @Test
    void listPublic_includesViewerOwnPublicNotesAndMarksOfficialOwners() {
        UUID viewerUserId = UUID.randomUUID();
        UUID officialOwnerUserId = UUID.randomUUID();
        UUID viewerNoteId = UUID.randomUUID();
        UUID officialNoteId = UUID.randomUUID();

        NoteEntity viewerNote = buildNote(viewerNoteId, viewerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "viewer content");
        viewerNote.setTitle("My public note");
        viewerNote.setCourseProgram("Nursing");
        NoteEntity officialNote = buildNote(officialNoteId, officialOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "official content");
        officialNote.setTitle("Official note");
        officialNote.setCourseProgram("Chemistry");
        officialNote.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);
        StudyPackEntity officialStudyPack = new StudyPackEntity();
        officialStudyPack.setId(UUID.randomUUID());
        officialStudyPack.setNoteId(officialNoteId);
        officialStudyPack.setSummary("Official summary preview");

        UserEntity viewer = new UserEntity();
        viewer.setId(viewerUserId);
        viewer.setFirstName("Viewer");
        viewer.setEmail("viewer@example.com");
        viewer.setLearnerLevel(LearnerLevel.COLLEGE);
        UserEntity officialOwner = new UserEntity();
        officialOwner.setId(officialOwnerUserId);
        officialOwner.setFirstName("Einar");
        officialOwner.setEmail("einar.lagera@gmail.com");
        officialOwner.setLearnerLevel(LearnerLevel.PROFESSIONAL);

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(viewerNote, officialNote));
        when(studyPackRepository.findByNoteIdIn(List.of(viewerNoteId, officialNoteId)))
                .thenReturn(List.of(officialStudyPack));
        when(userRepository.findAllById(List.of(viewerUserId, officialOwnerUserId)))
                .thenReturn(List.of(viewer, officialOwner));

        var response = noteService.listPublic(viewerUserId, null, null, null, null, null, null);

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(NoteListItemResponse::id)
                .containsExactly(viewerNoteId.toString(), officialNoteId.toString());
        assertThat(response.getFirst().ownerUserId()).isNull();
        assertThat(response.getFirst().courseProgram()).isEqualTo("Nursing");
        assertThat(response.getFirst().learnerLevel()).isEqualTo("COLLEGE");
        assertThat(response.getFirst().targetProfileType()).isEqualTo("STUDENT");
        assertThat(response.getFirst().authorDisplayName()).isEqualTo("Viewer");
        assertThat(response.getFirst().contentPreview()).isEqualTo("viewer content");
        assertThat(response.getFirst().summaryPreview()).isEmpty();
        assertThat(response.getFirst().isOfficialAuthor()).isFalse();
        assertThat(response.getFirst().isCurrentUser()).isTrue();
        assertThat(response.getFirst().copyCount()).isZero();
        assertThat(response.getFirst().likeCount()).isZero();
        assertThat(response.getFirst().shareCount()).isZero();
        assertThat(response.getFirst().viewCount()).isZero();
        assertThat(response.getFirst().copiedFromNoteId()).isNull();
        assertThat(response.getFirst().copiedFromPublic()).isFalse();
        assertThat(response.getFirst().likedByCurrentUser()).isFalse();
        assertThat(response.get(1).ownerUserId()).isNull();
        assertThat(response.get(1).courseProgram()).isEqualTo("Chemistry");
        assertThat(response.get(1).learnerLevel()).isEqualTo("PROFESSIONAL");
        assertThat(response.get(1).targetProfileType()).isEqualTo("BOARD_TAKER");
        assertThat(response.get(1).authorDisplayName()).isEqualTo("NoteLib");
        assertThat(response.get(1).contentPreview()).isEqualTo("official content");
        assertThat(response.get(1).summaryPreview()).isEqualTo("Official summary preview");
        assertThat(response.get(1).isOfficialAuthor()).isTrue();
        assertThat(response.get(1).isCurrentUser()).isFalse();
        assertThat(response.get(1).copyCount()).isZero();
        assertThat(response.get(1).likeCount()).isZero();
        assertThat(response.get(1).shareCount()).isZero();
        assertThat(response.get(1).viewCount()).isZero();
        assertThat(response.get(1).likedByCurrentUser()).isFalse();
    }

    @Test
    void listPublic_includesLikeCountsAndViewerLikeState() {
        UUID viewerUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID likedNoteId = UUID.randomUUID();
        UUID plainNoteId = UUID.randomUUID();

        NoteEntity likedNote = buildNote(likedNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "liked content");
        likedNote.setTitle("Liked note");
        NoteEntity plainNote = buildNote(plainNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "plain content");
        plainNote.setTitle("Plain note");
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        PublicNoteLikeCountProjection likedNoteLikes = mockLikeCount(likedNoteId, 12L);

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(likedNote, plainNote));
        when(userRepository.findAllById(List.of(ownerUserId))).thenReturn(List.of(owner));
        when(publicNoteLikeRepository.countLikesByNoteIds(List.of(likedNoteId, plainNoteId)))
                .thenReturn(List.of(likedNoteLikes));
        when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(viewerUserId, List.of(likedNoteId, plainNoteId)))
                .thenReturn(List.of(likedNoteId));

        var response = noteService.listPublic(viewerUserId, null, null, null, null, null, null);

        assertThat(response).extracting(NoteListItemResponse::likeCount).containsExactly(12L, 0L);
        assertThat(response).extracting(NoteListItemResponse::likedByCurrentUser).containsExactly(true, false);
    }

    @Test
    void togglePublicNoteLike_createsLikeWhenMissing() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, UUID.randomUUID(), NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        PublicNoteLikeCountProjection noteLikes = mockLikeCount(noteId, 1L);

        when(noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(note));
        when(publicNoteLikeRepository.findByNoteIdAndUserId(noteId, userId)).thenReturn(Optional.empty());
        when(publicNoteLikeRepository.countLikesByNoteIds(List.of(noteId))).thenReturn(List.of(noteLikes));

        var response = noteService.togglePublicNoteLike(noteId.toString(), userId);

        ArgumentCaptor<PublicNoteLikeEntity> captor = ArgumentCaptor.forClass(PublicNoteLikeEntity.class);
        verify(publicNoteLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getNoteId()).isEqualTo(noteId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1L);
    }

    @Test
    void togglePublicNoteLike_removesExistingLikeWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, UUID.randomUUID(), NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        PublicNoteLikeEntity existingLike = new PublicNoteLikeEntity();
        existingLike.setId(UUID.randomUUID());
        existingLike.setNoteId(noteId);
        existingLike.setUserId(userId);

        when(noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)).thenReturn(Optional.of(note));
        when(publicNoteLikeRepository.findByNoteIdAndUserId(noteId, userId)).thenReturn(Optional.of(existingLike));
        when(publicNoteLikeRepository.countLikesByNoteIds(List.of(noteId))).thenReturn(List.of());

        var response = noteService.togglePublicNoteLike(noteId.toString(), userId);

        verify(publicNoteLikeRepository).delete(existingLike);
        verify(publicNoteLikeRepository, never()).save(any(PublicNoteLikeEntity.class));
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isZero();
    }

    @Test
    void listMineSubjects_returnsDistinctNormalizedSubjectsSortedAlphabetically() {
        UUID ownerUserId = UUID.randomUUID();
        when(noteRepository.findSubjectValuesByOwnerUserId(ownerUserId))
                .thenReturn(List.of(" Biology – Cell Division ", "biology-cell division", "anatomy", "", "  ", "Chemistry"));

        List<String> subjects = noteService.listMineSubjects(ownerUserId);

        assertThat(subjects).containsExactly("anatomy", "Biology – Cell Division", "Chemistry");
    }

    @Test
    void listPublicSubjects_returnsDistinctNormalizedSubjectsSortedAlphabetically() {
        when(noteRepository.findSubjectValuesByVisibility(NoteVisibility.PUBLIC))
                .thenReturn(List.of("Physics", "biology - cell division", "Biology – Cell Division", "History"));

        List<String> subjects = noteService.listPublicSubjects();

        assertThat(subjects).containsExactly("Biology – Cell Division", "History", "Physics");
    }

    // --- listPublic sort tests ---

    @Test
    void listPublic_withNullSort_returnsNotesInDefaultDbOrder() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity note1 = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content1");
        note1.setTitle("Note1");
        NoteEntity note2 = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content2");
        note2.setTitle("Note2");
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(note1, note2));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, null, null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Note1", "Note2");
    }

    @Test
    void listPublic_withSortFeatured_sortsByScoreDescThenNewestFirst() {
        UUID ownerId = UUID.randomUUID();
        UUID highScoreId = UUID.randomUUID();
        UUID lowScoreId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.now();

        NoteEntity highScore = buildNote(highScoreId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "high");
        highScore.setTitle("HighScore");
        highScore.setCreatedAt(base.minusDays(1));
        NoteEntity lowScore = buildNote(lowScoreId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "low");
        lowScore.setTitle("LowScore");
        lowScore.setCreatedAt(base.minusDays(2));

        UserEntity owner = buildUser(ownerId, "user@example.com");
        NoteCopyCountProjection highCopies = mockCopyCount(highScoreId, 10L);
        NoteCopyCountProjection lowCopies = mockCopyCount(lowScoreId, 1L);
        PublicNoteLikeCountProjection highLikes = mockLikeCount(highScoreId, 3L);
        PublicNoteLikeCountProjection lowLikes = mockLikeCount(lowScoreId, 0L);
        PublicNoteEventCountProjection highViews = mockEventCount(highScoreId, 5L);
        PublicNoteEventCountProjection lowViews = mockEventCount(lowScoreId, 1L);
        StudyPackEntity highScorePack = buildStudyPack(highScoreId, "High summary");
        StudyPackEntity lowScorePack = buildStudyPack(lowScoreId, "Low summary");

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(lowScore, highScore));
        when(studyPackRepository.findByNoteIdIn(any())).thenReturn(List.of(highScorePack, lowScorePack));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any()))
                .thenReturn(List.of(highCopies, lowCopies));
        when(publicNoteLikeRepository.countLikesByNoteIds(any()))
                .thenReturn(List.of(highLikes, lowLikes));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED), any()))
                .thenReturn(List.of(highViews, lowViews));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "featured", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("HighScore", "LowScore");
    }

    @Test
    void listPublic_withSortFeatured_tiebreakByNewestCreatedAt() {
        UUID ownerId = UUID.randomUUID();
        UUID olderNoteId = UUID.randomUUID();
        UUID newerNoteId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.now();

        NoteEntity olderNote = buildNote(olderNoteId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "older");
        olderNote.setTitle("OlderNote");
        olderNote.setCreatedAt(base.minusDays(3));
        NoteEntity newerNote = buildNote(newerNoteId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "newer");
        newerNote.setTitle("NewerNote");
        newerNote.setCreatedAt(base.minusDays(1));

        UserEntity owner = buildUser(ownerId, "user@example.com");
        NoteCopyCountProjection olderCopies = mockCopyCount(olderNoteId, 5L);
        NoteCopyCountProjection newerCopies = mockCopyCount(newerNoteId, 5L);
        PublicNoteLikeCountProjection olderLikes = mockLikeCount(olderNoteId, 0L);
        PublicNoteLikeCountProjection newerLikes = mockLikeCount(newerNoteId, 0L);
        PublicNoteEventCountProjection olderViews = mockEventCount(olderNoteId, 5L);
        PublicNoteEventCountProjection newerViews = mockEventCount(newerNoteId, 5L);
        StudyPackEntity olderPack = buildStudyPack(olderNoteId, "Older summary");
        StudyPackEntity newerPack = buildStudyPack(newerNoteId, "Newer summary");

        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(olderNote, newerNote));
        when(studyPackRepository.findByNoteIdIn(any())).thenReturn(List.of(olderPack, newerPack));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any()))
                .thenReturn(List.of(olderCopies, newerCopies));
        when(publicNoteLikeRepository.countLikesByNoteIds(any()))
                .thenReturn(List.of(olderLikes, newerLikes));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED), any()))
                .thenReturn(List.of(olderViews, newerViews));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "featured", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("NewerNote", "OlderNote");
    }

    @Test
    void listPublic_withSortFeatured_excludesNotesWithoutMeaningfulSummary() {
        UUID ownerId = UUID.randomUUID();
        UUID eligibleId = UUID.randomUUID();
        UUID noSummaryId = UUID.randomUUID();

        NoteEntity eligible = buildNote(eligibleId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "eligible");
        eligible.setTitle("Eligible");
        NoteEntity noSummary = buildNote(noSummaryId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "no-summary");
        noSummary.setTitle("NoSummary");

        UserEntity owner = buildUser(ownerId, "user@example.com");
        NoteCopyCountProjection eligibleCopies = mockCopyCount(eligibleId, 2L);
        NoteCopyCountProjection noSummaryCopies = mockCopyCount(noSummaryId, 20L);
        PublicNoteLikeCountProjection eligibleLikes = mockLikeCount(eligibleId, 0L);
        PublicNoteLikeCountProjection noSummaryLikes = mockLikeCount(noSummaryId, 10L);
        PublicNoteEventCountProjection eligibleViews = mockEventCount(eligibleId, 4L);
        PublicNoteEventCountProjection noSummaryViews = mockEventCount(noSummaryId, 50L);
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(eligible, noSummary));
        when(studyPackRepository.findByNoteIdIn(any())).thenReturn(List.of(
                buildStudyPack(eligibleId, "Eligible summary"),
                buildStudyPack(noSummaryId, "   ")
        ));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any()))
                .thenReturn(List.of(eligibleCopies, noSummaryCopies));
        when(publicNoteLikeRepository.countLikesByNoteIds(any()))
                .thenReturn(List.of(eligibleLikes, noSummaryLikes));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED), any()))
                .thenReturn(List.of(eligibleViews, noSummaryViews));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "featured", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Eligible");
    }

    @Test
    void listPublic_withSortPopular_sortsByCopyCountDesc() {
        UUID ownerId = UUID.randomUUID();
        UUID manyId = UUID.randomUUID();
        UUID fewId = UUID.randomUUID();

        NoteEntity many = buildNote(manyId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "many");
        many.setTitle("ManyCopies");
        NoteEntity few = buildNote(fewId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "few");
        few.setTitle("FewCopies");

        UserEntity owner = buildUser(ownerId, "user@example.com");
        NoteCopyCountProjection manyCopies = mockCopyCount(manyId, 50L);
        NoteCopyCountProjection fewCopies = mockCopyCount(fewId, 3L);
        PublicNoteLikeCountProjection manyLikes = mockLikeCount(manyId, 2L);
        PublicNoteLikeCountProjection fewLikes = mockLikeCount(fewId, 8L);
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(few, many));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any()))
                .thenReturn(List.of(manyCopies, fewCopies));
        when(publicNoteLikeRepository.countLikesByNoteIds(any()))
                .thenReturn(List.of(manyLikes, fewLikes));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "popular", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("ManyCopies", "FewCopies");
    }

    @Test
    void listPublic_withSortPopular_filtersOutNotesBelowThreshold() {
        UUID ownerId = UUID.randomUUID();
        UUID popularId = UUID.randomUUID();
        UUID belowThresholdId = UUID.randomUUID();

        NoteEntity popular = buildNote(popularId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "popular");
        popular.setTitle("Popular");
        NoteEntity belowThreshold = buildNote(belowThresholdId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "below");
        belowThreshold.setTitle("BelowThreshold");

        UserEntity owner = buildUser(ownerId, "user@example.com");
        NoteCopyCountProjection popularCopies = mockCopyCount(popularId, 3L);
        NoteCopyCountProjection belowCopies = mockCopyCount(belowThresholdId, 2L);
        PublicNoteLikeCountProjection popularLikes = mockLikeCount(popularId, 4L);
        PublicNoteLikeCountProjection belowLikes = mockLikeCount(belowThresholdId, 12L);
        PublicNoteEventCountProjection popularViews = mockEventCount(popularId, 5L);
        PublicNoteEventCountProjection belowViews = mockEventCount(belowThresholdId, 19L);
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(popular, belowThreshold));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any()))
                .thenReturn(List.of(popularCopies, belowCopies));
        when(publicNoteLikeRepository.countLikesByNoteIds(any()))
                .thenReturn(List.of(popularLikes, belowLikes));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED), any()))
                .thenReturn(List.of(popularViews, belowViews));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "popular", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Popular");
    }

    @Test
    void listPublic_withSortRecent_sortsByCreatedAtDesc() {
        UUID ownerId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        OffsetDateTime base = OffsetDateTime.now();

        NoteEntity old = buildNote(oldId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "old");
        old.setTitle("OldNote");
        old.setCreatedAt(base.minusDays(5));
        NoteEntity recent = buildNote(newId, ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "new");
        recent.setTitle("RecentNote");
        recent.setCreatedAt(base.minusDays(1));

        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(old, recent));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "recent", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("RecentNote", "OldNote");
    }

    @Test
    void listPublic_withUnknownSort_returnsDefaultOrder() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity note1 = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "c1");
        note1.setTitle("First");
        NoteEntity note2 = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "c2");
        note2.setTitle("Second");
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(note1, note2));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, "unknown_value", null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("First", "Second");
    }

    @Test
    void listPublic_withEmptyPublicNotes_returnsEmptyRegardlessOfSort() {
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of());

        assertThat(noteService.listPublic(null, null, "featured", null, null, null, null)).isEmpty();
        assertThat(noteService.listPublic(null, null, "popular", null, null, null, null)).isEmpty();
        assertThat(noteService.listPublic(null, null, "recent", null, null, null, null)).isEmpty();
    }

    @Test
    void listPublic_withSubjectFilter_returnsOnlyMatchingNotes() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity match = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "c1");
        match.setTitle("MatchNote");
        match.setSubject("integral calculus");
        NoteEntity noMatch = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "c2");
        noMatch.setTitle("NoMatchNote");
        noMatch.setSubject("Physics");
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(match, noMatch));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        // Case-insensitive match
        var result = noteService.listPublic(null, null, null, "Integral Calculus", null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("MatchNote");
    }

    @Test
    void listPublic_withSearchFilter_matchesTitleCaseInsensitively() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity match = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "Cinco de Mayo overview");
        match.setTitle("Cinco de Mayo");
        match.setSubject("History");
        NoteEntity noMatch = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "Ohm's law overview");
        noMatch.setTitle("Ohm's Law");
        noMatch.setSubject("Physics");
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(match, noMatch));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, "cinco", null, null, null, null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Cinco de Mayo");
    }

    @Test
    void listPublic_withTagFilter_matchesNormalizedTagSlug() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity match = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "history content");
        match.setTitle("Battle Notes");
        match.setTags(new String[]{"Battle of Puebla", "History"});
        NoteEntity noMatch = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "science content");
        noMatch.setTitle("Physics Notes");
        noMatch.setTags(new String[]{"Motion"});
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(match, noMatch));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, null, null, List.of("battle-of-puebla"), null, null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Battle Notes");
    }

    @Test
    void listPublic_withCourseProgramAndTagFilters_combinesFilters() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity match = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "renal concepts");
        match.setTitle("Renal Review");
        match.setCourseProgram("Nursing");
        match.setTags(new String[]{"Kidneys", "Anatomy"});
        NoteEntity wrongCourseProgram = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "renal concepts");
        wrongCourseProgram.setTitle("Biology Renal Review");
        wrongCourseProgram.setCourseProgram("Biology");
        wrongCourseProgram.setTags(new String[]{"Kidneys"});
        NoteEntity wrongTag = buildNote(UUID.randomUUID(), ownerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "renal concepts");
        wrongTag.setTitle("General Nursing");
        wrongTag.setCourseProgram("Nursing");
        wrongTag.setTags(new String[]{"Circulation"});
        UserEntity owner = buildUser(ownerId, "user@example.com");
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(List.of(match, wrongCourseProgram, wrongTag));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        var result = noteService.listPublic(null, null, null, null, List.of("kidneys"), "nursing", null);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Renal Review");
    }

    @Test
    void listPublic_withTargetProfileFilter_usesNoteTargetProfileType() {
        UUID viewerUserId = UUID.randomUUID();
        UUID studentOwnerId = UUID.randomUUID();
        UUID teacherOwnerId = UUID.randomUUID();

        NoteEntity studentTargetNote = buildNote(UUID.randomUUID(), teacherOwnerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "student target");
        studentTargetNote.setTitle("Student target");
        studentTargetNote.setTargetProfileType(NoteTargetProfileType.STUDENT);
        NoteEntity boardTargetNote = buildNote(UUID.randomUUID(), studentOwnerId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "board target");
        boardTargetNote.setTitle("Board target");
        boardTargetNote.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);

        UserEntity studentOwner = buildUser(studentOwnerId, "student@example.com");
        studentOwner.setProfileType(ProfileType.STUDENT);
        UserEntity teacherOwner = buildUser(teacherOwnerId, "teacher@example.com");
        teacherOwner.setProfileType(ProfileType.TEACHER);

        when(noteRepository.findByVisibilityAndTargetProfileTypeOrderByUpdatedAtDesc(NoteVisibility.PUBLIC, NoteTargetProfileType.BOARD_TAKER))
                .thenReturn(List.of(boardTargetNote));
        when(userRepository.findAllById(List.of(studentOwnerId))).thenReturn(List.of(studentOwner));

        var result = noteService.listPublic(viewerUserId, null, null, null, null, null, NoteTargetProfileType.BOARD_TAKER);

        assertThat(result).extracting(NoteListItemResponse::title).containsExactly("Board target");
        assertThat(result.getFirst().targetProfileType()).isEqualTo("BOARD_TAKER");
    }

    // --- sort test helpers ---

    private NoteCopyCountProjection mockCopyCount(UUID noteId, long count) {
        NoteCopyCountProjection proj = mock(NoteCopyCountProjection.class);
        when(proj.getNoteId()).thenReturn(noteId);
        when(proj.getCopyCount()).thenReturn(count);
        return proj;
    }

    private PublicNoteLikeCountProjection mockLikeCount(UUID noteId, long count) {
        PublicNoteLikeCountProjection proj = mock(PublicNoteLikeCountProjection.class);
        when(proj.getNoteId()).thenReturn(noteId);
        when(proj.getLikeCount()).thenReturn(count);
        return proj;
    }

    private PublicNoteEventCountProjection mockEventCount(UUID noteId, long count) {
        PublicNoteEventCountProjection proj = mock(PublicNoteEventCountProjection.class);
        when(proj.getNoteId()).thenReturn(noteId);
        when(proj.getTotalCount()).thenReturn(count);
        return proj;
    }

    private StudyPackEntity buildStudyPack(UUID noteId, String summary) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setSummary(summary);
        studyPack.setQuiz(List.of(new QuizItem("Question", List.of("A", "B"), 0, "Concept", "Explanation")));
        return studyPack;
    }

    private UserEntity buildUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setFirstName("Test");
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setProfileType(ProfileType.STUDENT);
        return user;
    }

    private NoteEntity buildNote(
            UUID noteId,
            UUID ownerUserId,
            NoteStatus status,
            NoteVisibility visibility,
            String content
    ) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Title");
        note.setSubject("Subject");
        note.setCourseProgram("Course");
        note.setTags(new String[]{"tag"});
        note.setContent(content);
        note.setStatus(status);
        note.setVisibility(visibility);
        note.setTargetProfileType(NoteTargetProfileType.STUDENT);
        note.setSourceNoteId(null);
        note.setCopiedFromNoteId(null);
        note.setCopiedFromUserId(null);
        note.setCopiedFromTitle(null);
        note.setCopiedFromPublic(Boolean.FALSE);
        note.setCopiedAt(null);
        note.setCreatedAt(OffsetDateTime.now().minusDays(1));
        note.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return note;
    }
}
