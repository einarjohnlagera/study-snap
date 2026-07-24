package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.LongExamCompleteRequest;
import com.studysnap.backend.dto.LongExamMasteryReportResponse;
import com.studysnap.backend.dto.LongExamProgressRequest;
import com.studysnap.backend.dto.LongExamSessionResponse;
import com.studysnap.backend.dto.LongExamSourceNote;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.dto.LongExamStartRequest;
import com.studysnap.backend.dto.LongExamStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidLongExamDifficultyException;
import com.studysnap.backend.exception.InvalidLongExamSourceException;
import com.studysnap.backend.exception.LongExamGenerationFailedException;
import com.studysnap.backend.exception.LongExamSessionNotFoundException;
import com.studysnap.backend.exception.LongExamSessionNotInProgressException;
import com.studysnap.backend.exception.LongExamSessionNotPausableException;
import com.studysnap.backend.exception.MonthlyLongExamLimitReachedException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LongExamService {
    private static final String SESSION_STATE_DIFFICULTY = "difficulty";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";
    private static final String SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS = "timerStartedAtEpochSeconds";
    private static final String SESSION_STATE_TIME_LIMIT_SECONDS = "timeLimitSeconds";
    private static final String SOURCE_STUDY_PACK_ID_KEY = "studyPackId";
    private static final String SOURCE_NOTE_ID_KEY = "noteId";
    private static final String SOURCE_NOTE_TITLE_KEY = "noteTitle";
    private static final String SOURCE_QUESTION_COUNT_KEY = "questionCount";
    private static final String SESSION_METADATA_DOMAIN_BREAKDOWN = "domainBreakdown";
    private static final String SESSION_METADATA_WEAK_DOMAINS = "weakDomains";
    private static final String SESSION_METADATA_PERFORMANCE_SUMMARY = "performanceSummary";
    private static final String SESSION_METADATA_SUGGESTED_NEXT_STEP = "suggestedNextStep";
    private static final String DOMAIN_KEY = "domain";
    private static final String TOTAL_QUESTIONS_KEY = "totalQuestions";
    private static final String CORRECT_ANSWERS_KEY = "correctAnswers";
    private static final String ACCURACY_PERCENTAGE_KEY = "accuracyPercentage";
    private static final String UNKNOWN_DOMAIN_LABEL = "Uncategorized";
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_MEDIUM = "medium";
    private static final String DIFFICULTY_HARD = "hard";
    private static final String DIFFICULTY_MIXED = "mixed";
    private static final String PERFORMANCE_EXCELLENT = "Excellent";
    private static final String PERFORMANCE_GOOD = "Good";
    private static final String PERFORMANCE_FAIR = "Fair";
    private static final String PERFORMANCE_NEEDS_IMPROVEMENT = "Needs Improvement";
    private static final String SUGGESTED_REVIEW_WEAK_DOMAINS = "Review weak domains and retry";
    private static final String SUGGESTED_HARDER_DIFFICULTY = "Try a harder difficulty";
    private static final String SUGGESTED_REVIEW_ANSWERS = "Review your answers and revisit the source note";
    private static final String LONG_EXAM_FORFEITED_MESSAGE = "Long Exam session forfeited.";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_QUESTION_COUNT = "questionCount";
    private static final String ANALYTICS_METADATA_DIFFICULTY = "difficulty";
    private static final String ANALYTICS_METADATA_SCORE_PERCENTAGE = "scorePercentage";
    private static final int WEAK_DOMAIN_ACCURACY_THRESHOLD = 60;
    private static final int FAIR_SCORE_THRESHOLD = 50;
    private static final int GOOD_SCORE_THRESHOLD = 70;
    private static final int EXCELLENT_SCORE_THRESHOLD = 90;
    private static final int SECONDS_PER_QUESTION = 90;
    private static final int MAX_ADDITIONAL_SOURCE_COUNT = 3;
    private static final int MIN_QUESTIONS_PER_SOURCE = 3;
    private static final int QUOTA_UNITS_PER_SESSION = 1;
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<QuickReviewSessionStatus> ACTIVE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.PAUSED
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.PAUSED,
            QuickReviewSessionStatus.FAILED
    );

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final AuthService authService;
    private final QuizGenerationService quizGenerationService;
    private final AnalyticsService analyticsService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;
    private final TransactionOperations studyPackGenerationTransactionOperations;
    private final AsyncTaskExecutor studyPackGenerationTaskExecutor;
    private final AsyncTaskExecutor llmParallelTaskExecutor;
    private final ExamQuestionPoolService examQuestionPoolService;
    private final ConceptHealthService conceptHealthService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LongExamStartResponse startSession(String studyPackIdRaw, UUID userId, LongExamStartRequest request) {
        authService.requireEmailVerified(userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.LONG_EXAM_SESSION);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        String difficulty = resolveDifficulty(request);
        int questionCount = resolveQuestionCount(userId);
        List<UUID> additionalStudyPackIds = resolveAdditionalStudyPackIds(request, studyPackId);
        int sourceCount = additionalStudyPackIds.size() + 1;
        assertLongExamQuotaAvailable(userId, planType, QUOTA_UNITS_PER_SESSION);
        AtomicBoolean createdSession = new AtomicBoolean(false);
        AtomicBoolean poolSourcedSession = new AtomicBoolean(false);

        QuickReviewSessionEntity session = studyPackGenerationTransactionOperations.execute(status -> {
            StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
            QuickReviewSessionEntity existing = quickReviewSessionRepository
                    .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                            userId,
                            studyPackId,
                            QuickReviewSessionMode.LONG_EXAM,
                            ACTIVE_STATUSES
                    )
                    .orElse(null);
            if (existing != null && (existing.getStatus() == QuickReviewSessionStatus.GENERATING
                    || !QuizSessionStateUtils.extractQuiz(existing.getSessionState()).isEmpty())) {
                return existing;
            }

            List<LongExamSourceNoteRef> sourceNoteRefs = resolveSourceNoteRefs(
                    studyPack,
                    userId,
                    additionalStudyPackIds,
                    questionCount
            );
            if (additionalStudyPackIds.isEmpty()) {
                StudyPackGenerationContext generationContext = generationContextResolver.resolveForStudyPack(userId, studyPack);
                Optional<List<QuizItem>> pooledQuestions = examQuestionPoolService.sampleQuestions(
                        studyPackId,
                        ExamQuestionPoolService.MODE_LONG_EXAM,
                        questionCount,
                        generationContext.learnerLevel()
                );
                if (pooledQuestions.isPresent()) {
                    QuickReviewSessionEntity poolSession = buildGeneratingSession(
                            userId,
                            studyPack,
                            difficulty,
                            questionCount,
                            sourceNoteRefs
                    );
                    markSessionReady(poolSession, pooledQuestions.get(), difficulty);
                    poolSession.setSessionState(QuizSessionStateUtils.withPoolSourced(
                            poolSession.getSessionState(),
                            true
                    ));
                    QuickReviewSessionEntity saved = quickReviewSessionRepository.save(poolSession);
                    trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_STARTED, saved.getStudyPackId(), Map.of(
                            ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                            ANALYTICS_METADATA_QUESTION_COUNT, pooledQuestions.get().size(),
                            ANALYTICS_METADATA_DIFFICULTY, difficulty
                    ));
                    createdSession.set(true);
                    poolSourcedSession.set(true);
                    return saved;
                }
            }
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(buildGeneratingSession(
                    userId,
                    studyPack,
                    difficulty,
                    questionCount,
                    sourceNoteRefs
            ));
            dispatchLongExamGenerationAfterCommit(saved.getId(), difficulty);
            createdSession.set(true);
            return saved;
        });
        if (session == null) {
            throw new LongExamGenerationFailedException();
        }
        if (createdSession.get()) {
            userUsageService.incrementLongExamGenerationBy(userId, QUOTA_UNITS_PER_SESSION, OffsetDateTime.now(ZoneOffset.UTC));
        }
        if (createdSession.get() && !poolSourcedSession.get() && additionalStudyPackIds.isEmpty()) {
            studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                    .ifPresent(studyPack -> examQuestionPoolService.initiatePoolForUsage(
                            studyPack,
                            userId,
                            ExamQuestionPoolService.MODE_LONG_EXAM
                    ));
        }
        return buildStartResponse(session);
    }

    private void dispatchLongExamGenerationAfterCommit(
            UUID sessionId,
            String difficulty
    ) {
        Runnable generationTask = () -> generateLongExamAsync(sessionId, difficulty);
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

    private void generateLongExamAsync(UUID sessionId, String difficulty) {
        try {
            studyPackGenerationTransactionOperations.execute(status -> {
                QuickReviewSessionEntity session = quickReviewSessionRepository.findById(sessionId)
                        .orElseThrow(LongExamSessionNotFoundException::new);
                if (session.getStatus() != QuickReviewSessionStatus.GENERATING) {
                    return null;
                }
                UserEntity user = userRepository.findById(session.getUserId())
                        .orElseThrow(LongExamSessionNotFoundException::new);
                List<LongExamSourceNoteRef> sourceNoteRefs = extractSourceNoteRefs(session.getSessionState());
                if (sourceNoteRefs.isEmpty()) {
                    StudyPackEntity primaryStudyPack = findOwnedStudyPackForGenerationOrThrow(
                            session.getStudyPackId(),
                            session.getUserId()
                    );
                    sourceNoteRefs = List.of(buildSourceNoteRef(primaryStudyPack, safeTotalQuestions(session)));
                }
                List<QuizItem> longExamQuiz = generateQuizForSources(user, sourceNoteRefs, difficulty);
                int expectedQuestionCount = safeTotalQuestions(session);
                if (longExamQuiz.size() != expectedQuestionCount) {
                    throw new LongExamGenerationFailedException();
                }

                markSessionReady(session, longExamQuiz, difficulty);
                QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
                trackAnalytics(session.getUserId(), AnalyticsEventType.LONG_EXAM_STARTED, saved.getStudyPackId(), Map.of(
                        ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                        ANALYTICS_METADATA_QUESTION_COUNT, longExamQuiz.size(),
                        ANALYTICS_METADATA_DIFFICULTY, difficulty
                ));
                return null;
            });
        } catch (Exception ex) {
            log.warn("Long Exam generation failed for sessionId={}: {}", sessionId, ex.getMessage());
            studyPackGenerationTransactionOperations.execute(status -> {
                quickReviewSessionRepository.findById(sessionId).ifPresent(session -> {
                    markSessionFailed(session);
                    quickReviewSessionRepository.save(session);
                });
                return null;
            });
        }
    }

    @Transactional(readOnly = true)
    public LongExamSessionResponse getSession(UUID sessionId, UUID userId) {
        return buildSessionResponse(findOwnedSessionOrThrow(sessionId, userId));
    }

    @Transactional(readOnly = true)
    public LongExamStartResponse getActiveSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        findOwnedStudyPackOrThrow(studyPackId, userId);
        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.LONG_EXAM,
                        OBSERVABLE_STATUSES
                )
                .map(this::buildStartResponse)
                .orElseGet(() -> buildEmptyStartResponse(userId));
    }

    public LongExamSessionResponse saveProgress(UUID sessionId, UUID userId, LongExamProgressRequest request) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        assertSessionInProgress(session);

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        int currentQuestionIndex = Math.clamp(request.questionIndex(), 0, Math.max(0, quiz.size() - 1));
        session.setCurrentQuestionIndex(currentQuestionIndex);
        Map<String, Object> nextSessionState = QuizSessionStateUtils.withSelectedChoice(
                session.getSessionState(),
                request.questionIndex(),
                request.selectedChoiceIndex()
        );
        if (request.selectedMultiChoiceIndices() != null) {
            nextSessionState = QuizSessionStateUtils.withSelectedMultiChoice(
                    nextSessionState,
                    request.questionIndex(),
                    request.selectedMultiChoiceIndices()
            );
        }
        session.setSessionState(nextSessionState);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamSessionResponse pauseSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new LongExamSessionNotPausableException();
        }

        session.setStatus(QuickReviewSessionStatus.PAUSED);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamSessionResponse resumeSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamMasteryReportResponse completeSession(
            UUID sessionId,
            UUID userId,
            LongExamCompleteRequest request
    ) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS
                && session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, List<Integer>> selectedMultiChoices = QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz);
        LongExamStatistics statistics = computeStatistics(quiz, selectedChoices, selectedMultiChoices);
        BigDecimal scorePercentage = BigDecimal.valueOf(statistics.scorePercentage())
                .setScale(2, RoundingMode.HALF_UP);

        boolean isFirstCompletedSessionEver = !quickReviewSessionRepository
                .existsByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED);
        boolean isSecondCompletedSessionEver = quickReviewSessionRepository
                .countByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED) == 1;
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(quiz.size());
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(statistics.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setCompletedAt(now);
        session.setSessionState(markSessionStateCompleted(session.getSessionState()));
        session.setSessionMetadata(buildMasteryReportMetadata(statistics));

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        List<String> correctConcepts = QuizSessionReviewUtils.computeFullyCorrectKeyConcepts(
                quiz,
                selectedChoices,
                selectedMultiChoices
        );
        List<String> missedConcepts = QuizSessionReviewUtils.computeKeyConceptsWithMisses(
                quiz,
                selectedChoices,
                selectedMultiChoices
        );
        recordCorrectConceptsForSourcePacks(userId, saved, correctConcepts, now);
        recordIncorrectConceptsForSourcePacks(userId, saved, missedConcepts, now);
        if (QuizSessionStateUtils.extractPoolSourced(saved.getSessionState())) {
            examQuestionPoolService.markServed(
                    saved.getStudyPackId(),
                    ExamQuestionPoolService.MODE_LONG_EXAM,
                    quiz
            );
        }
        trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_COMPLETED, saved.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                ANALYTICS_METADATA_QUESTION_COUNT, statistics.totalQuestions(),
                ANALYTICS_METADATA_SCORE_PERCENTAGE, statistics.scorePercentage()
        ));
        return buildMasteryReportResponse(
                saved.getId(),
                statistics,
                saved.getSessionState(),
                isFirstCompletedSessionEver,
                isSecondCompletedSessionEver
        );
    }

    private void recordCorrectConceptsForSourcePacks(
            UUID userId,
            QuickReviewSessionEntity session,
            List<String> correctConcepts,
            OffsetDateTime now
    ) {
        if (correctConcepts.isEmpty()) {
            return;
        }
        recordConceptsForSourcePacks(userId, session, correctConcepts, now, true);
    }

    private void recordIncorrectConceptsForSourcePacks(
            UUID userId,
            QuickReviewSessionEntity session,
            List<String> incorrectConcepts,
            OffsetDateTime now
    ) {
        if (incorrectConcepts.isEmpty()) {
            return;
        }
        recordConceptsForSourcePacks(userId, session, incorrectConcepts, now, false);
    }

    private void recordConceptsForSourcePacks(
            UUID userId,
            QuickReviewSessionEntity session,
            List<String> concepts,
            OffsetDateTime now,
            boolean correct
    ) {
        for (UUID studyPackId : resolveSourceStudyPackIds(session)) {
            studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                    .ifPresent(studyPack -> {
                        if (correct) {
                            conceptHealthService.recordCorrectAnswersForKnownConcepts(
                                    userId,
                                    studyPackId,
                                    concepts,
                                    getKeyConcepts(studyPack),
                                    now
                            );
                        } else {
                            conceptHealthService.recordIncorrectAnswersForKnownConcepts(
                                    userId,
                                    studyPackId,
                                    concepts,
                                    getKeyConcepts(studyPack),
                                    now
                            );
                        }
                    });
        }
    }

    private Set<UUID> resolveSourceStudyPackIds(QuickReviewSessionEntity session) {
        Set<UUID> studyPackIds = new LinkedHashSet<>();
        if (session.getStudyPackId() != null) {
            studyPackIds.add(session.getStudyPackId());
        }
        for (LongExamSourceNoteRef sourceNoteRef : extractSourceNoteRefs(session.getSessionState())) {
            UUID studyPackId = parseOptionalUuid(sourceNoteRef.studyPackId());
            if (studyPackId != null) {
                studyPackIds.add(studyPackId);
            }
        }
        return studyPackIds;
    }

    private UUID parseOptionalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public SimpleMessageResponse forfeitSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS
                && session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(OffsetDateTime.now());
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_FORFEITED, saved.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString()
        ));
        return new SimpleMessageResponse(LONG_EXAM_FORFEITED_MESSAGE);
    }

    private QuickReviewSessionEntity findOwnedSessionOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.LONG_EXAM
                )
                .orElseThrow(LongExamSessionNotFoundException::new);
    }

    private int resolveQuestionCount(UUID userId) {
        LearnerLevel learnerLevel = resolveLearnerLevel(userId);
        return switch (learnerLevel == null ? LearnerLevel.COLLEGE : learnerLevel) {
            case GRADE_SCHOOL, JUNIOR_HIGH -> properties.getPricing().getLongExamLowTierCount();
            case BOARD_EXAM_REVIEW, PROFESSIONAL -> properties.getPricing().getLongExamHighTierCount();
            case SENIOR_HIGH, COLLEGE, PERSONAL_LEARNING -> properties.getPricing().getLongExamMidTierCount();
        };
    }

    private LearnerLevel resolveLearnerLevel(UUID userId) {
        return userRepository.findById(userId)
                .map(UserEntity::getLearnerLevel)
                .orElse(null);
    }

    private int assertLongExamQuotaAvailable(UUID userId, PlanType planType, int quotaUnits) {
        long usedThisMonth = countLongExamUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().resolveMonthlyLongExamLimit(planType);
        if (usedThisMonth + quotaUnits <= monthlyLimit) {
            return (int) usedThisMonth;
        }
        throw new MonthlyLongExamLimitReachedException();
    }

    private long countLongExamUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return userUsageService.getMonthlyUsage(userId, now).longExamUsedThisMonth();
    }

    private LongExamStartResponse buildStartResponse(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        boolean canResume = session.getStatus() == QuickReviewSessionStatus.IN_PROGRESS
                || session.getStatus() == QuickReviewSessionStatus.PAUSED;
        int totalQuestions = quiz.isEmpty() ? safeTotalQuestions(session) : quiz.size();
        PlanType planType = subscriptionService.resolvePlan(session.getUserId());
        return new LongExamStartResponse(
                session.getId(),
                session.getStatus().name(),
                canResume ? quiz : List.of(),
                totalQuestions,
                extractDifficulty(session.getSessionState()),
                canResume,
                extractTimeLimitSeconds(session.getSessionState(), totalQuestions),
                extractTimerStartedAtEpochSeconds(session.getSessionState()),
                extractSourceNoteRefs(session.getSessionState()),
                (int) countLongExamUsedThisMonth(session.getUserId()),
                properties.getPricing().resolveMonthlyLongExamLimit(planType)
        );
    }

    private LongExamStartResponse buildEmptyStartResponse(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        return new LongExamStartResponse(
                null,
                null,
                List.of(),
                0,
                DIFFICULTY_MIXED,
                false,
                0,
                0,
                List.of(),
                (int) countLongExamUsedThisMonth(userId),
                properties.getPricing().resolveMonthlyLongExamLimit(planType)
        );
    }

    private LongExamSessionResponse buildSessionResponse(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        int totalQuestions = quiz.isEmpty() ? safeTotalQuestions(session) : quiz.size();
        return new LongExamSessionResponse(
                session.getId(),
                session.getStatus().name(),
                quiz,
                QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz),
                QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                totalQuestions,
                extractDifficulty(session.getSessionState()),
                session.getStatus() == QuickReviewSessionStatus.PAUSED,
                extractTimeLimitSeconds(session.getSessionState(), totalQuestions),
                extractTimerStartedAtEpochSeconds(session.getSessionState()),
                extractSourceNoteRefs(session.getSessionState())
        );
    }

    private QuickReviewSessionEntity buildGeneratingSession(
            UUID userId,
            StudyPackEntity studyPack,
            String difficulty,
            int questionCount,
            List<LongExamSourceNoteRef> sourceNoteRefs
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPack.getId());
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.LONG_EXAM);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(questionCount);
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(buildInitialSessionState(difficulty, sourceNoteRefs));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        return session;
    }

    private void markSessionReady(QuickReviewSessionEntity session, List<QuizItem> quiz, String difficulty) {
        List<LongExamSourceNoteRef> sourceNoteRefs = extractSourceNoteRefs(session.getSessionState());
        Map<String, Object> state = QuizSessionStateUtils.withQuiz(quiz, buildInitialSessionState(difficulty, sourceNoteRefs));
        state.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        state.put(SESSION_STATE_TIME_LIMIT_SECONDS, quiz.size() * SECONDS_PER_QUESTION);

        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(state);
        session.setCompletedAt(null);
    }

    private void markSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setCompletedAt(null);
    }

    private LongExamStatistics computeStatistics(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        Map<String, DomainCounter> counters = new LinkedHashMap<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            String domain = normalizeDomain(item == null ? null : item.concept());
            DomainCounter counter = counters.computeIfAbsent(domain, unused -> new DomainCounter());
            counter.totalQuestions += 1;

            if (QuizSessionReviewUtils.isAnswerCorrect(item, index, selectedChoices, selectedMultiChoices)) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int answeredQuestions = countAnsweredQuestions(selectedChoices, selectedMultiChoices);
        int scorePercentage = answeredQuestions <= 0
                ? 0
                : calculateAccuracy(correctAnswers, answeredQuestions);
        List<LongExamMasteryReportResponse.LongExamDomainStat> domainBreakdown = counters.entrySet()
                .stream()
                .map(entry -> new LongExamMasteryReportResponse.LongExamDomainStat(
                        entry.getKey(),
                        entry.getValue().totalQuestions,
                        entry.getValue().correctAnswers,
                        calculateAccuracy(entry.getValue().correctAnswers, entry.getValue().totalQuestions)
                ))
                .toList();
        List<String> weakDomains = domainBreakdown.stream()
                .filter(stat -> stat.accuracyPercentage() < WEAK_DOMAIN_ACCURACY_THRESHOLD)
                .map(LongExamMasteryReportResponse.LongExamDomainStat::domain)
                .toList();
        String performanceSummary = resolvePerformanceSummary(scorePercentage);
        String suggestedNextStep = resolveSuggestedNextStep(scorePercentage, weakDomains);
        return new LongExamStatistics(
                quiz.size(),
                answeredQuestions,
                correctAnswers,
                scorePercentage,
                domainBreakdown,
                weakDomains,
                performanceSummary,
                suggestedNextStep
        );
    }

    private int countAnsweredQuestions(
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        Set<Integer> answeredQuestionIndexes = new LinkedHashSet<>();
        if (selectedChoices != null) {
            answeredQuestionIndexes.addAll(selectedChoices.keySet());
        }
        if (selectedMultiChoices != null) {
            selectedMultiChoices.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .forEach(answeredQuestionIndexes::add);
        }
        return answeredQuestionIndexes.size();
    }

    private Map<String, Object> buildMasteryReportMetadata(LongExamStatistics statistics) {
        List<Map<String, Object>> domainBreakdown = statistics.domainBreakdown().stream()
                .map(stat -> Map.<String, Object>of(
                        DOMAIN_KEY, stat.domain(),
                        TOTAL_QUESTIONS_KEY, stat.totalQuestions(),
                        CORRECT_ANSWERS_KEY, stat.correctAnswers(),
                        ACCURACY_PERCENTAGE_KEY, stat.accuracyPercentage()
                ))
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SESSION_METADATA_DOMAIN_BREAKDOWN, domainBreakdown);
        metadata.put(SESSION_METADATA_WEAK_DOMAINS, statistics.weakDomains());
        metadata.put(SESSION_METADATA_PERFORMANCE_SUMMARY, statistics.performanceSummary());
        metadata.put(SESSION_METADATA_SUGGESTED_NEXT_STEP, statistics.suggestedNextStep());
        return metadata;
    }

    private LongExamMasteryReportResponse buildMasteryReportResponse(
            UUID sessionId,
            LongExamStatistics statistics,
            Map<String, Object> sessionState,
            boolean isFirstCompletedSessionEver,
            boolean isSecondCompletedSessionEver
    ) {
        return new LongExamMasteryReportResponse(
                sessionId,
                statistics.totalQuestions(),
                statistics.answeredQuestions(),
                statistics.scorePercentage(),
                statistics.domainBreakdown(),
                statistics.weakDomains(),
                statistics.performanceSummary(),
                statistics.suggestedNextStep(),
                extractSourceNotes(sessionState),
                isFirstCompletedSessionEver,
                isSecondCompletedSessionEver
        );
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedLongExamSourceOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(InvalidLongExamSourceException::new);
    }

    private List<UUID> resolveAdditionalStudyPackIds(LongExamStartRequest request, UUID primaryStudyPackId) {
        if (request == null || request.additionalStudyPackIds() == null || request.additionalStudyPackIds().isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (String rawStudyPackId : request.additionalStudyPackIds()) {
            if (rawStudyPackId == null || rawStudyPackId.isBlank()) {
                throw new InvalidLongExamSourceException();
            }
            UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                    rawStudyPackId,
                    InvalidLongExamSourceException::new
            );
            if (studyPackId.equals(primaryStudyPackId)) {
                throw new InvalidLongExamSourceException();
            }
            uniqueIds.add(studyPackId);
        }
        if (uniqueIds.size() > MAX_ADDITIONAL_SOURCE_COUNT) {
            throw new InvalidLongExamSourceException();
        }
        return List.copyOf(uniqueIds);
    }

    private List<LongExamSourceNoteRef> resolveSourceNoteRefs(
            StudyPackEntity primaryStudyPack,
            UUID userId,
            List<UUID> additionalStudyPackIds,
            int questionCount
    ) {
        List<StudyPackEntity> sources = new ArrayList<>(1 + additionalStudyPackIds.size());
        sources.add(primaryStudyPack);
        String primarySubject = resolveNoteSubjectForStudyPack(primaryStudyPack);
        if (!additionalStudyPackIds.isEmpty() && primarySubject.isBlank()) {
            throw new InvalidLongExamSourceException();
        }
        for (UUID additionalStudyPackId : additionalStudyPackIds) {
            StudyPackEntity additionalStudyPack = findOwnedLongExamSourceOrThrow(additionalStudyPackId, userId);
            if (!primarySubject.equals(resolveNoteSubjectForStudyPack(additionalStudyPack))) {
                throw new InvalidLongExamSourceException();
            }
            sources.add(additionalStudyPack);
        }

        int sourceCount = sources.size();
        int baseQuestionCount = questionCount / sourceCount;
        if (baseQuestionCount < MIN_QUESTIONS_PER_SOURCE) {
            throw new InvalidLongExamSourceException();
        }
        int remainder = questionCount % sourceCount;
        List<LongExamSourceNoteRef> sourceNoteRefs = new ArrayList<>(sourceCount);
        for (int index = 0; index < sources.size(); index++) {
            StudyPackEntity source = sources.get(index);
            int sourceQuestionCount = baseQuestionCount + (index == 0 ? remainder : 0);
            sourceNoteRefs.add(buildSourceNoteRef(source, sourceQuestionCount));
        }
        return sourceNoteRefs;
    }

    private LongExamSourceNoteRef buildSourceNoteRef(StudyPackEntity studyPack, int questionCount) {
        return new LongExamSourceNoteRef(
                studyPack.getId().toString(),
                studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                questionCount
        );
    }

    private List<QuizItem> generateQuizForSources(
            UserEntity user,
            List<LongExamSourceNoteRef> sourceNoteRefs,
            String difficulty
    ) {
        List<QuizItem> mergedQuiz = new ArrayList<>();
        Set<String> disallowedQuestions = new LinkedHashSet<>();
        for (LongExamSourceNoteRef sourceNoteRef : sourceNoteRefs) {
            UUID sourceStudyPackId = UuidParsingUtils.parseUuidOrThrow(
                    sourceNoteRef.studyPackId(),
                    StudyPackNotFoundException::new
            );
            StudyPackEntity sourceStudyPack = findOwnedStudyPackForGenerationOrThrow(sourceStudyPackId, user.getId());
            StudyPackGenerationContext generationContext = generationContextResolver.resolveForStudyPack(
                    user.getId(),
                    sourceStudyPack
            );
            List<String> sourceDisallowedQuestions = extractQuestionTexts(sourceStudyPack.getQuiz());
            disallowedQuestions.addAll(QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(sourceDisallowedQuestions));
            List<QuizItem> generatedQuiz = quizGenerationService.generateLongExamParallel(
                    sourceStudyPack.getTitle(),
                    sourceStudyPack.getSummary(),
                    getKeyConcepts(sourceStudyPack),
                    sourceDisallowedQuestions,
                    sourceNoteRef.questionCount(),
                    difficulty,
                    generationContext,
                    llmParallelTaskExecutor
            );
            List<QuizItem> uniqueGeneratedQuiz = QuizDeduplicationUtils.uniqueQuestions(
                    generatedQuiz,
                    disallowedQuestions
            );
            mergedQuiz.addAll(uniqueGeneratedQuiz);
            disallowedQuestions.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(uniqueGeneratedQuiz));
        }
        return mergedQuiz;
    }

    private Map<String, Object> buildInitialSessionState(String difficulty, List<LongExamSourceNoteRef> sourceNoteRefs) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_DIFFICULTY, difficulty);
        state.put(SESSION_STATE_COMPLETED, false);
        state.put(SESSION_STATE_SOURCE_NOTE_REFS, sourceNoteRefsToState(sourceNoteRefs));
        return state;
    }

    private List<Map<String, Object>> sourceNoteRefsToState(List<LongExamSourceNoteRef> sourceNoteRefs) {
        if (sourceNoteRefs == null || sourceNoteRefs.isEmpty()) {
            return List.of();
        }
        return sourceNoteRefs.stream()
                .map(sourceNoteRef -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put(SOURCE_STUDY_PACK_ID_KEY, sourceNoteRef.studyPackId());
                    entry.put(SOURCE_NOTE_ID_KEY, sourceNoteRef.noteId());
                    entry.put(SOURCE_NOTE_TITLE_KEY, sourceNoteRef.noteTitle());
                    entry.put(SOURCE_QUESTION_COUNT_KEY, sourceNoteRef.questionCount());
                    return entry;
                })
                .toList();
    }

    private Map<String, Object> markSessionStateCompleted(Map<String, Object> existingState) {
        Map<String, Object> nextState = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            nextState.putAll(existingState);
        }
        nextState.put(SESSION_STATE_COMPLETED, true);
        return nextState;
    }

    private void assertSessionInProgress(QuickReviewSessionEntity session) {
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new LongExamSessionNotInProgressException();
        }
    }

    private String resolveDifficulty(LongExamStartRequest request) {
        if (request == null || request.difficulty() == null || request.difficulty().isBlank()) {
            return DIFFICULTY_MIXED;
        }
        String normalized = request.difficulty().trim().toLowerCase();
        return switch (normalized) {
            case DIFFICULTY_EASY, DIFFICULTY_MEDIUM, DIFFICULTY_HARD, DIFFICULTY_MIXED -> normalized;
            default -> throw new InvalidLongExamDifficultyException();
        };
    }

    private String extractDifficulty(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DIFFICULTY_MIXED;
        }
        Object raw = sessionState.get(SESSION_STATE_DIFFICULTY);
        if (raw instanceof String difficulty && !difficulty.isBlank()) {
            return difficulty;
        }
        return DIFFICULTY_MIXED;
    }

    private long extractTimerStartedAtEpochSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return 0L;
        }
        Object raw = sessionState.get(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private int extractTimeLimitSeconds(Map<String, Object> sessionState, int questionCount) {
        if (sessionState == null) {
            return questionCount * SECONDS_PER_QUESTION;
        }
        Object raw = sessionState.get(SESSION_STATE_TIME_LIMIT_SECONDS);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return questionCount * SECONDS_PER_QUESTION;
    }

    private List<LongExamSourceNoteRef> extractSourceNoteRefs(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return List.of();
        }
        Object raw = sessionState.get(SESSION_STATE_SOURCE_NOTE_REFS);
        if (!(raw instanceof List<?> rawEntries) || rawEntries.isEmpty()) {
            return List.of();
        }
        List<LongExamSourceNoteRef> sourceNoteRefs = new ArrayList<>(rawEntries.size());
        for (Object rawEntry : rawEntries) {
            LongExamSourceNoteRef sourceNoteRef = extractSourceNoteRef(rawEntry);
            if (sourceNoteRef != null) {
                sourceNoteRefs.add(sourceNoteRef);
            }
        }
        return sourceNoteRefs;
    }

    private LongExamSourceNoteRef extractSourceNoteRef(Object rawEntry) {
        if (rawEntry instanceof LongExamSourceNoteRef sourceNoteRef) {
            return sourceNoteRef;
        }
        if (!(rawEntry instanceof Map<?, ?> sourceMap)) {
            return null;
        }
        String studyPackId = readStringValue(sourceMap, SOURCE_STUDY_PACK_ID_KEY);
        String noteId = readStringValue(sourceMap, SOURCE_NOTE_ID_KEY);
        String noteTitle = readStringValue(sourceMap, SOURCE_NOTE_TITLE_KEY);
        int questionCount = readIntValue(sourceMap, SOURCE_QUESTION_COUNT_KEY);
        if (studyPackId == null || noteId == null || questionCount <= 0) {
            return null;
        }
        return new LongExamSourceNoteRef(studyPackId, noteId, noteTitle, questionCount);
    }

    private List<LongExamSourceNote> extractSourceNotes(Map<String, Object> sessionState) {
        return extractSourceNoteRefs(sessionState)
                .stream()
                .map(sourceNoteRef -> new LongExamSourceNote(sourceNoteRef.noteId(), sourceNoteRef.noteTitle()))
                .toList();
    }

    private String readStringValue(Map<?, ?> sourceMap, String key) {
        Object raw = sourceMap.get(key);
        if (raw instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private int readIntValue(Map<?, ?> sourceMap, String key) {
        Object raw = sourceMap.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private List<String> extractQuestionTexts(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }
        return quiz.stream()
                .map(QuizItem::question)
                .filter(question -> question != null && !question.isBlank())
                .toList();
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private String normalizeSubjectForMatch(String subject) {
        return subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveNoteSubjectForStudyPack(StudyPackEntity studyPack) {
        return noteRepository.findById(studyPack.getNoteId())
                .map(note -> normalizeSubjectForMatch(note.getSubject()))
                .orElseGet(() -> normalizeSubjectForMatch(studyPack.getSubject()));
    }

    private String normalizeDomain(String value) {
        if (value == null) {
            return UNKNOWN_DOMAIN_LABEL;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? UNKNOWN_DOMAIN_LABEL : normalized;
    }

    private int calculateAccuracy(int correctAnswers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((correctAnswers * 100.0) / totalQuestions);
    }

    private String resolvePerformanceSummary(int scorePercentage) {
        if (scorePercentage >= EXCELLENT_SCORE_THRESHOLD) {
            return PERFORMANCE_EXCELLENT;
        }
        if (scorePercentage >= GOOD_SCORE_THRESHOLD) {
            return PERFORMANCE_GOOD;
        }
        if (scorePercentage >= FAIR_SCORE_THRESHOLD) {
            return PERFORMANCE_FAIR;
        }
        return PERFORMANCE_NEEDS_IMPROVEMENT;
    }

    private String resolveSuggestedNextStep(int scorePercentage, List<String> weakDomains) {
        if (weakDomains != null && !weakDomains.isEmpty()) {
            return SUGGESTED_REVIEW_WEAK_DOMAINS;
        }
        if (scorePercentage >= EXCELLENT_SCORE_THRESHOLD) {
            return SUGGESTED_HARDER_DIFFICULTY;
        }
        return SUGGESTED_REVIEW_ANSWERS;
    }

    private int safeTotalQuestions(QuickReviewSessionEntity session) {
        return session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
    }

    private void trackAnalytics(
            UUID userId,
            AnalyticsEventType eventType,
            UUID entityId,
            Map<String, Object> metadata
    ) {
        try {
            analyticsService.trackEvent(userId, eventType, entityId, metadata);
        } catch (RuntimeException ignored) {
            // Analytics must never break the primary Long Exam action.
        }
    }

    private static final class DomainCounter {
        private int totalQuestions;
        private int correctAnswers;
    }

    private record LongExamStatistics(
            int totalQuestions,
            int answeredQuestions,
            int correctAnswers,
            int scorePercentage,
            List<LongExamMasteryReportResponse.LongExamDomainStat> domainBreakdown,
            List<String> weakDomains,
            String performanceSummary,
            String suggestedNextStep
    ) {
    }
}
