package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionPoolServiceTest {
    @Mock
    private ExamQuestionPoolRepository examQuestionPoolRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;

    private ExamQuestionPoolService service;
    private StudySnapProperties properties;

    @BeforeEach
    void setUp() {
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        properties = new StudySnapProperties();
        // Prod default is disabled (see v0.37.1); most of this suite predates the
        // kill-switch and asserts pre-warming behavior, so enable it here and test
        // the disabled path explicitly below.
        properties.getPricing().setExamPoolPrewarmEnabled(true);
        service = new ExamQuestionPoolService(
                examQuestionPoolRepository,
                studyPackRepository,
                quizGenerationService,
                generationContextResolver,
                properties,
                studyPackGenerationTaskDispatcher,
                transactionOperations,
                new SimpleAsyncTaskExecutor(),
                new SimpleAsyncTaskExecutor()
        );
        lenient().when(examQuestionPoolRepository.save(any(ExamQuestionPoolEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sampleQuestions_returnsEmptyWhenPoolStatusIsNotReady() {
        UUID studyPackId = UUID.randomUUID();
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "GENERATING", buildQuiz(48))));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.COLLEGE
        );

        assertThat(result).isEmpty();
    }

    @Test
    void sampleQuestions_returnsEmptyWhenAvailableQuestionsAreBelowRequestedCount() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(10));
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.COLLEGE
        );

        assertThat(result).isEmpty();
    }

    @Test
    void sampleQuestions_returnsEmptyAndRefreshesWhenLearnerLevelChanged() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        pool.setLearnerLevel(LearnerLevel.COLLEGE.name());
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.BOARD_EXAM_REVIEW
        );

        assertThat(result).isEmpty();
        assertThat(pool.getGenerationStatus()).isEqualTo("PENDING");
        verify(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_keepsCollegePoolForGradeSchoolReaderWhenNoteIsCollege() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL,
                "General Education",
                "Science",
                List.of(),
                null,
                LearnerLevel.COLLEGE
        );
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(context)
        );

        assertThat(result).isPresent();
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_readerLevelChangeDoesNotInvalidateAuthoredNoteLevelPool() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        StudyPackGenerationContext gradeSchoolReader = new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL, null, null, List.of(), null, LearnerLevel.COLLEGE
        );
        StudyPackGenerationContext professionalReader = new StudyPackGenerationContext(
                LearnerLevel.PROFESSIONAL, null, null, List.of(), null, LearnerLevel.COLLEGE
        );
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> first = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                10,
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(gradeSchoolReader)
        );
        Optional<List<QuizItem>> second = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                10,
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(professionalReader)
        );

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_noteWithoutAuthoredLevelFallsBackToReaderLevel() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        pool.setLearnerLevel(LearnerLevel.SENIOR_HIGH.name());
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.SENIOR_HIGH, null, null, List.of(), null, null
        );
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(context)
        );

        assertThat(result).isPresent();
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_unstampedPoolStillRefreshes() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        pool.setLearnerLevel(null);
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.COLLEGE
        );

        assertThat(result).isEmpty();
        assertThat(pool.getGenerationStatus()).isEqualTo("PENDING");
        verify(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_returnsRequestedCountFromUnservedSubset() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        pool.setServedQuestionKeys(List.of("question 0", "question 1"));
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.COLLEGE
        );

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(20);
        assertThat(result.get()).extracting(QuizItem::question)
                .doesNotContain("Question 0", "Question 1");
    }

    @Test
    void sampleQuestions_reservesQuestionsSoConsecutiveSamplesDoNotOverlap() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48));
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.of(pool));

        List<String> firstQuestions = service.sampleQuestions(
                        studyPackId,
                        ExamQuestionPoolService.MODE_LONG_EXAM,
                        20,
                        LearnerLevel.COLLEGE
                )
                .orElseThrow()
                .stream()
                .map(QuizItem::question)
                .toList();
        List<String> secondQuestions = service.sampleQuestions(
                        studyPackId,
                        ExamQuestionPoolService.MODE_LONG_EXAM,
                        20,
                        LearnerLevel.COLLEGE
                )
                .orElseThrow()
                .stream()
                .map(QuizItem::question)
                .toList();

        assertThat(secondQuestions).doesNotContainAnyElementsOf(firstQuestions);
    }

    @Test
    void markServed_addsNormalizedQuestionKeys() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_BOARD_EXAM, "READY", buildQuiz(24));
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_BOARD_EXAM
        )).thenReturn(Optional.of(pool));

        service.markServed(studyPackId, ExamQuestionPoolService.MODE_BOARD_EXAM, List.of(
                new QuizItem("Question 3", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation")
        ));

        assertThat(pool.getServedQuestionKeys()).contains("question 3");
    }

    @Test
    void markServed_triggersRefreshWhenAvailableCountDropsBelowOneExam() {
        UUID studyPackId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(studyPackId, ExamQuestionPoolService.MODE_BOARD_EXAM, "READY", buildQuiz(24));
        pool.setServedQuestionKeys(buildQuiz(12).stream()
                .map(QuizItem::question)
                .map(com.studysnap.backend.util.QuizDeduplicationUtils::normalizeQuestion)
                .toList());
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_BOARD_EXAM
        )).thenReturn(Optional.of(pool));

        service.markServed(studyPackId, ExamQuestionPoolService.MODE_BOARD_EXAM, List.of(
                new QuizItem("Question 12", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation")
        ));

        assertThat(pool.getGenerationStatus()).isEqualTo("PENDING");
        assertThat(pool.getServedQuestionKeys()).isEmpty();
        verify(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
    }

    @Test
    void initiatePool_isNoopWhenReadyPoolAlreadyExists() {
        UUID studyPackId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                eq(studyPackId),
                any()
        )).thenReturn(Optional.of(pool(studyPackId, ExamQuestionPoolService.MODE_LONG_EXAM, "READY", buildQuiz(48))));

        service.initiatePool(studyPack, userId);

        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void initiatePool_createsMissingPoolsAndDispatchesGeneration() {
        UUID studyPackId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                eq(studyPackId),
                any()
        )).thenReturn(Optional.empty());

        service.initiatePool(studyPack, userId);

        verify(examQuestionPoolRepository, org.mockito.Mockito.times(2)).save(any(ExamQuestionPoolEntity.class));
        verify(studyPackGenerationTaskDispatcher, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void generatePoolAsync_stampsTheNoteEffectiveCurriculumLevelInsteadOfTheReaderLevel() {
        UUID poolId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExamQuestionPoolEntity pool = pool(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                "PENDING",
                List.of()
        );
        pool.setId(poolId);
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Engineering mechanics");
        studyPack.setSummary("Summary");
        studyPack.setKeyConcepts(List.of("Forces"));
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Civil Engineering",
                "Mechanics",
                List.of(),
                null,
                LearnerLevel.BOARD_EXAM_REVIEW
        );
        int poolSize = properties.getPricing().getLongExamPoolSize();
        when(examQuestionPoolRepository.findByIdForUpdate(poolId)).thenReturn(Optional.of(pool));
        when(studyPackRepository.findById(studyPackId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(context);
        when(quizGenerationService.generateLongExamParallel(
                anyString(),
                anyString(),
                any(),
                any(),
                anyInt(),
                anyString(),
                eq(context),
                any()
        )).thenReturn(buildQuiz(poolSize));

        service.generatePoolAsync(poolId);

        assertThat(pool.getGenerationStatus()).isEqualTo("READY");
        assertThat(pool.getLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
        ArgumentCaptor<ExamQuestionPoolEntity> savedPool = ArgumentCaptor.forClass(ExamQuestionPoolEntity.class);
        verify(examQuestionPoolRepository, org.mockito.Mockito.atLeastOnce()).save(savedPool.capture());
        assertThat(savedPool.getValue().getLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
    }

    @Test
    void initiatePool_isNoopWhenPrewarmDisabled() {
        properties.getPricing().setExamPoolPrewarmEnabled(false);
        UUID studyPackId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);

        service.initiatePool(studyPack, userId);

        verify(examQuestionPoolRepository, never()).findByStudyPackIdAndModeForUpdate(any(), any());
        verify(examQuestionPoolRepository, never()).save(any(ExamQuestionPoolEntity.class));
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void sampleQuestions_createsUsageDrivenPoolWhenPrewarmDisabled() {
        properties.getPricing().setExamPoolPrewarmEnabled(false);
        UUID studyPackId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        when(examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM
        )).thenReturn(Optional.empty());
        lenient().when(studyPackRepository.findById(studyPackId)).thenReturn(Optional.of(studyPack));

        Optional<List<QuizItem>> result = service.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_LONG_EXAM,
                20,
                LearnerLevel.COLLEGE
        );

        assertThat(result).isEmpty();
        verify(examQuestionPoolRepository).save(any(ExamQuestionPoolEntity.class));
        verify(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
    }

    private ExamQuestionPoolEntity pool(
            UUID studyPackId,
            String mode,
            String status,
            List<QuizItem> questions
    ) {
        ExamQuestionPoolEntity pool = new ExamQuestionPoolEntity();
        pool.setId(UUID.randomUUID());
        pool.setStudyPackId(studyPackId);
        pool.setMode(mode);
        pool.setQuestions(questions);
        pool.setPoolSize(questions.size());
        pool.setGenerationStatus(status);
        pool.setLearnerLevel(LearnerLevel.COLLEGE.name());
        pool.setServedQuestionKeys(List.of());
        pool.setCreatedAt(OffsetDateTime.now());
        return pool;
    }

    private List<QuizItem> buildQuiz(int count) {
        java.util.ArrayList<QuizItem> quiz = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quiz.add(new QuizItem(
                    "Question " + index,
                    List.of("A", "B", "C", "D"),
                    index % 4,
                    "Concept",
                    "Explanation"
            ));
        }
        return quiz;
    }
}
