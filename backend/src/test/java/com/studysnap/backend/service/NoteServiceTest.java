package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.PublicNoteLikeEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.CourseProgramSelectionRequiredException;
import com.studysnap.backend.exception.CourseProgramTooLongException;
import com.studysnap.backend.exception.InvalidDomainContextException;
import com.studysnap.backend.exception.NoteGenerationInProgressException;
import com.studysnap.backend.exception.InvalidNoteLearnerLevelException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.SubjectTooLongException;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteShareRepository;
import com.studysnap.backend.repository.NoteStatusProjection;
import com.studysnap.backend.repository.PublicNoteLikeCountProjection;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    private static final String EXISTING_CREATOR_USERNAME = "einarjohn";
    private static final String ACCOUNTANCY_PROGRAM = "Accountancy";

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
    private NoteShareRepository noteShareRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuizSessionHistoryService quizSessionHistoryService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private OnboardingGuardService onboardingGuardService;
    @Mock
    private OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;
    @Mock
    private com.studysnap.backend.repository.CourseProgramCatalogRepository courseProgramCatalogRepository;
    @Mock
    private StudyPackQuizMasteryService studyPackQuizMasteryService;
    @Mock
    private StudyPackGenerationContextResolver studyPackGenerationContextResolver;
    private NoteService noteService;
    private final Map<UUID, NoteEntity> noteFixtures = new HashMap<>();

    @BeforeEach
    void setUp() {
        noteFixtures.clear();
        noteService = new NoteService(
                noteRepository,
                noteShareRepository,
                analyticsEventRepository,
                publicNoteLikeRepository,
                studyPackRepository,
                generatedQuizRepository,
                userRepository,
                quizSessionHistoryService,
                subscriptionService,
                featureGateService,
                analyticsService,
                contentModerationService,
                onboardingGuardService,
                officialChallengeQuizTemplateService,
                noteCourseProgramRepository,
                courseProgramCatalogRepository,
                studyPackQuizMasteryService,
                studyPackGenerationContextResolver
        );
        lenient().when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.findAllSubjectValues()).thenReturn(List.of());
        lenient().when(noteRepository.findPublicLibraryListItemProjectionsByIdIn(any())).thenAnswer(invocation -> {
            List<UUID> noteIds = invocation.getArgument(0);
            return noteIds.stream()
                    .map(noteFixtures::get)
                    .filter(java.util.Objects::nonNull)
                    .map(this::buildListItemProjection)
                    .toList();
        });
        lenient().when(noteRepository.findCourseProgramValuesByOwnerUserId(any())).thenReturn(List.of());
        lenient().when(noteRepository.findCourseProgramValuesByVisibility(any())).thenReturn(List.of());
        lenient().when(noteCourseProgramRepository.findNamesByOwnerUserId(any())).thenReturn(List.of());
        lenient().when(noteCourseProgramRepository.findNamesByVisibility(any())).thenReturn(List.of());
        lenient().when(generatedQuizRepository.findByNoteId(any())).thenReturn(Optional.empty());
        lenient().when(generatedQuizRepository.findLatestTargetLearnerLevelByNoteId(any())).thenReturn(Optional.empty());
        lenient().when(studyPackRepository.findByNoteId(any())).thenReturn(Optional.empty());
        lenient().when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.countCopiedPublicNotesBySourceNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.countLikesByNoteIds(any())).thenReturn(List.of());
        lenient().when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(any(), any())).thenReturn(List.of());
        lenient().when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(any(), any())).thenReturn(List.of());
        lenient().when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(any(), any())).thenReturn(java.util.Map.of());
        lenient().when(userRepository.findById(any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UserEntity user = buildUser(userId, "user@example.com");
            return Optional.of(user);
        });
        lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);
        lenient().when(featureGateService.hasFeatureAccess(any(PlanType.class), eq(Feature.ADAPTIVE_QUIZ))).thenReturn(false);
        lenient().when(studyPackQuizMasteryService.resolve(any(UUID.class), any()))
                .thenReturn(com.studysnap.backend.service.model.StudyPackQuizMastery.notMastered());
        lenient().when(studyPackGenerationContextResolver.resolve(any(UUID.class), any(NoteEntity.class)))
                .thenReturn(authoringContext(null));
    }

    @Test
    void create_rejectsMissingProfileTypeBeforeSaving() {
        UUID ownerUserId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        org.mockito.Mockito.doThrow(exception).when(onboardingGuardService).assertProfileComplete(ownerUserId);
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title",
                "Subject",
                null,
                null,
                null,
                List.of(),
                "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isSameAs(exception);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void create_usesRequestCourseProgramWhenOwnerHasNone() {
        // Onboarding creates its first note before the profile course/program is persisted, so the
        // request carries the only value. resolveRequestedCourseProgram throws when both are absent,
        // which made onboarding a dead end for every new user (finding B0). Uses the canonical
        // constructor rather than the compatibility overload so this exercises the production shape.
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setCourseProgram(null);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Newton's Laws of Motion",
                null,
                List.of(),
                "AWS Certification",
                null,
                null,
                List.of(),
                "content"
        );

        NoteResponse created = noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseProgram()).isEqualTo("AWS Certification");
        assertThat(created.courseProgram()).isEqualTo("AWS Certification");
    }

    @Test
    void create_treatsAMidOnboardingAdminAsALearnerRatherThanACurator() {
        // Onboarding's own-note path posts to POST /notes with a free-text program and no catalog ids,
        // because onboarding has no catalog picker. An ADMIN mid-onboarding used to take the curator
        // branch and be rejected for missing courseProgramIds, so onboarding could not be completed.
        UUID ownerUserId = UUID.randomUUID();
        UserEntity admin = buildUser(ownerUserId, "admin@example.com");
        admin.setRole(UserRole.ADMIN);
        admin.setCourseProgram(null);
        admin.setOnboardingCompletedAt(null);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(admin));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Newton's Laws", null, List.of(), "Accountancy", null, null, List.of(), "content"
        );

        NoteResponse created = noteService.create(request, ownerUserId);

        assertThat(created.courseProgram()).isEqualTo("Accountancy");
        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void create_stillRequiresCatalogProgramsForAnOnboardedAdmin() {
        // Scope guard: the exemption is onboarding-only. A fully onboarded admin remains a curator and
        // still authors through the catalog -- without this the fix would demote every curator silently.
        UUID ownerUserId = UUID.randomUUID();
        UserEntity admin = buildUser(ownerUserId, "admin@example.com");
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(admin));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Newton's Laws", null, List.of(), "Accountancy", null, null, List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(CourseProgramSelectionRequiredException.class);
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
                null,
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
        assertThat(saved.getDomainContext()).isNull();
        assertThat(saved.getLearnerLevel()).isNull();
        assertThat(saved.getContent()).isEqualTo("hooks and state");
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
        assertThat(saved.getCopiedFromUserId()).isNull();
        assertThat(saved.getCopiedFromPublic()).isFalse();

        assertThat(created.studyPackStatus()).isEqualTo("DRAFT");
        assertThat(created.courseProgram()).isEqualTo("Computer Science");
        assertThat(created.domainContext()).isNull();
        assertThat(created.learnerLevel()).isNull();
        assertThat(created.copiedFromUserId()).isNull();
        assertThat(created.copiedFromPublic()).isFalse();
        verify(noteRepository).flush();
        verify(analyticsService).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.NOTE_CREATED), eq(saved.getId()), any());
    }

    @Test
    void create_curatorRecordsAcceptedAutomaticDomainFromJoinedProgram() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UserEntity curator = curator(ownerUserId);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(eq(ownerUserId), any(NoteEntity.class)))
                .thenReturn(authoringContext("Civil Engineering"));

        noteService.create(curatorRequest(List.of(programId), null), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "Civil Engineering"),
                Map.entry("persistedDomainContext", "AUTOMATIC")
        );
        InOrder eventOrder = inOrder(
                noteCourseProgramRepository,
                studyPackGenerationContextResolver,
                analyticsService
        );
        eventOrder.verify(noteCourseProgramRepository).replace(any(UUID.class), eq(Set.of(programId)));
        eventOrder.verify(studyPackGenerationContextResolver).resolve(eq(ownerUserId), any(NoteEntity.class));
        eventOrder.verify(analyticsService).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.NOTE_AUTHORING_DOMAIN_RECORDED),
                any(UUID.class),
                any()
        );
    }

    @Test
    void create_curatorRecordsOverriddenDomainAlongsideAutomaticDomain() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(eq(ownerUserId), any(NoteEntity.class)))
                .thenReturn(authoringContext("Civil Engineering"));

        noteService.create(curatorRequest(List.of(programId), "ENGINEERING_SCIENCES"), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "Civil Engineering"),
                Map.entry("persistedDomainContext", "ENGINEERING_SCIENCES")
        );
    }

    @Test
    void create_curatorRecordsConfirmedDomainAlongsideAutomaticDomain() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(eq(ownerUserId), any(NoteEntity.class)))
                .thenReturn(authoringContext("Civil Engineering"));

        noteService.create(curatorRequest(List.of(programId), "CIVIL_ENGINEERING"), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "Civil Engineering"),
                Map.entry("persistedDomainContext", "CIVIL_ENGINEERING")
        );
    }

    @Test
    void create_curatorRecordsAutomaticNoneInsteadOfDroppingTheEvent() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(eq(ownerUserId), any(NoteEntity.class)))
                .thenReturn(authoringContext(null));

        noteService.create(curatorRequest(List.of(programId), null), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "NONE"),
                Map.entry("persistedDomainContext", "AUTOMATIC")
        );
    }

    @Test
    void update_curatorRecordsChangedDomainContextWithPreviousValue() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        note.setDomainContext(DomainContext.ENGINEERING_SCIENCES);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(ownerUserId, note))
                .thenReturn(authoringContext("Civil Engineering"));

        noteService.update(noteId.toString(), curatorRequest(List.of(programId), "CIVIL_ENGINEERING"), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "Civil Engineering"),
                Map.entry("persistedDomainContext", "CIVIL_ENGINEERING"),
                Map.entry("previousDomainContext", "ENGINEERING_SCIENCES")
        );
        // The create-path tests pin this ordering and the update path did not, which let a mutation
        // that fires the event BEFORE the join rows are replaced survive the whole suite. It is not
        // cosmetic: resolve() reads note_course_program, so firing first records the PREVIOUS program
        // set as automaticDomain -- wrong for exactly the curator save that changes programs and
        // Domain Context together, which is the multi-program case this release exists for.
        InOrder eventOrder = inOrder(
                noteCourseProgramRepository,
                studyPackGenerationContextResolver,
                analyticsService
        );
        eventOrder.verify(noteCourseProgramRepository).replace(any(UUID.class), eq(Set.of(programId)));
        eventOrder.verify(studyPackGenerationContextResolver).resolve(eq(ownerUserId), any(NoteEntity.class));
        eventOrder.verify(analyticsService).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.NOTE_AUTHORING_DOMAIN_RECORDED),
                any(UUID.class),
                any()
        );
    }

    @Test
    void update_curatorRecordsUnchangedDomainContextWithSamePreviousValue() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        note.setDomainContext(DomainContext.CIVIL_ENGINEERING);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(ownerUserId, note))
                .thenReturn(authoringContext("Civil Engineering"));

        noteService.update(noteId.toString(), curatorRequest(List.of(programId), "CIVIL_ENGINEERING"), ownerUserId);

        assertThat(authoringDomainMetadata(ownerUserId)).containsExactly(
                Map.entry("automaticDomain", "Civil Engineering"),
                Map.entry("persistedDomainContext", "CIVIL_ENGINEERING"),
                Map.entry("previousDomainContext", "CIVIL_ENGINEERING")
        );
    }

    @Test
    void learnerCreateAndUpdateDoNotRecordAuthoringDomain() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", "Course", null, null, List.of(), "content"
        );
        noteService.create(request, ownerUserId);
        noteService.update(noteId.toString(), request, ownerUserId);

        verify(analyticsService, never()).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.NOTE_AUTHORING_DOMAIN_RECORDED),
                any(),
                any()
        );
    }

    @Test
    void create_curatorStillSavesWhenAuthoringDomainAnalyticsThrows() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(studyPackGenerationContextResolver.resolve(eq(ownerUserId), any(NoteEntity.class)))
                .thenReturn(authoringContext("Civil Engineering"));
        lenient().doThrow(new RuntimeException("analytics unavailable")).when(analyticsService).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.NOTE_AUTHORING_DOMAIN_RECORDED),
                any(),
                any()
        );

        assertThatCode(() -> noteService.create(curatorRequest(List.of(programId), null), ownerUserId))
                .doesNotThrowAnyException();

        verify(noteRepository).save(any(NoteEntity.class));
    }

    @Test
    void create_rejectsRawSubjectThatExceedsStorageWithNamedException() {
        UUID ownerUserId = UUID.randomUUID();
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "x".repeat(65), "Nursing", null, null, List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(SubjectTooLongException.class)
                .hasMessage("Subject must be 64 characters or less.");

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void create_rejectsSubjectThatExceedsStorageOnlyAfterNormalization() {
        UUID ownerUserId = UUID.randomUUID();
        String rawSubject = "x".repeat(61) + "-y";
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", rawSubject, "Nursing", null, null, List.of(), "content"
        );

        assertThat(rawSubject).hasSize(63);
        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(SubjectTooLongException.class);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void create_acceptsAndPersistsSubjectAtStorageLimitUnchanged() {
        UUID ownerUserId = UUID.randomUUID();
        String subject = "x".repeat(64);
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", subject, "Nursing", null, null, List.of(), "content"
        );

        noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo(subject);
    }

    @Test
    void create_rejectsCourseProgramAboveStorageLimitAndAcceptsTheBoundary() {
        UUID ownerUserId = UUID.randomUUID();
        UpsertNoteRequest overlongRequest = new UpsertNoteRequest(
                "Title", "Subject", "x".repeat(121), null, null, List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.create(overlongRequest, ownerUserId))
                .isInstanceOf(CourseProgramTooLongException.class)
                .hasMessage("Course/program must be 120 characters or less.");

        String boundaryCourseProgram = "y".repeat(120);
        UpsertNoteRequest boundaryRequest = new UpsertNoteRequest(
                "Title", "Subject", boundaryCourseProgram, null, null, List.of(), "content"
        );
        noteService.create(boundaryRequest, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseProgram()).isEqualTo(boundaryCourseProgram);
    }

    @Test
    void create_persistsValidAuthoringAxesCaseInsensitively() {
        UUID ownerUserId = UUID.randomUUID();
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Engineering algebra",
                "Algebra",
                "Civil Engineering",
                "engineering_mathematics",
                "college",
                List.of(),
                "Algebra content"
        );

        NoteResponse created = noteService.create(request, ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getDomainContext()).isEqualTo(DomainContext.ENGINEERING_MATHEMATICS);
        assertThat(captor.getValue().getLearnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(created.domainContext()).isEqualTo("ENGINEERING_MATHEMATICS");
        assertThat(created.learnerLevel()).isEqualTo("COLLEGE");
    }

    @Test
    void create_rejectsUnknownDomainContext() {
        UUID ownerUserId = UUID.randomUUID();
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, "engineering_math", null, List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(InvalidDomainContextException.class)
                .hasMessageContaining("domainContext")
                .hasMessageContaining("Engineering Mathematics")
                .extracting(error -> ((AppException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void create_rejectsUnknownLearnerLevel() {
        UUID ownerUserId = UUID.randomUUID();
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, null, "university", List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.create(request, ownerUserId))
                .isInstanceOf(InvalidNoteLearnerLevelException.class)
                .hasMessageContaining("learnerLevel")
                .hasMessageContaining("College")
                .extracting(error -> ((AppException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    /**
     * GUARD 6 (v0.118.0 §20a). A note edit issued WHILE the note is GENERATING is rejected with 409 and the
     * stored content is left exactly as it was.
     *
     * <p>⚠️ The fixture must set the status to GENERATING BEFORE the call. A fixture that edits before
     * generation starts passes under both the defect and the fix.
     *
     * <p>⚠️ The primary assertion is on the ENTITY'S CONTENT, not on a `verify(...)`. NoteService.update
     * mutates the loaded entity in place and then saves it, so a missing guard shows up as the field
     * having been overwritten — which is the state a real database would then hold.
     */
    @Test
    void update_rejectedWithConflictAndLeavesContentUntouchedWhileTheNoteIsGenerating() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.GENERATING, NoteVisibility.PRIVATE, "original content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Edited title", "Edited subject", null, null, null, List.of(), "edited content"
        );

        assertThatThrownBy(() -> noteService.update(noteId.toString(), request, ownerUserId))
                .isInstanceOf(NoteGenerationInProgressException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_GENERATION_IN_PROGRESS");

        assertThat(note.getContent())
                .as("the stored content is untouched — the whole point of the guard")
                .isEqualTo("original content");
        assertThat(note.getTitle())
                .as("and so is every other field this upsert would have rewritten")
                .isEqualTo("Title");
        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void update_stillSucceedsOnANoteThatIsNotGenerating() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "original content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Edited title", "Edited subject", null, null, null, List.of(), "edited content"
        );

        noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(note.getContent())
                .as("the guard is scoped to GENERATING and does not lock ordinary editing")
                .isEqualTo("edited content");
    }

    @Test
    void update_rejectsUnknownDomainContext() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, "not_a_context", null, List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.update(noteId.toString(), request, ownerUserId))
                .isInstanceOf(InvalidDomainContextException.class)
                .extracting(error -> ((AppException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void update_rejectsUnknownLearnerLevel() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, null, "not_a_level", List.of(), "content"
        );

        assertThatThrownBy(() -> noteService.update(noteId.toString(), request, ownerUserId))
                .isInstanceOf(InvalidNoteLearnerLevelException.class)
                .extracting(error -> ((AppException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(noteRepository, never()).save(any(NoteEntity.class));
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
                "Nursing",
                null,
                null,
                List.of(),
                "content"
        );

        noteService.create(request, ownerUserId);

        verify(noteRepository).save(argThat(note -> note.getTargetProfileType() == NoteTargetProfileType.BOARD_TAKER));
    }

    @Test
    void create_professionalAutoAssignsProfessionalTargetProfileType() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = buildUser(ownerUserId, "professional@example.com");
        owner.setProfileType(ProfileType.PROFESSIONAL);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Professional note",
                "Subject",
                "Nursing",
                null,
                null,
                List.of(),
                "content"
        );

        noteService.create(request, ownerUserId);

        verify(noteRepository).save(argThat(note -> note.getTargetProfileType() == NoteTargetProfileType.PROFESSIONAL));
    }

    @Test
    void create_teacherDerivesValidStoredTargetProfileType() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UserEntity owner = buildUser(ownerUserId, "teacher@example.com");
        owner.setProfileType(ProfileType.TEACHER);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Teacher note",
                "Subject",
                List.of(programId),
                null,
                null,
                null,
                List.of(),
                "content"
        );

        noteService.create(request, ownerUserId);

        verify(noteRepository).save(argThat(note -> note.getTargetProfileType() == NoteTargetProfileType.STUDENT));
    }

    @Test
    void create_adminDerivesValidStoredTargetProfileType() {
        UUID ownerUserId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UserEntity owner = buildUser(ownerUserId, "admin@example.com");
        owner.setRole(UserRole.ADMIN);
        owner.setProfileType(ProfileType.BOARD_EXAM);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Admin note", "Subject", List.of(programId), null, null, null, List.of(), "content"
        );

        noteService.create(request, ownerUserId);

        verify(noteRepository).save(argThat(note -> note.getTargetProfileType() == NoteTargetProfileType.BOARD_TAKER));
    }

    /**
     * ⚠️ v0.120.0 — this is the guard that catches the SILENT NO-OP, which is this release's most
     * likely way to ship green and deliver nothing.
     *
     * <p>The note detail response must carry the STUDY PACK's title, not the note's. A plausible
     * copy-paste (`entity.getTitle()` instead of `studyPack.getTitle()`) compiles, keeps every other
     * test green, and makes the two titles always equal — so the title-suggestion card would simply
     * never appear, with no error anywhere.
     *
     * <p>⚠️ The fixture therefore gives the pack a DIFFERENT title from the note. Equal titles pass
     * under both the correct mapping and the mutant.
     */
    @Test
    void getById_carriesTheStudyPackTitleRatherThanTheNoteTitle() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        note.setTitle("Site Grading Principles");
        StudyPackEntity studyPack = buildStudyPack(noteId, "Summary");
        studyPack.setTitle("Site Grading Principles in Civil Engineering");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(studyPack));

        NoteResponse response = noteService.getById(noteId.toString(), ownerUserId);

        assertThat(response.title())
                .as("the note keeps the curator's own title")
                .isEqualTo("Site Grading Principles");
        assertThat(response.studyPackTitle())
                .as("and the pack's generated title travels beside it -- this is the ONLY input the"
                        + " title-suggestion card has, so mapping the note's title here would make the"
                        + " card silently never appear")
                .isEqualTo("Site Grading Principles in Civil Engineering");
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
    void updateVisibility_queuesOfficialTemplateSeedWithTheSavedNoteAndLinkedStudyPack() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        StudyPackEntity linkedStudyPack = buildStudyPack(noteId, "Summary");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(linkedStudyPack));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(officialChallengeQuizTemplateService).queueSeedIfEligible(noteCaptor.capture(), eq(linkedStudyPack));
        assertThat(noteCaptor.getValue().getId()).isEqualTo(noteId);
        assertThat(noteCaptor.getValue().getVisibility()).isEqualTo(NoteVisibility.PUBLIC);
    }

    @Test
    void updateVisibility_tracksPrivateToPublicPublicationExactlyOnce() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(analyticsService).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.PUBLIC_NOTE_PUBLISHED),
                eq(noteId),
                argThat(metadata -> "PRIVATE".equals(metadata.get("previousVisibility"))
                        && "PUBLIC".equals(metadata.get("newVisibility")))
        );
    }

    /**
     * ⚠️ THE COMPANION GUARD, AND THE BRANCH IT COVERS IS ONE v0.115.0 ITSELF MADE REACHABLE.
     *
     * <p>Before publishing cleared the copied rows, a copied note always carried at least one, so
     * {@code NoteCourseProgramShadowing.isShadowed} was true and {@code update} skipped the resolver
     * entirely. After clearing, the note has ZERO rows, is no longer shadowed, and every subsequent edit
     * reaches {@code resolveRequestedCourseProgram} — where an omitted {@code courseProgramText} used to
     * fall through to the OWNER'S PROFILE program and stamp it onto the note.
     *
     * <p>⚠️ THIS IS NOT HYPOTHETICAL: {@code UpsertNoteRequest} carries a {@code @JsonAlias} added
     * specifically because a client on a stale bundle sends the old field name, {@code courseProgramText}
     * reads as null, and the note is silently reassigned. That alias mitigates one cause; retaining the
     * stored value fixes the resolver itself.
     *
     * <p>The fixture asserts the note keeps ITS OWN value rather than acquiring the profile's — the two
     * are deliberately different strings, because a fixture where they match passes under both the defect
     * and the fix.
     */
    @Test
    void update_retainsTheNotesOwnCourseProgramWhenTheRequestOmitsItRatherThanStampingTheProfile() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity learner = buildUser(ownerUserId, "learner@example.com");
        learner.setCourseProgram("Profile Program");
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(learner));
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        existing.setCourseProgram("Learner Chosen Program");
        existing.setDomainContext(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));
        // Zero join rows is the post-publish state: not shadowed, so the resolver actually runs.
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of());

        noteService.update(
                noteId.toString(),
                new UpsertNoteRequest("Title", "Subject", null, null, null, List.of(), "content"),
                ownerUserId
        );

        ArgumentCaptor<NoteEntity> savedNote = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(savedNote.capture());
        assertThat(savedNote.getValue().getCourseProgram())
                .as("an omitted courseProgramText must retain the note's own stored value, never acquire "
                        + "the owner's profile program")
                .isEqualTo("Learner Chosen Program");
    }

    /**
     * ⚠️ THE PRE-DECLARED DISCRIMINATING GUARD FOR v0.115.0, AND IT IS THE CURATOR HALF THAT DISCRIMINATES.
     * A fixture that only exercises the learner path passes under a version that clears rows for EVERYONE
     * — which would strip authored applicability off every curated note on publication and destroy the
     * catalog — so it would prove nothing. This asserts the exclusion directly.
     */
    @Test
    void updateVisibility_keepsAuthoredApplicabilityWhenACuratorPublishes() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity curator = buildUser(ownerUserId, "curator@example.com");
        curator.setRole(UserRole.ADMIN);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator));
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(noteCourseProgramRepository, never()).replace(eq(noteId), any());
    }

    /**
     * ⚠️ THE TEACHER LEG OF THE CURATOR PREDICATE, PINNED SEPARATELY BECAUSE THE ADMIN GUARD ABOVE DOES
     * NOT COVER IT — AND THAT GAP WAS MEASURED, NOT ANTICIPATED. Replacing
     * {@code CuratorAuthoringPredicate.isCurator} with a bare {@code role == ADMIN} check SURVIVED the
     * whole 99-test class: the ADMIN fixture still kept its rows and the learner fixture still lost
     * them, so both guards passed while every TEACHER-owned curated note would have had its authored
     * applicability stripped on publication. Most of the public catalog is curator-authored, so that is
     * the catalog-destroying mutation the exclusion exists to prevent, reaching production green.
     */
    @Test
    void updateVisibility_keepsAuthoredApplicabilityWhenATeacherCuratorPublishes() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator(ownerUserId)));
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(noteCourseProgramRepository, never()).replace(eq(noteId), any());
    }

    /**
     * ⚠️ THE CLEARING IS SAFE ONLY BECAUSE OF AN INVARIANT THAT LIVES ELSEWHERE, SO THE ONE CASE THAT
     * ESCAPES IT IS PINNED AS A DECISION RATHER THAN LEFT AS AN ACCIDENT. Every join-row write path is
     * curator-gated ({@code NoteApplicableProgramsService} requires {@code isOwner && isCurator},
     * {@code create} and {@code update} both guard on {@code curator}) or copy-inherited
     * ({@code copyNote}) — so on a learner-owned note every row is copied, and clearing destroys
     * nothing the owner authored. An account that authored rows WHILE a curator and is no longer one
     * is the sole exception: its own rows are cleared. That is intended (it is no longer a curator, and
     * the string fallback un-shadows to carry discovery), but it is the reachable case, so it is
     * asserted here. **If a future release gives learners an Applicable Programs surface, this method
     * becomes destructive and must be narrowed first.**
     */
    @Test
    void updateVisibility_clearsRowsWhenAnOwnerWhoAuthoredThemIsNoLongerACurator() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity formerCurator = buildUser(ownerUserId, "former-curator@example.com");
        formerCurator.setProfileType(ProfileType.STUDENT);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(formerCurator));
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(noteCourseProgramRepository).replace(noteId, Set.of());
    }

    /**
     * The learner half of the same guard. Publishing transfers program classification from the curator
     * to the learner, so the copied rows are cleared and the learner's own field is un-shadowed.
     */
    @Test
    void updateVisibility_clearsCopiedApplicabilityWhenALearnerPublishesTheirCopy() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(noteCourseProgramRepository).replace(noteId, Set.of());
    }

    /**
     * ⚠️ The transition is what transfers authority, not the resulting state. A resave of an
     * already-public note must not re-run the clearing — the learner may have set their own programs
     * since publishing, and clearing again would silently destroy them on an unrelated action.
     */
    @Test
    void updateVisibility_doesNotClearApplicabilityWhenTheNoteWasAlreadyPublic() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(noteCourseProgramRepository, never()).replace(eq(noteId), any());
    }

    /**
     * ⚠️ THE SECOND PRE-DECLARED GUARD — the note goes public and UNSHELVED, never backfilled. A copy of
     * a post-slice-4 curator note carries a NULL program string (44.7% of curated notes do), and
     * publishing must leave it null so the learner answers for themselves. Filling it from the joined
     * catalog name would relaunder curator classification through a different column.
     */
    @Test
    void updateVisibility_leavesANullProgramStringNullWhenPublishingRatherThanBackfillingIt() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        existing.setCourseProgram(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        ArgumentCaptor<NoteEntity> savedNote = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(savedNote.capture());
        assertThat(savedNote.getValue().getCourseProgram()).isNull();
    }

    @Test
    void updateVisibility_doesNotTrackPublicToPublicResave() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PUBLIC", ownerUserId);

        verify(analyticsService, never()).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.PUBLIC_NOTE_PUBLISHED),
                eq(noteId),
                any()
        );
    }

    @Test
    void updateVisibility_doesNotTrackPublicToPrivateTransition() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity existing = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(existing));

        noteService.updateVisibility(noteId.toString(), "PRIVATE", ownerUserId);

        verify(analyticsService, never()).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.PUBLIC_NOTE_PUBLISHED),
                eq(noteId),
                any()
        );
    }

    @Test
    void create_reusesCanonicalSubjectFormattingWhenEquivalentSubjectAlreadyExists() {
        UUID ownerUserId = UUID.randomUUID();
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Cell note",
                " biology-cell division ",
                "Nursing",
                null,
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
                null,
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
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(draftNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "New title", "biology- cell division", "Pre-Med", null, null,
                List.of("cells"), "new content"
        );
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
    void update_generatedNote_allowsContentChange() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "old content");
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(generatedNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", "Nursing", null, null, List.of("tag"), "edited content"
        );

        NoteResponse response = noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(generatedNote.getContent()).isEqualTo("edited content");
        assertThat(response.content()).isEqualTo("edited content");
        verify(noteRepository).save(generatedNote);
    }

    @Test
    void update_setsAndExplicitlyClearsAuthoringAxes() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(draftNote));

        UpsertNoteRequest setRequest = new UpsertNoteRequest(
                "Title", "Subject", "Engineering", "ENGINEERING_SCIENCES", "SENIOR_HIGH",
                List.of(), "content"
        );
        NoteResponse setResponse = noteService.update(noteId.toString(), setRequest, ownerUserId);

        assertThat(draftNote.getDomainContext()).isEqualTo(DomainContext.ENGINEERING_SCIENCES);
        assertThat(draftNote.getLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(setResponse.domainContext()).isEqualTo("ENGINEERING_SCIENCES");
        assertThat(setResponse.learnerLevel()).isEqualTo("SENIOR_HIGH");

        UpsertNoteRequest clearRequest = new UpsertNoteRequest(
                "Title", "Subject", "Engineering", " ", null, List.of(), "content"
        );
        NoteResponse clearedResponse = noteService.update(noteId.toString(), clearRequest, ownerUserId);

        assertThat(draftNote.getDomainContext()).isNull();
        assertThat(draftNote.getLearnerLevel()).isNull();
        assertThat(clearedResponse.domainContext()).isNull();
        assertThat(clearedResponse.learnerLevel()).isNull();
    }

    @Test
    void update_byLearnerOwner_preservesJoinRowsInheritedFromACopy() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity copiedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(copiedNote));
        // Stub one inherited row explicitly rather than leaning on Mockito's default empty Set. Since C1
        // this method decides whether the multi-program invariant fires, so an unstubbed default would
        // make this test pass by exercising the zero-program path -- which is not what it claims to
        // cover, and would start throwing the moment anyone stubbed a wider set in setUp.
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of(UUID.randomUUID()));

        // A learner never authors join rows, but a note copied from curated content inherits them.
        // Clearing them on a learner save would destroy every inherited program during an unrelated
        // title fix -- which is exactly what copy inheritance exists to prevent.
        UpsertNoteRequest titleOnlyEdit = new UpsertNoteRequest(
                "Corrected title", "Subject", "Course", null, null, List.of("tag"), "content"
        );
        noteService.update(noteId.toString(), titleOnlyEdit, ownerUserId);

        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void update_shadowedLearnerNoteDoesNotRequireARequestedOrProfileProgram() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity copiedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        copiedNote.setCourseProgram(null);
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        owner.setCourseProgram(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(copiedNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of(UUID.randomUUID()));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, null, null, List.of(), "content"
        );

        NoteResponse updated = noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(updated.courseProgram()).isNull();
        assertThat(copiedNote.getCourseProgram()).isNull();
    }

    @Test
    void update_shadowedLearnerNoteLeavesAnExistingCourseProgramUntouched() {
        // A pre-slice-4 curated note kept its string while V107 gave it exactly one join row, and
        // copyNote carries both onto the copy. The string is unreadable while that row exists, but it
        // must not be silently destroyed by an unrelated edit -- nor replaced by the editor's profile
        // program, which is what falling through to the resolver would do on a surface with no field.
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity copiedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        copiedNote.setCourseProgram("Civil Engineering");
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        owner.setCourseProgram("Nursing");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(copiedNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of(UUID.randomUUID()));
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Retitled", "Subject", null, null, null, List.of(), "content"
        );

        noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(copiedNote.getCourseProgram()).isEqualTo("Civil Engineering");
    }

    @Test
    void update_nonShadowedLearnerNoteStillRequiresRequestedOrProfileProgram() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        note.setCourseProgram(null);
        UserEntity owner = buildUser(ownerUserId, "owner@example.com");
        owner.setCourseProgram(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of());
        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", null, null, null, List.of(), "content"
        );
        String noteIdRaw = noteId.toString();

        assertThatThrownBy(() -> noteService.update(noteIdRaw, request, ownerUserId))
                .isInstanceOf(CourseProgramSelectionRequiredException.class);
    }

    @Test
    void update_byLearnerOwner_allowsClearingDomainContextButGenerationThenRejects() {
        // Saving and generation now answer different questions. The copied note remains valid with its
        // stored Applicable Programs, but it is not generation-ready until a Domain Context is chosen.
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity copiedNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        copiedNote.setDomainContext(DomainContext.ENGINEERING_SCIENCES);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(copiedNote));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId))
                .thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        UpsertNoteRequest clearsDomainContext = new UpsertNoteRequest(
                "Title", "Subject", "Course", null, null, List.of(), "content"
        );

        noteService.update(noteId.toString(), clearsDomainContext, ownerUserId);

        assertThat(copiedNote.getDomainContext()).isNull();
        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository
        );
        assertThatThrownBy(() -> resolver.assertGenerationReady(copiedNote))
                .isInstanceOf(MultiProgramDomainContextRequiredException.class);
    }

    @Test
    void update_byCuratorOwner_validatesTheRequestedProgramsRatherThanTheStoredRows() {
        // The curator's request IS the new set -- replace() writes it moments later -- so the stored
        // rows are the *pre*-update state and must not be what the invariant reads. Validating them
        // here would block this legal reduction from three stored programs to one while clearing
        // domainContext, and would equally let an illegal one-to-many expansion through.
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID keptProgramId = UUID.randomUUID();
        UserEntity curator = buildUser(ownerUserId, "teacher@example.com");
        curator.setProfileType(ProfileType.TEACHER);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(curator));
        NoteEntity note = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        lenient().when(noteCourseProgramRepository.findIdsByNoteId(noteId))
                .thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID(), keptProgramId));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(keptProgramId)))
                .thenReturn(List.of(keptProgramId));

        UpsertNoteRequest reducesToOneProgram = new UpsertNoteRequest(
                "Title", "Subject", List.of(keptProgramId), null, null, null,
                List.of(), "content"
        );
        noteService.update(noteId.toString(), reducesToOneProgram, ownerUserId);

        verify(noteCourseProgramRepository).replace(noteId, Set.of(keptProgramId));
    }

    @Test
    void update_preservesStoredTargetProfileTypeForCuratorOwner() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        NoteEntity boardNote = buildNote(noteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "content");
        boardNote.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);
        UserEntity owner = buildUser(ownerUserId, "teacher@example.com");
        owner.setProfileType(ProfileType.TEACHER);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(boardNote));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));

        // A TEACHER owner now derives STUDENT on create. Re-deriving on this unrelated title edit would
        // overwrite the deliberately different historical BOARD_TAKER value retained on the row.
        UpsertNoteRequest titleOnlyEdit = new UpsertNoteRequest(
                "Corrected title", "Subject", List.of(programId), null, null, null, List.of("tag"), "content"
        );
        NoteResponse response = noteService.update(noteId.toString(), titleOnlyEdit, ownerUserId);

        assertThat(boardNote.getTargetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER);
        assertThat(boardNote.getTitle()).isEqualTo("Corrected title");
        assertThat(response.title()).isEqualTo("Corrected title");
    }

    @Test
    void update_derivesTargetProfileTypeForLegacyNoteWithoutOne() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity legacyNote = buildNote(noteId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "content");
        legacyNote.setTargetProfileType(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(legacyNote));

        UpsertNoteRequest request = new UpsertNoteRequest(
                "Title", "Subject", "Course", null, null, List.of("tag"), "content"
        );
        noteService.update(noteId.toString(), request, ownerUserId);

        assertThat(legacyNote.getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
    }

    @Test
    void copyOwnNote_createsDraftWithoutAttribution() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "source content");
        source.setTitle("Source title");
        source.setSubject("Math");
        source.setCourseProgram("Engineering");
        source.setDomainContext(DomainContext.ENGINEERING_MATHEMATICS);
        source.setLearnerLevel(LearnerLevel.COLLEGE);
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
        assertThat(saved.getDomainContext()).isEqualTo(DomainContext.ENGINEERING_MATHEMATICS);
        assertThat(saved.getLearnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
        assertThat(saved.getCopiedFromUserId()).isNull();
        assertThat(saved.getCopiedFromPublic()).isFalse();
        assertThat(copied.copiedFromUserId()).isNull();
        assertThat(copied.copiedFromPublic()).isFalse();
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
    }

    @Test
    void copySharedPrivateNoteCreatesAnIndependentPrivateCopy() {
        UUID recipientUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(
                sourceNoteId,
                sourceOwnerUserId,
                NoteStatus.GENERATED,
                NoteVisibility.PRIVATE,
                "source content"
        );
        source.setTitle("Shared source");
        StudyPackEntity sourceStudyPack = buildSourceStudyPack(sourceNoteId);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(noteShareRepository.existsLiveAuthorizedShare(sourceNoteId, recipientUserId)).thenReturn(true);
        when(studyPackRepository.findByNoteId(sourceNoteId)).thenReturn(Optional.of(sourceStudyPack));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), recipientUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        NoteEntity saved = noteCaptor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(recipientUserId);
        assertThat(saved.getVisibility()).isEqualTo(NoteVisibility.PRIVATE);
        assertThat(saved.getCopiedFromNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCopiedFromUserId()).isEqualTo(sourceOwnerUserId);
        assertThat(saved.getCopiedFromPublic()).isFalse();
        assertThat(copied.studyPackStatus()).isEqualTo("STUDY_PACK_READY");
        verify(analyticsService).trackEvent(
                eq(recipientUserId),
                eq(AnalyticsEventType.SHARED_NOTE_COPIED),
                eq(sourceNoteId),
                any()
        );
    }

    @Test
    void copyNote_clampsAStoredSubjectThatGrowsPastStorageInsteadOfFailingTheCopy() {
        // The person copying did not author this subject and cannot fix it. Re-normalization can
        // grow a stored value (a bare hyphen expands to " - "), so sharing the throwing path with
        // note create/update would 400 someone's copy over another author's metadata.
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "source content");
        source.setTitle("Source title");
        source.setSubject("x".repeat(61) + "-y");
        source.setTargetProfileType(NoteTargetProfileType.STUDENT);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void copyNote_clampsAStoredCourseProgramThatGrowsPastStorageInsteadOfFailingTheCopy() {
        // Sibling of the subject case above. normalizeForStorage expands a bare hyphen to " - ", so a
        // stored program near the 120 bound can cross it on re-normalization. The copier did not author
        // it, and inside plan adoption a throw here is swallowed into skippedCount -- a silently missing
        // note rather than a visible error.
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "source content");
        source.setTitle("Source title");
        source.setCourseProgram("x".repeat(117) + "-y");
        source.setTargetProfileType(NoteTargetProfileType.STUDENT);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));

        noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseProgram()).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    void copyNote_flushesTheParentBeforeWritingInheritedJoinRows() {
        // B1. replace() is raw JDBC and cannot see JPA's pending persistence context, so without a flush
        // between them the child insert hits the foreign key before the parent note row exists and copyNote
        // throws on any note carrying join rows -- since slice 4, every curated note. Asserting the ORDER
        // rather than merely that flush() was called is what makes this fail if the flush is removed or
        // moved below the join write.
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, ownerUserId, NoteStatus.GENERATED, NoteVisibility.PRIVATE, "source content");
        source.setCourseProgram("Engineering");
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(noteCourseProgramRepository.findIdsByNoteId(sourceNoteId)).thenReturn(Set.of(UUID.randomUUID()));

        noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        InOrder inOrder = inOrder(noteRepository, noteCourseProgramRepository);
        inOrder.verify(noteRepository).save(any(NoteEntity.class));
        inOrder.verify(noteRepository).flush();
        inOrder.verify(noteCourseProgramRepository).replace(any(), any());
    }

    @Test
    void copyNote_rejectsMissingProfileTypeBeforeSaving() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        org.mockito.Mockito.doThrow(exception).when(onboardingGuardService).assertProfileComplete(ownerUserId);

        assertThatThrownBy(() -> noteService.copyNote(sourceNoteId.toString(), ownerUserId))
                .isSameAs(exception);

        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void copyPublicNoteForPracticeFirst_copiesReadyStudyPackAndSetsAttributionFields() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        source.setTitle("Public source");
        source.setSubject("History");
        source.setCourseProgram("Humanities");
        source.setDomainContext(DomainContext.GENERAL_EDUCATION);
        source.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        source.setTags(new String[]{"ww2"});
        source.setTargetProfileType(NoteTargetProfileType.BOARD_TAKER);
        StudyPackEntity sourceStudyPack = buildSourceStudyPack(sourceNoteId);
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(studyPackRepository.findByNoteId(sourceNoteId)).thenReturn(Optional.of(sourceStudyPack));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        NoteEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NoteStatus.GENERATED);
        assertThat(saved.getCopiedFromNoteId()).isEqualTo(sourceNoteId);
        assertThat(saved.getCopiedFromUserId()).isEqualTo(sourceOwnerUserId);
        assertThat(saved.getCopiedFromTitle()).isEqualTo("Public source");
        assertThat(saved.getCourseProgram()).isEqualTo("Humanities");
        assertThat(saved.getDomainContext()).isEqualTo(DomainContext.GENERAL_EDUCATION);
        assertThat(saved.getLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(saved.getTargetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER);
        assertThat(saved.getCopiedFromPublic()).isTrue();
        assertThat(saved.getCopiedAt()).isNotNull();

        assertThat(copied.copiedFromNoteId()).isEqualTo(sourceNoteId.toString());
        assertThat(copied.copiedFromUserId()).isEqualTo(sourceOwnerUserId.toString());
        assertThat(copied.copiedFromTitle()).isEqualTo("Public source");
        assertThat(copied.copiedFromPublic()).isTrue();
        assertThat(copied.copiedAt()).isNotNull();
        assertThat(copied.studyPackStatus()).isEqualTo("STUDY_PACK_READY");
        assertThat(copied.summary()).isEqualTo("Copied summary");
        assertThat(copied.quizMastered()).isFalse();
        assertThat(copied.quizMasteredAt()).isNull();

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        StudyPackEntity copiedStudyPack = studyPackCaptor.getValue();
        assertThat(copiedStudyPack.getId()).isNotEqualTo(sourceStudyPack.getId());
        assertThat(copiedStudyPack.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(copiedStudyPack.getNoteId()).isEqualTo(saved.getId());
        assertThat(copiedStudyPack.getInputType()).isEqualTo(sourceStudyPack.getInputType());
        assertThat(copiedStudyPack.getTitle()).isEqualTo(sourceStudyPack.getTitle());
        assertThat(copiedStudyPack.getSummary()).isEqualTo(sourceStudyPack.getSummary());
        assertThat(copiedStudyPack.getSubject()).isEqualTo(sourceStudyPack.getSubject());
        assertThat(copiedStudyPack.getKeyConcepts()).containsExactlyElementsOf(sourceStudyPack.getKeyConcepts());
        assertThat(copiedStudyPack.getQuiz()).containsExactlyElementsOf(sourceStudyPack.getQuiz());
        assertThat(copiedStudyPack.getTags()).containsExactly(sourceStudyPack.getTags());
        assertThat(copiedStudyPack.getModelTier()).isEqualTo(sourceStudyPack.getModelTier());
        assertThat(copiedStudyPack.getModelUsed()).isEqualTo(sourceStudyPack.getModelUsed());
        assertThat(copiedStudyPack.getStatus()).isEqualTo(StudyPackStatus.DONE);
        assertThat(copiedStudyPack.getAnonId()).isNull();
        assertThat(copiedStudyPack.getShareToken()).isNull();
        assertThat(copiedStudyPack.getSourceText()).isNull();
        assertThat(copiedStudyPack.getInputTokens()).isNull();
        assertThat(copiedStudyPack.getOutputTokens()).isNull();
        assertThat(copiedStudyPack.getCachedInputTokens()).isNull();
        assertThat(copiedStudyPack.getEstimatedCost()).isNull();
        assertThat(copiedStudyPack.getOcrConfidence()).isNull();
        assertThat(copiedStudyPack.getErrorCode()).isNull();
        verify(studyPackQuizMasteryService).resolve(ownerUserId, copiedStudyPack);
        verify(studyPackQuizMasteryService, never()).resolve(sourceOwnerUserId, sourceStudyPack);
        verify(analyticsService).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), eq(sourceNoteId), any());
    }

    @Test
    void copyPublicNoteWithoutStudyPack_remainsDraft() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.DRAFT, NoteVisibility.PUBLIC, "source content");
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(studyPackRepository.findByNoteId(sourceNoteId)).thenReturn(Optional.empty());

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(copied.studyPackStatus()).isEqualTo("DRAFT");
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
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
        when(noteRepository.findFirstByOwnerUserIdAndCopiedFromNoteId(ownerUserId, sourceNoteId))
                .thenReturn(Optional.of(existingCopy));
        when(studyPackRepository.findByNoteId(existingCopyId)).thenReturn(Optional.of(existingStudyPack));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        assertThat(copied.id()).isEqualTo(existingCopyId.toString());
        assertThat(copied.summary()).isEqualTo("Existing summary");
        verify(noteRepository, never()).save(any(NoteEntity.class));
        verify(analyticsService, never()).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), eq(sourceNoteId), any());
    }

    @Test
    void copyPublicNote_backfillsStudyPackOnExistingCopyOnceSourceBecomesReady() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        UUID existingCopyId = UUID.randomUUID();

        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        NoteEntity existingCopy = buildNote(existingCopyId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "copied content");
        existingCopy.setCopiedFromNoteId(sourceNoteId);
        existingCopy.setCopiedFromUserId(sourceOwnerUserId);
        existingCopy.setCopiedFromPublic(Boolean.TRUE);
        StudyPackEntity sourceStudyPack = buildSourceStudyPack(sourceNoteId);

        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(noteRepository.findFirstByOwnerUserIdAndCopiedFromNoteId(ownerUserId, sourceNoteId))
                .thenReturn(Optional.of(existingCopy));
        when(studyPackRepository.findByNoteId(existingCopyId)).thenReturn(Optional.empty());
        when(studyPackRepository.findByNoteId(sourceNoteId)).thenReturn(Optional.of(sourceStudyPack));

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId);

        assertThat(copied.id()).isEqualTo(existingCopyId.toString());
        assertThat(copied.studyPackStatus()).isEqualTo("STUDY_PACK_READY");
        assertThat(copied.summary()).isEqualTo(sourceStudyPack.getSummary());

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getId()).isEqualTo(existingCopyId);
        assertThat(noteCaptor.getValue().getStatus()).isEqualTo(NoteStatus.GENERATED);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getId()).isNotEqualTo(sourceStudyPack.getId());
        assertThat(studyPackCaptor.getValue().getNoteId()).isEqualTo(existingCopyId);
        assertThat(studyPackCaptor.getValue().getOwnerUserId()).isEqualTo(ownerUserId);
        verify(analyticsService, never()).trackEvent(eq(ownerUserId), eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), eq(sourceNoteId), any());
    }

    @Test
    void copyPublicNote_doesNotBackfillWhenIncludeStudyPackIsFalse() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        UUID existingCopyId = UUID.randomUUID();

        NoteEntity source = buildNote(sourceNoteId, sourceOwnerUserId, NoteStatus.GENERATED, NoteVisibility.PUBLIC, "source content");
        NoteEntity existingCopy = buildNote(existingCopyId, ownerUserId, NoteStatus.DRAFT, NoteVisibility.PRIVATE, "copied content");
        existingCopy.setCopiedFromNoteId(sourceNoteId);
        existingCopy.setCopiedFromUserId(sourceOwnerUserId);
        existingCopy.setCopiedFromPublic(Boolean.TRUE);

        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(source));
        when(noteRepository.findFirstByOwnerUserIdAndCopiedFromNoteId(ownerUserId, sourceNoteId))
                .thenReturn(Optional.of(existingCopy));
        when(studyPackRepository.findByNoteId(existingCopyId)).thenReturn(Optional.empty());

        NoteResponse copied = noteService.copyNote(sourceNoteId.toString(), ownerUserId, false);

        assertThat(copied.id()).isEqualTo(existingCopyId.toString());
        assertThat(copied.studyPackStatus()).isEqualTo("DRAFT");
        verify(noteRepository, never()).save(any(NoteEntity.class));
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
    }

    @Test
    void copyPublicNoteForSignup_rejectsPrivateNotes() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        when(noteRepository.findByIdAndVisibility(sourceNoteId, NoteVisibility.PUBLIC)).thenReturn(Optional.empty());

        String id = sourceNoteId.toString();
        assertThatThrownBy(() -> noteService.copyPublicNoteForSignup(id, ownerUserId))
                .isInstanceOf(NoteNotFoundException.class);

        verify(noteRepository, never()).save(any(NoteEntity.class));
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
        // Personal strings now come from the join-guarded source (F6), same as the public list.
        when(noteCourseProgramRepository.findLegacyCourseProgramValuesByOwnerUserId(ownerUserId))
                .thenReturn(List.of("  nursing  ", "Nursing", "Senior High – STEM"));
        when(noteCourseProgramRepository.findNamesByOwnerUserId(ownerUserId))
                .thenReturn(List.of(ACCOUNTANCY_PROGRAM, "nursing"));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        List<String> coursePrograms = noteService.listMineCoursePrograms(ownerUserId);

        assertThat(coursePrograms).containsExactly(ACCOUNTANCY_PROGRAM, "Nursing", "Senior High – STEM");
    }

    @Test
    void listMineCoursePrograms_usesTheJoinGuardedSourceLikeThePublicListDoes() {
        // F6. C2 added the NOT EXISTS guard to the PUBLIC vocabulary but not the owner-scoped one, so the
        // private editor's own datalist kept suggesting a stale personal string from a note whose join rows
        // say something else. Same defect, same fix, one surface later. Asserts the WIRING; the guard
        // itself is SQL-level (finding B4).
        UUID ownerUserId = UUID.randomUUID();
        when(noteCourseProgramRepository.findLegacyCourseProgramValuesByOwnerUserId(ownerUserId))
                .thenReturn(List.of("Software Engineering"));
        when(noteCourseProgramRepository.findNamesByOwnerUserId(ownerUserId))
                .thenReturn(List.of(ACCOUNTANCY_PROGRAM));

        List<String> coursePrograms = noteService.listMineCoursePrograms(ownerUserId);

        assertThat(coursePrograms).contains(ACCOUNTANCY_PROGRAM, "Software Engineering");
        verify(noteRepository, never()).findCourseProgramValuesByOwnerUserId(any());
    }

    @Test
    void listPublicCoursePrograms_includesOnlyCatalogProgramsUsedByPublicNotes() {
        when(noteCourseProgramRepository.findNamesByVisibility(NoteVisibility.PUBLIC.name()))
                .thenReturn(List.of("Nursing", ACCOUNTANCY_PROGRAM, "nursing"));

        List<String> coursePrograms = noteService.listPublicCoursePrograms();

        assertThat(coursePrograms).containsExactly(ACCOUNTANCY_PROGRAM, "Nursing");
        verify(noteCourseProgramRepository, never())
                .findLegacyCourseProgramValuesByVisibility(NoteVisibility.PUBLIC.name());
        verify(noteRepository, never()).findCourseProgramValuesByVisibility(any());
    }

    @Test
    void listMineStatuses_resolvesAllNoteAndStudyPackStatusCombinations() {
        UUID ownerUserId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        UUID generatingNoteId = UUID.randomUUID();
        UUID failedNoteId = UUID.randomUUID();
        UUID generatedNoteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        when(noteRepository.findStatusProjectionsByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId))
                .thenReturn(List.of(
                        new NoteStatusProjection(draftNoteId, NoteStatus.DRAFT, now),
                        new NoteStatusProjection(generatingNoteId, NoteStatus.GENERATING, now.minusMinutes(1)),
                        new NoteStatusProjection(failedNoteId, NoteStatus.FAILED, now.minusMinutes(2)),
                        new NoteStatusProjection(generatedNoteId, NoteStatus.GENERATED, now.minusMinutes(3))
                ));
        StudyPackProgressProjection generatedPack = mock(StudyPackProgressProjection.class);
        when(generatedPack.getNoteId()).thenReturn(generatedNoteId);
        when(studyPackRepository.findProgressViewsByNoteIdIn(List.of(
                draftNoteId,
                generatingNoteId,
                failedNoteId,
                generatedNoteId
        ))).thenReturn(List.of(generatedPack));

        List<NoteStatusResponse> statuses = noteService.listMineStatuses(ownerUserId);

        assertThat(statuses).containsExactly(
                new NoteStatusResponse(draftNoteId.toString(), NoteStudyPackStatusResolver.DRAFT),
                new NoteStatusResponse(generatingNoteId.toString(), NoteStudyPackStatusResolver.GENERATING),
                new NoteStatusResponse(failedNoteId.toString(), NoteStudyPackStatusResolver.FAILED),
                new NoteStatusResponse(generatedNoteId.toString(), NoteStudyPackStatusResolver.STUDY_PACK_READY)
        );
    }

    @Test
    void listMineStatuses_skipsStudyPackLookupForEmptyLibrary() {
        UUID ownerUserId = UUID.randomUUID();
        when(noteRepository.findStatusProjectionsByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId))
                .thenReturn(List.of());

        assertThat(noteService.listMineStatuses(ownerUserId)).isEmpty();

        verify(studyPackRepository, never()).findProgressViewsByNoteIdIn(any());
    }

    @Test
    void listMine_mapsStudyPackKeyConceptCountAndKeepsItNullWithoutAStudyPack() {
        UUID ownerUserId = UUID.randomUUID();
        UUID readyNoteId = UUID.randomUUID();
        UUID draftNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(readyNoteId);
        studyPack.setKeyConcepts(List.of("Cells", "Genetics", "Evolution"));
        studyPack.setQuiz(List.of(
                new QuizItem("Question one", List.of("A", "B"), 0, "Cells", "Explanation"),
                new QuizItem("Question two", List.of("A", "B"), 0, "Genetics", "Explanation")
        ));

        NoteListItemProjection readyProjection = buildListItemProjection(readyNoteId, ownerUserId, NoteStatus.GENERATED);
        NoteListItemProjection draftProjection = buildListItemProjection(draftNoteId, ownerUserId, NoteStatus.DRAFT);
        when(noteRepository.findListItemProjectionsByOwnerUserId(eq(ownerUserId), any()))
                .thenReturn(List.of(readyProjection, draftProjection));
        when(studyPackRepository.findByNoteIdIn(List.of(readyNoteId, draftNoteId))).thenReturn(List.of(studyPack));

        List<NoteListItemResponse> response = noteService.listMine(ownerUserId);

        // Distinct counts (3 concepts, 2 questions) deliberately, not equal — these are adjacent
        // Integer params in a 31-arg positional constructor, populated on adjacent lines; a swap
        // between them would slip through if both fixture values were the same.
        assertThat(response).extracting(NoteListItemResponse::keyConceptCount).containsExactly(3, null);
        assertThat(response).extracting(NoteListItemResponse::quizCount).containsExactly(2, null);
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

        // ⚠️ REWIRED, NOT DELETED. Slug MATCHING now lives in SQL and is covered by
        // NativeQueryPostgresIntegrationTest.seoSlugResolutionIsBoundedAndKeepsItsFallbackSemantics --
        // a mocked repository cannot test it. What THIS test still covers is service glue the SQL knows
        // nothing about: author attribution and the study-pack join.
        when(noteRepository.findPublicNoteIdBySeoSlugs(anyString(), anyString(), anyBoolean()))
                .thenReturn(Optional.of(noteId));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(publicNote));
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

        // Rewired for the same reason as above: matching is SQL now; this covers the subject passthrough.
        when(noteRepository.findPublicNoteIdBySeoSlugs(anyString(), anyString(), anyBoolean()))
                .thenReturn(Optional.of(noteId));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(publicNote));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.empty());
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.empty());

        var response = noteService.getPublicBySeoPath("biology-cell-division", "mitosis-overview", null);

        assertThat(response.id()).isEqualTo(noteId.toString());
        assertThat(response.subject()).isEqualTo("Biology – Cell Division");
    }

    @Test
    void getPublicBySeoPath_rejectsMissingOrPrivateMatch() {
        // The 404 contract is service-level and survives the move to SQL: an unresolved slug pair
        // must throw rather than return anything.
        when(noteRepository.findPublicNoteIdBySeoSlugs(anyString(), anyString(), anyBoolean()))
                .thenReturn(Optional.empty());
        lenient().when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
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
        officialOwner.setDisplayName("Einar");
        officialOwner.setEmail("einar.lagera@gmail.com");
        officialOwner.setRole(UserRole.ADMIN);
        officialOwner.setLearnerLevel(LearnerLevel.PROFESSIONAL);

        when(noteRepository.countPublicLibraryMatches(any())).thenReturn(2L);
        when(noteRepository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(viewerNoteId, officialNoteId));
        when(studyPackRepository.findByNoteIdIn(List.of(viewerNoteId, officialNoteId)))
                .thenReturn(List.of(officialStudyPack));
        when(userRepository.findAllById(List.of(viewerUserId, officialOwnerUserId)))
                .thenReturn(List.of(viewer, officialOwner));

        var response = noteService.listPublic(viewerUserId, null, null, null, null, null, null, null);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(NoteListItemResponse::id)
                .containsExactly(viewerNoteId.toString(), officialNoteId.toString());
        assertThat(response.items().getFirst().ownerUserId()).isNull();
        assertThat(response.items().getFirst().courseProgram()).isEqualTo("Nursing");
        assertThat(response.items().getFirst().authorDisplayName()).isEqualTo("Viewer");
        assertThat(response.items().getFirst().contentPreview()).isEqualTo("viewer content");
        assertThat(response.items().getFirst().summaryPreview()).isEmpty();
        assertThat(response.items().getFirst().isOfficialAuthor()).isFalse();
        assertThat(response.items().getFirst().isCurrentUser()).isTrue();
        assertThat(response.items().getFirst().copyCount()).isZero();
        assertThat(response.items().getFirst().likeCount()).isZero();
        assertThat(response.items().getFirst().shareCount()).isZero();
        assertThat(response.items().getFirst().viewCount()).isZero();
        assertThat(response.items().getFirst().copiedFromNoteId()).isNull();
        assertThat(response.items().getFirst().copiedFromPublic()).isFalse();
        assertThat(response.items().getFirst().likedByCurrentUser()).isFalse();
        assertThat(response.items().get(1).ownerUserId()).isNull();
        assertThat(response.items().get(1).courseProgram()).isEqualTo("Chemistry");
        assertThat(response.items().get(1).authorDisplayName()).isEqualTo("Einar");
        assertThat(response.items().get(1).contentPreview()).isEqualTo("official content");
        assertThat(response.items().get(1).summaryPreview()).isEqualTo("Official summary preview");
        assertThat(response.items().get(1).isOfficialAuthor()).isTrue();
        assertThat(response.items().get(1).isCurrentUser()).isFalse();
        assertThat(response.items().get(1).copyCount()).isZero();
        assertThat(response.items().get(1).likeCount()).isZero();
        assertThat(response.items().get(1).shareCount()).isZero();
        assertThat(response.items().get(1).viewCount()).isZero();
        assertThat(response.items().get(1).likedByCurrentUser()).isFalse();
    }

    /**
     * ⚠️ v0.119.1 REMOVED EIGHTEEN {@code listPublic_*} CASES FROM THIS CLASS, and where each one's
     * coverage went is recorded here so the deletion can be checked rather than taken on trust.
     *
     * <p>They stubbed {@code findByVisibilityOrderByUpdatedAtDesc} and then asserted the filtering,
     * sorting and size-limiting that {@code listPublicLegacy} performed IN JAVA. That code is gone:
     * the unpaginated shape now filters, orders and limits in the database, because doing it in Java
     * meant loading the entire public catalog on an anonymous endpoint. A mocked repository cannot
     * assert an ORDER BY or a LIMIT, so rewriting them here would have produced tests that stub an id
     * order and then assert the same id order came back — green under both the defect and the fix.
     *
     * <p>Replacement coverage, all against real rows:
     * <ul>
     *   <li>subject, search, tag, course-program, creator and combined filters, and legacy-versus-
     *       paginated equivalence — {@code NoteServicePublicLibraryPaginationIntegrationTest}
     *       ({@code legacyModeUsesJoinedProgramsBeforeThePersonalNoteStringAndMatchesPaginatedResults},
     *       {@code legacyModePreservesCombinedFiltersTotalAndNullablePaginationFields},
     *       {@code paginatedSearchMatchesEachLegacyPreviewAndTagField}).
     *   <li>{@code size}, the new cap, the {@code updated_at desc} default and an unknown sort being
     *       ignored rather than rejected — {@code
     *       unpaginatedRequestIsBoundedKeepsUpdatedAtOrderAndNeverRejectsAnUnknownSort}.
     *   <li>every one of the eight sort orders and both eligibility filters — {@code
     *       NativeQueryPostgresIntegrationTest.rankedPublicLibrarySortsReproduceTheJavaRankingOrderExactly}
     *       against real PostgreSQL rows, plus {@code rankedSortsPreserveEligibilityAndUngatedSemantics}.
     *   <li>a note stored with any target profile type still being listed — {@code
     *       discoverySectionsAreCappedUnfilteredAndMutuallyExclusive}, which seeds a BOARD_TAKER note.
     * </ul>
     *
     * <p>The two cases kept below survived because their subject is {@code NoteService}'s own glue —
     * DTO enrichment and the pre-filter {@code total} — not the SQL.
     */
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

        when(noteRepository.countPublicLibraryMatches(any())).thenReturn(2L);
        when(noteRepository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(likedNoteId, plainNoteId));
        org.mockito.Mockito.doReturn(List.of(buildListItemProjection(likedNote), buildListItemProjection(plainNote)))
                .when(noteRepository).findPublicLibraryListItemProjectionsByIdIn(any());
        when(userRepository.findAllById(List.of(ownerUserId))).thenReturn(List.of(owner));
        when(publicNoteLikeRepository.countLikesByNoteIds(List.of(likedNoteId, plainNoteId)))
                .thenReturn(List.of(likedNoteLikes));
        when(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(viewerUserId, List.of(likedNoteId, plainNoteId)))
                .thenReturn(List.of(likedNoteId));

        var response = noteService.listPublic(viewerUserId, null, null, null, null, null, null, null);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).extracting(NoteListItemResponse::likeCount).containsExactly(12L, 0L);
        assertThat(response.items()).extracting(NoteListItemResponse::likedByCurrentUser).containsExactly(true, false);
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

    @Test
    void listPublicTags_returnsTrimmedCaseDeduplicatedTagsSortedAlphabetically() {
        when(noteRepository.findDistinctPublicTags())
                .thenReturn(java.util.Arrays.asList("Biology", "biology", "Chemistry", " algebra ", "", null));

        List<String> tags = noteService.listPublicTags();

        assertThat(tags).containsExactly("algebra", "Biology", "Chemistry");
        verify(noteRepository).findDistinctPublicTags();
    }

    // --- listPublic sort tests ---

    @Test
    void listPublic_legacyModeDropsMissingProjectionWithoutChangingPreFilterTotal() {
        NoteEntity retained = buildNote(
                UUID.randomUUID(), UUID.randomUUID(), NoteStatus.DRAFT, NoteVisibility.PUBLIC, "retained"
        );
        UUID deletedBetweenQueries = UUID.randomUUID();
        when(noteRepository.countPublicLibraryMatches(any())).thenReturn(2L);
        when(noteRepository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(retained.getId(), deletedBetweenQueries));
        org.mockito.Mockito.doReturn(List.of(buildListItemProjection(retained)))
                .when(noteRepository).findPublicLibraryListItemProjectionsByIdIn(any());

        var result = noteService.listPublic(null, null, null, null, null, null, null, null);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(NoteListItemResponse::id)
                .containsExactly(retained.getId().toString());
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

    private StudyPackEntity buildSourceStudyPack(UUID noteId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Copied title");
        studyPack.setSummary("Copied summary");
        studyPack.setSubject("Copied subject");
        studyPack.setKeyConcepts(List.of("Concept A", "Concept B"));
        studyPack.setQuiz(List.of(new QuizItem("Question", List.of("A", "B"), 0, "Concept A", "Explanation")));
        studyPack.setTags(new String[]{"copied", "ready"});
        studyPack.setModelTier(ModelTier.PREMIUM);
        studyPack.setModelUsed("gpt-4.1");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setAnonId("anon-source");
        studyPack.setShareToken("share-source");
        studyPack.setSourceText("source text");
        studyPack.setInputTokens(10);
        studyPack.setOutputTokens(20);
        studyPack.setCachedInputTokens(5);
        studyPack.setEstimatedCost(new BigDecimal("0.0100"));
        studyPack.setOcrConfidence(0.9);
        studyPack.setErrorCode("SOURCE_ERROR");
        return studyPack;
    }

    private UserEntity buildUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setFirstName("Test");
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setProfileType(ProfileType.STUDENT);
        // A fully onboarded account is the realistic default for every test here. Curator branches are
        // gated on onboarding being complete -- nobody curates mid-onboarding -- so a null value would
        // silently make every ADMIN/TEACHER fixture behave as a learner.
        user.setOnboardingCompletedAt(OffsetDateTime.now());
        return user;
    }

    private UserEntity curator(UUID userId) {
        UserEntity user = buildUser(userId, "curator@example.com");
        user.setProfileType(ProfileType.TEACHER);
        return user;
    }

    private UpsertNoteRequest curatorRequest(List<UUID> courseProgramIds, String domainContext) {
        return new UpsertNoteRequest(
                "Title",
                "Subject",
                courseProgramIds,
                null,
                domainContext,
                null,
                List.of(),
                "content"
        );
    }

    private StudyPackGenerationContext authoringContext(String automaticDomain) {
        return new StudyPackGenerationContext(
                null,
                automaticDomain,
                "Subject",
                List.of(),
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authoringDomainMetadata(UUID ownerUserId) {
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(
                eq(ownerUserId),
                eq(AnalyticsEventType.NOTE_AUTHORING_DOMAIN_RECORDED),
                any(UUID.class),
                metadataCaptor.capture()
        );
        return metadataCaptor.getValue();
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
        noteFixtures.put(noteId, note);
        return note;
    }

    private NoteListItemProjection buildListItemProjection(NoteEntity note) {
        NoteListItemProjection projection = mock(NoteListItemProjection.class);
        lenient().when(projection.getId()).thenReturn(note.getId());
        lenient().when(projection.getOwnerUserId()).thenReturn(note.getOwnerUserId());
        lenient().when(projection.getTitle()).thenAnswer(ignored -> note.getTitle());
        lenient().when(projection.getCourseProgram()).thenAnswer(ignored -> note.getCourseProgram());
        lenient().when(projection.getDomainContext()).thenAnswer(ignored -> note.getDomainContext());
        lenient().when(projection.getLearnerLevel()).thenAnswer(ignored -> note.getLearnerLevel());
        lenient().when(projection.getSubject()).thenAnswer(ignored -> note.getSubject());
        lenient().when(projection.getTags()).thenAnswer(ignored -> note.getTags());
        lenient().when(projection.getContent()).thenAnswer(ignored -> note.getContent());
        lenient().when(projection.getStatus()).thenAnswer(ignored -> note.getStatus());
        lenient().when(projection.getVisibility()).thenAnswer(ignored -> note.getVisibility());
        lenient().when(projection.getCreatedAt()).thenAnswer(ignored -> note.getCreatedAt());
        lenient().when(projection.getUpdatedAt()).thenAnswer(ignored -> note.getUpdatedAt());
        lenient().when(projection.getCopiedFromNoteId()).thenAnswer(ignored -> note.getCopiedFromNoteId());
        lenient().when(projection.getCopiedFromPublic()).thenAnswer(ignored -> note.getCopiedFromPublic());
        return projection;
    }

    private NoteListItemProjection buildListItemProjection(UUID noteId, UUID ownerUserId, NoteStatus status) {
        NoteListItemProjection projection = mock(NoteListItemProjection.class);
        when(projection.getId()).thenReturn(noteId);
        when(projection.getOwnerUserId()).thenReturn(ownerUserId);
        when(projection.getTitle()).thenReturn("Title");
        when(projection.getCourseProgram()).thenReturn("Course");
        when(projection.getSubject()).thenReturn("Subject");
        when(projection.getTags()).thenReturn(new String[]{"tag"});
        when(projection.getContent()).thenReturn("content");
        when(projection.getStatus()).thenReturn(status);
        when(projection.getVisibility()).thenReturn(NoteVisibility.PRIVATE);
        when(projection.getCreatedAt()).thenReturn(OffsetDateTime.now().minusDays(1));
        when(projection.getUpdatedAt()).thenReturn(OffsetDateTime.now());
        return projection;
    }
}
