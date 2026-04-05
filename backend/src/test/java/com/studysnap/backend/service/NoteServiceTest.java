package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
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
import static org.mockito.Mockito.lenient;
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
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
    @Mock
    private AnalyticsService analyticsService;

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(
                noteRepository,
                analyticsEventRepository,
                studyPackRepository,
                userRepository,
                subscriptionService,
                featureGateService,
                analyticsService
        );
        lenient().when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.findAllSubjectValues()).thenReturn(List.of());
        lenient().when(noteRepository.findCourseProgramValuesByOwnerUserId(any())).thenReturn(List.of());
        lenient().when(noteRepository.findCourseProgramValuesByVisibility(any())).thenReturn(List.of());
        lenient().when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any())).thenReturn(List.of());
        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(), any())).thenReturn(List.of());
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
        assertThat(saved.getContent()).isEqualTo("hooks and state");
        assertThat(saved.getCopiedFromUserId()).isNull();
        assertThat(saved.getCopiedFromPublic()).isFalse();

        assertThat(created.studyPackStatus()).isEqualTo("DRAFT");
        assertThat(created.courseProgram()).isEqualTo("Computer Science");
        assertThat(created.copiedFromUserId()).isNull();
        assertThat(created.copiedFromPublic()).isFalse();
        verify(analyticsService).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.NOTE_CREATED), eq(saved.getId()), any());
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
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));

        UpsertNoteRequest request = new UpsertNoteRequest("New title", "biology- cell division", "Pre-Med", List.of("cells"), "new content");
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
    void update_generatedNote_rejectsContentChange() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "locked content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(generatedNote));

        UpsertNoteRequest request = new UpsertNoteRequest("Title", "Subject", "Nursing", List.of("tag"), "edited content");


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
        source.setTags(new String[]{"algebra"});
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(saved.getSourceNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCourseProgram()).isEqualTo("Engineering");
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
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getCopiedFromNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCopiedFromUserId()).isEqualTo(sourceOwnerUserId);
        assertThat(saved.getCopiedFromTitle()).isEqualTo("Public source");
        assertThat(saved.getCourseProgram()).isEqualTo("Humanities");
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
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId.toString());
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

        var response = noteService.listPublic(viewerUserId);

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(NoteListItemResponse::id)
                .containsExactly(viewerNoteId.toString(), officialNoteId.toString());
        assertThat(response.getFirst().ownerUserId()).isEqualTo(viewerUserId.toString());
        assertThat(response.getFirst().courseProgram()).isEqualTo("Nursing");
        assertThat(response.getFirst().learnerLevel()).isEqualTo("COLLEGE");
        assertThat(response.getFirst().authorDisplayName()).isEqualTo("Viewer");
        assertThat(response.getFirst().contentPreview()).isEqualTo("viewer content");
        assertThat(response.getFirst().summaryPreview()).isEmpty();
        assertThat(response.getFirst().isOfficialAuthor()).isFalse();
        assertThat(response.getFirst().isCurrentUser()).isTrue();
        assertThat(response.getFirst().copyCount()).isZero();
        assertThat(response.getFirst().shareCount()).isZero();
        assertThat(response.getFirst().viewCount()).isZero();
        assertThat(response.get(1).ownerUserId()).isEqualTo(officialOwnerUserId.toString());
        assertThat(response.get(1).courseProgram()).isEqualTo("Chemistry");
        assertThat(response.get(1).learnerLevel()).isEqualTo("PROFESSIONAL");
        assertThat(response.get(1).authorDisplayName()).isEqualTo("NoteLib");
        assertThat(response.get(1).contentPreview()).isEqualTo("official content");
        assertThat(response.get(1).summaryPreview()).isEqualTo("Official summary preview");
        assertThat(response.get(1).isOfficialAuthor()).isTrue();
        assertThat(response.get(1).isCurrentUser()).isFalse();
        assertThat(response.get(1).copyCount()).isZero();
        assertThat(response.get(1).shareCount()).isZero();
        assertThat(response.get(1).viewCount()).isZero();
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
