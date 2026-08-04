package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.ExamQuestionPoolGenerationFailedException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamQuestionPoolService {
    public static final String MODE_LONG_EXAM = "LONG_EXAM";
    public static final String MODE_BOARD_EXAM = "BOARD_EXAM";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DIFFICULTY_MIXED = "mixed";
    private static final int BOARD_EXAM_SESSION_QUESTION_COUNT = 12;

    private final ExamQuestionPoolRepository examQuestionPoolRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuizGenerationService quizGenerationService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final StudySnapProperties properties;
    private final StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;
    private final TransactionOperations studyPackGenerationTransactionOperations;
    private final AsyncTaskExecutor studyPackGenerationTaskExecutor;
    private final AsyncTaskExecutor llmParallelTaskExecutor;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void initiatePool(StudyPackEntity studyPack, UUID ownerUserId) {
        if (studyPack == null || studyPack.getId() == null || ownerUserId == null) {
            return;
        }
        initiatePoolForMode(studyPack, ownerUserId, MODE_LONG_EXAM, true);
        initiatePoolForMode(studyPack, ownerUserId, MODE_BOARD_EXAM, true);
    }

    /**
     * Starts a pool only after a learner has used the corresponding PRO exam path. This is
     * intentionally independent of the eager prewarm kill switch used by Study Pack generation.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void initiatePoolForUsage(StudyPackEntity studyPack, UUID ownerUserId, String mode) {
        if (studyPack == null || studyPack.getId() == null || ownerUserId == null) {
            return;
        }
        initiatePoolForMode(studyPack, ownerUserId, normalizeMode(mode), false);
    }

    public Optional<List<QuizItem>> sampleQuestions(
            UUID studyPackId,
            String mode,
            int count,
            LearnerLevel effectiveCurriculumLevel
    ) {
        String normalizedMode = normalizeMode(mode);
        Optional<ExamQuestionPoolEntity> poolOptional = examQuestionPoolRepository
                .findByStudyPackIdAndModeForUpdate(studyPackId, normalizedMode);
        if (poolOptional.isEmpty()) {
            scheduleLazyInitiation(studyPackId, normalizedMode);
            return Optional.empty();
        }

        ExamQuestionPoolEntity pool = poolOptional.get();
        if (STATUS_FAILED.equals(pool.getGenerationStatus())) {
            refreshPool(pool);
            return Optional.empty();
        }
        if (!STATUS_READY.equals(pool.getGenerationStatus())) {
            return Optional.empty();
        }
        if (!sameLearnerLevel(pool.getLearnerLevel(), effectiveCurriculumLevel)) {
            refreshPool(pool);
            return Optional.empty();
        }

        List<QuizItem> availableQuestions = unservedQuestions(pool);
        if (availableQuestions.size() < count) {
            return Optional.empty();
        }

        List<QuizItem> shuffled = new ArrayList<>(availableQuestions);
        Collections.shuffle(shuffled);
        List<QuizItem> sampled = List.copyOf(shuffled.subList(0, count));
        pool.setServedQuestionKeys(mergeServedQuestionKeys(pool.getServedQuestionKeys(), sampled));
        examQuestionPoolRepository.save(pool);
        return Optional.of(sampled);
    }

    public void markServed(UUID studyPackId, String mode, List<QuizItem> questions) {
        String normalizedMode = normalizeMode(mode);
        examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(studyPackId, normalizedMode)
                .ifPresent(pool -> {
                    pool.setServedQuestionKeys(mergeServedQuestionKeys(pool.getServedQuestionKeys(), questions));
                    if (availableQuestionCount(pool) < examSizeForMode(normalizedMode)) {
                        refreshPool(pool);
                        return;
                    }
                    examQuestionPoolRepository.save(pool);
                });
    }

    public void refreshPool(UUID studyPackId, String mode) {
        examQuestionPoolRepository.findByStudyPackIdAndModeForUpdate(studyPackId, normalizeMode(mode))
                .ifPresent(this::refreshPool);
    }

    public void generatePoolAsync(UUID poolId) {
        try {
            PoolGenerationTarget target = studyPackGenerationTransactionOperations.execute(status -> {
                ExamQuestionPoolEntity pool = examQuestionPoolRepository.findByIdForUpdate(poolId)
                        .orElseThrow(ExamQuestionPoolGenerationFailedException::new);
                if (STATUS_READY.equals(pool.getGenerationStatus())) {
                    return null;
                }
                pool.setGenerationStatus(STATUS_GENERATING);
                examQuestionPoolRepository.save(pool);

                StudyPackEntity studyPack = studyPackRepository.findById(pool.getStudyPackId())
                        .orElseThrow(StudyPackNotFoundException::new);
                StudyPackGenerationContext context = generationContextResolver.resolveForStudyPack(
                        studyPack.getOwnerUserId(),
                        studyPack
                );
                return new PoolGenerationTarget(pool.getId(), pool.getMode(), studyPack, context);
            });
            if (target == null) {
                return;
            }

            List<QuizItem> generated = generatePoolQuestions(target);
            int expectedPoolSize = poolSizeForMode(target.mode());
            if (generated.size() < expectedPoolSize) {
                throw new ExamQuestionPoolGenerationFailedException();
            }
            List<QuizItem> poolQuestions = List.copyOf(generated.subList(0, expectedPoolSize));
            String learnerLevel = learnerLevelName(
                    StudyPackGenerationContextResolver.effectiveCurriculumLevel(target.context())
            );

            studyPackGenerationTransactionOperations.execute(status -> {
                ExamQuestionPoolEntity pool = examQuestionPoolRepository.findByIdForUpdate(target.poolId())
                        .orElseThrow(ExamQuestionPoolGenerationFailedException::new);
                pool.setQuestions(poolQuestions);
                pool.setPoolSize(poolQuestions.size());
                pool.setGenerationStatus(STATUS_READY);
                pool.setLearnerLevel(learnerLevel);
                pool.setServedQuestionKeys(List.of());
                pool.setRefreshedAt(OffsetDateTime.now(ZoneOffset.UTC));
                examQuestionPoolRepository.save(pool);
                return null;
            });
        } catch (Exception ex) {
            log.warn("Exam question pool generation failed for poolId={}: {}", poolId, ex.getMessage());
            studyPackGenerationTransactionOperations.execute(status -> {
                examQuestionPoolRepository.findByIdForUpdate(poolId).ifPresent(pool -> {
                    pool.setGenerationStatus(STATUS_FAILED);
                    examQuestionPoolRepository.save(pool);
                });
                return null;
            });
        }
    }

    private void initiatePoolForMode(
            StudyPackEntity studyPack,
            UUID ownerUserId,
            String mode,
            boolean requireEagerPrewarmEnabled
    ) {
        if (requireEagerPrewarmEnabled && !properties.getPricing().isExamPoolPrewarmEnabled()) {
            return;
        }
        studyPackGenerationTransactionOperations.execute(status -> {
            Optional<ExamQuestionPoolEntity> existingPool = examQuestionPoolRepository
                    .findByStudyPackIdAndModeForUpdate(studyPack.getId(), mode);
            if (existingPool.isPresent()) {
                ExamQuestionPoolEntity pool = existingPool.get();
                if (STATUS_READY.equals(pool.getGenerationStatus())
                        || STATUS_PENDING.equals(pool.getGenerationStatus())
                        || STATUS_GENERATING.equals(pool.getGenerationStatus())) {
                    return null;
                }
            }
            ExamQuestionPoolEntity pool = existingPool.orElseGet(() -> newPool(studyPack.getId(), mode));
            pool.setGenerationStatus(STATUS_PENDING);
            pool.setServedQuestionKeys(List.of());
            ExamQuestionPoolEntity saved = examQuestionPoolRepository.save(pool);
            dispatchPoolGenerationAfterCommit(saved.getId());
            return null;
        });
    }

    private void scheduleLazyInitiation(UUID studyPackId, String mode) {
        studyPackRepository.findById(studyPackId)
                .ifPresent(studyPack -> initiatePoolForUsage(studyPack, studyPack.getOwnerUserId(), mode));
    }

    private ExamQuestionPoolEntity newPool(UUID studyPackId, String mode) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ExamQuestionPoolEntity pool = new ExamQuestionPoolEntity();
        pool.setId(UUID.randomUUID());
        pool.setStudyPackId(studyPackId);
        pool.setMode(mode);
        pool.setQuestions(List.of());
        pool.setPoolSize(0);
        pool.setGenerationStatus(STATUS_PENDING);
        pool.setLearnerLevel(null);
        pool.setServedQuestionKeys(List.of());
        pool.setCreatedAt(now);
        pool.setRefreshedAt(null);
        return pool;
    }

    private List<QuizItem> generatePoolQuestions(PoolGenerationTarget target) {
        int expectedPoolSize = poolSizeForMode(target.mode());
        return switch (target.mode()) {
            case MODE_LONG_EXAM -> QuizDeduplicationUtils.uniqueQuestions(
                    quizGenerationService.generateLongExamParallel(
                            target.studyPack().getTitle(),
                            target.studyPack().getSummary(),
                            getKeyConcepts(target.studyPack()),
                            List.of(),
                            expectedPoolSize,
                            DIFFICULTY_MIXED,
                            target.context(),
                            llmParallelTaskExecutor
                    ),
                    Set.of()
            );
            case MODE_BOARD_EXAM -> generateBoardExamPool(target);
            default -> throw new ExamQuestionPoolGenerationFailedException();
        };
    }

    private List<QuizItem> generateBoardExamPool(PoolGenerationTarget target) {
        List<QuizItem> firstBatch = quizGenerationService.generateBoardExamQuiz(
                target.studyPack().getTitle(),
                target.studyPack().getSummary(),
                getKeyConcepts(target.studyPack()),
                List.of(),
                BOARD_EXAM_SESSION_QUESTION_COUNT,
                DIFFICULTY_MIXED,
                target.context()
        );
        List<QuizItem> secondBatch = quizGenerationService.generateBoardExamQuiz(
                target.studyPack().getTitle(),
                target.studyPack().getSummary(),
                getKeyConcepts(target.studyPack()),
                List.of(),
                BOARD_EXAM_SESSION_QUESTION_COUNT,
                DIFFICULTY_MIXED,
                target.context()
        );
        List<QuizItem> combined = new ArrayList<>(firstBatch);
        combined.addAll(secondBatch);
        return QuizDeduplicationUtils.uniqueQuestions(combined, Set.of());
    }

    private List<QuizItem> unservedQuestions(ExamQuestionPoolEntity pool) {
        Set<String> servedKeys = new LinkedHashSet<>(safeServedQuestionKeys(pool));
        List<QuizItem> available = new ArrayList<>();
        for (QuizItem item : safeQuestions(pool)) {
            String key = normalizedQuestionKey(item);
            if (!key.isBlank() && !servedKeys.contains(key)) {
                available.add(item);
            }
        }
        return available;
    }

    private int availableQuestionCount(ExamQuestionPoolEntity pool) {
        return unservedQuestions(pool).size();
    }

    private List<String> mergeServedQuestionKeys(List<String> existingKeys, List<QuizItem> questions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existingKeys != null) {
            for (String key : existingKeys) {
                if (key != null && !key.isBlank()) {
                    merged.add(key);
                }
            }
        }
        if (questions != null) {
            for (QuizItem question : questions) {
                String key = normalizedQuestionKey(question);
                if (!key.isBlank()) {
                    merged.add(key);
                }
            }
        }
        return List.copyOf(merged);
    }

    private void refreshPool(ExamQuestionPoolEntity pool) {
        pool.setServedQuestionKeys(List.of());
        pool.setGenerationStatus(STATUS_PENDING);
        ExamQuestionPoolEntity saved = examQuestionPoolRepository.save(pool);
        dispatchPoolGenerationAfterCommit(saved.getId());
    }

    private void dispatchPoolGenerationAfterCommit(UUID poolId) {
        Runnable generationTask = () -> generatePoolAsync(poolId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    studyPackGenerationTaskDispatcher.execute(generationTask);
                }
            });
            return;
        }
        studyPackGenerationTaskDispatcher.execute(generationTask);
    }

    private int poolSizeForMode(String mode) {
        return switch (normalizeMode(mode)) {
            case MODE_LONG_EXAM -> properties.getPricing().getLongExamPoolSize();
            case MODE_BOARD_EXAM -> properties.getPricing().getBoardExamPoolSize();
            default -> throw new ExamQuestionPoolGenerationFailedException();
        };
    }

    private int examSizeForMode(String mode) {
        return switch (normalizeMode(mode)) {
            case MODE_LONG_EXAM -> properties.getPricing().getLongExamHighTierCount();
            case MODE_BOARD_EXAM -> BOARD_EXAM_SESSION_QUESTION_COUNT;
            default -> throw new ExamQuestionPoolGenerationFailedException();
        };
    }

    private String normalizeMode(String mode) {
        if (MODE_LONG_EXAM.equals(mode) || MODE_BOARD_EXAM.equals(mode)) {
            return mode;
        }
        throw new ExamQuestionPoolGenerationFailedException();
    }

    private boolean sameLearnerLevel(String generatedLearnerLevel, LearnerLevel effectiveCurriculumLevel) {
        return generatedLearnerLevel != null
                && !generatedLearnerLevel.isBlank()
                && generatedLearnerLevel.equals(learnerLevelName(effectiveCurriculumLevel));
    }

    private String learnerLevelName(LearnerLevel learnerLevel) {
        return learnerLevel == null ? null : learnerLevel.name();
    }

    private List<QuizItem> safeQuestions(ExamQuestionPoolEntity pool) {
        return pool.getQuestions() == null ? List.of() : pool.getQuestions();
    }

    private List<String> safeServedQuestionKeys(ExamQuestionPoolEntity pool) {
        return pool.getServedQuestionKeys() == null ? List.of() : pool.getServedQuestionKeys();
    }

    private String normalizedQuestionKey(QuizItem question) {
        return question == null ? "" : QuizDeduplicationUtils.normalizeQuestion(question.question());
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private record PoolGenerationTarget(
            UUID poolId,
            String mode,
            StudyPackEntity studyPack,
            StudyPackGenerationContext context
    ) {
    }
}
