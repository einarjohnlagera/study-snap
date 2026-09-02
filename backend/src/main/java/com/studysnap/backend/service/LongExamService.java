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
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
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
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidLongExamDifficultyException;
import com.studysnap.backend.exception.InvalidLongExamSourceException;
import com.studysnap.backend.exception.LongExamGenerationFailedException;
import com.studysnap.backend.exception.LongExamInsufficientEligibleSourcesException;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    private static final String SESSION_STATE_SHORT_EXAM = "shortExam";
    private static final String SESSION_STATE_EXPECTED_QUESTION_COUNT = "expectedQuestionCount";
    public static final String SESSION_STATE_LONG_EXAM_QUOTA_RESERVED = "longExamQuotaReserved";
    public static final String SESSION_STATE_LONG_EXAM_QUOTA_REVERSED = "longExamQuotaReversed";
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
    private static final String ANALYTICS_METADATA_SOURCE_COUNT = "sourceCount";
    private static final String ANALYTICS_METADATA_SOURCE_SCOPE = "sourceScope";
    private static final String SOURCE_SCOPE_PLAN = "plan";
    private static final String SOURCE_SCOPE_MANUAL = "manual";
    private static final String ANALYTICS_METADATA_SCORE_PERCENTAGE = "scorePercentage";
    private static final int WEAK_DOMAIN_ACCURACY_THRESHOLD = 60;
    private static final int FAIR_SCORE_THRESHOLD = 50;
    private static final int GOOD_SCORE_THRESHOLD = 70;
    private static final int EXCELLENT_SCORE_THRESHOLD = 90;
    private static final int SECONDS_PER_QUESTION = 90;
    private static final int MAX_ADDITIONAL_SOURCE_COUNT = 3;

    public static final int QUOTA_UNITS_PER_SESSION = 1;
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
    private final PlanSourcedExamVerifier planSourcedExamVerifier;
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
    private final LongExamPlanSourceSampler longExamPlanSourceSampler;
    private final GenerationRecoveryRowWriter generationRecoveryRowWriter;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LongExamStartResponse startSession(String studyPackIdRaw, UUID userId, LongExamStartRequest request) {
        authService.requireEmailVerified(userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.LONG_EXAM_SESSION);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        String difficulty = resolveDifficulty(request);
        int questionCount = resolveQuestionCount(userId);
        boolean claimsPlanScope = request != null
                && request.sourceCollectionId() != null
                && !request.sourceCollectionId().isBlank();
        List<UUID> additionalStudyPackIds = resolveAdditionalStudyPackIds(
                request,
                studyPackId,
                claimsPlanScope ? resolveMaxSourceNotes(questionCount) - 1 : MAX_ADDITIONAL_SOURCE_COUNT
        );
        // ⚠️ Seeded from the claim only so a path that never reaches verification still reports something;
        // resolveSourceNoteRefs overwrites it with the VERIFIED outcome. Reporting the claim would let a
        // client set the one metric that separates plan-sourced exams from manual ones.
        AtomicReference<String> verifiedSourceScope = new AtomicReference<>(
                claimsPlanScope ? SOURCE_SCOPE_PLAN : SOURCE_SCOPE_MANUAL
        );
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

            UUID generationSessionId = UUID.randomUUID();
            ResolvedExamSources resolvedSources = resolveSourceNoteRefs(
                    studyPack,
                    userId,
                    additionalStudyPackIds,
                    questionCount,
                    request == null ? null : request.sourceCollectionId(),
                    generationSessionId
            );
            List<LongExamSourceNoteRef> sourceNoteRefs = resolvedSources.sourceNoteRefs();
            // The VERIFIED scope, replacing the claim computed before the request was checked.
            verifiedSourceScope.set(resolvedSources.planSourced() ? SOURCE_SCOPE_PLAN : SOURCE_SCOPE_MANUAL);
            // ⚠️ `additionalStudyPackIds.isEmpty()` ALONE IS NOT THE SINGLE-NOTE TEST ANY MORE. A plan-sourced
            // start sends only `sourceCollectionId`, so that list is empty while the exam is sampled across
            // the whole plan. Without the planSourced clause the second and every later plan launch is served
            // this PRIMARY-ONLY question pool while the session still records the sampled multi-source
            // sourceNoteRefs and sourceScope=plan — a single-note exam presented, and reported, as a
            // curriculum exam. `sourceScope` must record the VERIFIED outcome, and a dated checkpoint reads it.
            if (additionalStudyPackIds.isEmpty() && !resolvedSources.planSourced()) {
                StudyPackGenerationContext generationContext = generationContextResolver.resolveForStudyPack(userId, studyPack);
                Optional<List<QuizItem>> pooledQuestions = examQuestionPoolService.sampleQuestions(
                        studyPackId,
                        ExamQuestionPoolService.MODE_LONG_EXAM,
                        questionCount,
                        StudyPackGenerationContextResolver.effectiveCurriculumLevel(generationContext)
                );
                if (pooledQuestions.isPresent()) {
                    QuickReviewSessionEntity poolSession = buildGeneratingSession(
                            generationSessionId,
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
                            ANALYTICS_METADATA_DIFFICULTY, difficulty,
                            ANALYTICS_METADATA_SOURCE_COUNT, sourceNoteRefs.size(),
                            ANALYTICS_METADATA_SOURCE_SCOPE, verifiedSourceScope.get()
                    ));
                    createdSession.set(true);
                    poolSourcedSession.set(true);
                    return saved;
                }
            }
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(buildGeneratingSession(
                    generationSessionId,
                    userId,
                    studyPack,
                    difficulty,
                    questionCount,
                    sourceNoteRefs
            ));
            createdSession.set(true);
            return saved;
        });
        if (session == null) {
            throw new LongExamGenerationFailedException();
        }
        if (createdSession.get()) {
            // A crash after the session commit but before this charge leaves a reserved-but-uncharged row.
            // The async reservation is deliberate: charging inside the transaction re-opens the quota bypass.
            userUsageService.incrementLongExamGenerationBy(userId, QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
        }
        if (createdSession.get() && !poolSourcedSession.get()) {
            dispatchLongExamGenerationAfterCommit(session.getId(), difficulty, verifiedSourceScope.get());
        }
        if (createdSession.get()
                && !poolSourcedSession.get()
                && additionalStudyPackIds.isEmpty()
                // Warming a PRIMARY-ONLY pool for a plan-sourced session is what created the pool that the
                // next plan launch was then served from. Plan exams are sampled fresh, never pooled.
                && !SOURCE_SCOPE_PLAN.equals(verifiedSourceScope.get())) {
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
            String difficulty,
            String sourceScope
    ) {
        Runnable generationTask = () -> generateLongExamAsync(sessionId, difficulty, sourceScope);
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

    // sourceScope is threaded through rather than re-derived: this method reloads the session from the
    // database and the session state does not record how its sources were chosen. Both LONG_EXAM_STARTED
    // sites must carry the same metadata shape, or a funnel read has to special-case which branch fired.
    private void generateLongExamAsync(UUID sessionId, String difficulty, String sourceScope) {
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
                GeneratedLongExamQuiz generatedLongExamQuiz = generateQuizForSources(user, sourceNoteRefs, difficulty);
                List<QuizItem> longExamQuiz = generatedLongExamQuiz.quiz();
                int expectedQuestionCount = safeTotalQuestions(session);
                if (longExamQuiz.size() < properties.getPricing().getLongExamMinimumAssembledQuestions()
                        || generatedLongExamQuiz.contributingSourceCount()
                        < Math.min(
                                sourceNoteRefs.size(),
                                properties.getPricing().getLongExamMinimumContributingSources()
                        )) {
                    throw new LongExamGenerationFailedException();
                }

                // ⚠️ FREE-QUOTA RACE GUARD. Generation runs INSIDE this transaction and can take longer
                // than the stale-session sweeper's cutoff (a 10-source exam is bounded at 10 x 240s = 40
                // minutes against a 30-minute cutoff, and the transaction has no timeout). The sweeper can
                // therefore mark this session FAILED and REFUND its quota unit while we are still
                // generating. Without this guard the write below resurrects the refunded session as
                // IN_PROGRESS with a full quiz — a free, usable exam — because the entity carries no
                // @Version and the status was last read before generation began.
                // ⚠️ LOCK FIRST, THEN RE-READ. findByIdForUpdate serialises against the sweeper, but it
                // returns the instance already in the persistence context, so its getStatus() can still be
                // a stale GENERATING. The scalar projection is what actually reaches the database.
                quickReviewSessionRepository.findByIdForUpdate(sessionId)
                        .orElseThrow(LongExamSessionNotFoundException::new);
                if (quickReviewSessionRepository.findStatusById(sessionId)
                        .filter(QuickReviewSessionStatus.GENERATING::equals)
                        .isEmpty()) {
                    return null;
                }

                markSessionReady(session, longExamQuiz, difficulty);
                markShortExam(session, expectedQuestionCount, longExamQuiz.size());
                QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
                trackAnalytics(session.getUserId(), AnalyticsEventType.LONG_EXAM_STARTED, saved.getStudyPackId(), Map.of(
                        ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                        ANALYTICS_METADATA_QUESTION_COUNT, longExamQuiz.size(),
                        ANALYTICS_METADATA_DIFFICULTY, difficulty,
                        ANALYTICS_METADATA_SOURCE_COUNT, sourceNoteRefs.size(),
                        // The verified scope, threaded in by dispatchLongExamGenerationAfterCommit.
                        ANALYTICS_METADATA_SOURCE_SCOPE, sourceScope
                ));
                return null;
            });
        } catch (Exception ex) {
            log.warn("Long Exam generation failed for sessionId={}: {}", sessionId, ex.getMessage());
            generationRecoveryRowWriter.failLongExamSession(sessionId);
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
        if (request.selectedIdentificationAnswer() != null) {
            nextSessionState = QuizSessionStateUtils.withSelectedIdentificationAnswer(
                    nextSessionState,
                    request.questionIndex(),
                    request.selectedIdentificationAnswer()
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
        Map<Integer, String> selectedIdentificationAnswers = QuizSessionStateUtils.extractSelectedIdentificationAnswers(
                session.getSessionState(),
                quiz
        );
        LongExamStatistics statistics = computeStatistics(quiz, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers);
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
        Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack =
                QuizSessionReviewUtils.computeKeyConceptBreakdownBySourceStudyPack(
                        quiz,
                        selectedChoices,
                        selectedMultiChoices,
                        selectedIdentificationAnswers,
                        Map.of()
                );
        recordConceptsForSourcePacks(
                userId,
                saved,
                conceptBreakdownBySourceStudyPack,
                now,
                true
        );
        recordConceptsForSourcePacks(
                userId,
                saved,
                conceptBreakdownBySourceStudyPack,
                now,
                false
        );
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

    private void recordConceptsForSourcePacks(
            UUID userId,
            QuickReviewSessionEntity session,
            Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack,
            OffsetDateTime now,
            boolean correct
    ) {
        Map<UUID, Set<String>> conceptsByStudyPack = new LinkedHashMap<>();
        conceptBreakdownBySourceStudyPack.forEach((sourceStudyPackId, conceptBreakdown) -> {
            List<String> concepts = correct
                    ? QuizSessionReviewUtils.computeFullyCorrectConcepts(conceptBreakdown)
                    : QuizSessionReviewUtils.computeConceptsWithMisses(conceptBreakdown);
            if (concepts.isEmpty()) {
                return;
            }
            UUID stampedSourceStudyPackId = parseOptionalUuid(sourceStudyPackId);
            if (stampedSourceStudyPackId != null) {
                conceptsByStudyPack.computeIfAbsent(stampedSourceStudyPackId, ignored -> new LinkedHashSet<>())
                        .addAll(concepts);
                return;
            }
            // Only pre-v0.104.0 (unstamped) items take the historical Long Exam broadcast fallback.
            // Stamped items never consult sourceNoteRefs, so they cannot over-attribute to sibling packs.
            for (UUID fallbackStudyPackId : resolveSourceStudyPackIds(session)) {
                conceptsByStudyPack.computeIfAbsent(fallbackStudyPackId, ignored -> new LinkedHashSet<>())
                        .addAll(concepts);
            }
        });
        for (Map.Entry<UUID, Set<String>> entry : conceptsByStudyPack.entrySet()) {
            UUID studyPackId = entry.getKey();
            List<String> concepts = List.copyOf(entry.getValue());
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
        return properties.getPricing().resolveLongExamQuestionCount(resolveLearnerLevel(userId));
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
                properties.getPricing().resolveMonthlyLongExamLimit(planType),
                resolveMaxSourceNotes(resolveQuestionCount(session.getUserId()))
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
                properties.getPricing().resolveMonthlyLongExamLimit(planType),
                resolveMaxSourceNotes(resolveQuestionCount(userId))
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
                QuizSessionStateUtils.extractSelectedIdentificationAnswers(session.getSessionState(), quiz),
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
            UUID sessionId,
            UUID userId,
            StudyPackEntity studyPack,
            String difficulty,
            int questionCount,
            List<LongExamSourceNoteRef> sourceNoteRefs
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
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
        // ⚠️ CARRY THE QUOTA FLAGS FORWARD RATHER THAN REGENERATING THEM. buildInitialSessionState writes
        // longExamQuotaReserved=true and knows nothing of longExamQuotaReversed, so rebuilding state here
        // would re-arm a refund that has already been paid out and erase the idempotency stamp that stops
        // it happening twice. This is a defect independent of the race guard above: ANY future path that
        // marks a session ready would otherwise silently reopen the refund.
        carryForwardQuotaFlags(session.getSessionState(), state);
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

    private void carryForwardQuotaFlags(Map<String, Object> previousState, Map<String, Object> nextState) {
        if (previousState == null) {
            return;
        }
        for (String key : List.of(SESSION_STATE_LONG_EXAM_QUOTA_RESERVED, SESSION_STATE_LONG_EXAM_QUOTA_REVERSED)) {
            Object value = previousState.get(key);
            if (value != null) {
                nextState.put(key, value);
            }
        }
    }

    private void markShortExam(QuickReviewSessionEntity session, int expectedQuestionCount, int actualQuestionCount) {
        Map<String, Object> state = new LinkedHashMap<>(session.getSessionState());
        state.put(SESSION_STATE_EXPECTED_QUESTION_COUNT, expectedQuestionCount);
        state.put(SESSION_STATE_SHORT_EXAM, actualQuestionCount < expectedQuestionCount);
        session.setSessionState(state);
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
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        Map<String, DomainCounter> counters = new LinkedHashMap<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            String domain = normalizeDomain(item == null ? null : item.concept());
            DomainCounter counter = counters.computeIfAbsent(domain, unused -> new DomainCounter());
            counter.totalQuestions += 1;

            if (QuizSessionReviewUtils.isAnswerCorrect(
                    item,
                    index,
                    selectedChoices,
                    selectedMultiChoices,
                    selectedIdentificationAnswers,
                    Map.of()
            )) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int answeredQuestions = countAnsweredQuestions(selectedChoices, selectedMultiChoices, selectedIdentificationAnswers);
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
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
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
        if (selectedIdentificationAnswers != null) {
            selectedIdentificationAnswers.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
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
                extractShortExam(sessionState),
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

    private List<UUID> resolveAdditionalStudyPackIds(
            LongExamStartRequest request,
            UUID primaryStudyPackId,
            int claimedMaxAdditional
    ) {
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
        // Reject an over-cap request BEFORE the primary pack is loaded, because that load takes a row
        // lock — validating request shape after acquiring a lock is the wrong order. The bound used here
        // is the one the CLAIM allows: a caller naming no plan gets the manual cap immediately. The claim
        // is not trusted, only used to size this early check; resolveSourceNoteRefs re-applies the cap
        // that the VERIFIED scope allows, so an unverifiable plan claim still falls back to the manual
        // cap rather than keeping the larger one.
        if (uniqueIds.size() > claimedMaxAdditional) {
            throw new InvalidLongExamSourceException();
        }
        return List.copyOf(uniqueIds);
    }

    /**
     * Most sources this learner may combine, counting the primary.
     *
     * <p>⚠️ Derived, never a constant. {@code questionCount} comes from the learner's LEVEL rather than
     * their selection, and the three-questions-per-source floor is checked against
     * {@code questionCount / sourceCount} below — so the true ceiling is 6 / 8 / 10 by level, and a
     * College learner (the default) fails at 9. Exposing this on the response is what stops the
     * frontend re-implementing the level mapping.
     */
    int resolveMaxSourceNotes(int questionCount) {
        return ExamSourceLimitResolver.resolveMaxSourceNotes(questionCount);
    }

    /**
     * Resolved sources plus whether the plan claim actually held.
     *
     * <p>⚠️ {@code planSourced} is the VERIFIED outcome, not the request's claim. Analytics must record
     * this rather than the claim: a caller who owns a collection that does not contain the primary is
     * treated as a manual exam in every respect, and reporting them as plan-sourced would let the client
     * set the one metric that distinguishes the new path from the old one.
     */
    record ResolvedExamSources(List<LongExamSourceNoteRef> sourceNoteRefs, boolean planSourced) {
    }

    private ResolvedExamSources resolveSourceNoteRefs(
            StudyPackEntity primaryStudyPack,
            UUID userId,
            List<UUID> additionalStudyPackIds,
            int questionCount,
            String sourceCollectionIdRaw,
            UUID sessionId
    ) {
        Set<UUID> planMemberNoteIds = planSourcedExamVerifier.resolvePlanMemberNoteIds(
                sourceCollectionIdRaw,
                userId,
                InvalidLongExamSourceException::new
        );
        List<PlanSourcedExamVerifier.PlanExamMember> planMembers = planSourcedExamVerifier.resolvePlanMembers(
                sourceCollectionIdRaw,
                userId,
                InvalidLongExamSourceException::new
        );
        // ⚠️ The PRIMARY must be a member too, or naming an unrelated collection the caller happens to
        // own would relax the rule for its members while the exam is anchored somewhere else entirely.
        boolean planSourced = planMemberNoteIds.contains(primaryStudyPack.getNoteId());
        if (planSourced && !planMembers.isEmpty()) {
            List<LongExamPlanSourceSampler.EligiblePlanSource> eligiblePool = resolveEligiblePlanSourcePool(
                    planMembers,
                    userId
            );
            int minimumSources = properties.getPricing().getLongExamMinimumContributingSources();
            if (eligiblePool.size() < minimumSources) {
                throw new LongExamInsufficientEligibleSourcesException(eligiblePool.size(), minimumSources);
            }
            List<StudyPackEntity> sampledSources = longExamPlanSourceSampler.sample(
                            eligiblePool,
                            primaryStudyPack.getId(),
                            resolveMaxSourceNotes(questionCount),
                            sessionId
                    ).stream()
                    .map(LongExamPlanSourceSampler.EligiblePlanSource::studyPack)
                    .toList();
            return new ResolvedExamSources(allocateQuestionsAcrossSources(sampledSources, questionCount), true);
        }

        List<StudyPackEntity> sources = new ArrayList<>(1 + additionalStudyPackIds.size());
        sources.add(primaryStudyPack);
        String primarySubject = resolveNoteSubjectForStudyPack(primaryStudyPack);

        int maxAdditional = planSourced ? resolveMaxSourceNotes(questionCount) - 1 : MAX_ADDITIONAL_SOURCE_COUNT;
        if (additionalStudyPackIds.size() > maxAdditional) {
            throw new InvalidLongExamSourceException();
        }

        // The same-subject rule still applies to a note the plan does not contain. Skipping it wholesale
        // once any plan is named would let one plan-member source smuggle in arbitrary others.
        if (!additionalStudyPackIds.isEmpty() && !planSourced && primarySubject.isBlank()) {
            throw new InvalidLongExamSourceException();
        }
        for (UUID additionalStudyPackId : additionalStudyPackIds) {
            StudyPackEntity additionalStudyPack = findOwnedLongExamSourceOrThrow(additionalStudyPackId, userId);
            boolean memberOfPlan = planSourced && planMemberNoteIds.contains(additionalStudyPack.getNoteId());
            if (!memberOfPlan && !primarySubject.equals(resolveNoteSubjectForStudyPack(additionalStudyPack))) {
                throw new InvalidLongExamSourceException();
            }
            sources.add(additionalStudyPack);
        }
        return new ResolvedExamSources(allocateQuestionsAcrossSources(sources, questionCount), planSourced);
    }

    /** Pool A: every verified plan member that independently has a ready, caller-owned Study Pack. */
    private List<LongExamPlanSourceSampler.EligiblePlanSource> resolveEligiblePlanSourcePool(
            List<PlanSourcedExamVerifier.PlanExamMember> planMembers,
            UUID userId
    ) {
        Map<UUID, StudyPackEntity> readyPacksByNoteId = studyPackRepository
                .findByOwnerUserIdAndNoteIdInAndStatus(
                        userId,
                        planMembers.stream().map(PlanSourcedExamVerifier.PlanExamMember::noteId).toList(),
                        StudyPackStatus.DONE
                ).stream()
                .collect(Collectors.toMap(StudyPackEntity::getNoteId, pack -> pack));
        return planMembers.stream()
                .map(member -> {
                    StudyPackEntity studyPack = readyPacksByNoteId.get(member.noteId());
                    return studyPack == null ? null : new LongExamPlanSourceSampler.EligiblePlanSource(
                            studyPack,
                            member.label(),
                            member.position()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<LongExamSourceNoteRef> allocateQuestionsAcrossSources(
            List<StudyPackEntity> sources,
            int questionCount
    ) {
        int sourceCount = sources.size();
        int baseQuestionCount = questionCount / sourceCount;
        if (baseQuestionCount < ExamSourceLimitResolver.minimumQuestionsPerSource()) {
            throw new InvalidLongExamSourceException();
        }
        int remainder = questionCount % sourceCount;
        List<LongExamSourceNoteRef> sourceNoteRefs = new ArrayList<>(sourceCount);
        for (int index = 0; index < sources.size(); index++) {
            sourceNoteRefs.add(buildSourceNoteRef(sources.get(index), baseQuestionCount + (index == 0 ? remainder : 0)));
        }
        return List.copyOf(sourceNoteRefs);
    }

    private LongExamSourceNoteRef buildSourceNoteRef(StudyPackEntity studyPack, int questionCount) {
        return new LongExamSourceNoteRef(
                studyPack.getId().toString(),
                studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                questionCount
        );
    }

    private GeneratedLongExamQuiz generateQuizForSources(
            UserEntity user,
            List<LongExamSourceNoteRef> sourceNoteRefs,
            String difficulty
    ) {
        List<QuizItem> mergedQuiz = new ArrayList<>();
        Set<String> disallowedQuestions = new LinkedHashSet<>();
        int contributingSourceCount = 0;
        for (LongExamSourceNoteRef sourceNoteRef : sourceNoteRefs) {
            try {
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
                List<QuizItem> uniqueGeneratedQuiz = QuizDeduplicationUtils.uniqueQuestions(generatedQuiz, disallowedQuestions);
                List<QuizItem> stampedGeneratedQuiz = uniqueGeneratedQuiz.stream()
                        .map(item -> item.withSourceStudyPackId(sourceStudyPackId.toString()))
                        .toList();
                if (!stampedGeneratedQuiz.isEmpty()) {
                    contributingSourceCount++;
                    mergedQuiz.addAll(stampedGeneratedQuiz);
                    disallowedQuestions.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(stampedGeneratedQuiz));
                }
            } catch (RuntimeException sourceFailure) {
                log.warn("Long Exam source did not contribute studyPackId={}: {}", sourceNoteRef.studyPackId(), sourceFailure.getMessage());
            }
        }
        return new GeneratedLongExamQuiz(List.copyOf(mergedQuiz), contributingSourceCount);
    }

    private record GeneratedLongExamQuiz(List<QuizItem> quiz, int contributingSourceCount) {
    }

    private Map<String, Object> buildInitialSessionState(String difficulty, List<LongExamSourceNoteRef> sourceNoteRefs) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_DIFFICULTY, difficulty);
        state.put(SESSION_STATE_COMPLETED, false);
        state.put(SESSION_STATE_SOURCE_NOTE_REFS, sourceNoteRefsToState(sourceNoteRefs));
        // This commits before the synchronous charge above; that accepted reserved-before-charged window is
        // documented in RELEASES.md because preserving the async quota gate prevents concurrent bypasses.
        state.put(SESSION_STATE_LONG_EXAM_QUOTA_RESERVED, true);
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

    private boolean extractShortExam(Map<String, Object> sessionState) {
        return sessionState != null && Boolean.TRUE.equals(sessionState.get(SESSION_STATE_SHORT_EXAM));
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
