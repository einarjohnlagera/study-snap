package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.StudyPackListPageResponse;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.OcrDisabledException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.exception.NoteGenerationInProgressException;
import com.studysnap.backend.exception.NoteRegenerationStudyPackRequiredException;
import com.studysnap.backend.exception.NoteRegenerationTopicRequiredException;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.SubjectTooLongException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackListItemProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.CreatedAtIdCursorUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class StudyPackServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private StudyPackDraftRepository studyPackDraftRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OcrService ocrService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private ActivityTrackingService activityTrackingService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private StudyPackUsageService studyPackUsageService;
    @Mock
    private OcrRateLimitService ocrRateLimitService;
    @Mock
    private OcrUsageProtectionService ocrUsageProtectionService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private ExamQuestionPoolService examQuestionPoolService;
    @Mock
    private OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    @Mock
    private OnboardingGuardService onboardingGuardService;
    @Mock
    private StudyPackQuizMasteryService studyPackQuizMasteryService;
    @Mock
    private NoteGenerationService noteGenerationService;
    @Mock
    private NoteGenerationUsageProtectionService noteGenerationUsageProtectionService;
    @Mock
    private GeneratedQuizService generatedQuizService;

    private StudyPackService studyPackService;
    private static final TransactionOperations TEST_TRANSACTION_OPERATIONS = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @BeforeEach
    void setUp() {
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                new StudySnapProperties(),
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(Runnable::run),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );
        lenient().when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(studyPackRepository.findByNoteId(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(noteRepository.findAllSubjectValues()).thenReturn(List.of());
        lenient().when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(generationContextResolver.resolve(any(UUID.class), any())).thenReturn(
                new com.studysnap.backend.service.model.StudyPackGenerationContext(null, null, null, List.of())
        );
        lenient().when(studyPackQuizMasteryService.resolve(any(UUID.class), any(StudyPackEntity.class)))
                .thenReturn(com.studysnap.backend.service.model.StudyPackQuizMastery.notMastered());
    }

    @Test
    void listMine_mapsLeanProjectionAndNativeQuizCountsWithUnchangedPagination() {
        UUID userId = UUID.randomUUID();
        StudyPackListItemProjection first = listProjection(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "First",
                "A long summary that should be preserved through the existing summary preview utility.",
                "Biology",
                new String[]{"cells", "exam"},
                OffsetDateTime.parse("2026-05-03T10:00:00Z")
        );
        StudyPackListItemProjection second = listProjection(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Second",
                "Short summary",
                null,
                null,
                OffsetDateTime.parse("2026-05-02T10:00:00Z")
        );
        StudyPackListItemProjection extra = listProjection(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Extra",
                "Extra summary",
                "Chemistry",
                new String[]{"hidden"},
                OffsetDateTime.parse("2026-05-01T10:00:00Z")
        );
        when(studyPackRepository.findListItemProjectionsByOwnerUserIdOrderByCreatedAtDescIdDesc(
                userId,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(first, second, extra));
        when(studyPackRepository.findQuizCountsByIdIn(List.of(first.id(), second.id())))
                .thenReturn(List.<Object[]>of(
                        new Object[]{first.id(), 3},
                        new Object[]{second.id(), null}
                ));

        StudyPackListPageResponse response = studyPackService.listMine(userId, 2, null);

        assertThat(response.hasMore()).isTrue();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).id()).isEqualTo(first.id().toString());
        assertThat(response.items().get(0).title()).isEqualTo("First");
        assertThat(response.items().get(0).summaryPreview()).isEqualTo("A long summary that should be preserved through the existing summary preview utility.");
        assertThat(response.items().get(0).quizCount()).isEqualTo(3);
        assertThat(response.items().get(0).subject()).isEqualTo("Biology");
        assertThat(response.items().get(0).tags()).containsExactly("cells", "exam");
        assertThat(response.items().get(0).createdAt()).isEqualTo(first.createdAt());
        assertThat(response.items().get(1).quizCount()).isZero();
        assertThat(response.items().get(1).tags()).isEmpty();
        CreatedAtIdCursorUtils.CursorToken nextCursor = CreatedAtIdCursorUtils.decode(response.nextCursor());
        assertThat(nextCursor.createdAt()).isEqualTo(second.createdAt());
        assertThat(nextCursor.id()).isEqualTo(second.id());
        verify(studyPackRepository).findQuizCountsByIdIn(List.of(first.id(), second.id()));
    }

    @Test
    void listMine_usesProjectionCursorFinderForSubsequentPages() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime cursorCreatedAt = OffsetDateTime.parse("2026-05-02T10:00:00Z");
        UUID cursorId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String cursor = CreatedAtIdCursorUtils.encode(cursorCreatedAt, cursorId);
        StudyPackListItemProjection next = listProjection(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Next",
                "Next summary",
                "Chemistry",
                new String[0],
                OffsetDateTime.parse("2026-05-01T10:00:00Z")
        );
        when(studyPackRepository.findListItemProjectionsByOwnerUserIdAndCursor(
                userId,
                cursorCreatedAt,
                cursorId,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(next));
        when(studyPackRepository.findQuizCountsByIdIn(List.of(next.id())))
                .thenReturn(List.<Object[]>of(new Object[]{next.id(), 1}));

        StudyPackListPageResponse response = studyPackService.listMine(userId, 2, cursor);

        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(next.id().toString());
        assertThat(response.items().get(0).quizCount()).isEqualTo(1);
        verify(studyPackRepository).findListItemProjectionsByOwnerUserIdAndCursor(
                userId,
                cursorCreatedAt,
                cursorId,
                PageRequest.of(0, 3)
        );
    }

    @Test
    void listMine_emptyPageDoesNotRunQuizCountQuery() {
        UUID userId = UUID.randomUUID();
        when(studyPackRepository.findListItemProjectionsByOwnerUserIdOrderByCreatedAtDescIdDesc(
                userId,
                PageRequest.of(0, 21)
        )).thenReturn(List.of());

        StudyPackListPageResponse response = studyPackService.listMine(userId, null, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasMore()).isFalse();
        verify(studyPackRepository, never()).findQuizCountsByIdIn(any());
    }

    @Test
    void updateMetadata_rejectsSubjectExpandedPastStorageAfterNormalization() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Title");
        studyPack.setSubject("Subject");
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        String rawSubject = "x".repeat(61) + "-y";

        assertThatThrownBy(() -> studyPackService.updateMetadata(
                studyPackId.toString(), userId, "Title", rawSubject
        )).isInstanceOf(SubjectTooLongException.class);

        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
    }

    @Test
    void createFromText_withDraftNote_marksNoteGenerated_andConsumesGenerationCredit() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Generated title",
                "Generated summary",
                "Biology",
                List.of("cells", "review"),
                List.of("Cell membrane", "Mitochondria"),
                List.of(new QuizItem(
                        "What powers the cell?",
                        List.of("Nucleus", "Mitochondria", "Ribosome", "Golgi body"),
                        "Mitochondria",
                        "Cell biology",
                        "Mitochondria generate ATP."
                )),
                "gpt-4.1-mini",
                100,
                220,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(generationContextResolver.resolve(userId, draftNote)).thenReturn(
                new StudyPackGenerationContext(
                        LearnerLevel.COLLEGE,
                        "Biology",
                        "Subject",
                        List.of("draft")
                )
        );
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        StudyPackResponse response = studyPackService.createFromText(
                new CreateStudyPackRequest(null, noteId.toString()),
                userId
        );

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        StudyPackEntity savedStudyPack = studyPackCaptor.getValue();
        assertThat(savedStudyPack.getNoteId()).isEqualTo(noteId);
        assertThat(savedStudyPack.getSummary()).isEqualTo("Generated summary");
        assertThat(savedStudyPack.getKeyConcepts()).containsExactly("Cell membrane", "Mitochondria");
        assertThat(savedStudyPack.getQuiz()).hasSize(1);

        verify(noteRepository).save(draftNote);
        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATED);

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateStudyPack(eq("draft note content"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("Biology");
        assertThat(contextCaptor.getValue().subject()).isEqualTo("Subject");
        assertThat(contextCaptor.getValue().tags()).containsExactly("draft");

        verify(userUsageService).incrementStudyPackGeneration(eq(userId), any(OffsetDateTime.class));
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.STUDY_PACK_GENERATED), eq(savedStudyPack.getId()), any());
        assertThat(response.noteId()).isEqualTo(noteId.toString());
        assertThat(response.summary()).isEqualTo("Generated summary");
        assertThat(response.keyConcepts()).containsExactly("Cell membrane", "Mitochondria");
        assertThat(response.quiz()).hasSize(1);
    }

    @Test
    void createFromText_multiProgramNoteWithoutDomainContextRejectsBeforeQuotaOrLlm() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        MultiProgramDomainContextRequiredException exception =
                new MultiProgramDomainContextRequiredException();
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        doThrow(exception).when(generationContextResolver).assertGenerationReady(draftNote);
        CreateStudyPackRequest request = new CreateStudyPackRequest(null, noteId.toString());

        assertThatThrownBy(() -> studyPackService.createFromText(request, userId))
                .isSameAs(exception);

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.DRAFT);
        verify(studyPackUsageService, never()).resolveUsage(any(UUID.class), any(OffsetDateTime.class));
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), anyString());
        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(), any());
    }

    @Test
    void createFromText_withoutNoteId_persistsTargetProfileTypeDerivedFromTheOwner() {
        // The /study paste-text flow: createStudyPackFromText posts {notesText} with no noteId, so
        // resolveSourceNoteForGeneration returns null and createFromText takes the createGeneratedNote
        // branch. That branch never set target_profile_type, which is NOT NULL with no database
        // default, so the insert failed AFTER the LLM call had already billed quota.
        //
        // Assert the PERSISTED value, not merely that the call succeeds -- "it returns without
        // throwing" is exactly the assertion shape that let this ship for five months, because every
        // other fixture supplied the value by hand.
        UUID userId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        owner.setProfileType(ProfileType.BOARD_EXAM);
        stubTextGeneration(userId, owner);

        studyPackService.createFromText(new CreateStudyPackRequest("pasted notes", null), userId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        // BOARD_EXAM is the discriminating case: a hardcoded STUDENT would pass a weaker assertion.
        assertThat(noteCaptor.getValue().getTargetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER);
    }

    @Test
    void createFromText_withoutNoteId_persistsNonNullTargetProfileTypeWhenTheOwnerCannotBeLoaded() {
        // The column is NOT NULL, so a missing owner must still produce a valid value rather than a
        // constraint violation.
        UUID userId = UUID.randomUUID();
        stubTextGeneration(userId, null);

        studyPackService.createFromText(new CreateStudyPackRequest("pasted notes", null), userId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
    }

    @Test
    void createFromText_clampsOverlongGeneratedSubjectAndStillPersistsStudyPack() {
        UUID userId = UUID.randomUUID();
        String generatedSubject = "A generated grouping subject with several descriptive words near the storage boundary and beyond";
        stubTextGeneration(userId, null, generatedSubject);

        studyPackService.createFromText(new CreateStudyPackRequest("pasted notes", null), userId);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getSubject())
                .hasSizeLessThanOrEqualTo(64)
                .isEqualTo("A generated grouping subject with several descriptive words near");
    }

    private void stubTextGeneration(UUID userId, UserEntity owner) {
        stubTextGeneration(userId, owner, "Biology");
    }

    private void stubTextGeneration(UUID userId, UserEntity owner, String generatedSubject) {
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(generationContextResolver.resolve(userId, null)).thenReturn(
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Subject", List.of())
        );
        when(llmStudyPackService.generateStudyPack(eq("pasted notes"), any(StudyPackGenerationContext.class)))
                .thenReturn(new GeneratedStudyPackContent(
                        "Generated title",
                        "Generated summary",
                        generatedSubject,
                        List.of("cells"),
                        List.of("Cell membrane"),
                        List.of(new QuizItem(
                                "What powers the cell?",
                                List.of("Nucleus", "Mitochondria", "Ribosome", "Golgi body"),
                                "Mitochondria",
                                "Cell biology",
                                "Mitochondria generate ATP."
                        )),
                        "gpt-4.1-mini",
                        100,
                        220,
                        0,
                        new BigDecimal("0.0100")
                ));
        when(userRepository.findById(userId)).thenReturn(Optional.ofNullable(owner));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createFromText_queuesOfficialTemplateSeedForTheSavedStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Generated title",
                "Generated summary",
                "Biology",
                List.of("cells", "review"),
                List.of("Cell membrane", "Mitochondria"),
                List.of(new QuizItem(
                        "What powers the cell?",
                        List.of("Nucleus", "Mitochondria", "Ribosome", "Golgi body"),
                        "Mitochondria",
                        "Cell biology",
                        "Mitochondria generate ATP."
                )),
                "gpt-4.1-mini",
                100,
                220,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(generationContextResolver.resolve(userId, draftNote)).thenReturn(
                new StudyPackGenerationContext(
                        LearnerLevel.COLLEGE,
                        "Biology",
                        "Subject",
                        List.of("draft")
                )
        );
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.createFromText(new CreateStudyPackRequest(null, noteId.toString()), userId);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(officialChallengeQuizTemplateService).queueSeedIfEligible(eq(draftNote), studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getNoteId()).isEqualTo(noteId);
    }

    @Test
    void createFromText_reusesCanonicalSubjectWhenGeneratedSubjectMatchesExistingVariant() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Generated title",
                "Generated summary",
                "biology-cell division",
                List.of("cells", "review"),
                List.of("Cell membrane", "Mitosis"),
                List.of(new QuizItem(
                        "Which process divides the nucleus?",
                        List.of("Mitosis", "Meiosis", "Translation", "Respiration"),
                        "Mitosis",
                        "Cell division",
                        "Mitosis divides the nucleus."
                )),
                "gpt-4.1-mini",
                100,
                220,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(noteRepository.findAllSubjectValues()).thenReturn(List.of("Biology – Cell Division"));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.createFromText(new CreateStudyPackRequest(null, noteId.toString()), userId);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getSubject()).isEqualTo("Biology – Cell Division");
    }

    @Test
    void createFromText_rejectsGenerationWhenNoteAlreadyHasStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildDraftNote(noteId, userId, "already generated note");
        generatedNote.setStatus(NoteStatus.GENERATED);
        StudyPackEntity existingStudyPack = new StudyPackEntity();
        existingStudyPack.setId(UUID.randomUUID());
        existingStudyPack.setOwnerUserId(userId);
        existingStudyPack.setNoteId(noteId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(generatedNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.of(existingStudyPack));

        CreateStudyPackRequest request = new CreateStudyPackRequest(null, noteId.toString());
        assertThatThrownBy(() -> studyPackService.createFromText(
                request,
                userId
        ))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_ALREADY_HAS_STUDY_PACK");

        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void createFromText_rejectsWhenAiRateLimitIsExceeded() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        org.mockito.Mockito.doThrow(new AppException(
                "TOO_MANY_REQUESTS",
                "Too many requests. Please wait a moment and try again.",
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
        )).when(aiRateLimitService).assertAllowed(userId, PlanType.FREE, "study-pack");

        CreateStudyPackRequest request = new CreateStudyPackRequest(null, noteId.toString());
        assertThatThrownBy(() -> studyPackService.createFromText(
                request,
                userId
        ))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("TOO_MANY_REQUESTS");

        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void startAsyncGenerationFromNote_rejectsMissingProfileTypeBeforeLoadingNote() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        doThrow(exception).when(onboardingGuardService).assertProfileComplete(userId);

        assertThatThrownBy(() -> studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId))
                .isSameAs(exception);

        verify(noteRepository, never()).findByIdAndOwnerUserId(any(UUID.class), any(UUID.class));
        verify(noteRepository, never()).save(any(NoteEntity.class));
    }

    @Test
    void createFromImage_throwsTypedErrorWhenOcrIsDisabled() {
        UUID userId = UUID.randomUUID();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getOcr().setEnabled(false);
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                properties,
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(Runnable::run),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );
        MockMultipartFile image = new MockMultipartFile("image", "note.png", "image/png", "fake-image".getBytes());

        assertThatThrownBy(() -> studyPackService.createFromImage(image, null, userId))
                .isInstanceOf(OcrDisabledException.class);

        verify(ocrService, never()).extractText(any(MultipartFile.class));
        verify(subscriptionService, never()).resolvePlan(any(UUID.class));
    }

    @Test
    void createFromText_blocksOnlyAfterStudyPackLimitIsReached() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        StudySnapProperties properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyStudyPackLimit(5);
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                properties,
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(Runnable::run),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        5
                ));

        CreateStudyPackRequest request = new CreateStudyPackRequest(null, noteId.toString());
        assertThatThrownBy(() -> studyPackService.createFromText(
                request,
                userId
        ))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("MONTHLY_STUDY_PACK_LIMIT_REACHED");

        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void createFromText_doesNotConsumeCreditWhenGenerationFails() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        4
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenThrow(new RuntimeException("LLM unavailable"));

        CreateStudyPackRequest request = new CreateStudyPackRequest(null, noteId.toString());
        assertThatThrownBy(() -> studyPackService.createFromText(
                request,
                userId
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LLM unavailable");

        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
    }

    @Test
    void startAsyncGenerationFromNote_marksNoteGeneratingAndDefersLlmWork() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        List<Runnable> generationTasks = new ArrayList<>();
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                new StudySnapProperties(),
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(generationTasks::add),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATING);
        assertThat(draftNote.getGenerationEnqueuedAt()).isNotNull();
        ArgumentCaptor<NoteEntity> savedNote = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(savedNote.capture());
        assertThat(savedNote.getValue().getGenerationEnqueuedAt()).isNotNull();
        assertThat(generationTasks).hasSize(1);
        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void startAsyncGenerationFromNote_multiProgramWithoutDomainContextKeepsStatusAndSpendsNoQuota() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        MultiProgramDomainContextRequiredException exception =
                new MultiProgramDomainContextRequiredException();
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        doThrow(exception).when(generationContextResolver).assertGenerationReady(draftNote);
        String noteIdRaw = noteId.toString();

        assertThatThrownBy(() -> studyPackService.startAsyncGenerationFromNote(noteIdRaw, userId))
                .isSameAs(exception);

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(draftNote.getGenerationEnqueuedAt()).isNull();
        verify(noteRepository, never()).save(any(NoteEntity.class));
        verify(studyPackUsageService, never()).resolveUsage(any(UUID.class), any(OffsetDateTime.class));
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), anyString());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(), any());
    }

    @Test
    void startAsyncGenerationFromNote_retryReplacesPreviousGenerationEnqueuedAt() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        OffsetDateTime previousAttempt = OffsetDateTime.now().minusDays(1);
        NoteEntity failedNote = buildDraftNote(noteId, userId, "draft note content");
        failedNote.setStatus(NoteStatus.FAILED);
        failedNote.setGenerationEnqueuedAt(previousAttempt);
        List<Runnable> generationTasks = new ArrayList<>();
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                new StudySnapProperties(),
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(generationTasks::add),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(failedNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        assertThat(failedNote.getStatus()).isEqualTo(NoteStatus.GENERATING);
        assertThat(failedNote.getGenerationEnqueuedAt()).isAfter(previousAttempt);
        assertThat(generationTasks).hasSize(1);
    }

    @Test
    void startAsyncGenerationFromNote_lateCompletionAfterRecoveryPersistsNoStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        List<Runnable> generationTasks = new ArrayList<>();
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                userRepository,
                ocrService,
                llmStudyPackService,
                new StudySnapProperties(),
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                ocrRateLimitService,
                ocrUsageProtectionService,
                aiRateLimitService,
                generationContextResolver,
                TEST_TRANSACTION_OPERATIONS,
                new StudyPackGenerationTaskDispatcher(generationTasks::add),
                contentModerationService,
                examQuestionPoolService,
                officialChallengeQuizTemplateService,
                onboardingGuardService,
                studyPackQuizMasteryService,
                noteGenerationService,
                noteGenerationUsageProtectionService,
                generatedQuizService
        );
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generatedContent("Recovered too late"));

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);
        draftNote.setStatus(NoteStatus.FAILED);
        generationTasks.getFirst().run();

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.FAILED);
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
        // The three assertions above ALL held while the interlock was throwing an NPE that the outer
        // catch swallowed into a re-FAIL — they cannot tell a clean discard from a crash. These two
        // can: a discarded generation must not announce itself, and must not take the failure path
        // that re-writes FAILED and bumps updated_at (which floats the note up a library sorted by it).
        verify(analyticsService, never())
                .trackEvent(any(UUID.class), eq(AnalyticsEventType.STUDY_PACK_GENERATED), any(UUID.class), any());
        // Exactly one save: the enqueue that stamped GENERATING. A second would mean the worker
        // took the failure path and re-wrote FAILED over a row recovery had already resolved.
        verify(noteRepository, times(1)).save(any(NoteEntity.class));
    }

    @Test
    void startAsyncGenerationFromNote_marksFailedAndDoesNotConsumeCreditWhenWorkerFails() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenThrow(new RuntimeException("LLM unavailable"));

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.FAILED);
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
    }

    @Test
    void startAsyncGenerationFromNote_fromGeneratedNoteUpdatesExistingStudyPackInPlace() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity generatedNote = buildDraftNote(noteId, userId, "updated note content");
        generatedNote.setStatus(NoteStatus.GENERATED);
        StudyPackEntity existingStudyPack = new StudyPackEntity();
        existingStudyPack.setId(studyPackId);
        existingStudyPack.setOwnerUserId(userId);
        existingStudyPack.setNoteId(noteId);
        existingStudyPack.setCreatedAt(OffsetDateTime.now().minusDays(5));
        existingStudyPack.setSummary("Old summary");
        existingStudyPack.setKeyConcepts(List.of("Old concept"));
        existingStudyPack.setQuiz(List.of(new QuizItem("Old question", List.of("A", "B"), 0, "Old", "Old explanation")));
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "New title",
                "New summary",
                "Biology",
                List.of("cells"),
                List.of("New concept"),
                List.of(new QuizItem("New question", List.of("A", "B"), 1, "New", "New explanation")),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(generatedNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.of(existingStudyPack));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(existingStudyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("updated note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        StudyPackEntity savedStudyPack = studyPackCaptor.getValue();
        assertThat(savedStudyPack.getId()).isEqualTo(studyPackId);
        assertThat(savedStudyPack.getSummary()).isEqualTo("New summary");
        assertThat(savedStudyPack.getKeyConcepts()).containsExactly("New concept");
        assertThat(savedStudyPack.getQuiz()).extracting(QuizItem::question).containsExactly("New question");
        assertThat(savedStudyPack.getStatus()).isEqualTo(StudyPackStatus.DONE);
        assertThat(generatedNote.getStatus()).isEqualTo(NoteStatus.GENERATED);
        verify(userUsageService).incrementStudyPackGeneration(eq(userId), any(OffsetDateTime.class));
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.STUDY_PACK_GENERATED), eq(studyPackId), any());
    }

    @Test
    void startAsyncGenerationFromNote_regenerationFailurePreservesExistingStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildDraftNote(noteId, userId, "updated note content");
        generatedNote.setStatus(NoteStatus.GENERATED);
        StudyPackEntity existingStudyPack = new StudyPackEntity();
        existingStudyPack.setId(UUID.randomUUID());
        existingStudyPack.setOwnerUserId(userId);
        existingStudyPack.setNoteId(noteId);
        existingStudyPack.setSummary("Old summary");

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(generatedNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.of(existingStudyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("updated note content"), any(StudyPackGenerationContext.class)))
                .thenThrow(new RuntimeException("LLM unavailable"));

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        assertThat(generatedNote.getStatus()).isEqualTo(NoteStatus.FAILED);
        assertThat(existingStudyPack.getSummary()).isEqualTo("Old summary");
        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void startAsyncGenerationFromNote_keepsAiMetadataTransientByDefault() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        draftNote.setTitle("My Note");
        draftNote.setSubject("General Science");
        draftNote.setTags(new String[]{"history"});
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Suggested Title",
                "Generated summary",
                "History",
                List.of("History", "Battle of Puebla", "Culture"),
                List.of("Key concept"),
                List.of(),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        assertThat(draftNote.getTitle()).isEqualTo("My Note");
        assertThat(draftNote.getSubject()).isEqualTo("General Science");
        assertThat(draftNote.getTags()).containsExactly("history");
    }

    @Test
    void startAsyncGenerationFromNote_autoApplyMetadataUpdatesEmptyFieldsWhenRequested() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        draftNote.setSubject(null);
        draftNote.setTags(new String[0]);
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Suggested Title",
                "Generated summary",
                "History",
                List.of("History", "Battle of Puebla", "Culture"),
                List.of("Key concept"),
                List.of(),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId, true);

        assertThat(draftNote.getSubject()).isEqualTo("History");
        assertThat(draftNote.getTags()).containsExactly("History", "Battle of Puebla", "Culture");
    }

    /**
     * ⚠️ REWRITTEN BY v0.120.0, NOT DELETED -- and the rename is the point. This test previously
     * asserted {@code draftNote.getTitle()).isEqualTo("AI Refined Title")}, i.e. it PINNED THE DEFECT:
     * bulk generation overwriting a curator's typed topic with the Study Pack's generated title. That
     * is how the behaviour survived review -- a green suite was actively protecting it.
     *
     * <p>Everything else it checked was correct and is kept verbatim: the curator's batch subject is
     * still stamped, the LLM's tags are still applied, and the bulk bypass still spends no usage.
     */
    @Test
    void startAsyncGenerationFromNote_bulkBypassKeepsTheCuratorTitleAndPreservesSubjectWithoutUsage() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        draftNote.setTitle("Pasted topic");
        draftNote.setSubject("Admin Subject");
        draftNote.setTags(new String[0]);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Admin Subject",
                List.of()
        );
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "AI Refined Title",
                "Generated summary",
                "AI Subject",
                List.of("ai-tag", "review"),
                List.of("Key concept"),
                List.of(),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateStudyPack("draft note content", context)).thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(
                noteId.toString(),
                userId,
                false,
                false,
                context,
                "Admin Subject"
        );

        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATED);
        assertThat(draftNote.getTitle())
                .as("the curator's typed topic IS the canonical title -- the generated"
                        + " \"AI Refined Title\" stays on the Study Pack")
                .isEqualTo("Pasted topic");
        assertThat(draftNote.getSubject()).isEqualTo("Admin Subject");
        assertThat(draftNote.getTags())
                .as("the LLM's own tags still win whenever it returned any -- only the FALLBACK"
                        + " changed, and it is consulted solely when the LLM returned none")
                .containsExactly("ai-tag", "review");
        verify(studyPackUsageService, never()).resolveUsage(any(UUID.class), any(OffsetDateTime.class));
        verify(aiRateLimitService, never()).assertAllowed(any(UUID.class), any(PlanType.class), anyString());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void startAsyncGenerationFromNote_succeedsWhenAiSubjectSuggestionWasRejected() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        draftNote.setSubject("Electrical Engineering");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Suggested Title",
                "Generated summary",
                null,
                List.of("circuits", "voltage", "current"),
                List.of("Voltage", "Current"),
                List.of(new QuizItem(
                        "What does Ohm's Law relate?",
                        List.of("Force and mass", "Voltage and current", "Heat and pressure", "Speed and time"),
                        "Voltage and current",
                        "Ohm's Law",
                        "Ohm's Law relates voltage, current, and resistance."
                )),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId, true);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getSubject()).isNull();
        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATED);
        assertThat(draftNote.getSubject()).isEqualTo("Electrical Engineering");
        verify(userUsageService).incrementStudyPackGeneration(eq(userId), any(OffsetDateTime.class));
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.STUDY_PACK_GENERATED), eq(studyPackCaptor.getValue().getId()), any());
    }

    @Test
    void startAsyncGenerationFromNote_succeedsWhenOptionalMetadataSuggestionsAreEmpty() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Suggested Title",
                "Generated summary",
                null,
                List.of(),
                List.of("Core idea"),
                List.of(new QuizItem(
                        "What is the core idea?",
                        List.of("A", "B", "C", "D"),
                        "A",
                        "Core idea",
                        "The note explains the core idea."
                )),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        0
                ));
        when(llmStudyPackService.generateStudyPack(eq("draft note content"), any(StudyPackGenerationContext.class)))
                .thenReturn(generated);

        studyPackService.startAsyncGenerationFromNote(noteId.toString(), userId);

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        assertThat(studyPackCaptor.getValue().getSubject()).isNull();
        assertThat(studyPackCaptor.getValue().getTags()).containsExactly("Suggested Title");
        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATED);
        verify(userUsageService).incrementStudyPackGeneration(eq(userId), any(OffsetDateTime.class));
    }

    private NoteEntity buildDraftNote(UUID noteId, UUID ownerUserId, String content) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Draft title");
        note.setSubject("Subject");
        note.setTags(new String[]{"draft"});
        note.setContent(content);
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setCreatedAt(OffsetDateTime.now().minusHours(2));
        note.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        note.setCopiedFromPublic(Boolean.FALSE);
        return note;
    }

    private GeneratedStudyPackContent generatedContent(String title) {
        return new GeneratedStudyPackContent(
                title,
                "Generated summary",
                "Biology",
                List.of("cells"),
                List.of("Cell structure"),
                List.of(new QuizItem(
                        "What surrounds a cell?",
                        List.of("Membrane", "Bone", "Bark", "Stone"),
                        "Membrane",
                        "Cell structure",
                        "The cell membrane surrounds the cell."
                )),
                "gpt-4.1-mini",
                10,
                20,
                0,
                new BigDecimal("0.0100")
        );
    }

    private StudyPackListItemProjection listProjection(
            UUID id,
            String title,
            String summary,
            String subject,
            String[] tags,
            OffsetDateTime createdAt
    ) {
        return new StudyPackListItemProjection(id, title, summary, subject, tags, createdAt);
    }

    // ---------------------------------------------------------------------------------------------
    // v0.118.0 — combined Note + Study Pack regeneration, pre-LLM rejections.
    // The persisted-state guards (1, 2, 3, 11) live in NativeQueryPostgresIntegrationTest: a mocked
    // UserUsageService cannot express "the counter did not move", only "the method was not called".
    // ---------------------------------------------------------------------------------------------

    /**
     * GUARD 9. A note with NO existing Study Pack is rejected before any LLM call. This is regeneration,
     * not first generation: without a prior pack it would silently become "first generation that
     * overwrites the learner's typed content".
     */
    @Test
    void noteAndStudyPackRegeneration_rejectsANoteThatHasNoStudyPackYet() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = regenerationNote(noteId, ownerUserId, "Shear Force Diagrams");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studyPackService.startAsyncNoteAndStudyPackRegeneration(noteId.toString(), ownerUserId))
                .isInstanceOf(NoteRegenerationStudyPackRequiredException.class);

        assertThat(note.getStatus())
                .as("the note is NOT moved to GENERATING by a rejected request")
                .isEqualTo(NoteStatus.GENERATED);
        verify(llmStudyPackService, never()).generateNoteFromTopic(anyString(), any());
        verify(llmStudyPackService, never()).generateStudyPack(anyString(), any());
        verify(noteGenerationUsageProtectionService, never()).recordUsage(any(UUID.class), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any());
        verify(userUsageService, never()).incrementNoteGeneration(any(UUID.class), any());
    }

    /**
     * GUARD 8. A blank/null title is rejected before any LLM call. The note title IS the topic, and
     * because the service builds GenerateNoteFromTopicRequest internally the DTO's @NotBlank never fires.
     * Subject and content are deliberately NOT a fallback topic.
     */
    @Test
    void noteAndStudyPackRegeneration_rejectsANoteWithNoTitleBeforeAnyLlmCall() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = regenerationNote(noteId, ownerUserId, "   ");
        note.setSubject("Structural Engineering");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        StudyPackEntity existingPack = new StudyPackEntity();
        existingPack.setId(UUID.randomUUID());
        when(studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)).thenReturn(Optional.of(existingPack));

        assertThatThrownBy(() -> studyPackService.startAsyncNoteAndStudyPackRegeneration(noteId.toString(), ownerUserId))
                .isInstanceOf(NoteRegenerationTopicRequiredException.class);

        assertThat(note.getStatus())
                .as("the note is NOT moved to GENERATING by a rejected request")
                .isEqualTo(NoteStatus.GENERATED);
        verify(llmStudyPackService, never()).generateNoteFromTopic(anyString(), any());
        verify(noteGenerationUsageProtectionService, never()).recordUsage(any(UUID.class), any());
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any());
    }

    /** A note already GENERATING is rejected with the existing 409 contract, on the new path too. */
    @Test
    void noteAndStudyPackRegeneration_rejectsANoteThatIsAlreadyGenerating() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = regenerationNote(noteId, ownerUserId, "Shear Force Diagrams");
        note.setStatus(NoteStatus.GENERATING);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> studyPackService.startAsyncNoteAndStudyPackRegeneration(noteId.toString(), ownerUserId))
                .isInstanceOf(NoteGenerationInProgressException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_GENERATION_IN_PROGRESS");
    }

    /**
     * The note-generation meter is asserted FIRST. The learner-facing copy is "Uses 1 topic note and 1
     * Study Pack", so which limit reports back when both are exhausted is observable.
     */
    @Test
    void noteAndStudyPackRegeneration_assertsTheNoteGenerationQuotaBeforeTheStudyPackQuota() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = regenerationNote(noteId, ownerUserId, "Shear Force Diagrams");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        StudyPackEntity existingPack = new StudyPackEntity();
        existingPack.setId(UUID.randomUUID());
        when(studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)).thenReturn(Optional.of(existingPack));
        when(subscriptionService.resolvePlan(ownerUserId)).thenReturn(PlanType.FREE);
        doThrow(new MonthlyNoteGenerationLimitReachedException())
                .when(noteGenerationUsageProtectionService).assertQuotaAvailable(ownerUserId, PlanType.FREE);

        assertThatThrownBy(() -> studyPackService.startAsyncNoteAndStudyPackRegeneration(noteId.toString(), ownerUserId))
                .isInstanceOf(MonthlyNoteGenerationLimitReachedException.class);

        assertThat(note.getStatus()).isEqualTo(NoteStatus.GENERATED);
        verify(studyPackUsageService, never()).resolveUsage(any(UUID.class), any(OffsetDateTime.class));
        verify(llmStudyPackService, never()).generateNoteFromTopic(anyString(), any());
    }

    /**
     * ⚠️ PINS THE POST-COMMIT SIDE EFFECTS ON THE COMBINED PATH — the prompt names this the most likely
     * silent defect in the whole change, and it is silent by construction.
     *
     * <p>⚠️ CORRECTED after a cold falsification pass. An earlier version of this comment claimed
     * {@code initiatePool} REFRESHES a pool built from the replaced content. IT DOES NOT, and the claim
     * was wrong in the implementation prompt, here, and in the feature doc:
     * {@code ExamQuestionPoolService} returns early when a pool row already exists with status
     * {@code READY}, {@code PENDING} or {@code GENERATING}, and every note on this path already has a
     * pack, so any pool it has is one of those. What this test actually pins is that the combined path
     * still performs the SAME post-commit side effects as the existing path — no more, no less. The
     * stale pool is a REAL, SEPARATE limitation recorded in {@code RELEASES.md}; do not re-derive this
     * comment into a claim that this call fixes it.
     *
     * <p>The combined path inherits all three by extending the shared async method rather than adding a
     * sibling, which is precisely the shape that drops them. This test pins that inheritance.
     */
    @Test
    void noteAndStudyPackRegeneration_performsTheSamePostCommitSideEffectsAsTheExistingPath() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = regenerationNote(noteId, ownerUserId, "Shear Force Diagrams");
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(note));
        StudyPackEntity existingPack = new StudyPackEntity();
        existingPack.setId(UUID.randomUUID());
        existingPack.setNoteId(noteId);
        existingPack.setCreatedAt(OffsetDateTime.now().minusDays(2));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)).thenReturn(Optional.of(existingPack));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(existingPack));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(ownerUserId)).thenReturn(PlanType.FREE);
        when(studyPackUsageService.resolveUsage(eq(ownerUserId), any(OffsetDateTime.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0));
        when(noteGenerationService.generateFromTopic(any(GenerateNoteFromTopicRequest.class), eq(ownerUserId), any(), eq(false), eq(true)))
                .thenReturn(new GenerateNoteFromTopicResponse("Regenerated body."));
        when(llmStudyPackService.generateStudyPack(anyString(), any())).thenReturn(generatedContent("Regenerated pack"));

        studyPackService.startAsyncNoteAndStudyPackRegeneration(noteId.toString(), ownerUserId);

        verify(examQuestionPoolService).initiatePool(any(StudyPackEntity.class), eq(ownerUserId));
        verify(analyticsService).trackEvent(
                eq(ownerUserId), eq(AnalyticsEventType.STUDY_PACK_GENERATED), any(UUID.class), any());
        verify(officialChallengeQuizTemplateService).queueSeedIfEligible(any(NoteEntity.class), any(StudyPackEntity.class));
        // The content really was replaced, so the assertions above are not describing a no-op run.
        assertThat(note.getContent()).isEqualTo("Regenerated body.");
        assertThat(note.getStatus()).isEqualTo(NoteStatus.GENERATED);
    }

    private NoteEntity regenerationNote(UUID noteId, UUID ownerUserId, String title) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setContent("Existing body.");
        note.setStatus(NoteStatus.GENERATED);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setCreatedAt(OffsetDateTime.now().minusDays(2));
        note.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return note;
    }

}
