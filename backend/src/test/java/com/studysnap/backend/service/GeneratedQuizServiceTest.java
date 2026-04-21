package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GeneratedQuizExportNotAllowedException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                quizDocxExportService
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
                new UserUsageService.MonthlyUsage(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 0, 0, 0, 0)
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(generatedQuizRepository.findByIdAndOwnerUserId(quizId, userId)).thenReturn(Optional.of(generatedQuiz));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(quizDocxExportService.buildFilename("Cell Structure", QuizDocxExportMode.WITH_ANSWERS))
                .thenReturn("cell-structure-quiz-with-answers.docx");
        when(quizDocxExportService.exportQuizToDocx(any(QuizDocxExportService.ExportableQuiz.class), eq(QuizDocxExportMode.WITH_ANSWERS)))
                .thenReturn("docx".getBytes());

        QuizDocxExportService.QuizDocxFile exported = generatedQuizService.exportDocx(
                quizId.toString(),
                userId,
                QuizDocxExportMode.WITH_ANSWERS
        );

        assertThat(exported.getFilename()).isEqualTo("cell-structure-quiz-with-answers.docx");
        assertThat(exported.getContent()).isEqualTo("docx".getBytes());
        verify(quizDocxExportService).exportQuizToDocx(any(QuizDocxExportService.ExportableQuiz.class), eq(QuizDocxExportMode.WITH_ANSWERS));
        verify(quizGenerationService, never()).generateTeacherQuiz(any(), any(), any(), any(Integer.class), any(StudyPackGenerationContext.class));
    }

    @Test
    void exportDocx_blocksNonTeacherNonAdminUsers() {
        UUID userId = UUID.randomUUID();
        UserEntity student = buildUser(userId, UserRole.USER, ProfileType.STUDENT);
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> generatedQuizService.exportDocx(UUID.randomUUID().toString(), userId, QuizDocxExportMode.QUIZ_ONLY))
                .isInstanceOf(GeneratedQuizExportNotAllowedException.class);

        verify(generatedQuizRepository, never()).findByIdAndOwnerUserId(any(UUID.class), eq(userId));
        verify(quizDocxExportService, never()).exportQuizToDocx(any(), any());
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
                eq(new QuizDocxExportService.CombinedQuizDocxOptions(true, true))
        )).thenReturn("combined-docx".getBytes());

        QuizDocxExportService.QuizDocxFile exported = generatedQuizService.exportCombinedDocx(
                List.of(firstNoteId.toString(), secondNoteId.toString()),
                userId,
                true,
                true
        );

        assertThat(exported.getFilename()).isEqualTo("combined-exam-with-answers.docx");
        assertThat(exported.getContent()).isEqualTo("combined-docx".getBytes());

        ArgumentCaptor<List<QuizDocxExportService.ExportableQuiz>> captor = ArgumentCaptor.forClass(List.class);
        verify(quizDocxExportService).exportCombinedQuizToDocx(
                captor.capture(),
                eq(new QuizDocxExportService.CombinedQuizDocxOptions(true, true))
        );
        assertThat(captor.getValue()).extracting(QuizDocxExportService.ExportableQuiz::title)
                .containsExactly("First Note", "Second Note");
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
                List.of(noteId.toString()),
                userId,
                true,
                false
        )).hasMessage("Only notes with generated quizzes can be included in an exam export.");

        verify(quizDocxExportService, never()).exportCombinedQuizToDocx(any(), any());
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
        return java.util.stream.IntStream.range(0, 10)
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
