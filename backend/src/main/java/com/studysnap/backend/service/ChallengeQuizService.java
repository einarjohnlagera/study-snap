package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.GenerateMoreChallengeQuizResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.exception.NotEnoughNewQuestionsException;
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.ChallengeQuizPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizSessionSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.ChallengeQuizGenerationFailedException;
import com.studysnap.backend.exception.ChallengeQuizNotAvailableException;
import com.studysnap.backend.exception.ChallengeQuizSessionNotFoundException;
import com.studysnap.backend.exception.ChallengeQuizSessionNotInProgressException;
import com.studysnap.backend.exception.InvalidBoardExamSourceException;
import com.studysnap.backend.exception.BoardExamInsufficientEligibleSourcesException;
import com.studysnap.backend.exception.InvalidChallengeQuizModeException;
import com.studysnap.backend.exception.InvalidChallengeQuizResultException;
import com.studysnap.backend.exception.MonthlyBoardExamLimitReachedException;
import com.studysnap.backend.exception.MonthlyChallengeQuizLimitReachedException;
import com.studysnap.backend.exception.MonthlyMultiNoteLimitReachedException;
import com.studysnap.backend.exception.MatchingQuestionGroupSourceMismatchException;
import com.studysnap.backend.exception.MultiNoteChallengeQuizSourceNotAllowedException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ChallengeQuizService {
    private static final String SESSION_STATE_TIME_LIMIT_SECONDS = "timeLimitSeconds";
    private static final String SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS = "timerStartedAtEpochSeconds";
    private static final String SESSION_STATE_SELECTED_CHOICES = "selectedChoices";
    private static final String SESSION_STATE_SELECTED_MULTI_CHOICES = "selectedMultiChoices";
    private static final String SESSION_STATE_SELECTED_IDENTIFICATION_ANSWERS = "selectedIdentificationAnswers";
    private static final String SESSION_STATE_SELECTED_ENUMERATION_ANSWERS = "selectedEnumerationAnswers";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final String SESSION_STATE_DIFFICULTY = "difficulty";
    public static final String SESSION_STATE_MODE = "mode";
    private static final String SESSION_STATE_QUIZ = "quiz";
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";
    private static final String SOURCE_STUDY_PACK_ID_KEY = "studyPackId";
    private static final String SOURCE_NOTE_ID_KEY = "noteId";
    private static final String SOURCE_NOTE_TITLE_KEY = "noteTitle";
    private static final String SOURCE_QUESTION_COUNT_KEY = "questionCount";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String SESSION_METADATA_CONCEPT_BREAKDOWN = "conceptBreakdown";
    private static final String CONCEPT_KEY = "concept";
    private static final String CORRECT_ANSWERS_KEY = "correctAnswers";
    private static final String TOTAL_QUESTIONS_KEY = "totalQuestions";
    private static final String ACCURACY_PERCENTAGE_KEY = "accuracyPercentage";
    private static final String UNKNOWN_CONCEPT_LABEL = "Uncategorized";
    private static final String AI_RATE_LIMIT_SCOPE = "challenge-quiz";
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_MEDIUM = "medium";
    private static final String DIFFICULTY_HARD = "hard";
    private static final String DIFFICULTY_MIXED = "mixed";
    private static final String PERFORMANCE_LEVEL_EXCELLENT = "Excellent";
    private static final String PERFORMANCE_LEVEL_GOOD = "Good";
    private static final String PERFORMANCE_LEVEL_FAIR = "Fair";
    private static final String PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT = "Needs Improvement";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_SOURCE_COUNT = "sourceCount";
    private static final String ANALYTICS_METADATA_SOURCE_SCOPE = "sourceScope";
    private static final String ANALYTICS_METADATA_SCORE_PERCENTAGE = "scorePercentage";
    private static final String ANALYTICS_METADATA_QUESTION_COUNT = "questionCount";
    private static final String ANALYTICS_METADATA_DIFFICULTY = "difficulty";
    private static final String ANALYTICS_METADATA_MODE = "mode";
    private static final String CHALLENGE_QUIZ_SESSION_ALREADY_ENDED_MESSAGE = "Challenge Quiz session has already ended.";
    private static final String CHALLENGE_QUIZ_SESSION_FORFEITED_MESSAGE = "Challenge Quiz session forfeited.";
    private static final String SESSION_REVIEW_NOT_AVAILABLE_CODE = "SESSION_REVIEW_NOT_AVAILABLE";
    private static final String CHALLENGE_QUIZ_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE = "Challenge Quiz session review is only available after completion.";
    private static final String MODE_CHALLENGE = "challenge";
    public static final String MODE_BOARD_EXAM = "board_exam";
    /** A Board Exam reserves both its Challenge and Board meters under this one state stamp. */
    public static final String SESSION_STATE_BOARD_EXAM_QUOTA_RESERVED = "boardExamQuotaReserved";
    public static final String SESSION_STATE_BOARD_EXAM_QUOTA_REVERSED = "boardExamQuotaReversed";
    private static final String SOURCE_SCOPE_MANUAL = "manual";
    private static final String SOURCE_SCOPE_PLAN = "plan";
    private static final String HISTORY_MODE_BOARD_EXAM = "BOARD_EXAM";
    private static final String MATCHING_FORMAT = "MATCHING";
    private static final List<QuickReviewSessionStatus> ACTIVE_GENERATION_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.FAILED
    );
    private static final List<QuickReviewSessionStatus> USAGE_COUNTED_STATUSES = List.of(
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.COMPLETED,
            QuickReviewSessionStatus.FORFEITED
    );
    private static final int FIRST_PAGE = 0;
    private static final int MAX_RECENT_SESSION_LIMIT = 10;
    private static final int WEAK_CONCEPT_ACCURACY_THRESHOLD = 60;
    private static final int LOW_SCORE_THRESHOLD = 50;
    private static final int MID_SCORE_THRESHOLD = 80;
    private static final int HIGH_SCORE_THRESHOLD = 90;
    private static final int LOW_SCORE_QUESTION_COUNT = 10;
    private static final int MID_SCORE_QUESTION_COUNT = 12;
    private static final int HIGH_SCORE_QUESTION_COUNT = 15;
    /**
     * Question count for a MULTI-NOTE Challenge Quiz, fixed rather than score-adaptive.
     *
     * <p>⚠️ A single-note Challenge Quiz sizes itself from the learner's last Quick Review score
     * (10 / 12 / 15). Letting a multi-note session do the same made the SOURCE CAP move between
     * sessions on the same plan while the prestart renders it as a stable promise. Owner ruled
     * 2026-09-02 to fix it, so what the prestart shows is what the start will enforce.
     *
     * <p>⚠️ THE VALUE IS 18 BECAUSE OF A CEILING, NOT A PREFERENCE. At three questions per source this
     * gives {@code 18 / 3 = 6} sources for Plus and Pro — rejecting the earlier 4, which was pure
     * arithmetic leakage from a 12-question count. The original intent was ~10 sources, and that is
     * UNREACHABLE: it needs 30 questions, ten past {@link #MAX_CHALLENGE_QUIZ_QUESTIONS} (20), which
     * {@code +5 More Questions} also depends on. **Do NOT raise this past 20, and do NOT lift that
     * ceiling to chase ~10** — the owner declined that as a Challenge Quiz identity change.
     */
    private static final int MULTI_NOTE_CHALLENGE_QUESTION_COUNT = 18;
    private static final int INITIAL_CHALLENGE_QUIZ_COUNT = 5;
    public static final int MAX_CHALLENGE_QUIZ_QUESTIONS = 20;
    private static final int GENERATE_MORE_BATCH_SIZE = 5;
    private static final int BOARD_EXAM_QUESTIONS_PER_SOURCE = 12;
    private static final int MAX_BOARD_EXAM_TOTAL_QUESTIONS = 30;
    private static final int MIN_NEW_QUESTIONS_AFTER_DEDUP = 3;
    private static final int MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT = 2;
    private static final int MIN_BOARD_EXAM_QUESTIONS_PER_SOURCE = 3;
    public static final int BOARD_EXAM_QUOTA_UNITS_PER_SESSION = 1;
    private static final int SECONDS_PER_QUESTION_CHALLENGE = 90;
    private static final int SECONDS_PER_QUESTION_BOARD_EXAM = 60;
    private static final String DEFAULT_SELECTED_DIFFICULTY = DIFFICULTY_MEDIUM;

    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final PlanSourcedExamVerifier planSourcedExamVerifier;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final QuizGenerationService quizGenerationService;
    private final SubscriptionService subscriptionService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;
    private final ActivityTrackingService activityTrackingService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final ExamQuestionPoolService examQuestionPoolService;
    private final ChallengeQuizQuestionBankService challengeQuizQuestionBankService;
    private final OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    private final ConceptHealthService conceptHealthService;
    private final StudyPackQuizMasteryService studyPackQuizMasteryService;
    private final StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;
    private final org.springframework.transaction.support.TransactionOperations studyPackGenerationTransactionOperations;
    private final GenerationRecoveryRowWriter generationRecoveryRowWriter;
    private final NoteCollectionRepository noteCollectionRepository;
    private final NoteCollectionItemRepository noteCollectionItemRepository;
    private final LongExamPlanSourceSampler longExamPlanSourceSampler;

    @Transactional
    public ChallengeQuizStartResponse startSession(String studyPackIdRaw, UUID userId, ChallengeQuizStartRequest request) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        String selectedMode = resolveSelectedMode(request);
        String selectedDifficulty = resolveSelectedDifficulty(selectedMode);
        boolean boardReviewSet = MODE_BOARD_EXAM.equals(selectedMode)
                && request != null
                && request.sourceCollectionId() != null
                && !request.sourceCollectionId().isBlank();

        Optional<ChallengeQuizStartResponse> existingSession = resolveExistingChallengeSession(
                userId,
                studyPackId,
                studyPack,
                planType
        );
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        int maxChallengeSourceNotes = resolveMaxChallengeSourceNotes(planType);
        List<UUID> additionalStudyPackIds = MODE_BOARD_EXAM.equals(selectedMode)
                ? resolveAdditionalBoardExamStudyPackIds(request, studyPackId)
                : resolveAdditionalChallengeStudyPackIds(request, studyPackId, maxChallengeSourceNotes - 1);
        boolean multiNoteChallenge = MODE_CHALLENGE.equals(selectedMode) && !additionalStudyPackIds.isEmpty();
        int usedThisMonth = assertChallengeQuizQuotaAvailable(userId, planType);
        if (multiNoteChallenge) {
            // ⚠️ CONDITIONAL, and that matters. The counter is per user while the Study Pack lock is per
            // note, so concurrent starts from different notes could otherwise both see the same remaining
            // allowance. But taking it unconditionally put a PESSIMISTIC_WRITE on the user row for EVERY
            // Challenge and Board Exam start, held across LLM generation by @Transactional — serializing
            // that account's other quiz starts and blocking anything else that writes the same row.
            // Only the multi-note path has a counter at stake, so only it takes the lock.
            userRepository.findByIdForUpdate(userId);
            assertMultiNoteQuotaAvailable(userId, planType);
        }
        int boardExamUsedThisMonth = 0;
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            boardExamUsedThisMonth = assertBoardExamQuotaAvailable(userId, planType, BOARD_EXAM_QUOTA_UNITS_PER_SESSION);
        }
        ChallengeGenerationProfile profile = resolveGenerationProfile(userId, studyPackId, selectedDifficulty, selectedMode);
        int quizCount = boardReviewSet
                ? properties.getPricing().getBoardExamTargetQuestionCount()
                : MODE_BOARD_EXAM.equals(selectedMode)
                ? resolveBoardExamQuestionCount(additionalStudyPackIds.size() + 1)
                : multiNoteChallenge ? MULTI_NOTE_CHALLENGE_QUESTION_COUNT : profile.questionCount();
        UUID boardExamGenerationSessionId = MODE_BOARD_EXAM.equals(selectedMode) ? UUID.randomUUID() : null;
        if (boardReviewSet && !additionalStudyPackIds.isEmpty()) {
            // ⚠️ REJECT, NEVER SILENTLY DISCARD. On the sampled path the server chooses the sources, so a
            // caller-supplied list has no meaning — and silently dropping it is how "silently ignored"
            // becomes "silently accepted". It also made the ONLY route into this capability the one that
            // destroyed the learner's selection.
            throw InvalidBoardExamSourceException.sourcesNotSelectable();
        }
        ResolvedPlanSources resolvedPlanSources = boardReviewSet
                ? resolveBoardExamReviewSetSourceNoteRefs(
                        studyPack,
                        userId,
                        request == null ? null : request.sourceCollectionId(),
                        quizCount,
                        boardExamGenerationSessionId
                )
                // ⚠️ MODE_BOARD_EXAM MUST STAY IN THIS BRANCH. Narrowing it to multiNoteChallenge alone
                // dropped a LEGACY multi-note Board Exam (additional packs, no Review Set) to
                // ResolvedPlanSources.empty() — silently skipping the ownership check, the same-subject
                // check and the 3-source cap, and quietly reducing the exam to its primary note. Unowned
                // or mismatched sources were accepted-then-discarded instead of rejected.
                : MODE_BOARD_EXAM.equals(selectedMode) || multiNoteChallenge
                ? resolveBoardExamSourceNoteRefs(
                        studyPack,
                        userId,
                        additionalStudyPackIds,
                        quizCount,
                        request == null ? null : request.sourceCollectionId()
                )
                : ResolvedPlanSources.empty();
        List<LongExamSourceNoteRef> sourceNoteRefs = multiNoteChallenge
                ? allocateQuestionsAcrossSources(resolvedPlanSources.sourceNoteRefs(), quizCount)
                : resolvedPlanSources.sourceNoteRefs();
        StudyPackGenerationContext generationContext = null;
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            generationContext = buildQuizGenerationContext(userId, studyPack);
            // ⚠️ RESTORED GATE. This was narrowed to `boardReviewSet`, which silently removed the ready
            // question pool from every LEGACY single-note Board Exam — the path this release was not meant
            // to touch — sending each one to a paid LLM generation and to GENERATING instead of an
            // immediate IN_PROGRESS. The pool is keyed on the single primary pack, so the correct gate is
            // "no additional sources", exactly as before.
            // ⚠️ A REVIEW-SET Board Exam is excluded on purpose, mirroring the v0.105.0 Long Exam rule: a
            // sampled multi-source exam must never be served primary-only pooled questions while its
            // session records the sampled sources.
            if (!boardReviewSet && additionalStudyPackIds.isEmpty()) {
                Optional<List<QuizItem>> pooledQuestions = examQuestionPoolService.sampleQuestions(
                        studyPackId,
                        ExamQuestionPoolService.MODE_BOARD_EXAM,
                        quizCount,
                        StudyPackGenerationContextResolver.effectiveCurriculumLevel(generationContext)
                );
                // ⚠️ THE ANSWER-KEY FILTER FOR POOLED QUESTIONS LIVES IN ExamQuestionPoolService, NOT HERE.
                // It was first written at this call site and that was the wrong layer twice over: it would
                // have filtered AFTER sampleQuestions had already marked those questions served, burning a
                // whole exam's worth of clean pool rows on every hit, and it would have left the identical
                // hole open on the Long Exam pooled path. The pool now excludes the note's saved quiz at
                // both generation and sampling time, so anything returned here is already answer-key safe.
                if (pooledQuestions.isPresent()) {
                    QuickReviewSessionEntity session = buildGeneratingSession(
                            userId,
                            studyPackId,
                            studyPack,
                            profile.difficulty(),
                            selectedMode
                    );
                    markSessionReady(session, pooledQuestions.get(), profile.difficulty());
                    session.setSessionState(QuizSessionStateUtils.withPoolSourced(
                            session.getSessionState(),
                            true
                    ));
                    QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
                    userUsageService.incrementChallengeQuizGeneration(userId, saved.getCreatedAt());
                    userUsageService.incrementBoardExamGenerationBy(userId, BOARD_EXAM_QUOTA_UNITS_PER_SESSION, saved.getCreatedAt());
                    try {
                        analyticsService.trackEvent(userId, AnalyticsEventType.BOARD_EXAM_STARTED, studyPackId, Map.of(
                                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                                ANALYTICS_METADATA_QUESTION_COUNT, pooledQuestions.get().size(),
                                ANALYTICS_METADATA_DIFFICULTY, profile.difficulty(),
                                ANALYTICS_METADATA_MODE, selectedMode
                        ));
                    } catch (RuntimeException ignored) {
                        // Analytics must never turn a successfully started Board Exam into a failed session.
                    }
                    return toStartResponse(saved, studyPack, boardExamUsedThisMonth + BOARD_EXAM_QUOTA_UNITS_PER_SESSION, planType);
                }
            }
        }
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(
                boardExamGenerationSessionId,
                userId,
                studyPackId,
                studyPack,
                profile.difficulty(),
                selectedMode,
                sourceNoteRefs
        ));
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            // ⚠️ THE CHARGE RUNS HERE, INSIDE THE REQUEST TRANSACTION, ALONGSIDE THE SESSION ROW.
            // It used to run in an afterCommit callback, and that was BROKEN IN PRODUCTION AND INVISIBLE
            // TO EVERY TEST: this class is @Transactional at CLASS level, so startSession always has an
            // active transaction and the callback always fired — where a PROPAGATION_REQUIRED write joins
            // the already-committed transaction and throws. Every Board Exam start returned 500, while the
            // session row had already committed as GENERATING carrying boardExamQuotaReserved=true, so the
            // sweeper later refunded BOTH meters for a charge that never happened — handing back quota the
            // learner had spent on genuine Challenge Quiz sessions. The unit suite never saw it because
            // MockitoExtension has no transaction manager, so every test took the inline fallback branch.
            // ⚠️ The old design's stated reason — that charging inside would let concurrent starts observe
            // the same remaining quota — DOES NOT HOLD: assertBoardExamQuotaAvailable is an unlocked read
            // and no row lock is taken on this path, so that race is identical either way.
            // Charging here also makes the charge ATOMIC with the session: a rollback undoes both, which
            // removes the reserved-before-charged window entirely and makes the two meters impossible to
            // split. The refund still exists for the case this release exists to handle — generation
            // failing AFTER a successful commit.
            userUsageService.incrementChallengeQuizGeneration(userId, session.getCreatedAt());
            userUsageService.incrementBoardExamGenerationBy(userId, BOARD_EXAM_QUOTA_UNITS_PER_SESSION, session.getCreatedAt());
            // ⚠️ RE-ARMED HERE BECAUSE THIS EARLY RETURN MADE THE OLD CALL SITE DEAD. Moving generation off
            // the transaction routed Board Exam out before the try-block that used to call assertAllowed,
            // so the mode silently lost its AI rate limit — a cost and abuse control on a PRO path. Third
            // instance in this release of the same class: a gate that stopped firing when a branch moved.
            aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
            dispatchBoardExamGenerationAfterCommit(session.getId(), profile.difficulty(), userId, planType);
            return toStartResponse(session, studyPack, boardExamUsedThisMonth + BOARD_EXAM_QUOTA_UNITS_PER_SESSION, planType);
        }
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        Set<String> disallowedQuestionKeys = QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions);
        if (generationContext == null) {
            generationContext = buildQuizGenerationContext(userId, studyPack);
        }
        LearnerLevel effectiveCurriculumLevel = StudyPackGenerationContextResolver.effectiveCurriculumLevel(
                generationContext
        );
        try {
            List<QuizItem> challengeQuiz;
            // ⚠️ TRIPWIRE, NOT DEAD-CODE PADDING. Every board_exam start returns above, into the async
            // resilient path. The synchronous branch that used to live here — and the
            // generateBoardExamQuizForSources helper it called — were deleted because they were not merely
            // unreachable, they encoded a DIFFERENT and now-superseded rule: `quiz.size() != quizCount`
            // fails the session outright, whereas the resilient path accepts a short exam above the two
            // assembly floors and marks it `shortExam`. Leaving both rules in one class is how a future
            // edit silently reinstates the old one. If the early return is ever removed, this fails loudly
            // and forces that choice to be made deliberately.
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                throw new IllegalStateException(
                        "board_exam must not reach the synchronous Challenge generation path");
            }
            if (multiNoteChallenge) {
                aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
                challengeQuiz = QuizDeduplicationUtils.uniqueQuestions(
                        generateChallengeQuizForSources(userId, sourceNoteRefs, profile.difficulty()),
                        disallowedQuestionKeys
                );
            } else {
                List<QuizItem> bankedQuestions = challengeQuizQuestionBankService.claimEligibleQuestions(
                        userId,
                        studyPackId,
                        effectiveCurriculumLevel,
                        session.getId(),
                        disallowedQuestionKeys,
                        quizCount
                );
                List<QuizItem> templateQuestions = officialChallengeQuizTemplateService.copyTemplateQuestions(
                        userId,
                        studyPackId,
                        effectiveCurriculumLevel,
                        session.getId(),
                        disallowedQuestionKeys,
                        quizCount - bankedQuestions.size()
                );
                bankedQuestions = new ArrayList<>(bankedQuestions);
                bankedQuestions.addAll(templateQuestions);
                Set<String> combinedQuestionKeys = new LinkedHashSet<>(disallowedQuestionKeys);
                combinedQuestionKeys.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(bankedQuestions));
                int shortfall = quizCount - bankedQuestions.size();
                List<QuizItem> generatedQuiz = List.of();
                GeneratedChallengeQuizContent generatedContent = null;
                if (shortfall > 0) {
                    aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
                    generatedContent = quizGenerationService.generateChallengeQuiz(
                            studyPack.getTitle(),
                            studyPack.getSummary(),
                            getKeyConcepts(studyPack),
                            appendQuestionTexts(disallowedQuestions, bankedQuestions),
                            shortfall,
                            profile.difficulty(),
                            generationContext
                    );
                    generatedQuiz = generatedContent.quizItems();
                }
                List<QuizItem> uniqueGeneratedQuiz = QuizDeduplicationUtils.uniqueQuestions(generatedQuiz, combinedQuestionKeys);
                challengeQuiz = new ArrayList<>(bankedQuestions);
                challengeQuiz.addAll(uniqueGeneratedQuiz);
                if (!uniqueGeneratedQuiz.isEmpty()) {
                    challengeQuizQuestionBankService.persistGeneratedQuestions(
                            userId,
                            studyPackId,
                            session.getId(),
                            effectiveCurriculumLevel,
                            uniqueGeneratedQuiz
                    );
                }
                accumulateLlmUsage(session, generatedContent);
            }
            if (challengeQuiz.size() != quizCount) {
                throw new ChallengeQuizGenerationFailedException();
            }
            challengeQuiz = stampUnstampedQuestionsWithPrimarySource(challengeQuiz, studyPackId);
            if (!MODE_BOARD_EXAM.equals(selectedMode)) {
                challengeQuiz = shuffleQuestionOrderPreservingMatchingGroups(challengeQuiz);
            }

            markSessionReady(session, challengeQuiz, profile.difficulty());
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
            userUsageService.incrementChallengeQuizGeneration(userId, saved.getCreatedAt());
            if (multiNoteChallenge) {
                userUsageService.incrementMultiNoteGeneration(userId, saved.getCreatedAt());
            }
            // ⚠️ UNREACHABLE MONEY CODE, KEPT ONLY BECAUSE REMOVING IT WIDENS A SIGNOFF DIFF. board_exam
            // cannot reach here — the tripwire above throws first — so this charge never fires. Do not read
            // it as the Board Exam charge; that lives in the early return, inside the request transaction.
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                userUsageService.incrementBoardExamGenerationBy(userId, BOARD_EXAM_QUOTA_UNITS_PER_SESSION, saved.getCreatedAt());
            }
            try {
                AnalyticsEventType startedEventType = MODE_BOARD_EXAM.equals(selectedMode)
                        ? AnalyticsEventType.BOARD_EXAM_STARTED
                        : AnalyticsEventType.CHALLENGE_QUIZ_STARTED;
                Map<String, Object> analyticsMetadata = MODE_BOARD_EXAM.equals(selectedMode)
                        ? Map.of(
                                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                                ANALYTICS_METADATA_QUESTION_COUNT, challengeQuiz.size(),
                                ANALYTICS_METADATA_DIFFICULTY, profile.difficulty(),
                                ANALYTICS_METADATA_MODE, selectedMode
                        )
                        : Map.of(
                                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                                ANALYTICS_METADATA_QUESTION_COUNT, challengeQuiz.size(),
                                ANALYTICS_METADATA_DIFFICULTY, profile.difficulty(),
                                ANALYTICS_METADATA_MODE, selectedMode,
                                ANALYTICS_METADATA_SOURCE_COUNT, sourceNoteRefs.isEmpty() ? 1 : sourceNoteRefs.size(),
                                ANALYTICS_METADATA_SOURCE_SCOPE, resolvedPlanSources.planSourced()
                                        ? SOURCE_SCOPE_PLAN : SOURCE_SCOPE_MANUAL
                        );
                analyticsService.trackEvent(userId, startedEventType, studyPackId, analyticsMetadata);
            } catch (RuntimeException ignored) {
                // Analytics must never turn a successfully generated quiz into a failed session.
            }
            if (!MODE_BOARD_EXAM.equals(selectedMode) && !multiNoteChallenge) {
                trackChallengeLaunchMasterySplit(userId, studyPack, saved);
            }
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                return toStartResponse(saved, studyPack, boardExamUsedThisMonth + BOARD_EXAM_QUOTA_UNITS_PER_SESSION, planType);
            }
            return toStartResponse(saved, studyPack, usedThisMonth + 1, planType);
        } catch (Exception ex) {
            if (!MODE_BOARD_EXAM.equals(selectedMode)) {
                challengeQuizQuestionBankService.releaseClaims(userId, studyPackId, session.getId());
            }
            markSessionFailed(session);
            QuickReviewSessionEntity failed = quickReviewSessionRepository.save(session);
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                return toStartResponse(failed, studyPack, boardExamUsedThisMonth, planType);
            }
            return toStartResponse(failed, studyPack, usedThisMonth, planType);
        }
    }

    private void trackChallengeLaunchMasterySplit(
            UUID userId,
            StudyPackEntity studyPack,
            QuickReviewSessionEntity session
    ) {
        try {
            studyPackQuizMasteryService.tryResolve(userId, studyPack).ifPresent(mastery -> {
                AnalyticsEventType eventType = mastery.mastered()
                        ? AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_AFTER_MASTERY
                        : AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_BEFORE_MASTERY;
                analyticsService.trackEvent(userId, eventType, studyPack.getId(), Map.of(
                        ANALYTICS_METADATA_SESSION_ID, session.getId().toString(),
                        ANALYTICS_METADATA_MODE, MODE_CHALLENGE
                ));
            });
        } catch (RuntimeException exception) {
            log.warn(
                    "challenge_quiz_mastery_analytics_failed userId={} studyPackId={} sessionId={}",
                    userId,
                    studyPack.getId(),
                    session.getId(),
                    exception
            );
        }
    }

    /**
     * Board generation must begin only after the session commits. The charge intentionally runs first and
     * outside the request transaction: moving it back inside would make concurrent Board starts observe
     * the same remaining quota. A process death after the row commit but before this charge is the accepted
     * reserved-before-charged window documented in the release notes.
     */
    private void dispatchBoardExamGenerationAfterCommit(UUID sessionId, String difficulty, UUID userId, PlanType planType) {
        Runnable generationTask = () -> generateBoardExamAsync(sessionId, difficulty, userId, planType);
        // ⚠️ DISPATCH ONLY. Never perform a @Transactional write in an afterCommit callback: it joins the
        // committed transaction and throws. Every other registerSynchronization site in this codebase
        // dispatches only, for the same reason — this one copied the pattern from a site that dispatched
        // and then added a write it could not survive.
        Runnable chargeThenDispatch = () -> studyPackGenerationTaskDispatcher.execute(generationTask);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    chargeThenDispatch.run();
                }
            });
            return;
        }
        chargeThenDispatch.run();
    }

    private void generateBoardExamAsync(UUID sessionId, String difficulty, UUID userId, PlanType planType) {
        try {
            studyPackGenerationTransactionOperations.execute(status -> {
                QuickReviewSessionEntity session = quickReviewSessionRepository.findById(sessionId)
                        .orElseThrow(ChallengeQuizSessionNotFoundException::new);
                if (session.getStatus() != QuickReviewSessionStatus.GENERATING) {
                    return null;
                }
                List<LongExamSourceNoteRef> sourceNoteRefs = extractSourceNoteRefs(session.getSessionState());
                GeneratedBoardExamQuiz generated = generateBoardExamQuizResiliently(userId, sourceNoteRefs, difficulty);
                int minimumQuestions = properties.getPricing().getBoardExamMinimumAssembledQuestions();
                int minimumSources = Math.min(
                        sourceNoteRefs.size(),
                        properties.getPricing().getBoardExamMinimumContributingSources()
                );
                if (generated.quiz().size() < minimumQuestions || generated.contributingSourceCount() < minimumSources) {
                    throw new ChallengeQuizGenerationFailedException();
                }
                // Lock first, then query the scalar status: the locked entity can be stale in this context.
                quickReviewSessionRepository.findByIdForUpdate(sessionId)
                        .orElseThrow(ChallengeQuizSessionNotFoundException::new);
                if (quickReviewSessionRepository.findStatusById(sessionId)
                        .filter(QuickReviewSessionStatus.GENERATING::equals)
                        .isEmpty()) {
                    return null;
                }
                int expectedQuestionCount = sourceNoteRefs.stream()
                        .mapToInt(LongExamSourceNoteRef::questionCount)
                        .sum();
                markSessionReady(session, generated.quiz(), difficulty);
                markBoardExamShort(session, expectedQuestionCount, generated.quiz().size());
                QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
                try {
                    analyticsService.trackEvent(userId, AnalyticsEventType.BOARD_EXAM_STARTED, saved.getStudyPackId(), Map.of(
                            ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                            ANALYTICS_METADATA_QUESTION_COUNT, generated.quiz().size(),
                            ANALYTICS_METADATA_DIFFICULTY, difficulty,
                            ANALYTICS_METADATA_MODE, MODE_BOARD_EXAM
                    ));
                } catch (RuntimeException ignored) {
                    // Analytics is never allowed to turn a ready Board Exam into a failed session.
                }
                return null;
            });
        } catch (Exception exception) {
            log.warn("Board Exam generation failed for sessionId={}: {}", sessionId, exception.getMessage());
            generationRecoveryRowWriter.failBoardExamSession(sessionId);
        }
    }

    private GeneratedBoardExamQuiz generateBoardExamQuizResiliently(
            UUID userId,
            List<LongExamSourceNoteRef> sourceNoteRefs,
            String difficulty
    ) {
        List<QuizItem> merged = new ArrayList<>();
        Set<String> disallowed = new LinkedHashSet<>();
        int contributors = 0;
        for (LongExamSourceNoteRef source : sourceNoteRefs) {
            try {
                UUID sourceId = parseBoardExamSourceStudyPackId(source.studyPackId());
                StudyPackEntity sourcePack = findOwnedStudyPackForGenerationOrThrow(sourceId, userId);
                StudyPackGenerationContext context = buildQuizGenerationContext(userId, sourcePack);
                List<String> sourceSavedQuestions = extractQuestionTexts(sourcePack.getQuiz());
                // ⚠️ THE PACK'S OWN SAVED QUIZ IS A HARD FILTER, NOT MERELY A PROMPT HINT. Passing it to the
                // generator asks the model not to repeat those questions; it does not STOP it. The previous
                // synchronous path also added them to the dedup set, and this restructure dropped that,
                // leaving only the prompt. LongExamService does add them (see its per-source loop).
                // ⚠️ It matters beyond duplication: those questions are visible WITH THEIR ANSWERS on the
                // note's Quiz tab, and Board Exam writes ConceptHealth — so a leaked item both hands over
                // the answer key and corrupts a mastery signal locked since v0.37.0 to genuine assessment.
                disallowed.addAll(QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(sourceSavedQuestions));
                List<QuizItem> generated = quizGenerationService.generateBoardExamQuiz(
                        sourcePack.getTitle(), sourcePack.getSummary(), getKeyConcepts(sourcePack),
                        sourceSavedQuestions, source.questionCount(), difficulty, context
                );
                List<QuizItem> unique = stampQuestionsWithSourceStudyPack(
                        QuizDeduplicationUtils.uniqueQuestions(generated, disallowed), sourceId
                );
                if (!unique.isEmpty()) {
                    contributors++;
                    merged.addAll(unique);
                    disallowed.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(unique));
                }
            } catch (RuntimeException sourceFailure) {
                log.warn("Board Exam source generation failed session-source={}", source.studyPackId(), sourceFailure);
            }
        }
        return new GeneratedBoardExamQuiz(List.copyOf(merged), contributors);
    }

    private void markBoardExamShort(QuickReviewSessionEntity session, int expectedQuestionCount, int assembledQuestionCount) {
        if (assembledQuestionCount >= expectedQuestionCount) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>(session.getSessionState());
        state.put("shortExam", true);
        state.put("expectedQuestionCount", expectedQuestionCount);
        session.setSessionState(state);
    }

    private record GeneratedBoardExamQuiz(List<QuizItem> quiz, int contributingSourceCount) {
    }

    /**
     * Starts a regular Challenge Quiz session from the learner's own previously incorrect banked
     * questions. The session mode remains CHALLENGE and is intentionally quota-exempt because
     * starting it incurs no new LLM generation cost. Note: {@link #generateMoreQuestions} has no
     * quota-exempt awareness, so a "+5 Questions" request on this session falls through to normal
     * LLM generation like any other Challenge session — the zero-cost guarantee covers only the
     * start of the session, not any subsequent generate-more request.
     */
    @Transactional
    public ChallengeQuizStartResponse startRedoMissedSession(String studyPackIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        Optional<ChallengeQuizStartResponse> existingSession = resolveExistingChallengeSession(
                userId,
                studyPackId,
                studyPack,
                planType,
                true
        );
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        ChallengeGenerationProfile profile = resolveGenerationProfile(userId, studyPackId, null, MODE_CHALLENGE);
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);
        LearnerLevel effectiveCurriculumLevel = StudyPackGenerationContextResolver.effectiveCurriculumLevel(
                generationContext
        );
        QuickReviewSessionEntity generatingSession = buildGeneratingSession(
                userId,
                studyPackId,
                studyPack,
                profile.difficulty(),
                MODE_CHALLENGE
        );
        generatingSession.setSessionState(
                QuizSessionStateUtils.withRedoMissedSource(generatingSession.getSessionState(), true)
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(generatingSession);
        List<QuizItem> missedQuestions = challengeQuizQuestionBankService.claimIncorrectQuestions(
                userId,
                studyPackId,
                effectiveCurriculumLevel,
                session.getId(),
                INITIAL_CHALLENGE_QUIZ_COUNT,
                ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS
        );
        missedQuestions = shuffleQuestionOrderPreservingMatchingGroups(
                stampUnstampedQuestionsWithPrimarySource(missedQuestions, studyPackId)
        );
        markSessionReady(session, missedQuestions, profile.difficulty());
        session.setQuotaExempt(true);
        session.setSessionState(QuizSessionStateUtils.withRedoMissedSource(session.getSessionState(), true));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return toStartResponse(saved, studyPack, resolveUsedThisMonthForResponse(userId, saved), planType);
    }

    @Transactional(readOnly = true)
    public ChallengeQuizStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);

        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        OBSERVABLE_STATUSES
                )
                .map(session -> toStartResponse(
                        session,
                        studyPack,
                        resolveUsedThisMonthForResponse(userId, session),
                        planType
                ))
                .orElseGet(() -> buildEmptyStartResponse(studyPack, (int) countChallengeQuizUsedThisMonth(userId), planType));
    }

    @Transactional(readOnly = true)
    public List<ChallengeQuizSessionSummaryResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);

        int normalizedLimit = Math.clamp(limit, 1, MAX_RECENT_SESSION_LIMIT);
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(FIRST_PAGE, normalizedLimit)
                ).stream()
                .map(this::toSessionSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChallengeQuizPerformanceSummaryResponse getPerformanceSummary(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);

        long attempts = quickReviewSessionRepository.countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE
        );
        if (attempts == 0) {
            return new ChallengeQuizPerformanceSummaryResponse(null, 0L, null, null, null, List.of());
        }

        BigDecimal bestScore = quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE
        );
        QuickReviewSessionEntity latest = quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(FIRST_PAGE, 1)
                ).stream()
                .findFirst()
                .orElse(null);

        BigDecimal latestScore = latest == null ? null : latest.getScorePercentage();
        return new ChallengeQuizPerformanceSummaryResponse(
                bestScore,
                attempts,
                latestScore,
                latest == null ? null : latest.getCompletedAt(),
                latestScore == null ? null : resolvePerformanceLevel(latestScore),
                latest == null ? List.of() : extractWeakConcepts(latest)
        );
    }

    public ChallengeQuizStartResponse updateSessionProgress(
            String sessionIdRaw,
            UUID userId,
            ChallengeQuizProgressRequest request
    ) {
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);

        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        int normalizedIndex = Math.clamp(request.currentQuestionIndex(), 0, Math.max(0, totalQuestions - 1));
        session.setCurrentQuestionIndex(normalizedIndex);
        session.setSessionState(mergeSessionState(session.getSessionState(), request.sessionState()));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(saved.getStudyPackId(), userId);
        int usedThisMonth = resolveUsedThisMonthForResponse(userId, saved);
        PlanType planType = subscriptionService.resolvePlan(userId);
        return toStartResponse(saved, studyPack, usedThisMonth, planType);
    }

    public ChallengeQuizSessionResponse completeSession(String sessionIdRaw, UUID userId, ChallengeQuizCompleteRequest request) {
        QuickReviewSessionEntity session = findChallengeSessionForUpdateOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);
        int totalQuestions = session.getTotalQuestions() == null ? request.totalQuestions() : session.getTotalQuestions();
        if (request.correctAnswers() > totalQuestions) {
            throw new InvalidChallengeQuizResultException();
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, List<Integer>> selectedMultiChoices = QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, String> selectedIdentificationAnswers = QuizSessionStateUtils.extractSelectedIdentificationAnswers(session.getSessionState(), quiz);
        Map<Integer, List<String>> selectedEnumerationAnswers = QuizSessionStateUtils.extractSelectedEnumerationAnswers(session.getSessionState(), quiz);
        ChallengeStatistics statistics = computeStatistics(
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers,
                selectedEnumerationAnswers,
                request.correctAnswers(),
                totalQuestions
        );

        BigDecimal scorePercentage = BigDecimal.valueOf(statistics.correctAnswers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(statistics.totalQuestions()), 2, RoundingMode.HALF_UP);

        boolean isFirstCompletedSessionEver = !quickReviewSessionRepository
                .existsByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED);
        boolean isSecondCompletedSessionEver = quickReviewSessionRepository
                .countByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED) == 1;
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(statistics.totalQuestions());
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(statistics.totalQuestions());
        session.setCorrectAnswers(statistics.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setCompletedAt(now);
        session.setSessionState(markSessionStateCompleted(session.getSessionState()));
        session.setSessionMetadata(buildCompletionSessionMetadata(statistics));

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack =
                QuizSessionReviewUtils.computeConceptBreakdownBySourceStudyPack(
                        quiz,
                        selectedChoices,
                        selectedMultiChoices,
                        selectedIdentificationAnswers,
                        selectedEnumerationAnswers,
                        // ⚠️ This service labels an absent concept "Uncategorized" while
                        // QuizSessionReviewUtils labels it "Unknown". ConceptHealth is keyed
                        // (user_id, study_pack_id, concept), so the label IS the row identity —
                        // adopting the other label would fork the row and orphan its streak.
                        UNKNOWN_CONCEPT_LABEL
                );
        recordCorrectConceptsBySourceStudyPack(userId, saved, conceptBreakdownBySourceStudyPack, now);
        List<String> qualifyingConcepts = recordIncorrectConceptsBySourceStudyPack(
                userId,
                saved,
                conceptBreakdownBySourceStudyPack,
                now
        );
        String completedMode = extractMode(saved.getSessionState());
        List<String> twiceMissedConcepts = MODE_CHALLENGE.equals(completedMode) ? qualifyingConcepts : List.of();
        if (MODE_BOARD_EXAM.equals(extractMode(saved.getSessionState()))
                && QuizSessionStateUtils.extractPoolSourced(saved.getSessionState())) {
            examQuestionPoolService.markServed(
                    saved.getStudyPackId(),
                    ExamQuestionPoolService.MODE_BOARD_EXAM,
                    quiz
            );
        }
        if (MODE_CHALLENGE.equals(extractMode(saved.getSessionState()))) {
            challengeQuizQuestionBankService.updateOutcomesAndReleaseClaims(
                    userId,
                    saved.getStudyPackId(),
                    saved.getId(),
                    quiz,
                    selectedChoices,
                    selectedMultiChoices,
                    selectedIdentificationAnswers,
                    selectedEnumerationAnswers
            );
        }
        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_CHALLENGE_QUIZ, saved.getStudyPackId());
        // ⚠️ Both funnels ended here with a START and no END. BOARD_EXAM_COMPLETED did not exist, and
        // CHALLENGE_QUIZ_COMPLETED existed in the enum while being fired from NOWHERE — enum membership
        // is not instrumentation. This is the one site that completes either mode, so both fire here.
        // Analytics must never turn a successfully completed session into a failed one.
        try {
            analyticsService.trackEvent(
                    userId,
                    MODE_BOARD_EXAM.equals(completedMode)
                            ? AnalyticsEventType.BOARD_EXAM_COMPLETED
                            : AnalyticsEventType.CHALLENGE_QUIZ_COMPLETED,
                    saved.getStudyPackId(),
                    Map.of(
                            ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                            ANALYTICS_METADATA_QUESTION_COUNT, statistics.totalQuestions(),
                            ANALYTICS_METADATA_SCORE_PERCENTAGE, scorePercentage,
                            // ⚠️ NOT extractSourceNoteRefs(...).size() on its own. buildInitialSessionState
                            // only persists sourceNoteRefs when there is MORE THAN ONE source, so that
                            // expression reports 0 for every single-source session — meaning
                            // CHALLENGE_QUIZ_COMPLETED was always 0 and Board Exam skipped 1 entirely.
                            // A count of 0 is also ambiguous across three distinct states.
                            ANALYTICS_METADATA_SOURCE_COUNT,
                            Math.max(1, extractSourceNoteRefs(saved.getSessionState()).size())
                    )
            );
        } catch (RuntimeException ignored) {
            // Deliberately swallowed, matching the BOARD_EXAM_STARTED site above.
        }
        return new ChallengeQuizSessionResponse(
                saved.getId().toString(),
                saved.getStudyPackId().toString(),
                saved.getStatus(),
                saved.getTotalQuestions() == null ? 0 : saved.getTotalQuestions(),
                saved.getCorrectAnswers() == null ? 0 : saved.getCorrectAnswers(),
                saved.getScorePercentage() == null ? BigDecimal.ZERO : saved.getScorePercentage(),
                statistics.performanceLevel(),
                statistics.conceptBreakdown(),
                statistics.weakConcepts(),
                saved.getDurationSeconds(),
                saved.getCreatedAt(),
                saved.getCompletedAt(),
                isFirstCompletedSessionEver,
                isSecondCompletedSessionEver,
                twiceMissedConcepts
        );
    }

    private void recordCorrectConceptsBySourceStudyPack(
            UUID userId,
            QuickReviewSessionEntity session,
            Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack,
            OffsetDateTime now
    ) {
        Map<UUID, Set<String>> conceptsByStudyPack = collectConceptsBySourceStudyPack(
                session,
                conceptBreakdownBySourceStudyPack,
                true
        );
        conceptsByStudyPack.forEach((studyPackId, concepts) -> studyPackRepository
                .findByIdAndOwnerUserId(studyPackId, userId)
                .ifPresent(ignored -> conceptHealthService.recordCorrectAnswers(
                        userId, studyPackId, List.copyOf(concepts), now
                )));
    }

    private List<String> recordIncorrectConceptsBySourceStudyPack(
            UUID userId,
            QuickReviewSessionEntity session,
            Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack,
            OffsetDateTime now
    ) {
        Set<String> twiceMissedConcepts = new LinkedHashSet<>();
        Map<UUID, Set<String>> conceptsByStudyPack = collectConceptsBySourceStudyPack(
                session,
                conceptBreakdownBySourceStudyPack,
                false
        );
        conceptsByStudyPack.forEach((studyPackId, concepts) -> studyPackRepository
                .findByIdAndOwnerUserId(studyPackId, userId)
                .ifPresent(ignored -> twiceMissedConcepts.addAll(conceptHealthService.recordIncorrectAnswers(
                        userId, studyPackId, List.copyOf(concepts), now
                ))));
        return List.copyOf(twiceMissedConcepts);
    }

    private Map<UUID, Set<String>> collectConceptsBySourceStudyPack(
            QuickReviewSessionEntity session,
            Map<String, List<ChallengeQuizConceptStatResponse>> conceptBreakdownBySourceStudyPack,
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
            UUID studyPackId = parseSourceStudyPackId(sourceStudyPackId);
            // A pre-v0.104.0 item has no stamp. Challenge Quiz historically attributes it to the
            // primary pack, so preserve that behaviour rather than dropping already-earned evidence.
            if (studyPackId == null) {
                studyPackId = session.getStudyPackId();
            }
            if (studyPackId != null) {
                conceptsByStudyPack.computeIfAbsent(studyPackId, ignored -> new LinkedHashSet<>()).addAll(concepts);
            }
        });
        return conceptsByStudyPack;
    }

    private UUID parseSourceStudyPackId(String sourceStudyPackId) {
        if (sourceStudyPackId == null || sourceStudyPackId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(sourceStudyPackId.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public SimpleMessageResponse forfeitSession(String sessionIdRaw, UUID userId) {
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(CHALLENGE_QUIZ_SESSION_ALREADY_ENDED_MESSAGE);
        }

        markSessionForfeited(session);
        quickReviewSessionRepository.save(session);
        if (MODE_CHALLENGE.equals(extractMode(session.getSessionState()))) {
            challengeQuizQuestionBankService.releaseClaims(userId, session.getStudyPackId(), session.getId());
        }
        return new SimpleMessageResponse(CHALLENGE_QUIZ_SESSION_FORFEITED_MESSAGE);
    }

    public GenerateMoreChallengeQuizResponse generateMoreQuestions(String sessionIdRaw, UUID userId) {
        QuickReviewSessionEntity session = findChallengeSessionForUpdateOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);

        if (isSessionExpired(session)) {
            forfeitExpiredSession(session, userId);
            throw new ChallengeQuizSessionNotInProgressException();
        }

        if (MODE_BOARD_EXAM.equals(extractMode(session.getSessionState()))) {
            throw new AppException("BOARD_EXAM_MODE_NOT_SUPPORTED",
                    "Generate more is not available in Board Exam Mode.", org.springframework.http.HttpStatus.CONFLICT);
        }

        List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        // ⚠️ Refuse BEFORE generating when the remaining headroom cannot produce a viable batch, not
        // after paying for the LLM call. A batch is rejected downstream unless it yields at least
        // MIN_NEW_QUESTIONS_AFTER_DEDUP new questions, so an 18-question multi-note session — headroom
        // 2 — could never succeed: it generated, threw, and the frontend swallowed the failure into
        // "no more questions". The button was live, cost a call, and was deterministically dead.
        if (MAX_CHALLENGE_QUIZ_QUESTIONS - existingQuiz.size() < MIN_NEW_QUESTIONS_AFTER_DEDUP) {
            throw new AppException("MAX_QUESTIONS_REACHED",
                    "This session has reached the maximum of " + MAX_CHALLENGE_QUIZ_QUESTIONS + " questions.",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        int batchSize = Math.min(GENERATE_MORE_BATCH_SIZE, MAX_CHALLENGE_QUIZ_QUESTIONS - existingQuiz.size());
        List<String> disallowedQuestions = extractQuestionTexts(existingQuiz);
        List<String> existingConcepts = List.copyOf(extractConcepts(existingQuiz));
        String difficulty = extractDifficulty(session.getSessionState());

        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(session.getStudyPackId(), userId);
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);
        LearnerLevel effectiveCurriculumLevel = StudyPackGenerationContextResolver.effectiveCurriculumLevel(
                generationContext
        );
        Set<String> disallowedQuestionKeys = QuizDeduplicationUtils.toNormalizedQuestionSet(existingQuiz);
        List<QuizItem> bankedQuestions = challengeQuizQuestionBankService.claimEligibleQuestions(
                userId,
                session.getStudyPackId(),
                effectiveCurriculumLevel,
                session.getId(),
                disallowedQuestionKeys,
                batchSize
        );
        List<QuizItem> templateQuestions = officialChallengeQuizTemplateService.copyTemplateQuestions(
                userId,
                session.getStudyPackId(),
                effectiveCurriculumLevel,
                session.getId(),
                disallowedQuestionKeys,
                batchSize - bankedQuestions.size()
        );
        bankedQuestions = new ArrayList<>(bankedQuestions);
        bankedQuestions.addAll(templateQuestions);
        Set<String> combinedQuestionKeys = new LinkedHashSet<>(disallowedQuestionKeys);
        combinedQuestionKeys.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(bankedQuestions));
        int shortfall = batchSize - bankedQuestions.size();
        List<QuizItem> unique;
        GeneratedChallengeQuizContent generatedContent = null;
        try {
            List<QuizItem> generated = List.of();
            if (shortfall > 0) {
                generatedContent = quizGenerationService.generateMoreChallengeQuiz(
                        studyPack.getTitle(),
                        studyPack.getSummary(),
                        getKeyConcepts(studyPack),
                        appendQuestionTexts(disallowedQuestions, bankedQuestions),
                        existingConcepts,
                        shortfall,
                        difficulty,
                        generationContext
                );
                generated = generatedContent.quizItems();
            }

            List<QuizItem> uniqueGenerated = stampQuestionsWithSourceStudyPack(
                    QuizDeduplicationUtils.uniqueQuestions(generated, combinedQuestionKeys),
                    session.getStudyPackId()
            );
            // +5 generation, bank reuse, and template reuse all use the session primary pack only;
            // even in a mixed session these new items genuinely originate from that primary source.
            unique = new ArrayList<>(stampQuestionsWithSourceStudyPack(bankedQuestions, session.getStudyPackId()));
            unique.addAll(uniqueGenerated);
            unique = shuffleQuestionOrderPreservingMatchingGroups(unique);

            if (unique.size() < MIN_NEW_QUESTIONS_AFTER_DEDUP) {
                throw new NotEnoughNewQuestionsException();
            }
            if (!uniqueGenerated.isEmpty()) {
                challengeQuizQuestionBankService.persistGeneratedQuestions(
                        userId,
                        session.getStudyPackId(),
                        session.getId(),
                        effectiveCurriculumLevel,
                        uniqueGenerated
                );
            }
        } catch (RuntimeException exception) {
            challengeQuizQuestionBankService.releaseClaims(userId, session.getStudyPackId(), session.getId());
            throw exception;
        }

        int previousQuizSize = existingQuiz.size();
        Map<String, Object> nextSessionState = QuizSessionStateUtils.appendQuizItems(session.getSessionState(), unique);
        int previousLimit = extractTimeLimitSeconds(session.getSessionState());
        int newTotal = previousQuizSize + unique.size();
        int addedQuestions = newTotal - previousQuizSize;
        int extension = Math.max(0, addedQuestions) * SECONDS_PER_QUESTION_CHALLENGE;
        int newTimeLimitSeconds = previousLimit + extension;
        nextSessionState.put(SESSION_STATE_TIME_LIMIT_SECONDS, newTimeLimitSeconds);
        session.setSessionState(nextSessionState);
        session.setTotalQuestions(newTotal);
        accumulateLlmUsage(session, generatedContent);
        quickReviewSessionRepository.save(session);

        return new GenerateMoreChallengeQuizResponse(
                unique,
                newTotal,
                newTimeLimitSeconds,
                extractTimerStartedAtEpochSeconds(nextSessionState)
        );
    }

    @Transactional(readOnly = true)
    public QuizSessionReviewResponse getSessionReview(String studyPackIdRaw, String sessionIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        if (!studyPackId.equals(session.getStudyPackId())) {
            throw new ChallengeQuizSessionNotFoundException();
        }
        if (session.getCompletedAt() == null) {
            throw new AppException(
                    SESSION_REVIEW_NOT_AVAILABLE_CODE,
                    CHALLENGE_QUIZ_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE,
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, List<Integer>> selectedMultiChoices = QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, String> selectedIdentificationAnswers = QuizSessionStateUtils.extractSelectedIdentificationAnswers(session.getSessionState(), quiz);
        Map<Integer, List<String>> selectedEnumerationAnswers = QuizSessionStateUtils.extractSelectedEnumerationAnswers(session.getSessionState(), quiz);
        List<ChallengeQuizConceptStatResponse> conceptBreakdown = extractConceptBreakdown(session);
        if (conceptBreakdown.isEmpty()) {
            conceptBreakdown = QuizSessionReviewUtils.computeConceptBreakdown(
                    quiz,
                    selectedChoices,
                    selectedMultiChoices,
                    selectedIdentificationAnswers,
                    selectedEnumerationAnswers
            );
        }
        List<String> weakConcepts = extractWeakConcepts(session);
        if (weakConcepts.isEmpty()) {
            weakConcepts = QuizSessionReviewUtils.computeWeakConcepts(conceptBreakdown);
        }

        return new QuizSessionReviewResponse(
                session.getId().toString(),
                session.getStudyPackId().toString(),
                resolveReviewSessionMode(session),
                session.getStatus(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getDurationSeconds(),
                weakConcepts,
                conceptBreakdown,
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers,
                selectedEnumerationAnswers,
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private String resolveReviewSessionMode(QuickReviewSessionEntity session) {
        return MODE_BOARD_EXAM.equals(extractMode(session.getSessionState()))
                ? HISTORY_MODE_BOARD_EXAM
                : session.getSessionMode().name();
    }

    private int assertChallengeQuizQuotaAvailable(UUID userId, PlanType planType) {
        long usedThisMonth = countChallengeQuizUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new MonthlyChallengeQuizLimitReachedException();
    }

    private long countChallengeQuizUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        long usedFromCountedSessions = quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                USAGE_COUNTED_STATUSES,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        long quotaExemptSessions = quickReviewSessionRepository
                .countByUserIdAndSessionModeAndStatusInAndQuotaExemptTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId,
                        QuickReviewSessionMode.CHALLENGE,
                        USAGE_COUNTED_STATUSES,
                        usagePeriod.periodStart(),
                        usagePeriod.periodEnd()
                );
        long usedFromUsage = userUsageService.getMonthlyUsage(userId, now).challengeQuizGenerations();
        return Math.max(Math.max(0L, usedFromCountedSessions - quotaExemptSessions), usedFromUsage);
    }

    private int assertBoardExamQuotaAvailable(UUID userId, PlanType planType, int quotaUnits) {
        long usedThisMonth = countBoardExamUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().resolveMonthlyBoardExamLimit(planType);
        if (usedThisMonth + quotaUnits <= monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new MonthlyBoardExamLimitReachedException();
    }

    private long countBoardExamUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return userUsageService.getMonthlyUsage(userId, now).boardExamUsedThisMonth();
    }

    private void assertMultiNoteQuotaAvailable(UUID userId, PlanType planType) {
        int usedThisMonth = userUsageService.getMonthlyUsage(userId, OffsetDateTime.now(ZoneOffset.UTC))
                .multiNoteGenerations();
        int monthlyLimit = properties.getPricing().resolveMonthlyMultiNoteLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return;
        }
        throw new MonthlyMultiNoteLimitReachedException(monthlyLimit);
    }

    private int resolveUsedThisMonthForResponse(UUID userId, QuickReviewSessionEntity session) {
        String mode = extractMode(session.getSessionState());
        if (MODE_BOARD_EXAM.equals(mode)) {
            return (int) countBoardExamUsedThisMonth(userId);
        }
        return (int) countChallengeQuizUsedThisMonth(userId);
    }

    private ChallengeGenerationProfile resolveGenerationProfile(
            UUID userId,
            UUID studyPackId,
            String selectedDifficulty,
            String selectedMode
    ) {
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DIFFICULTY_MIXED);
        }
        QuickReviewSessionEntity latestQuickReview = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        BigDecimal previousScore = latestQuickReview == null ? null : latestQuickReview.getScorePercentage();
        if (previousScore == null) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DEFAULT_SELECTED_DIFFICULTY);
        }

        if (previousScore.compareTo(BigDecimal.valueOf(LOW_SCORE_THRESHOLD)) < 0) {
            return new ChallengeGenerationProfile(LOW_SCORE_QUESTION_COUNT, DIFFICULTY_EASY);
        }
        if (previousScore.compareTo(BigDecimal.valueOf(MID_SCORE_THRESHOLD)) < 0) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DEFAULT_SELECTED_DIFFICULTY);
        }
        return new ChallengeGenerationProfile(HIGH_SCORE_QUESTION_COUNT, DIFFICULTY_HARD);
    }

    private Optional<ChallengeQuizStartResponse> resolveExistingChallengeSession(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            PlanType planType
    ) {
        return resolveExistingChallengeSession(userId, studyPackId, studyPack, planType, false);
    }

    private Optional<ChallengeQuizStartResponse> resolveExistingChallengeSession(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            PlanType planType,
            boolean requireRedoMissedSource
    ) {
        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        ACTIVE_GENERATION_STATUSES
                )
                .orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        QuickReviewSessionStatus observedStatus = existing.getStatus();
        QuickReviewSessionEntity lockedExisting = quickReviewSessionRepository
                .findByIdAndUserIdAndSessionModeForUpdate(existing.getId(), userId, QuickReviewSessionMode.CHALLENGE)
                .orElse(null);
        if (lockedExisting == null || lockedExisting.getStatus() != observedStatus) {
            return Optional.empty();
        }
        if (lockedExisting.getStatus() == QuickReviewSessionStatus.GENERATING) {
            if (requireRedoMissedSource
                    && !QuizSessionStateUtils.extractRedoMissedSource(lockedExisting.getSessionState())) {
                forfeitStaleOrdinarySession(lockedExisting, userId);
                return Optional.empty();
            }
            return Optional.of(toStartResponse(
                    lockedExisting,
                    studyPack,
                    resolveUsedThisMonthForResponse(userId, lockedExisting),
                    planType
            ));
        }
        if (!QuizSessionStateUtils.extractQuiz(lockedExisting.getSessionState()).isEmpty()) {
            if (requireRedoMissedSource
                    && !QuizSessionStateUtils.extractRedoMissedSource(lockedExisting.getSessionState())) {
                forfeitStaleOrdinarySession(lockedExisting, userId);
                return Optional.empty();
            }
            if (isSessionExpired(lockedExisting)) {
                forfeitExpiredSession(lockedExisting, userId);
                return Optional.empty();
            }
            return Optional.of(toStartResponse(
                    lockedExisting,
                    studyPack,
                    resolveUsedThisMonthForResponse(userId, lockedExisting),
                    planType
            ));
        }
        markSessionForfeited(lockedExisting);
        quickReviewSessionRepository.save(lockedExisting);
        return Optional.empty();
    }

    private void forfeitStaleOrdinarySession(QuickReviewSessionEntity session, UUID userId) {
        markSessionForfeited(session);
        try {
            quickReviewSessionRepository.save(session);
        } catch (RuntimeException exception) {
            log.warn("Could not persist stale Challenge Quiz session forfeit for session {}.", session.getId(), exception);
        }
        try {
            challengeQuizQuestionBankService.releaseClaims(userId, session.getStudyPackId(), session.getId());
        } catch (RuntimeException exception) {
            log.warn("Could not release Challenge Quiz claims for stale session {}.", session.getId(), exception);
        }
    }

    private boolean isSessionExpired(QuickReviewSessionEntity session) {
        long timerStartedAtEpochSeconds = extractTimerStartedAtEpochSeconds(session.getSessionState());
        if (timerStartedAtEpochSeconds <= 0L) {
            return false;
        }
        long deadlineEpochSeconds = timerStartedAtEpochSeconds + extractTimeLimitSeconds(session.getSessionState());
        return OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() >= deadlineEpochSeconds;
    }

    private void forfeitExpiredSession(QuickReviewSessionEntity session, UUID userId) {
        markSessionForfeited(session);
        try {
            quickReviewSessionRepository.save(session);
        } catch (RuntimeException exception) {
            log.warn("Could not persist expired Challenge Quiz session forfeit for session {}.", session.getId(), exception);
        }
        if (!MODE_CHALLENGE.equals(extractMode(session.getSessionState()))) {
            return;
        }
        try {
            challengeQuizQuestionBankService.releaseClaims(userId, session.getStudyPackId(), session.getId());
        } catch (RuntimeException exception) {
            log.warn("Could not release Challenge Quiz claims for expired session {}.", session.getId(), exception);
        }
    }

    private int resolveQuestionCountForDifficulty(String difficulty) {
        return switch (difficulty) {
            case DIFFICULTY_EASY -> LOW_SCORE_QUESTION_COUNT;
            case DIFFICULTY_HARD -> HIGH_SCORE_QUESTION_COUNT;
            case DIFFICULTY_MIXED -> MID_SCORE_QUESTION_COUNT;
            default -> MID_SCORE_QUESTION_COUNT;
        };
    }

    private List<UUID> resolveAdditionalBoardExamStudyPackIds(
            ChallengeQuizStartRequest request,
            UUID primaryStudyPackId
    ) {
        if (request == null || request.additionalStudyPackIds() == null || request.additionalStudyPackIds().isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (String rawStudyPackId : request.additionalStudyPackIds()) {
            if (rawStudyPackId == null || rawStudyPackId.isBlank()) {
                throw InvalidBoardExamSourceException.sourceUnavailable();
            }
            UUID studyPackId = parseBoardExamSourceStudyPackId(rawStudyPackId);
            if (studyPackId.equals(primaryStudyPackId)) {
                throw InvalidBoardExamSourceException.sourceUnavailable();
            }
            uniqueIds.add(studyPackId);
        }
        if (uniqueIds.size() > MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT) {
            throw InvalidBoardExamSourceException.tooManySources();
        }
        return List.copyOf(uniqueIds);
    }

    private int resolveBoardExamQuestionCount(int sourceCount) {
        return Math.min(BOARD_EXAM_QUESTIONS_PER_SOURCE * sourceCount, MAX_BOARD_EXAM_TOTAL_QUESTIONS);
    }

    private List<UUID> resolveAdditionalChallengeStudyPackIds(
            ChallengeQuizStartRequest request,
            UUID primaryStudyPackId,
            int maxAdditionalSources
    ) {
        if (request == null || request.additionalStudyPackIds() == null || request.additionalStudyPackIds().isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (String rawStudyPackId : request.additionalStudyPackIds()) {
            if (rawStudyPackId == null || rawStudyPackId.isBlank()) {
                throw new MultiNoteChallengeQuizSourceNotAllowedException();
            }
            UUID studyPackId = parseBoardExamSourceStudyPackId(rawStudyPackId);
            if (studyPackId.equals(primaryStudyPackId)) {
                throw new MultiNoteChallengeQuizSourceNotAllowedException();
            }
            uniqueIds.add(studyPackId);
        }
        // This is deliberately a rejection, not the old silent drop. An unentitled or over-cap caller
        // must never receive a single-note session that looks like their mixed request succeeded.
        if (uniqueIds.size() > maxAdditionalSources) {
            throw new MultiNoteChallengeQuizSourceNotAllowedException();
        }
        return List.copyOf(uniqueIds);
    }

    private UUID parseBoardExamSourceStudyPackId(String rawStudyPackId) {
        try {
            return UUID.fromString(rawStudyPackId.trim());
        } catch (RuntimeException ex) {
            throw InvalidBoardExamSourceException.sourceUnavailable();
        }
    }

    private ResolvedPlanSources resolveBoardExamSourceNoteRefs(
            StudyPackEntity primaryStudyPack,
            UUID userId,
            List<UUID> additionalStudyPackIds,
            int questionCount,
            String sourceCollectionIdRaw
    ) {
        String primarySubject = noteRepository.findById(primaryStudyPack.getNoteId())
                .map(note -> normalizeSubjectForMatch(note.getSubject()))
                .orElseGet(() -> normalizeSubjectForMatch(primaryStudyPack.getSubject()));

        // Same defect, same fix as the Long Exam path: the plan CTA pre-selects a plan's own notes and
        // this method then rejected them for not sharing a subject. The gate is per source and anchored —
        // the primary must be a member too, and a source the plan does not contain still answers to the
        // subject rule. ⚠️ The Board Exam CAP is deliberately unchanged: its question count scales with
        // source count, so raising it is quota-adjacent arithmetic and a separate decision.
        Set<UUID> planMemberNoteIds = planSourcedExamVerifier.resolvePlanMemberNoteIds(
                sourceCollectionIdRaw,
                userId,
                InvalidBoardExamSourceException::subjectMismatch
        );
        boolean planSourced = !planMemberNoteIds.isEmpty()
                && planMemberNoteIds.contains(primaryStudyPack.getNoteId());

        if (!additionalStudyPackIds.isEmpty() && !planSourced && primarySubject.isBlank()) {
            throw InvalidBoardExamSourceException.primarySubjectRequired();
        }

        List<StudyPackEntity> additionalStudyPacks = new ArrayList<>(additionalStudyPackIds.size());
        for (UUID additionalStudyPackId : additionalStudyPackIds) {
            additionalStudyPacks.add(findOwnedBoardExamSourceOrThrow(additionalStudyPackId, userId));
        }

        if (!additionalStudyPacks.isEmpty()) {
            List<UUID> additionalNoteIds = additionalStudyPacks.stream().map(StudyPackEntity::getNoteId).toList();
            Map<UUID, String> subjectByNoteId = noteRepository.findAllById(additionalNoteIds).stream()
                    .collect(Collectors.toMap(NoteEntity::getId, note -> normalizeSubjectForMatch(note.getSubject())));
            for (StudyPackEntity additionalStudyPack : additionalStudyPacks) {
                String additionalSubject = subjectByNoteId.getOrDefault(
                        additionalStudyPack.getNoteId(),
                        normalizeSubjectForMatch(additionalStudyPack.getSubject())
                );
                boolean memberOfPlan = planSourced && planMemberNoteIds.contains(additionalStudyPack.getNoteId());
                if (!memberOfPlan && !primarySubject.equals(additionalSubject)) {
                    throw InvalidBoardExamSourceException.subjectMismatch();
                }
            }
        }

        List<StudyPackEntity> sources = new ArrayList<>(1 + additionalStudyPacks.size());
        sources.add(primaryStudyPack);
        sources.addAll(additionalStudyPacks);

        int sourceCount = sources.size();
        int baseQuestionCount = questionCount / sourceCount;
        if (baseQuestionCount < MIN_BOARD_EXAM_QUESTIONS_PER_SOURCE) {
            throw InvalidBoardExamSourceException.tooManySources();
        }
        int remainder = questionCount % sourceCount;
        List<LongExamSourceNoteRef> sourceNoteRefs = new ArrayList<>(sourceCount);
        for (int index = 0; index < sources.size(); index++) {
            StudyPackEntity source = sources.get(index);
            int sourceQuestionCount = baseQuestionCount + (index == 0 ? remainder : 0);
            sourceNoteRefs.add(buildSourceNoteRef(source, sourceQuestionCount));
        }
        return new ResolvedPlanSources(List.copyOf(sourceNoteRefs), planSourced);
    }

    /**
     * Board Exam alone walks the whole owned Review Set. This is deliberately separate from
     * {@link #resolveBoardExamSourceNoteRefs}: that older method is also the multi-note Challenge path,
     * whose Free/Plus cap semantics must remain byte-for-byte unchanged.
     */
    private ResolvedPlanSources resolveBoardExamReviewSetSourceNoteRefs(
            StudyPackEntity primaryStudyPack,
            UUID userId,
            String reviewSetIdRaw,
            int questionCount,
            UUID sessionId
    ) {
        if (reviewSetIdRaw == null || reviewSetIdRaw.isBlank()) {
            throw InvalidBoardExamSourceException.sourceUnavailable();
        }
        UUID reviewSetId = com.studysnap.backend.util.UuidParsingUtils.parseUuidOrThrow(
                reviewSetIdRaw,
                InvalidBoardExamSourceException::sourceUnavailable
        );
        NoteCollectionEntity claimed = noteCollectionRepository.findByIdAndOwnerUserId(reviewSetId, userId)
                .orElseThrow(InvalidBoardExamSourceException::sourceUnavailable);
        // ⚠️ WALK UP TO THE REVIEW SET. A learner reaches Board Exam from whichever collection page they
        // were on, which is normally a SUBJECT PLAN — a child. Using the claimed collection directly made
        // the childless branch fire and shipped "assess across the plan you came from", which is Long
        // Exam's job, not Board Exam's. Board Exam's identity is the WHOLE Review Set, so a child claim
        // resolves to its parent. Ownership is re-verified on the parent; a child of a set you do not own
        // is not a route into someone else's curriculum.
        NoteCollectionEntity reviewSet = claimed.getParentCollectionId() == null
                ? claimed
                : noteCollectionRepository.findByIdAndOwnerUserId(claimed.getParentCollectionId(), userId)
                        .orElseThrow(InvalidBoardExamSourceException::sourceUnavailable);
        List<NoteCollectionEntity> subjectPlans = noteCollectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(reviewSet.getId(), userId);
        // This exactly mirrors the Goal endpoint: child plans win; a childless top-level plan is one stratum.
        List<NoteCollectionEntity> strata = subjectPlans.isEmpty() ? List.of(reviewSet) : subjectPlans;
        List<BoardExamCandidate> candidates = new ArrayList<>();
        for (int stratumIndex = 0; stratumIndex < strata.size(); stratumIndex++) {
            NoteCollectionEntity stratum = strata.get(stratumIndex);
            for (NoteCollectionItemEntity item : noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(stratum.getId())) {
                candidates.add(new BoardExamCandidate(item.getNoteId(), stratum.getId().toString(), item.getPosition(), stratumIndex));
            }
        }
        List<UUID> candidateNoteIds = candidates.stream().map(BoardExamCandidate::noteId).distinct().toList();
        Map<UUID, StudyPackEntity> readyPackByNoteId = studyPackRepository
                .findByOwnerUserIdAndNoteIdInAndStatus(userId, candidateNoteIds, StudyPackStatus.DONE).stream()
                .collect(Collectors.toMap(StudyPackEntity::getNoteId, pack -> pack));
        // ⚠️ DEDUPE BY STUDY PACK, NOT JUST BY NOTE ID. `candidateNoteIds` is distinct, but a note that
        // belongs to TWO Subject Plans of the same Review Set produces two candidates, hence two pool
        // entries carrying the SAME StudyPackEntity — and round-robin then draws it from both buckets.
        // The exam would report more sources than it has, and `contributingSourceCount` would double-count
        // one note, letting a SINGLE note satisfy the two-contributing-sources assembly floor.
        // First occurrence wins, so a shared note is attributed to its earliest stratum and position.
        Map<UUID, LongExamPlanSourceSampler.EligiblePlanSource> eligibleByStudyPackId = new LinkedHashMap<>();
        for (BoardExamCandidate candidate : candidates) {
            StudyPackEntity pack = readyPackByNoteId.get(candidate.noteId());
            if (pack == null) {
                continue;
            }
            eligibleByStudyPackId.putIfAbsent(pack.getId(), new LongExamPlanSourceSampler.EligiblePlanSource(
                    pack,
                    candidate.coverageBucketLabel(),
                    candidate.stratumIndex() * 1_000_000 + candidate.position()
            ));
        }
        List<LongExamPlanSourceSampler.EligiblePlanSource> eligiblePool = List.copyOf(eligibleByStudyPackId.values());
        int minimumSources = Math.min(
                eligiblePool.size(),
                properties.getPricing().getBoardExamMinimumContributingSources()
        );
        if (eligiblePool.size() < properties.getPricing().getBoardExamMinimumContributingSources()) {
            throw new BoardExamInsufficientEligibleSourcesException(
                    eligiblePool.size(),
                    properties.getPricing().getBoardExamMinimumContributingSources()
            );
        }
        if (eligiblePool.stream().noneMatch(source -> source.studyPack().getId().equals(primaryStudyPack.getId()))) {
            throw InvalidBoardExamSourceException.sourceUnavailable();
        }
        int sampleLimit = ExamSourceLimitResolver.resolveMaxSourceNotes(questionCount);
        List<LongExamPlanSourceSampler.EligiblePlanSource> sampled = longExamPlanSourceSampler.sample(
                eligiblePool,
                primaryStudyPack.getId(),
                sampleLimit,
                sessionId
        );
        if (sampled.size() < minimumSources) {
            throw new BoardExamInsufficientEligibleSourcesException(sampled.size(), minimumSources);
        }
        int baseQuestionCount = questionCount / sampled.size();
        if (baseQuestionCount < MIN_BOARD_EXAM_QUESTIONS_PER_SOURCE) {
            throw InvalidBoardExamSourceException.tooManySources();
        }
        int remainder = questionCount % sampled.size();
        List<LongExamSourceNoteRef> refs = new ArrayList<>(sampled.size());
        for (int index = 0; index < sampled.size(); index++) {
            refs.add(buildSourceNoteRef(sampled.get(index).studyPack(), baseQuestionCount + (index == 0 ? remainder : 0)));
        }
        return new ResolvedPlanSources(List.copyOf(refs), true);
    }

    private record BoardExamCandidate(UUID noteId, String coverageBucketLabel, int position, int stratumIndex) {
    }

    private record ResolvedPlanSources(List<LongExamSourceNoteRef> sourceNoteRefs, boolean planSourced) {
        private static ResolvedPlanSources empty() {
            return new ResolvedPlanSources(List.of(), false);
        }
    }

    /**
     * Most notes a multi-note Challenge Quiz may draw from, counting the primary.
     *
     * <p>⚠️ DERIVED FROM {@link #MULTI_NOTE_CHALLENGE_QUESTION_COUNT}, never the Long Exam one. The
     * first implementation used {@code resolveLongExamQuestionCount} (20/25/30 → 6/8/10), but a
     * Challenge Quiz is far shorter — so a Plus learner could select 8 sources for a 12-question quiz
     * and seven of those notes would contribute a SINGLE question each. That is not mixed retrieval,
     * and it contradicts {@link ExamSourceLimitResolver}'s guarantee that every source gets enough
     * questions to be worth including.
     *
     * <p>⚠️ It is also STABLE, which the score-adaptive count would not have been: the cap is always
     * {@code 18 / 3 = 6} for Plus and Pro, and {@code min(3, 6) = 3} for Free.
     *
     * <p>⚠️ Raising the question count is NOT the fix: {@link #MAX_CHALLENGE_QUIZ_QUESTIONS} is 20, so a
     * 25-question base would breach that ceiling outright. At 18 the headroom is 2, which is below the
     * minimum viable batch — so {@code +5 More Questions} is correctly refused up front on a multi-note
     * session rather than generating a batch that cannot pass.
     *
     * <p>The owner's ruling — Plus uses the same level-derived formula as Pro rather than an artificial
     * constant — is preserved exactly. Only the input is corrected.
     */
    private int resolveMaxChallengeSourceNotes(PlanType planType) {
        int derived = ExamSourceLimitResolver.resolveMaxSourceNotes(MULTI_NOTE_CHALLENGE_QUESTION_COUNT);
        if (planType == PlanType.FREE) {
            return Math.min(properties.getPricing().getFreeMultiNoteSourceCap(), derived);
        }
        return derived;
    }


    // Package-private so the floor guard below can be pinned directly. The cap now makes it
    // unreachable through startSession, which is exactly why it would otherwise go untested — and an
    // untested guard is how the hole it closes gets silently re-opened by a later cap change.
    List<LongExamSourceNoteRef> allocateQuestionsAcrossSources(
            List<LongExamSourceNoteRef> sourceNoteRefs,
            int totalQuestionCount
    ) {
        int sourceCount = sourceNoteRefs.size();
        int baseQuestionCount = totalQuestionCount / sourceCount;
        // ⚠️ The cap should already prevent this. The guard exists because the first implementation
        // sized the cap from a DIFFERENT question count than the one allocated here, and nothing
        // failed — seven of eight notes silently received one question each.
        if (baseQuestionCount < ExamSourceLimitResolver.minimumQuestionsPerSource()) {
            throw new MultiNoteChallengeQuizSourceNotAllowedException();
        }
        int remainder = totalQuestionCount % sourceCount;
        List<LongExamSourceNoteRef> allocated = new ArrayList<>(sourceCount);
        for (int index = 0; index < sourceCount; index++) {
            LongExamSourceNoteRef source = sourceNoteRefs.get(index);
            allocated.add(new LongExamSourceNoteRef(
                    source.studyPackId(),
                    source.noteId(),
                    source.noteTitle(),
                    baseQuestionCount + (index == 0 ? remainder : 0)
            ));
        }
        return List.copyOf(allocated);
    }

    private LongExamSourceNoteRef buildSourceNoteRef(StudyPackEntity studyPack, int questionCount) {
        return new LongExamSourceNoteRef(
                studyPack.getId().toString(),
                studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                questionCount
        );
    }

    private List<QuizItem> generateChallengeQuizForSources(
            UUID userId,
            List<LongExamSourceNoteRef> sourceNoteRefs,
            String difficulty
    ) {
        List<QuizItem> mergedQuiz = new ArrayList<>();
        Set<String> disallowedQuestions = new LinkedHashSet<>();
        for (LongExamSourceNoteRef sourceNoteRef : sourceNoteRefs) {
            UUID sourceStudyPackId = parseBoardExamSourceStudyPackId(sourceNoteRef.studyPackId());
            StudyPackEntity sourceStudyPack = findOwnedStudyPackForGenerationOrThrow(sourceStudyPackId, userId);
            StudyPackGenerationContext sourceContext = buildQuizGenerationContext(userId, sourceStudyPack);
            GeneratedChallengeQuizContent generatedContent = quizGenerationService.generateChallengeQuiz(
                    sourceStudyPack.getTitle(),
                    sourceStudyPack.getSummary(),
                    getKeyConcepts(sourceStudyPack),
                    List.copyOf(disallowedQuestions),
                    sourceNoteRef.questionCount(),
                    difficulty,
                    sourceContext
            );
            List<QuizItem> uniqueGeneratedQuiz = QuizDeduplicationUtils.uniqueQuestions(
                    generatedContent.quizItems(),
                    disallowedQuestions
            );
            List<QuizItem> stampedGeneratedQuiz = stampQuestionsWithSourceStudyPack(
                    uniqueGeneratedQuiz,
                    sourceStudyPackId
            );
            mergedQuiz.addAll(stampedGeneratedQuiz);
            disallowedQuestions.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(stampedGeneratedQuiz));
        }
        return mergedQuiz;
    }

    private List<QuizItem> stampQuestionsWithSourceStudyPack(List<QuizItem> quiz, UUID sourceStudyPackId) {
        if (quiz == null || quiz.isEmpty() || sourceStudyPackId == null) {
            return quiz == null ? List.of() : List.copyOf(quiz);
        }
        String sourceStudyPackIdRaw = sourceStudyPackId.toString();
        return quiz.stream()
                .filter(Objects::nonNull)
                .map(item -> item.withSourceStudyPackId(sourceStudyPackIdRaw))
                .toList();
    }

    private List<QuizItem> stampUnstampedQuestionsWithPrimarySource(List<QuizItem> quiz, UUID primaryStudyPackId) {
        if (quiz == null || quiz.isEmpty() || primaryStudyPackId == null) {
            return quiz == null ? List.of() : List.copyOf(quiz);
        }
        String primaryStudyPackIdRaw = primaryStudyPackId.toString();
        return quiz.stream()
                .filter(Objects::nonNull)
                .map(item -> item.sourceStudyPackId() == null || item.sourceStudyPackId().isBlank()
                        ? item.withSourceStudyPackId(primaryStudyPackIdRaw)
                        : item)
                .toList();
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

    private List<String> appendQuestionTexts(List<String> existingQuestionTexts, List<QuizItem> additionalQuestions) {
        List<String> allQuestionTexts = new ArrayList<>(existingQuestionTexts == null ? List.of() : existingQuestionTexts);
        allQuestionTexts.addAll(extractQuestionTexts(additionalQuestions));
        return List.copyOf(allQuestionTexts);
    }

    private Set<String> extractConcepts(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return Set.of();
        }
        return quiz.stream()
                .map(QuizItem::concept)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ChallengeQuizStartResponse toStartResponse(
            QuickReviewSessionEntity session,
            StudyPackEntity studyPack,
            int usedThisMonth,
            PlanType planType
    ) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (quiz.isEmpty()) {
            if (session.getStatus() != QuickReviewSessionStatus.GENERATING
                    && session.getStatus() != QuickReviewSessionStatus.FAILED) {
                throw new ChallengeQuizNotAvailableException();
            }
        }
        int timeLimitSeconds = extractTimeLimitSeconds(session.getSessionState());
        String mode = extractMode(session.getSessionState());
        int limit = MODE_BOARD_EXAM.equals(mode)
                ? properties.getPricing().resolveMonthlyBoardExamLimit(planType)
                : resolveMonthlyChallengeQuizLimit(planType);
        int boardExamUsedThisMonth = MODE_BOARD_EXAM.equals(mode)
                ? usedThisMonth
                : (int) countBoardExamUsedThisMonth(session.getUserId());
        return new ChallengeQuizStartResponse(
                session.getId().toString(),
                session.getStatus(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                quiz.size(),
                timeLimitSeconds,
                usedThisMonth,
                limit,
                boardExamUsedThisMonth,
                properties.getPricing().resolveMonthlyBoardExamLimit(planType),
                mode,
                extractDifficulty(session.getSessionState()),
                quiz,
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                sanitizeSessionStateForClient(session.getSessionState()),
                extractResponseSourceNoteRefs(session.getSessionState()),
                resolveMaxChallengeSourceNotes(planType)
        );
    }

    private String resolveSelectedDifficulty(String selectedMode) {
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            return DIFFICULTY_MIXED;
        }
        return null;
    }

    private String resolveSelectedMode(ChallengeQuizStartRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            return MODE_CHALLENGE;
        }
        String normalized = request.mode().trim().toLowerCase();
        return switch (normalized) {
            case MODE_CHALLENGE, MODE_BOARD_EXAM -> normalized;
            default -> throw new InvalidChallengeQuizModeException();
        };
    }

    private ChallengeQuizSessionSummaryResponse toSessionSummaryResponse(QuickReviewSessionEntity session) {
        BigDecimal score = session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage();
        return new ChallengeQuizSessionSummaryResponse(
                session.getId().toString(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                score,
                resolvePerformanceLevel(score),
                extractConceptBreakdown(session),
                extractWeakConcepts(session),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private Map<String, Object> buildInitialSessionState(
            String difficulty,
            String mode,
            List<LongExamSourceNoteRef> sourceNoteRefs
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        state.put(SESSION_STATE_COMPLETED, false);
        state.put(SESSION_STATE_DIFFICULTY, difficulty);
        state.put(SESSION_STATE_MODE, mode);
        if (MODE_BOARD_EXAM.equals(mode)) {
            // The reservation commits before the asynchronous charge; the matched dispatch comment
            // documents that accepted crash window. Recovery refunds only this marked reservation.
            state.put(SESSION_STATE_BOARD_EXAM_QUOTA_RESERVED, true);
        }
        if (sourceNoteRefs != null && (sourceNoteRefs.size() > 1 || MODE_BOARD_EXAM.equals(mode))) {
            state.put(SESSION_STATE_SOURCE_NOTE_REFS, sourceNoteRefsToState(sourceNoteRefs));
        }
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

    private String extractMode(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return MODE_CHALLENGE;
        }
        Object raw = sessionState.get(SESSION_STATE_MODE);
        if (raw instanceof String mode && !mode.isBlank()) {
            return mode;
        }
        return MODE_CHALLENGE;
    }

    private String extractDifficulty(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DEFAULT_SELECTED_DIFFICULTY;
        }
        Object raw = sessionState.get(SESSION_STATE_DIFFICULTY);
        if (raw instanceof String difficulty && !difficulty.isBlank()) {
            return difficulty;
        }
        return DEFAULT_SELECTED_DIFFICULTY;
    }

    private int extractTimeLimitSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE;
        }
        Object raw = sessionState.get(SESSION_STATE_TIME_LIMIT_SECONDS);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE;
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

    private Map<String, Object> sanitizeSessionStateForClient(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(sessionState);
        sanitized.remove(SESSION_STATE_QUIZ);
        // ⚠️ INTERNAL QUOTA BOOKKEEPING NEVER LEAVES THE SERVER. This flag is the sweeper's sole record of
        // whether a crashed Board Exam still owes a refund. It is not writable today — mergeSessionState is
        // an allowlist of the four selected-answer maps — but the client currently ECHOES session state back
        // on every progress save, so a later widening of that allowlist would turn a visible key into a
        // quota bypass. Stripping it here means the client never learns the key exists.
        sanitized.remove(SESSION_STATE_BOARD_EXAM_QUOTA_RESERVED);
        return sanitized;
    }

    private Map<String, Object> mergeSessionState(
            Map<String, Object> existingState,
            Map<String, Object> incomingState
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            merged.putAll(existingState);
        }
        if (incomingState != null && !incomingState.isEmpty()) {
            Object selectedChoices = incomingState.get(SESSION_STATE_SELECTED_CHOICES);
            if (selectedChoices instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_CHOICES, selectedChoices);
            }
            Object selectedMultiChoices = incomingState.get(SESSION_STATE_SELECTED_MULTI_CHOICES);
            if (selectedMultiChoices instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_MULTI_CHOICES, selectedMultiChoices);
            }
            Object selectedIdentificationAnswers = incomingState.get(SESSION_STATE_SELECTED_IDENTIFICATION_ANSWERS);
            if (selectedIdentificationAnswers instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_IDENTIFICATION_ANSWERS, selectedIdentificationAnswers);
            }
            Object selectedEnumerationAnswers = incomingState.get(SESSION_STATE_SELECTED_ENUMERATION_ANSWERS);
            if (selectedEnumerationAnswers instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_ENUMERATION_ANSWERS, selectedEnumerationAnswers);
            }
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_CHOICES)) {
            merged.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_MULTI_CHOICES)) {
            merged.put(SESSION_STATE_SELECTED_MULTI_CHOICES, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_IDENTIFICATION_ANSWERS)) {
            merged.put(SESSION_STATE_SELECTED_IDENTIFICATION_ANSWERS, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_ENUMERATION_ANSWERS)) {
            merged.put(SESSION_STATE_SELECTED_ENUMERATION_ANSWERS, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_TIME_LIMIT_SECONDS)) {
            merged.put(SESSION_STATE_TIME_LIMIT_SECONDS, INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE);
        }
        if (!merged.containsKey(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS)) {
            merged.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        }
        if (!merged.containsKey(SESSION_STATE_COMPLETED)) {
            merged.put(SESSION_STATE_COMPLETED, false);
        }
        return merged;
    }

    private Map<String, Object> markSessionStateCompleted(Map<String, Object> existingState) {
        Map<String, Object> nextState = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            nextState.putAll(existingState);
        }
        nextState.put(SESSION_STATE_COMPLETED, true);
        return nextState;
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(weakConceptsRaw instanceof List<?> weakConceptsList) || weakConceptsList.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>(weakConceptsList.size());
        for (Object value : weakConceptsList) {
            if (!(value instanceof String concept)) {
                continue;
            }
            String trimmed = concept.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(normalized));
    }

    private List<ChallengeQuizConceptStatResponse> extractConceptBreakdown(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object conceptBreakdownRaw = session.getSessionMetadata().get(SESSION_METADATA_CONCEPT_BREAKDOWN);
        if (!(conceptBreakdownRaw instanceof List<?> conceptBreakdownList) || conceptBreakdownList.isEmpty()) {
            return List.of();
        }

        List<ChallengeQuizConceptStatResponse> conceptStats = new ArrayList<>(conceptBreakdownList.size());
        for (Object entry : conceptBreakdownList) {
            if (!(entry instanceof Map<?, ?> valueMap)) {
                continue;
            }

            String concept = readString(valueMap.get(CONCEPT_KEY));
            Integer correctAnswers = readInteger(valueMap.get(CORRECT_ANSWERS_KEY));
            Integer totalQuestions = readInteger(valueMap.get(TOTAL_QUESTIONS_KEY));
            Integer accuracyPercentage = readInteger(valueMap.get(ACCURACY_PERCENTAGE_KEY));
            if (concept == null || correctAnswers == null || totalQuestions == null || accuracyPercentage == null) {
                continue;
            }

            conceptStats.add(new ChallengeQuizConceptStatResponse(
                    concept,
                    correctAnswers,
                    totalQuestions,
                    accuracyPercentage
            ));
        }
        return conceptStats.isEmpty() ? List.of() : List.copyOf(conceptStats);
    }

    private String readString(Object value) {
        if (!(value instanceof String raw)) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<LongExamSourceNoteRef> extractResponseSourceNoteRefs(Map<String, Object> sessionState) {
        List<LongExamSourceNoteRef> sourceNoteRefs = extractSourceNoteRefs(sessionState);
        return sourceNoteRefs.size() > 1 ? sourceNoteRefs : null;
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
        String studyPackId = readString(sourceMap.get(SOURCE_STUDY_PACK_ID_KEY));
        String noteId = readString(sourceMap.get(SOURCE_NOTE_ID_KEY));
        String noteTitle = readString(sourceMap.get(SOURCE_NOTE_TITLE_KEY));
        Integer questionCount = readInteger(sourceMap.get(SOURCE_QUESTION_COUNT_KEY));
        if (studyPackId == null || noteId == null || questionCount == null || questionCount <= 0) {
            return null;
        }
        return new LongExamSourceNoteRef(studyPackId, noteId, noteTitle, questionCount);
    }

    private ChallengeStatistics computeStatistics(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers,
            int fallbackCorrectAnswers,
            int fallbackTotalQuestions
    ) {
        if (quiz == null || quiz.isEmpty()) {
            int totalQuestions = Math.max(1, fallbackTotalQuestions);
            int correctAnswers = Math.clamp(fallbackCorrectAnswers, 0, totalQuestions);
            BigDecimal percentage = BigDecimal.valueOf(correctAnswers)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
            return new ChallengeStatistics(
                    correctAnswers,
                    totalQuestions,
                    resolvePerformanceLevel(percentage),
                    List.of(),
                    List.of()
            );
        }

        Map<String, ConceptCounter> conceptCounters = new LinkedHashMap<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            if (item == null) {
                continue;
            }
            String concept = normalizeConcept(item.concept());
            ConceptCounter counter = conceptCounters.computeIfAbsent(concept, unused -> new ConceptCounter());
            counter.totalQuestions += 1;

            if (QuizSessionReviewUtils.isAnswerCorrect(
                    item,
                    index,
                    selectedChoices,
                    selectedMultiChoices,
                    selectedIdentificationAnswers,
                    selectedEnumerationAnswers
            )) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int totalQuestions = selectedChoices.isEmpty()
                && (selectedMultiChoices == null || selectedMultiChoices.isEmpty())
                && (selectedIdentificationAnswers == null || selectedIdentificationAnswers.isEmpty())
                && (selectedEnumerationAnswers == null || selectedEnumerationAnswers.isEmpty())
                ? quiz.size()
                : countAnsweredQuestions(selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers);
        BigDecimal percentage = BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.max(1, totalQuestions)), 2, RoundingMode.HALF_UP);

        List<ChallengeQuizConceptStatResponse> conceptBreakdown = conceptCounters.entrySet()
                .stream()
                .map(entry -> {
                    int accuracy = calculateAccuracy(entry.getValue().correctAnswers, entry.getValue().totalQuestions);
                    return new ChallengeQuizConceptStatResponse(
                            entry.getKey(),
                            entry.getValue().correctAnswers,
                            entry.getValue().totalQuestions,
                            accuracy
                    );
                })
                .toList();

        List<String> weakConcepts = conceptBreakdown.stream()
                .filter(stat -> stat.accuracyPercentage() < WEAK_CONCEPT_ACCURACY_THRESHOLD)
                .map(ChallengeQuizConceptStatResponse::concept)
                .toList();

        return new ChallengeStatistics(
                correctAnswers,
                totalQuestions,
                resolvePerformanceLevel(percentage),
                conceptBreakdown,
                weakConcepts
        );
    }

    private int countAnsweredQuestions(
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
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
        if (selectedEnumerationAnswers != null) {
            selectedEnumerationAnswers.entrySet().stream()
                    .filter(entry -> entry.getValue() != null
                            && entry.getValue().stream().anyMatch(answer -> answer != null && !answer.isBlank()))
                    .map(Map.Entry::getKey)
                    .forEach(answeredQuestionIndexes::add);
        }
        return answeredQuestionIndexes.size();
    }

    private String normalizeConcept(String value) {
        if (value == null) {
            return UNKNOWN_CONCEPT_LABEL;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? UNKNOWN_CONCEPT_LABEL : normalized;
    }

    private int calculateAccuracy(int correctAnswers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((correctAnswers * 100.0) / totalQuestions);
    }

    private String resolvePerformanceLevel(BigDecimal scorePercentage) {
        if (scorePercentage.compareTo(BigDecimal.valueOf(HIGH_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_EXCELLENT;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return PERFORMANCE_LEVEL_GOOD;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(LOW_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_FAIR;
        }
        return PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT;
    }

    private Map<String, Object> buildCompletionSessionMetadata(ChallengeStatistics statistics) {
        List<Map<String, Object>> conceptBreakdown = statistics.conceptBreakdown().stream()
                .map(stat -> Map.<String, Object>of(
                        CONCEPT_KEY, stat.concept(),
                        CORRECT_ANSWERS_KEY, stat.correctAnswers(),
                        TOTAL_QUESTIONS_KEY, stat.totalQuestions(),
                        ACCURACY_PERCENTAGE_KEY, stat.accuracyPercentage()
                ))
                .toList();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SESSION_METADATA_WEAK_CONCEPTS, statistics.weakConcepts());
        metadata.put(SESSION_METADATA_CONCEPT_BREAKDOWN, conceptBreakdown);
        return metadata;
    }

    private static final class ConceptCounter {
        private int correctAnswers;
        private int totalQuestions;
    }

    private record ChallengeStatistics(
            int correctAnswers,
            int totalQuestions,
            String performanceLevel,
            List<ChallengeQuizConceptStatResponse> conceptBreakdown,
            List<String> weakConcepts
    ) {
    }

    private record ChallengeGenerationProfile(int questionCount, String difficulty) {
    }

    private UUID parseStudyPackId(String studyPackIdRaw) {
        try {
            return UUID.fromString(Objects.requireNonNull(studyPackIdRaw).trim());
        } catch (RuntimeException ex) {
            throw new StudyPackNotFoundException();
        }
    }

    private UUID parseSessionId(String sessionIdRaw) {
        try {
            return UUID.fromString(Objects.requireNonNull(sessionIdRaw).trim());
        } catch (RuntimeException ex) {
            throw new ChallengeQuizSessionNotFoundException();
        }
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackGenerationContext buildQuizGenerationContext(UUID userId, StudyPackEntity studyPack) {
        return generationContextResolver.resolveForStudyPack(userId, studyPack);
    }

    private QuickReviewSessionEntity findChallengeSessionOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(ChallengeQuizSessionNotFoundException::new);
    }

    private QuickReviewSessionEntity findChallengeSessionForUpdateOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(ChallengeQuizSessionNotFoundException::new);
    }

    private void assertSessionInProgress(QuickReviewSessionEntity session) {
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new ChallengeQuizSessionNotInProgressException();
        }
    }

    private void markSessionForfeited(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(null);
    }

    private void accumulateLlmUsage(
            QuickReviewSessionEntity session,
            GeneratedChallengeQuizContent generatedContent
    ) {
        if (generatedContent == null) {
            return;
        }
        if (session.getModelUsed() == null && generatedContent.modelUsed() != null) {
            session.setModelUsed(generatedContent.modelUsed());
        }
        session.setInputTokens(addTokenUsage(session.getInputTokens(), generatedContent.inputTokens()));
        session.setOutputTokens(addTokenUsage(session.getOutputTokens(), generatedContent.outputTokens()));
        session.setCachedInputTokens(addTokenUsage(
                session.getCachedInputTokens(),
                generatedContent.cachedInputTokens()
        ));
    }

    private Integer addTokenUsage(Integer accumulatedTokens, Integer additionalTokens) {
        if (additionalTokens == null) {
            return accumulatedTokens;
        }
        return accumulatedTokens == null ? additionalTokens : accumulatedTokens + additionalTokens;
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private int resolveMonthlyChallengeQuizLimit(PlanType planType) {
        return properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
    }

    private ChallengeQuizStartResponse buildEmptyStartResponse(
            StudyPackEntity studyPack,
            int usedThisMonth,
            PlanType planType
    ) {
        return new ChallengeQuizStartResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                0,
                INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE,
                usedThisMonth,
                resolveMonthlyChallengeQuizLimit(planType),
                (int) countBoardExamUsedThisMonth(studyPack.getOwnerUserId()),
                properties.getPricing().resolveMonthlyBoardExamLimit(planType),
                MODE_CHALLENGE,
                DEFAULT_SELECTED_DIFFICULTY,
                List.of(),
                0,
                null,
                null,
                resolveMaxChallengeSourceNotes(planType)
        );
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedBoardExamSourceOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(InvalidBoardExamSourceException::sourceUnavailable);
    }

    private String normalizeSubjectForMatch(String subject) {
        return subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
    }

    private QuickReviewSessionEntity buildGeneratingSession(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            String difficulty,
            String mode
    ) {
        return buildGeneratingSession(null, userId, studyPackId, studyPack, difficulty, mode, List.of());
    }

    private QuickReviewSessionEntity buildGeneratingSession(
            UUID sessionId,
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            String difficulty,
            String mode,
            List<LongExamSourceNoteRef> sourceNoteRefs
    ) {
        OffsetDateTime createdAt = OffsetDateTime.now();
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId == null ? UUID.randomUUID() : sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(buildInitialSessionState(difficulty, mode, sourceNoteRefs));
        session.setCreatedAt(createdAt);
        session.setCompletedAt(null);
        return session;
    }

    private void markSessionReady(QuickReviewSessionEntity session, List<QuizItem> challengeQuiz, String difficulty) {
        String mode = extractMode(session.getSessionState());
        List<LongExamSourceNoteRef> sourceNoteRefs = extractSourceNoteRefs(session.getSessionState());
        int rateSeconds = MODE_BOARD_EXAM.equals(mode) ? SECONDS_PER_QUESTION_BOARD_EXAM : SECONDS_PER_QUESTION_CHALLENGE;
        int timeLimitSeconds = challengeQuiz.size() * rateSeconds;
        Map<String, Object> state = buildInitialSessionState(difficulty, mode, sourceNoteRefs);
        state.put(SESSION_STATE_TIME_LIMIT_SECONDS, timeLimitSeconds);
        state.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());

        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(challengeQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                challengeQuiz,
                state
        ));
        session.setCompletedAt(null);
    }

    /**
     * Shuffles question order without ever splitting a MATCHING block apart — the frontend
     * (`lib/quiz.ts`) groups consecutive same-{@code questionGroup} MATCHING items by scanning the
     * array in order, so scattering them would silently break that rendering.
     */
    private List<QuizItem> shuffleQuestionOrderPreservingMatchingGroups(List<QuizItem> questions) {
        if (questions == null || questions.size() < 2) {
            return questions == null ? List.of() : List.copyOf(questions);
        }
        List<List<QuizItem>> blocks = new ArrayList<>();
        int index = 0;
        while (index < questions.size()) {
            String questionGroup = resolveMatchingQuestionGroup(questions.get(index));
            int endIndex = index + 1;
            if (questionGroup != null) {
                // ⚠️ A block must not span two source packs. challenge-quiz-developer.txt instructs EVERY
                // generation to label its matching block "group-1", so two independently generated sources
                // routinely emit the SAME label; because generateChallengeQuizForSources appends sources
                // back to back, A's trailing block and B's leading block would otherwise merge into one
                // block with ambiguous provenance. Breaking on the source stamp fixes that at the cause —
                // detection alone turned a working multi-note session into a hard failure after both LLM
                // calls had been paid for.
                String sourceStudyPackId = questions.get(index).sourceStudyPackId();
                while (endIndex < questions.size()
                        && questionGroup.equals(resolveMatchingQuestionGroup(questions.get(endIndex)))
                        && Objects.equals(sourceStudyPackId, questions.get(endIndex).sourceStudyPackId())) {
                    endIndex += 1;
                }
            }
            List<QuizItem> block = new ArrayList<>(questions.subList(index, endIndex));
            assertMatchingGroupHasOneSourceStudyPack(block);
            blocks.add(block);
            index = endIndex;
        }
        Collections.shuffle(blocks);
        List<QuizItem> shuffled = new ArrayList<>(questions.size());
        blocks.forEach(shuffled::addAll);
        return shuffled;
    }

    private String resolveMatchingQuestionGroup(QuizItem question) {
        if (question == null || !MATCHING_FORMAT.equals(question.questionFormat()) || question.questionGroup() == null) {
            return null;
        }
        String normalized = question.questionGroup().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void assertMatchingGroupHasOneSourceStudyPack(List<QuizItem> matchingGroup) {
        if (matchingGroup == null || matchingGroup.size() < 2) {
            return;
        }
        Set<String> sourceStudyPackIds = matchingGroup.stream()
                .map(QuizItem::sourceStudyPackId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(sourceStudyPackId -> !sourceStudyPackId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sourceStudyPackIds.size() > 1) {
            throw new MatchingQuestionGroupSourceMismatchException();
        }
    }

    private void markSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setCompletedAt(null);
    }

}
