package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.MultiNoteQuizDocxExportRequest;
import com.studysnap.backend.dto.QuizDocxExportHeaderOverrideRequest;
import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GeneratedQuizExportNotAllowedException;
import com.studysnap.backend.exception.InvalidGeneratedQuizQuestionCountException;
import com.studysnap.backend.exception.InvalidQuizDocxVersionCountException;
import com.studysnap.backend.exception.MultipleExamVersionsNotAllowedForPlanException;
import com.studysnap.backend.exception.QuestionCountNotAllowedForPlanException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedQuizServiceTest {

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private GeneratedQuizRepository generatedQuizRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private AuthService authService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuizDocxExportService quizDocxExportService;
    @Mock
    private ExportUsageProtectionService exportUsageProtectionService;

    private GeneratedQuizService generatedQuizService;

    @BeforeEach
    void setUp() {
        generatedQuizService = new GeneratedQuizService(
                noteRepository,
                generatedQuizRepository,
                quizGenerationService,
                subscriptionService,
                new StudySnapProperties(),
                userUsageService,
                authService,
                aiRateLimitService,
                generationContextResolver,
                userRepository,
                quizDocxExportService,
                exportUsageProtectionService
        );
    }

    @Test
    void generate_createsTeacherQuizFromOwnedNoteAndConsumesQuizCredit() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        List<QuizItem> questions = buildQuestions();
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        when(generationContextResolver.resolve(userId, note)).thenReturn(
                new StudyPackGenerationContext(null, "Biology", "Biology", List.of("cells"))
        );
        when(quizGenerationService.generateTeacherQuiz(
                eq("Cell Structure"),
                eq("Cell membrane and nucleus notes"),
                eq(List.of()),
                eq(10),
                any(StudyPackGenerationContext.class)
        )).thenReturn(questions);
        when(generatedQuizRepository.save(any(GeneratedQuizEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GeneratedQuizResponse response = generatedQuizService.generate(noteId.toString(), userId);

        verify(authService).requireEmailVerified(userId);
        verify(aiRateLimitService).assertAllowed(userId, PlanType.FREE, "generated-quiz");
        verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
        assertThat(response.noteId()).isEqualTo(noteId.toString());
        assertThat(response.questions()).hasSize(10);

        ArgumentCaptor<GeneratedQuizEntity> captor = ArgumentCaptor.forClass(GeneratedQuizEntity.class);
        verify(generatedQuizRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getNoteId()).isEqualTo(noteId);
        assertThat(captor.getValue().getQuestions()).hasSize(10);
    }

    /**
     * ⚠️ v0.100.0. Moving the multi-program Domain Context block from save to generation made
     * "2+ programs, null Domain Context" a legal STORED state, which newly exposes every note-reading LLM
     * path -- not just Study Pack generation. The failure is silent: a null authoring domain drops the
     * Domain line and its constraint from the prompt with no error and no log. Found by the signoff cold
     * agent, which noted the Study-Pack claim held while this quiz path was ungated.
     */
    @Test
    void generate_rejectsAnAmbiguousNoteBeforeSpendingQuizCreditOrCallingTheLlm() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        doThrow(new MultiProgramDomainContextRequiredException())
                .when(generationContextResolver).assertGenerationReady(note);

        assertThatThrownBy(() -> generatedQuizService.generate(noteId.toString(), userId))
                .isInstanceOf(MultiProgramDomainContextRequiredException.class);

        verify(quizGenerationService, never()).generateTeacherQuiz(
                any(), any(), any(), anyInt(), any(StudyPackGenerationContext.class));
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(), any(OffsetDateTime.class));
        verify(generatedQuizRepository, never()).save(any(GeneratedQuizEntity.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 25})
    void generate_rejectsInvalidQuestionCount(int questionCount) {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);

        String id = noteId.toString();
        assertThatThrownBy(() -> generatedQuizService.generate(id, userId, questionCount))
                .isInstanceOf(InvalidGeneratedQuizQuestionCountException.class)
                .extracting(error -> ((InvalidGeneratedQuizQuestionCountException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(quizGenerationService, never()).generateTeacherQuiz(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void generate_blocksLongerTeacherQuizzesOnFreeBeforeLlmGeneration() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, UserRole.USER, ProfileType.TEACHER)));

        String id = noteId.toString();
        assertThatThrownBy(() -> generatedQuizService.generate(id, userId, 20))
                .isInstanceOf(QuestionCountNotAllowedForPlanException.class)
                .satisfies(error -> {
                    QuestionCountNotAllowedForPlanException exception = (QuestionCountNotAllowedForPlanException) error;
                    assertThat(exception.getCode()).isEqualTo("QUESTION_COUNT_NOT_ALLOWED");
                    assertThat(exception.getAction()).isEqualTo("UPGRADE_TO_PLUS");
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.PAYMENT_REQUIRED);
                });

        verify(userUsageService, never()).getMonthlyUsage(eq(userId), any(OffsetDateTime.class));
        verify(quizGenerationService, never()).generateTeacherQuiz(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void generate_allowsPlusTeacherToRequestThirtyQuestions() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        List<QuizItem> questions = buildQuestions(30);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, UserRole.USER, ProfileType.TEACHER)));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        when(generationContextResolver.resolve(userId, note)).thenReturn(
                new StudyPackGenerationContext(null, "Biology", "Biology", List.of("cells"))
        );
        when(quizGenerationService.generateTeacherQuiz(
                eq("Cell Structure"),
                eq("Cell membrane and nucleus notes"),
                eq(List.of()),
                eq(30),
                any(StudyPackGenerationContext.class)
        )).thenReturn(questions);
        when(generatedQuizRepository.save(any(GeneratedQuizEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GeneratedQuizResponse response = generatedQuizService.generate(noteId.toString(), userId, 30);

        assertThat(response.questions()).hasSize(30);
        verify(quizGenerationService).generateTeacherQuiz(any(), any(), any(), eq(30), any(StudyPackGenerationContext.class));
    }

    @Test
    void generate_teacherTargetLevelOverridesAuthoredCurriculumWithoutReaderScaffoldingContext() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        when(generationContextResolver.resolve(userId, note)).thenReturn(
                new StudyPackGenerationContext(
                        LearnerLevel.GRADE_SCHOOL,
                        "Education",
                        "Biology",
                        List.of("cells"),
                        null,
                        LearnerLevel.COLLEGE
                )
        );
        when(quizGenerationService.generateTeacherQuiz(any(), any(), any(), eq(10), any(StudyPackGenerationContext.class)))
                .thenReturn(buildQuestions());
        when(generatedQuizRepository.save(any(GeneratedQuizEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        generatedQuizService.generate(noteId.toString(), userId, 10, "senior_high");

        verify(quizGenerationService).generateTeacherQuiz(
                any(),
                any(),
                any(),
                eq(10),
                argThat(context -> context.learnerLevel() == null
                        && context.noteLearnerLevel() == LearnerLevel.SENIOR_HIGH
                        && "Education".equals(context.courseProgram()))
        );
        ArgumentCaptor<GeneratedQuizEntity> quizCaptor = ArgumentCaptor.forClass(GeneratedQuizEntity.class);
        verify(generatedQuizRepository).save(quizCaptor.capture());
        assertThat(quizCaptor.getValue().getTargetLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
    }

    @Test
    void generate_leavesTargetLearnerLevelNullWhenTheTeacherChoseNoTargetLevel() {
        // targetLearnerLevel means "the level the teacher last explicitly targeted". NULL means "never
        // targeted" and is load-bearing: findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc
        // exists to encode it, and NoteService surfaces it as lastUsedTargetLearnerLevel, which the
        // teacher UI pre-fills from. Writing effectiveCurriculumLevel here floored it to COLLEGE and made
        // the UI pre-fill a level nobody chose, locking every later generation to it.
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        when(generationContextResolver.resolve(userId, note)).thenReturn(
                new StudyPackGenerationContext(
                        null,
                        "Education",
                        null,
                        List.of(),
                        null,
                        null
                )
        );
        when(quizGenerationService.generateTeacherQuiz(any(), any(), any(), eq(10), any(StudyPackGenerationContext.class)))
                .thenReturn(buildQuestions());
        when(generatedQuizRepository.save(any(GeneratedQuizEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        generatedQuizService.generate(noteId.toString(), userId, 10, null);

        ArgumentCaptor<GeneratedQuizEntity> quizCaptor = ArgumentCaptor.forClass(GeneratedQuizEntity.class);
        verify(generatedQuizRepository).save(quizCaptor.capture());
        assertThat(quizCaptor.getValue().getTargetLearnerLevel()).isNull();
    }

    @Test
    void generate_usesNoteAuthoredLearnerLevelWhenTargetLevelIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0, 0, 0)
        );
        when(generationContextResolver.resolve(userId, note)).thenReturn(
                new StudyPackGenerationContext(
                        LearnerLevel.PROFESSIONAL,
                        "Education",
                        "Biology",
                        List.of("cells"),
                        null,
                        LearnerLevel.SENIOR_HIGH
                )
        );
        when(quizGenerationService.generateTeacherQuiz(any(), any(), any(), eq(10), any(StudyPackGenerationContext.class)))
                .thenReturn(buildQuestions());
        when(generatedQuizRepository.save(any(GeneratedQuizEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        generatedQuizService.generate(noteId.toString(), userId, 10, null);

        verify(quizGenerationService).generateTeacherQuiz(
                any(),
                any(),
                any(),
                eq(10),
                argThat(context -> context.learnerLevel() == LearnerLevel.PROFESSIONAL
                        && context.noteLearnerLevel() == LearnerLevel.SENIOR_HIGH)
        );
    }

    @Test
    void getByNoteId_returnsPersistedTeacherQuiz() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(UUID.randomUUID());
        generatedQuiz.setOwnerUserId(userId);
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setQuestions(buildQuestions());
        generatedQuiz.setGeneratedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        generatedQuiz.setUpdatedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(generatedQuizRepository.findByNoteIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(generatedQuiz));

        GeneratedQuizResponse response = generatedQuizService.getByNoteId(noteId.toString(), userId);

        assertThat(response.id()).isEqualTo(generatedQuiz.getId().toString());
        assertThat(response.noteId()).isEqualTo(noteId.toString());
        assertThat(response.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        assertThat(response.questions()).hasSize(10);
    }

    @Test
    void exportDocx_returnsGeneratedQuizDocumentWithoutCallingQuizGeneration() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        note.setSubject("Biology");
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(quizId);
        generatedQuiz.setOwnerUserId(userId);
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setQuestions(buildQuestions());
        generatedQuiz.setGeneratedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        generatedQuiz.setUpdatedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        UserEntity teacher = buildUser(userId, UserRole.USER, ProfileType.TEACHER);
        teacher.setSchoolName("  NoteLib Academy  ");

        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(generatedQuizRepository.findByIdAndOwnerUserId(quizId, userId)).thenReturn(Optional.of(generatedQuiz));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(quizDocxExportService.buildFilename("Cell Structure", QuizDocxExportMode.WITH_ANSWERS))
                .thenReturn("cell-structure-quiz-with-answers.docx");
        when(quizDocxExportService.exportQuizToDocx(
                any(QuizDocxExportService.ExportableQuiz.class),
                eq(QuizDocxExportMode.WITH_ANSWERS),
                any(QuizDocxExportService.DocxHeaderOptions.class),
                eq(3)
        ))
                .thenReturn("docx".getBytes());

        QuizDocxExportService.QuizDocxFile exported = generatedQuizService.exportDocx(
                quizId.toString(),
                userId,
                QuizDocxExportMode.WITH_ANSWERS,
                new QuizDocxExportHeaderOverrideRequest("  Grade 7 - Rizal  ", false),
                3,
                Locale.CANADA_FRENCH
        );

        assertThat(exported.getFilename()).isEqualTo("cell-structure-quiz-with-answers.docx");
        assertThat(exported.getContent()).isEqualTo("docx".getBytes());
        ArgumentCaptor<QuizDocxExportService.DocxHeaderOptions> headerOptionsCaptor = ArgumentCaptor.forClass(
                QuizDocxExportService.DocxHeaderOptions.class
        );
        verify(quizDocxExportService).exportQuizToDocx(
                any(QuizDocxExportService.ExportableQuiz.class),
                eq(QuizDocxExportMode.WITH_ANSWERS),
                headerOptionsCaptor.capture(),
                eq(3)
        );
        assertThat(headerOptionsCaptor.getValue().schoolName()).isEqualTo("NoteLib Academy");
        assertThat(headerOptionsCaptor.getValue().className()).isEqualTo("Grade 7 - Rizal");
        assertThat(headerOptionsCaptor.getValue().includeDate()).isFalse();
        assertThat(headerOptionsCaptor.getValue().locale()).isEqualTo(Locale.CANADA_FRENCH);
        verify(quizGenerationService, never()).generateTeacherQuiz(any(), any(), any(), any(Integer.class), any(StudyPackGenerationContext.class));
    }

    @Test
    void exportDocx_blocksNonTeacherNonAdminUsers() {
        UUID userId = UUID.randomUUID();
        UserEntity student = buildUser(userId, UserRole.USER, ProfileType.STUDENT);
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        String uuidStr = UUID.randomUUID().toString();
        assertThatThrownBy(() -> generatedQuizService.exportDocx(uuidStr, userId, QuizDocxExportMode.QUIZ_ONLY))
                .isInstanceOf(GeneratedQuizExportNotAllowedException.class);

        verify(generatedQuizRepository, never()).findByIdAndOwnerUserId(any(UUID.class), eq(userId));
        verify(quizDocxExportService, never()).exportQuizToDocx(any(), any(), any(), any(Integer.class));
    }

    @Test
    void exportDocx_blocksMultipleVersionsForFreeTeacherBeforeDocxRender() {
        UUID userId = UUID.randomUUID();
        UserEntity teacher = buildUser(userId, UserRole.USER, ProfileType.TEACHER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        String quizId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> generatedQuizService.exportDocx(
                quizId,
                userId,
                QuizDocxExportMode.QUIZ_ONLY,
                null,
                2,
                Locale.US
        ))
                .isInstanceOf(MultipleExamVersionsNotAllowedForPlanException.class)
                .satisfies(error -> {
                    MultipleExamVersionsNotAllowedForPlanException exception =
                            (MultipleExamVersionsNotAllowedForPlanException) error;
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.PAYMENT_REQUIRED);
                    assertThat(exception.getAction()).isEqualTo("UPGRADE_TO_PLUS");
                });

        verify(quizDocxExportService, never()).exportQuizToDocx(any(), any(), any(), any(Integer.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4})
    void exportDocx_rejectsInvalidVersionCount(int versionCount) {
        UUID userId = UUID.randomUUID();
        UserEntity teacher = buildUser(userId, UserRole.USER, ProfileType.TEACHER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);

        String quizId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> generatedQuizService.exportDocx(
                quizId,
                userId,
                QuizDocxExportMode.QUIZ_ONLY,
                null,
                versionCount,
                Locale.US
        )).isInstanceOf(InvalidQuizDocxVersionCountException.class);

        verify(quizDocxExportService, never()).exportQuizToDocx(any(), any(), any(), any(Integer.class));
    }

    @Test
    void exportCombinedDocx_preservesSelectedNoteOrderAndUsesCombinedExporter() {
        UUID userId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        NoteEntity firstNote = buildNote(firstNoteId, userId);
        firstNote.setTitle("First Note");
        firstNote.setSubject("Biology");
        NoteEntity secondNote = buildNote(secondNoteId, userId);
        secondNote.setTitle("Second Note");
        secondNote.setSubject("Chemistry");

        GeneratedQuizEntity firstQuiz = buildGeneratedQuiz(firstNoteId, userId, "First explanation");
        GeneratedQuizEntity secondQuiz = buildGeneratedQuiz(secondNoteId, userId, "Second explanation");
        UserEntity teacher = buildUser(userId, UserRole.USER, ProfileType.TEACHER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(List.of(firstNote, secondNote));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(firstNoteId, secondNoteId)))
                .thenReturn(List.of(firstQuiz, secondQuiz));
        when(quizDocxExportService.buildCombinedFilename(true, true)).thenReturn("combined-exam-with-answers.docx");
        when(quizDocxExportService.exportCombinedQuizToDocx(
                any(List.class),
                eq(new QuizDocxExportService.CombinedQuizDocxOptions(true, true)),
                any(QuizDocxExportService.DocxHeaderOptions.class)
        )).thenReturn("combined-docx".getBytes());

        QuizDocxExportService.QuizDocxFile exported = generatedQuizService.exportCombinedDocx(
                List.of(
                        new MultiNoteQuizDocxExportRequest.Section(
                                "Section A",
                                List.of(new MultiNoteQuizDocxExportRequest.QuestionRef(firstNoteId.toString(), 0))
                        ),
                        new MultiNoteQuizDocxExportRequest.Section(
                                "Section B",
                                List.of(new MultiNoteQuizDocxExportRequest.QuestionRef(secondNoteId.toString(), 0))
                        )
                ),
                userId,
                true,
                true
        );

        assertThat(exported.getFilename()).isEqualTo("combined-exam-with-answers.docx");
        assertThat(exported.getContent()).isEqualTo("combined-docx".getBytes());

        ArgumentCaptor<List<QuizDocxExportService.ExportableSection>> captor = ArgumentCaptor.forClass(List.class);
        verify(quizDocxExportService).exportCombinedQuizToDocx(
                captor.capture(),
                eq(new QuizDocxExportService.CombinedQuizDocxOptions(true, true)),
                any(QuizDocxExportService.DocxHeaderOptions.class)
        );
        assertThat(captor.getValue()).extracting(QuizDocxExportService.ExportableSection::title)
                .containsExactly("Section A", "Section B");
        assertThat(captor.getValue().get(0).questions()).extracting(QuizItem::question)
                .containsExactly("Question?");
        assertThat(captor.getValue().get(1).questions()).extracting(QuizItem::question)
                .containsExactly("Question?");
    }

    @Test
    void exportCombinedDocx_rejectsNotesWithoutGeneratedQuiz() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity note = buildNote(noteId, userId);
        UserEntity teacher = buildUser(userId, UserRole.USER, ProfileType.TEACHER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, List.of(noteId))).thenReturn(List.of());

        assertThatThrownBy(() -> generatedQuizService.exportCombinedDocx(
                List.of(new MultiNoteQuizDocxExportRequest.Section(
                        "Section A",
                        List.of(new MultiNoteQuizDocxExportRequest.QuestionRef(noteId.toString(), 0))
                )),
                userId,
                true,
                false
        )).hasMessage("Only notes with generated quizzes can be included in an exam export.");

        verify(quizDocxExportService, never()).exportCombinedQuizToDocx(any(), any(), any());
    }

    private NoteEntity buildNote(UUID noteId, UUID userId) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setTitle("Cell Structure");
        note.setContent("Cell membrane and nucleus notes");
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setCreatedAt(OffsetDateTime.now().minusDays(1));
        note.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return note;
    }

    private List<QuizItem> buildQuestions() {
        return buildQuestions(10);
    }

    private List<QuizItem> buildQuestions(int questionCount) {
        return java.util.stream.IntStream.range(0, questionCount)
                .mapToObj(index -> new QuizItem(
                        "Question " + index + "?",
                        List.of("A", "B", "C", "D"),
                        0,
                        "Cells",
                        "Explanation " + index
                ))
                .toList();
    }

    private UserEntity buildUser(UUID userId, UserRole role, ProfileType profileType) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole(role);
        user.setProfileType(profileType);
        return user;
    }

    private GeneratedQuizEntity buildGeneratedQuiz(UUID noteId, UUID userId, String explanation) {
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(UUID.randomUUID());
        generatedQuiz.setOwnerUserId(userId);
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setQuestions(List.of(new QuizItem(
                "Question?",
                List.of("A", "B", "C", "D"),
                1,
                "Concept",
                explanation
        )));
        generatedQuiz.setGeneratedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        generatedQuiz.setUpdatedAt(OffsetDateTime.parse("2026-04-17T09:00:00Z"));
        return generatedQuiz;
    }
}
