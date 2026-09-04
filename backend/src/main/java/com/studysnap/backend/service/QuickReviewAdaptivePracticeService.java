package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdaptivePracticeCompleteResponse;
import com.studysnap.backend.dto.AdaptivePracticeFocusConceptResponse;
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.AdaptivePracticeSessionNotFoundException;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.QuickReviewSessionAnchorException;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import com.studysnap.backend.model.StudyPackProgressProjection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class QuickReviewAdaptivePracticeService {

    private static final int BASE_QUESTION_COUNT = 5;
    private static final int MID_QUESTION_COUNT = 7;
    private static final int HIGH_QUESTION_COUNT = 10;
    private static final String FOCUS_MESSAGE = "Focusing on concepts you need to improve.";
    private static final String ADAPTIVE_GENERATING_MESSAGE = "Generating your adaptive practice.";
    private static final String ADAPTIVE_GENERATION_FAILED_MESSAGE = "We couldn't generate Adaptive Practice this time. Try again.";
    private static final String NO_SOURCE_SESSION_MESSAGE = "Complete a Quick Review or Challenge Quiz first to unlock adaptive practice.";
    private static final String NO_WEAK_CONCEPTS_MESSAGE = "No weak concepts found from your latest review.";
    private static final String ADAPTIVE_GENERATION_FAILED_CODE = "ADAPTIVE_QUIZ_GENERATION_FAILED";
    private static final String ADAPTIVE_GENERATION_FAILED_DETAIL = "Could not generate enough unique adaptive questions. Please try again.";
    private static final String PREMIUM_FEATURE_REQUIRED_CODE = "PREMIUM_FEATURE_REQUIRED";
    private static final String PREMIUM_FEATURE_REQUIRED_MESSAGE = "Adaptive Practice is not available on your current plan.";
    private static final String MONTHLY_LIMIT_REACHED_CODE = "MONTHLY_ADAPTIVE_PRACTICE_LIMIT_REACHED";
    private static final String MONTHLY_LIMIT_REACHED_MESSAGE = "You've reached your monthly Adaptive Practice limit.";
    private static final String INVALID_SESSION_RESULT_CODE = "INVALID_SESSION_RESULT";
    private static final String INVALID_SESSION_RESULT_MESSAGE = "Correct answers cannot exceed total questions.";
    private static final String ADAPTIVE_PRACTICE_SESSION_COMPLETED_MESSAGE = "Adaptive Practice session completed.";
    private static final String ADAPTIVE_PRACTICE_SESSION_ALREADY_COMPLETED_MESSAGE = "Adaptive Practice session already completed.";
    private static final String ADAPTIVE_PRACTICE_SESSION_ALREADY_ENDED_MESSAGE = "Adaptive Practice session has already ended.";
    private static final String ADAPTIVE_PRACTICE_SESSION_FORFEITED_MESSAGE = "Adaptive Practice session forfeited.";
    private static final String AI_RATE_LIMIT_SCOPE = "adaptive-practice";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String SESSION_STATE_SOURCE_COLLECTION_ID = "sourceCollectionId";
    // Same key and shape LongExamService and ChallengeQuizService already write, and that
    // QuizSessionHistoryService.findParticipatingNoteIds already reads. Plan-scoped Adaptive has no
    // note anchor, so this is the ONLY thing that credits its source notes with the completion.
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";
    private static final String SOURCE_NOTE_ID_KEY = "noteId";
    private static final String SESSION_STATE_FOCUS_CONCEPTS = "adaptiveFocusConcepts";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_WEAK_CONCEPT_COUNT = "weakConceptCount";
    private static final String ANALYTICS_METADATA_ENTRY = "entry";
    private static final String ANALYTICS_METADATA_SOURCE_SCOPE = "sourceScope";
    private static final String SUB_MODE_INTERVIEW = "INTERVIEW";
    private static final String INTERVIEW_SESSION_ACTIVE_MESSAGE =
        "You have an Interview Practice session in progress on this note. Finish or end it before starting Adaptive Practice.";
    private static final int MAX_PLAN_SOURCE_PACKS = 3;
    private static final int MAX_PLAN_FOCUS_CONCEPTS = 10;
    private static final String ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_TODAY_FOCUS = "dashboard-today-focus";
    private static final String ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_FOCUS_AREAS = "dashboard-focus-areas";
    private static final String ADAPTIVE_PRACTICE_ENTRY_CHALLENGE_QUIZ_RESULT = "challenge-quiz-result";
    private static final String ADAPTIVE_PRACTICE_ENTRY_INTERVIEW_PRACTICE_GAP = "interview-practice-gap";
    private static final String ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_CONTINUE = "dashboard-continue";
    private static final String ADAPTIVE_PRACTICE_ENTRY_NOTE_DETAIL = "note-detail";
    private static final String ADAPTIVE_PRACTICE_ENTRY_NOTE_DETAIL_DUE_CONCEPTS = "note-detail-due-concepts";
    private static final String ADAPTIVE_PRACTICE_ENTRY_COLLECTION_DETAIL = "collection-detail";
    private static final String ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_PLAN = "dashboard-plan";
    private static final String ADAPTIVE_PRACTICE_ENTRY_DIRECT = "direct";
    private static final Set<String> KNOWN_ADAPTIVE_PRACTICE_ENTRIES = Set.of(
        ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_TODAY_FOCUS,
        ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_FOCUS_AREAS,
        ADAPTIVE_PRACTICE_ENTRY_CHALLENGE_QUIZ_RESULT,
        ADAPTIVE_PRACTICE_ENTRY_INTERVIEW_PRACTICE_GAP,
        ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_CONTINUE,
        ADAPTIVE_PRACTICE_ENTRY_NOTE_DETAIL,
        ADAPTIVE_PRACTICE_ENTRY_NOTE_DETAIL_DUE_CONCEPTS,
        ADAPTIVE_PRACTICE_ENTRY_COLLECTION_DETAIL,
        ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_PLAN
    );
    private static final List<QuickReviewSessionStatus> ACTIVE_GENERATION_STATUSES = List.of(
        QuickReviewSessionStatus.GENERATING,
        QuickReviewSessionStatus.IN_PROGRESS
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
        QuickReviewSessionStatus.GENERATING,
        QuickReviewSessionStatus.IN_PROGRESS,
        QuickReviewSessionStatus.FAILED
    );

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final QuizGenerationService quizGenerationService;
    private final ActivityTrackingService activityTrackingService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final ConceptHealthService conceptHealthService;
    private final NoteCollectionRepository noteCollectionRepository;
    private final NoteCollectionItemRepository noteCollectionItemRepository;
    private final LongExamPlanSourceSampler longExamPlanSourceSampler;

    public QuickReviewAdaptiveQuizResponse generateAdaptiveQuiz(String studyPackIdRaw, UUID userId) {
        return generateAdaptiveQuiz(studyPackIdRaw, userId, null);
    }

    public QuickReviewAdaptiveQuizResponse generateAdaptiveQuiz(String studyPackIdRaw, UUID userId, String entry) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                ACTIVE_GENERATION_STATUSES
            )
            .orElse(null);
        if (existing != null && isInterviewSession(existing)) {
            // Interview Practice shares the ADAPTIVE discriminator and therefore the
            // (user_id, study_pack_id, session_mode) unique index on active sessions. Without this
            // guard the branch below would either hand the learner interview questions inside an
            // Adaptive Practice session, or -- worse -- FORFEIT a session this mode does not own.
            // Starting a new session instead is not an option: the unique index would reject it.
            return interviewSessionActiveResponse(studyPack);
        }
        if (existing != null) {
            List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(existing.getSessionState());
            if (existing.getStatus() == QuickReviewSessionStatus.GENERATING || !existingQuiz.isEmpty()) {
                return toAdaptiveResponse(existing, studyPack);
            }
            markSessionForfeited(existing);
            quickReviewSessionRepository.save(existing);
        }

        QuickReviewSessionEntity latestCompletedSession = resolveLatestAdaptiveSourceSession(userId, studyPackId);

        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_SOURCE_SESSION_MESSAGE
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        AdaptiveFocus adaptiveFocus = resolveAdaptiveFocus(
            userId,
            studyPackId,
            studyPack,
            weakConcepts,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        List<String> focusConcepts = adaptiveFocus.concepts();
        if (focusConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_WEAK_CONCEPTS_MESSAGE
            );
        }

        assertAdaptivePracticeQuotaAvailable(userId, planType);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
        int questionCount = resolveAdaptiveQuestionCount(focusConcepts.size());
        List<AdaptivePracticeFocusConceptResponse> focusEntries = toFocusEntries(studyPack, adaptiveFocus);
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(
            userId,
            studyPackId,
            studyPack,
            focusEntries,
            null,
            List.of()
        ));
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);
        try {
            List<QuizItem> generatedQuiz = quizGenerationService.generateAdaptivePracticeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                getKeyConcepts(studyPack),
                focusConcepts,
                disallowedQuestions,
                questionCount,
                generationContext
            );
            List<QuizItem> adaptiveQuiz = QuizDeduplicationUtils.uniqueQuestions(
                generatedQuiz,
                QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (adaptiveQuiz.size() != questionCount) {
                throw new AppException(
                    ADAPTIVE_GENERATION_FAILED_CODE,
                    ADAPTIVE_GENERATION_FAILED_DETAIL,
                    HttpStatus.BAD_GATEWAY
                );
            }

            markSessionReady(session, adaptiveQuiz, focusEntries);
            QuickReviewSessionEntity savedSession = quickReviewSessionRepository.save(session);
            userUsageService.incrementAdaptiveQuizGeneration(userId, savedSession.getCreatedAt());

            try {
                activityTrackingService.recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, studyPackId);
                analyticsService.trackEvent(userId, AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED, studyPackId, Map.of(
                    ANALYTICS_METADATA_SESSION_ID, savedSession.getId().toString(),
                    ANALYTICS_METADATA_WEAK_CONCEPT_COUNT, focusConcepts.size(),
                    ANALYTICS_METADATA_ENTRY, normalizeAdaptivePracticeEntry(entry),
                    ANALYTICS_METADATA_SOURCE_SCOPE, "note"
                ));
            } catch (RuntimeException ignored) {
                // Activity/analytics failures must not turn a generated quiz into a failed session.
            }

            return toAdaptiveResponse(savedSession, studyPack);
        } catch (Exception ex) {
            markSessionFailed(session);
            QuickReviewSessionEntity failedSession = quickReviewSessionRepository.save(session);
            return toAdaptiveResponse(failedSession, studyPack);
        }
    }

    public QuickReviewAdaptiveQuizResponse generateAdaptiveQuizForCollection(
            String collectionIdRaw,
            UUID userId,
            String entry
    ) {
        authService.requireEmailVerified(userId);
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(collectionIdRaw, CollectionNotFoundException::new);
        NoteCollectionEntity collection = noteCollectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId)
                .orElseThrow(CollectionNotFoundException::new);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);
        List<QuickReviewSessionEntity> activeAdaptiveSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId, QuickReviewSessionMode.ADAPTIVE, ACTIVE_GENERATION_STATUSES);
        QuickReviewSessionEntity collectionSession = activeAdaptiveSessions
                .stream()
                .filter(session -> !isInterviewSession(session))
                .filter(session -> collectionId.equals(resolveSourceCollectionId(session)))
                .findFirst()
                .orElse(null);
        if (collectionSession != null) {
            return toAdaptiveResponse(collectionSession, null, collection);
        }

        // ⚠️ GATES SIT HERE ON PURPOSE: AFTER the resume return, BEFORE the expensive load.
        //
        // Below this line, resolving eligibility reads every candidate pack of the collection — for a
        // Review Set, hundreds of rows. Gating afterwards made an over-quota learner pay that cost on
        // every click before being refused, which is why the checks were moved earlier.
        //
        // ⚠️ BUT THEY WERE MOVED TOO FAR, ABOVE THE RESUME BRANCH, AND THAT WAS A LIVE DEFECT: a
        // learner at their monthly limit could not RESUME a session they had already paid for, and
        // every resume burned a rate-limit token for a request that makes no LLM call. Resuming
        // consumes neither quota nor generation capacity — only STARTING does.
        //
        // ⚠️ Do not move these above the resume return again. The active-session lookup that precedes
        // them is a single indexed query, not the expensive part.
        assertAdaptivePracticeQuotaAvailable(userId, planType);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);

        List<NoteCollectionEntity> children = noteCollectionRepository
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId);
        List<NoteCollectionEntity> strata = children.isEmpty() ? List.of(collection) : children;
        List<CollectionCandidate> candidates = new ArrayList<>();
        int ordinal = 0;
        for (NoteCollectionEntity stratum : strata) {
            for (NoteCollectionItemEntity item : noteCollectionItemRepository
                    .findByCollectionIdOrderByPositionAsc(stratum.getId())) {
                // Bucket by stratum ID, not title: titles are mutable and non-unique, so two same-titled
                // Subject Plans would collapse into one coverage bucket. ChallengeQuizService's Board
                // Exam call site (shipped one release earlier, same sampler) already uses the id.
                candidates.add(new CollectionCandidate(item.getNoteId(), stratum.getId().toString(), ordinal++));
            }
        }
        List<UUID> noteIds = candidates.stream().map(CollectionCandidate::noteId).distinct().toList();

        // ⚠️ PHASE 1 — PROJECTIONS, NOT ENTITIES. Deciding eligibility needs only id, noteId,
        // keyConcepts, ownerUserId and status, and this collection can be a whole Review Set. Loading
        // full entities here materialized every DONE pack's `quiz` and `summary` JSON just to choose
        // at most MAX_PLAN_SOURCE_PACKS of them. `StudyPackRepositoryTest` pins the emitted SQL:
        // it selects `key_concepts` and never `quiz` or `summary`.
        //
        // ⚠️ THIS IS A REDUCTION, NOT A BOUND, and that distinction is load-bearing. Phase 2 below
        // loads entities for the packs that survive focus filtering — typically a small
        // fraction. In the worst case, where EVERY pack has a due-or-weak concept, phase 2 loads what
        // phase 1 used to and this has bought a query. A true bound needs
        // LongExamPlanSourceSampler.EligiblePlanSource to stop taking a StudyPackEntity, and that
        // sampler is shared with Long Exam and Board Exam.
        //
        // ⚠️ OWNER AND STATUS ARE FILTERED IN JAVA HERE because findProgressViewsByNoteIdIn filters
        // neither — but be precise about what this filter IS. It is an OPTIMISATION, not the access
        // boundary: phase 2 re-applies owner and status in SQL, so deleting these two conditions
        // changes no observable behaviour (verified by mutation — dropping either leaves the suite
        // green, and that is CORRECT rather than a missing test). What they buy is not loading
        // concept health for packs that will be discarded anyway.
        //
        // ⚠️ THE ACCESS BOUNDARY IS PHASE 2's findByOwnerUserIdAndNoteIdInAndStatus. Do not "simplify"
        // phase 2 to a by-id lookup that drops the owner predicate on the grounds that phase 1
        // already filtered — phase 1 is deliberately not load-bearing for access.
        Map<UUID, CollectionCandidate> candidateByNoteId = new LinkedHashMap<>();
        for (CollectionCandidate candidate : candidates) {
            candidateByNoteId.putIfAbsent(candidate.noteId(), candidate);
        }
        Map<UUID, List<String>> conceptsByPack = new LinkedHashMap<>();
        Map<UUID, UUID> noteIdByPackId = new LinkedHashMap<>();
        for (StudyPackProgressProjection projection : studyPackRepository.findProgressViewsByNoteIdIn(noteIds)) {
            if (!userId.equals(projection.getOwnerUserId()) || projection.getStatus() != StudyPackStatus.DONE) {
                continue;
            }
            if (noteIdByPackId.containsValue(projection.getNoteId())) {
                continue;
            }
            noteIdByPackId.put(projection.getId(), projection.getNoteId());
            conceptsByPack.put(
                    projection.getId(),
                    projection.getKeyConcepts() == null ? List.of() : projection.getKeyConcepts());
        }
        if (conceptsByPack.isEmpty()) {
            return emptyCollectionResponse(collection, NO_SOURCE_SESSION_MESSAGE);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<UUID, List<String>> dueByPack = conceptHealthService
                .getDueConceptsByStudyPackIds(userId, conceptsByPack, now);
        Map<UUID, List<String>> weakByPack = conceptHealthService
                .getPersistentlyWeakConceptsByStudyPackIds(userId, List.copyOf(conceptsByPack.keySet()));
        Map<FocusKey, ConceptSelectionReason> reasonByFocus = new LinkedHashMap<>();
        for (UUID packId : conceptsByPack.keySet()) {
            dueByPack.getOrDefault(packId, List.of()).forEach(concept ->
                    reasonByFocus.put(new FocusKey(packId, concept.trim()), ConceptSelectionReason.DUE));
            weakByPack.getOrDefault(packId, List.of()).forEach(concept -> reasonByFocus.compute(
                    new FocusKey(packId, concept.trim()),
                    (ignored, current) -> current == ConceptSelectionReason.DUE
                            ? ConceptSelectionReason.BOTH : ConceptSelectionReason.WEAK));
        }
        if (reasonByFocus.isEmpty()) {
            return emptyCollectionResponse(collection, NO_WEAK_CONCEPTS_MESSAGE);
        }

        Set<UUID> withFocus = reasonByFocus.keySet().stream()
                .map(FocusKey::studyPackId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (withFocus.isEmpty()) {
            return emptyCollectionResponse(collection, NO_WEAK_CONCEPTS_MESSAGE);
        }

        // Plan-scoped practice is anchored on the collection, not on one of its source packs. An
        // active note-scoped Adaptive or Interview session therefore does not make that pack
        // ineligible as a source. The collection row lock plus the collection partial unique index
        // serialise concurrent starts for this plan; source sampling remains independently bounded.
        List<UUID> focusEligiblePackIds = List.copyOf(withFocus);

        // ⚠️ PHASE 2 — full entities, ONLY for the packs that survived focus filtering.
        // Owner and status stay in SQL here (findByOwnerUserIdAndNoteIdInAndStatus), so this is a
        // second, independent access check rather than a repeat of phase 1's Java-side one.
        List<UUID> focusEligibleNoteIds = focusEligiblePackIds.stream()
                .map(noteIdByPackId::get)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, StudyPackEntity> packById = studyPackRepository
                .findByOwnerUserIdAndNoteIdInAndStatus(userId, focusEligibleNoteIds, StudyPackStatus.DONE)
                .stream()
                .collect(Collectors.toMap(StudyPackEntity::getId, pack -> pack, (left, right) -> left));
        Map<UUID, LongExamPlanSourceSampler.EligiblePlanSource> eligibleByPack = new LinkedHashMap<>();
        for (UUID packId : focusEligiblePackIds) {
            StudyPackEntity pack = packById.get(packId);
            CollectionCandidate candidate = candidateByNoteId.get(noteIdByPackId.get(packId));
            if (pack != null && candidate != null) {
                eligibleByPack.put(packId, new LongExamPlanSourceSampler.EligiblePlanSource(
                        pack, candidate.bucket(), candidate.position()));
            }
        }
        List<LongExamPlanSourceSampler.EligiblePlanSource> focusEligible = List.copyOf(eligibleByPack.values());
        if (focusEligible.isEmpty()) {
            return emptyCollectionResponse(collection, NO_SOURCE_SESSION_MESSAGE);
        }
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity samplingSeed = focusEligible.getFirst().studyPack();
        List<LongExamPlanSourceSampler.EligiblePlanSource> sampled = longExamPlanSourceSampler.sample(
                focusEligible, samplingSeed.getId(), MAX_PLAN_SOURCE_PACKS, sessionId);
        List<AdaptivePracticeFocusConceptResponse> focusEntries = buildBoundedFocusEntries(
                sampled, reasonByFocus, eligibleByPack);

        // ⚠️ ONE rate-limit unit, deliberately, even though generateCollectionQuiz issues up to
        // MAX_PLAN_SOURCE_PACKS LLM calls where the note path issues one. Decided (owner,
        // 2026-09-03) rather than left implicit: checking per call would fail PART WAY THROUGH
        // generation and strand a FAILED session, which is worse for the learner than a 3x
        // allowance on a path whose fan-out is already bounded. Revisit only if the bound moves.
        int questionCount = resolveAdaptiveQuestionCount(focusEntries.size());
        QuickReviewSessionEntity session = buildGeneratingSession(
                userId, null, null, focusEntries, collectionId, sampled.stream()
                        .map(source -> source.studyPack().getNoteId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        session.setId(sessionId);
        session = quickReviewSessionRepository.save(session);
        try {
            List<QuizItem> quiz = generateCollectionQuiz(userId, sampled, focusEntries, questionCount);
            if (quiz.size() != questionCount) {
                throw new AppException(ADAPTIVE_GENERATION_FAILED_CODE, ADAPTIVE_GENERATION_FAILED_DETAIL,
                        HttpStatus.BAD_GATEWAY);
            }
            markSessionReady(session, quiz, focusEntries);
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
            userUsageService.incrementAdaptiveQuizGeneration(userId, saved.getCreatedAt());
            try {
                activityTrackingService.recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, samplingSeed.getId());
                analyticsService.trackEvent(userId, AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED, samplingSeed.getId(), Map.of(
                        ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                        ANALYTICS_METADATA_WEAK_CONCEPT_COUNT, focusEntries.size(),
                        ANALYTICS_METADATA_ENTRY, normalizeAdaptivePracticeEntry(entry),
                        ANALYTICS_METADATA_SOURCE_SCOPE, children.isEmpty() ? "plan" : "review-set"));
            } catch (RuntimeException ignored) {
                // Activity/analytics failures must not turn a generated quiz into a failed session.
            }
            return toAdaptiveResponse(saved, null, collection);
        } catch (RuntimeException failure) {
            markSessionFailed(session);
            return toAdaptiveResponse(quickReviewSessionRepository.save(session), null, collection);
        }
    }

    private List<QuizItem> generateCollectionQuiz(
            UUID userId,
            List<LongExamPlanSourceSampler.EligiblePlanSource> sampled,
            List<AdaptivePracticeFocusConceptResponse> focusEntries,
            int questionCount
    ) {
        List<QuizItem> merged = new ArrayList<>();
        Set<String> disallowed = new LinkedHashSet<>();
        int base = questionCount / sampled.size();
        int remainder = questionCount % sampled.size();
        for (int index = 0; index < sampled.size(); index++) {
            StudyPackEntity pack = sampled.get(index).studyPack();
            List<String> concepts = focusEntries.stream()
                    .filter(focus -> focus.sourceStudyPackId().equals(pack.getId().toString()))
                    .map(AdaptivePracticeFocusConceptResponse::concept)
                    .toList();
            if (concepts.isEmpty()) {
                continue;
            }
            List<String> excludedForSource = new ArrayList<>(extractQuestionTexts(pack.getQuiz()));
            excludedForSource.addAll(extractQuestionTexts(merged));
            disallowed.addAll(QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(excludedForSource));
            List<QuizItem> generated = quizGenerationService.generateAdaptivePracticeQuiz(
                    pack.getTitle(), pack.getSummary(), getKeyConcepts(pack), concepts, excludedForSource,
                    base + (index < remainder ? 1 : 0), buildQuizGenerationContext(userId, pack));
            List<QuizItem> unique = QuizDeduplicationUtils.uniqueQuestions(generated, disallowed).stream()
                    .map(item -> item.withSourceStudyPackId(pack.getId().toString()))
                    .toList();
            merged.addAll(unique);
            disallowed.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(unique));
        }
        return List.copyOf(merged);
    }

    private List<AdaptivePracticeFocusConceptResponse> buildBoundedFocusEntries(
            List<LongExamPlanSourceSampler.EligiblePlanSource> sampled,
            Map<FocusKey, ConceptSelectionReason> reasons,
            Map<UUID, LongExamPlanSourceSampler.EligiblePlanSource> eligibleByPack
    ) {
        Map<UUID, List<Map.Entry<FocusKey, ConceptSelectionReason>>> byPack = new LinkedHashMap<>();
        for (LongExamPlanSourceSampler.EligiblePlanSource source : sampled) {
            byPack.put(source.studyPack().getId(), reasons.entrySet().stream()
                    .filter(entry -> entry.getKey().studyPackId().equals(source.studyPack().getId()))
                    .toList());
        }
        List<AdaptivePracticeFocusConceptResponse> result = new ArrayList<>();
        int offset = 0;
        while (result.size() < MAX_PLAN_FOCUS_CONCEPTS) {
            boolean added = false;
            for (Map.Entry<UUID, List<Map.Entry<FocusKey, ConceptSelectionReason>>> packEntry : byPack.entrySet()) {
                if (offset >= packEntry.getValue().size() || result.size() >= MAX_PLAN_FOCUS_CONCEPTS) {
                    continue;
                }
                Map.Entry<FocusKey, ConceptSelectionReason> focus = packEntry.getValue().get(offset);
                StudyPackEntity pack = eligibleByPack.get(packEntry.getKey()).studyPack();
                result.add(new AdaptivePracticeFocusConceptResponse(
                        focus.getKey().concept(), pack.getId().toString(), pack.getTitle(), focus.getValue().name()));
                added = true;
            }
            if (!added) {
                break;
            }
            offset++;
        }
        return List.copyOf(result);
    }

    private QuickReviewAdaptiveQuizResponse emptyCollectionResponse(NoteCollectionEntity collection, String message) {
        return new QuickReviewAdaptiveQuizResponse(
                null, null, null, null, collection.getTitle(), List.of(), List.of(), message);
    }

    private QuickReviewAdaptiveQuizResponse interviewSessionActiveResponse(StudyPackEntity studyPack) {
        return new QuickReviewAdaptiveQuizResponse(
            null,
            null,
            studyPack.getId().toString(),
            studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
            studyPack.getTitle(),
            List.of(),
            List.of(),
            INTERVIEW_SESSION_ACTIVE_MESSAGE
        );
    }

    private boolean isInterviewSession(QuickReviewSessionEntity session) {
        return SUB_MODE_INTERVIEW.equals(QuizSessionStateUtils.extractSubMode(session.getSessionState()));
    }

    public UUID resolveSourceCollectionId(QuickReviewSessionEntity session) {
        UUID persistedCollectionId = session.getSourceCollectionId();
        return persistedCollectionId != null ? persistedCollectionId : extractSourceCollectionId(session);
    }

    /**
     * Legacy fallback for plan-scoped sessions created before V133. Do not remove this JSONB read
     * while a pre-migration in-flight session can still need to resume.
     */
    private UUID extractSourceCollectionId(QuickReviewSessionEntity session) {
        if (session.getSessionState() == null) {
            return null;
        }
        Object raw = session.getSessionState().get(SESSION_STATE_SOURCE_COLLECTION_ID);
        try {
            return raw instanceof String value ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeAdaptivePracticeEntry(String entry) {
        return entry != null && KNOWN_ADAPTIVE_PRACTICE_ENTRIES.contains(entry)
            ? entry
            : ADAPTIVE_PRACTICE_ENTRY_DIRECT;
    }

    @Transactional(readOnly = true)
    public QuickReviewAdaptiveQuizResponse getAdaptiveQuizSession(String studyPackIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                OBSERVABLE_STATUSES
            )
            .orElse(null);
        if (existing != null && isInterviewSession(existing)) {
            return interviewSessionActiveResponse(studyPack);
        }
        if (existing != null) {
            return toAdaptiveResponse(existing, studyPack);
        }

        QuickReviewSessionEntity latestCompletedSession = resolveLatestAdaptiveSourceSession(userId, studyPackId);
        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_SOURCE_SESSION_MESSAGE
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        List<String> focusConcepts = resolveAdaptiveFocus(
            userId,
            studyPackId,
            studyPack,
            weakConcepts,
            OffsetDateTime.now(ZoneOffset.UTC)
        ).concepts();
        if (focusConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_WEAK_CONCEPTS_MESSAGE
            );
        }

        return new QuickReviewAdaptiveQuizResponse(
            null,
            null,
            studyPack.getId().toString(),
            studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
            studyPack.getTitle(),
            focusConcepts.stream().map(concept -> new AdaptivePracticeFocusConceptResponse(
                    concept, studyPack.getId().toString(), studyPack.getTitle(), null)).toList(),
            List.of(),
            FOCUS_MESSAGE
        );
    }

    @Transactional(readOnly = true)
    public QuickReviewAdaptiveQuizResponse getAdaptiveSessionById(String sessionIdRaw, UUID userId) {
        // Mirrors getAdaptiveQuizSession's identity gate. The FEATURE gate is deliberately NOT taken
        // here: completeAdaptiveSession and forfeitAdaptiveSession carry no feature check, so gating
        // only the read would let a learner whose plan lapsed mid-session finish a session they can
        // no longer see. Stated as a decision rather than left as an omission.
        authService.requireEmailVerified(userId);
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                AdaptivePracticeSessionNotFoundException::new
        );
        // OBSERVABLE_STATUSES, matching the note-addressed read: a COMPLETED or FORFEITED session is
        // not resumable. Without this filter the client renders its "Start Adaptive Practice" card
        // for a finished session -- currentIndex resets to 0 on load, so isComplete is false and the
        // results branch is unreachable -- and the start button dead-ends on a null note.
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.ADAPTIVE
                )
                .filter(candidate -> OBSERVABLE_STATUSES.contains(candidate.getStatus()))
                .filter(candidate -> !isInterviewSession(candidate))
                .orElseThrow(AdaptivePracticeSessionNotFoundException::new);

        UUID sourceCollectionId = resolveSourceCollectionId(session);
        if (sourceCollectionId != null) {
            Optional<NoteCollectionEntity> collection = noteCollectionRepository
                    .findByIdAndOwnerUserId(sourceCollectionId, userId);
            if (collection.isPresent()) {
                return toAdaptiveResponse(session, null, collection.get());
            }
            // ⚠️ The collection is gone but the session may still be usable. A PRE-MIGRATION
            // plan-scoped row is pack-anchored AND carries a JSONB collection id, so deleting that
            // collection leaves a perfectly resumable session -- and the note-addressed route DOES
            // resume it. Throwing here made the two routes disagree about the same row.
            // A post-migration row has no pack anchor and genuinely cannot be resolved, so it still
            // 404s below.
            if (session.getStudyPackId() == null) {
                throw new AdaptivePracticeSessionNotFoundException();
            }
        }
        if (session.getStudyPackId() == null) {
            throw new QuickReviewSessionAnchorException();
        }
        StudyPackEntity studyPack = studyPackRepository
                .findByIdAndOwnerUserId(session.getStudyPackId(), userId)
                .orElseThrow(AdaptivePracticeSessionNotFoundException::new);
        return toAdaptiveResponse(session, studyPack);
    }

    public AdaptivePracticeCompleteResponse completeAdaptiveSession(
        String sessionIdRaw,
        UUID userId,
        Integer correctAnswers,
        Integer totalQuestions,
        Integer durationSeconds,
        List<String> correctConceptNames
    ) {
        return completeAdaptiveSession(sessionIdRaw, userId, correctAnswers, totalQuestions, durationSeconds,
                correctConceptNames, null, null);
    }

    public AdaptivePracticeCompleteResponse completeAdaptiveSession(
        String sessionIdRaw,
        UUID userId,
        Integer correctAnswers,
        Integer totalQuestions,
        Integer durationSeconds,
        List<String> correctConceptNames,
        Map<Integer, Integer> submittedSelectedChoices,
        Map<Integer, List<Integer>> submittedSelectedMultiChoices
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, AdaptivePracticeSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
            )
            .filter(candidate -> !isInterviewSession(candidate))
            .orElseThrow(AdaptivePracticeSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new AdaptivePracticeCompleteResponse(
                ADAPTIVE_PRACTICE_SESSION_ALREADY_COMPLETED_MESSAGE,
                false,
                false,
                List.of()
            );
        }

        int safeTotalQuestions = session.getTotalQuestions() == null
            ? Optional.ofNullable(totalQuestions)
              .orElse(0)
            : session.getTotalQuestions();
        int safeCorrectAnswers = correctAnswers == null ? 0 : Math.max(0, correctAnswers);
        if (safeCorrectAnswers > safeTotalQuestions) {
            throw new AppException(
                INVALID_SESSION_RESULT_CODE,
                INVALID_SESSION_RESULT_MESSAGE,
                HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = safeTotalQuestions == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(safeCorrectAnswers)
              .multiply(BigDecimal.valueOf(100))
              .divide(BigDecimal.valueOf(safeTotalQuestions), 2, RoundingMode.HALF_UP);
        boolean isFirstCompletedSessionEver = !quickReviewSessionRepository
            .existsByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED);
        boolean isSecondCompletedSessionEver = quickReviewSessionRepository
            .countByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED) == 1;
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(safeTotalQuestions);
        session.setTotalQuestions(safeTotalQuestions);
        session.setCorrectAnswers(safeCorrectAnswers);
        session.setScorePercentage(scorePercentage);
        session.setDurationSeconds(durationSeconds);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setCompletedAt(now);
        quickReviewSessionRepository.save(session);
        List<QuizItem> storedQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> effectiveSelectedChoices = submittedSelectedChoices == null
                ? QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), storedQuiz)
                : submittedSelectedChoices;
        Map<Integer, List<Integer>> effectiveSelectedMultiChoices = submittedSelectedMultiChoices == null
                ? QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), storedQuiz)
                : submittedSelectedMultiChoices;
        Map<Integer, String> selectedIdentificationAnswers =
                QuizSessionStateUtils.extractSelectedIdentificationAnswers(session.getSessionState(), storedQuiz);
        Map<Integer, List<String>> selectedEnumerationAnswers =
                QuizSessionStateUtils.extractSelectedEnumerationAnswers(session.getSessionState(), storedQuiz);
        Map<String, List<ChallengeQuizConceptStatResponse>> breakdownBySource =
                effectiveSelectedChoices.isEmpty() && effectiveSelectedMultiChoices.isEmpty()
                        && selectedIdentificationAnswers.isEmpty() && selectedEnumerationAnswers.isEmpty()
                ? Map.of()
                : QuizSessionReviewUtils.computeConceptBreakdownBySourceStudyPack(
                        storedQuiz,
                        effectiveSelectedChoices,
                        effectiveSelectedMultiChoices,
                        selectedIdentificationAnswers,
                        selectedEnumerationAnswers);
        List<String> twiceMissedConcepts = new ArrayList<>();
        if (breakdownBySource.isEmpty()) {
            // No stored quiz/selections to derive a breakdown — fall back to the frontend-reported
            // correct concepts and record no misses (they cannot be computed reliably).
            List<String> correctConcepts = correctConceptNames == null ? List.of() : correctConceptNames;
            UUID fallbackStudyPackId = parseSourceStudyPackId(null, session.getStudyPackId());
            if (!correctConcepts.isEmpty() && fallbackStudyPackId != null) {
                conceptHealthService.recordCorrectAnswers(
                        userId,
                        fallbackStudyPackId,
                        correctConcepts,
                        now
                );
            } else if (!correctConcepts.isEmpty()) {
                logUnattributableConcepts(session, null, correctConcepts.size());
            }
        } else {
            for (Map.Entry<String, List<ChallengeQuizConceptStatResponse>> entry : breakdownBySource.entrySet()) {
                UUID sourceStudyPackId = parseSourceStudyPackId(entry.getKey(), session.getStudyPackId());
                if (sourceStudyPackId == null) {
                    // ⚠️ SKIP the unattributable entry; do NOT abort the completion. The guarantee
                    // that matters is that a null key never reaches ConceptHealth, and skipping
                    // preserves it. Throwing here rolled back a session that had already reached
                    // COMPLETED, leaving it IN_PROGRESS -- where the retry failed identically, the
                    // recovery sweeper covers only LONG_EXAM and CHALLENGE, and the row kept
                    // occupying the collection index, so the learner could not start a new
                    // plan-scoped session either. One unattributable item must not brick a plan.
                    logUnattributableConcepts(session, entry.getKey(), entry.getValue().size());
                    continue;
                }
                List<String> correctConcepts = QuizSessionReviewUtils.computeFullyCorrectConcepts(entry.getValue());
                List<String> missedConcepts = QuizSessionReviewUtils.computeConceptsWithMisses(entry.getValue());
                if (!correctConcepts.isEmpty()) {
                    conceptHealthService.recordCorrectAnswers(userId, sourceStudyPackId, correctConcepts, now);
                }
                if (!missedConcepts.isEmpty()) {
                    twiceMissedConcepts.addAll(conceptHealthService.recordIncorrectAnswers(
                            userId, sourceStudyPackId, missedConcepts, now));
                }
            }
        }
        return new AdaptivePracticeCompleteResponse(
            ADAPTIVE_PRACTICE_SESSION_COMPLETED_MESSAGE,
            isFirstCompletedSessionEver,
            isSecondCompletedSessionEver,
            twiceMissedConcepts
        );
    }

    public SimpleMessageResponse forfeitAdaptiveSession(String sessionIdRaw, UUID userId) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, AdaptivePracticeSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
            )
            .filter(candidate -> !isInterviewSession(candidate))
            .orElseThrow(AdaptivePracticeSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_ALREADY_ENDED_MESSAGE);
        }

        markSessionForfeited(session);
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_FORFEITED_MESSAGE);
    }

    private void markSessionForfeited(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(null);
    }


    private UUID parseSourceStudyPackId(String raw, UUID fallback) {
        try {
            return raw == null ? fallback : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * Records that concept evidence could not be attributed, loudly, without destroying the session.
     *
     * <p>⚠️ This REPLACED a thrown {@code QuickReviewSessionAnchorException}. The fail-loud guarantee
     * is unchanged in the sense that matters — a null study-pack key still never reaches
     * {@code ConceptHealth} — but the failure is no longer allowed to roll back a completion that
     * already succeeded. Two independent reviewers could not reach an unstamped item, so this is a
     * latent path; if it ever fires, this log is the signal that the stamping seam has regressed.
     */
    private void logUnattributableConcepts(QuickReviewSessionEntity session, String rawKey, int conceptCount) {
        log.error(
                "Adaptive Practice session {} could not attribute {} concept stat(s) to a source study pack"
                        + " (rawKey={}, studyPackId={}, sourceCollectionId={}). Evidence skipped; the session"
                        + " still completes. This means the source stamp is missing at the generation seam.",
                session.getId(), conceptCount, rawKey, session.getStudyPackId(), session.getSourceCollectionId());
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
    }

    private QuickReviewSessionEntity buildGeneratingSession(
        UUID userId,
        UUID studyPackId,
        StudyPackEntity studyPack,
        List<AdaptivePracticeFocusConceptResponse> focusConcepts,
        UUID sourceCollectionId,
        List<UUID> sourceNoteIds
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        if (sourceCollectionId == null) {
            session.setStudyPackId(studyPackId);
            session.setNoteId(studyPack == null ? null : studyPack.getNoteId());
        } else {
            session.setStudyPackId(null);
            session.setNoteId(null);
        }
        session.setSourceCollectionId(sourceCollectionId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(Map.of(SESSION_METADATA_WEAK_CONCEPTS,
                focusConcepts.stream().map(AdaptivePracticeFocusConceptResponse::concept).toList()));
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(SESSION_STATE_FOCUS_CONCEPTS, focusConcepts);
        if (sourceCollectionId != null) {
            initialState.put(SESSION_STATE_SOURCE_COLLECTION_ID, sourceCollectionId.toString());
            // Every sampled note, not one borrowed anchor. Before v0.113.0 a plan-scoped session
            // carried the primary pack's note_id and credited exactly ONE of up to
            // MAX_PLAN_SOURCE_PACKS notes; with the anchor gone it would otherwise credit NONE and
            // Study Plan progress would never advance.
            initialState.put(SESSION_STATE_SOURCE_NOTE_REFS, sourceNoteIds.stream()
                    .map(noteId -> Map.<String, Object>of(SOURCE_NOTE_ID_KEY, noteId.toString()))
                    .toList());
        }
        session.setSessionState(initialState);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        // ⚠️ The ENTITY owns the anchor rule; this call is eager invocation, not a second copy.
        // Without it a mocked repository would never fire @PrePersist and the service tests would
        // validate nothing.
        session.validateAnchor();
        return session;
    }

    private void markSessionReady(
        QuickReviewSessionEntity session,
        List<QuizItem> adaptiveQuiz,
        List<AdaptivePracticeFocusConceptResponse> focusConcepts
    ) {
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(adaptiveQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(Map.of(SESSION_METADATA_WEAK_CONCEPTS,
                focusConcepts.stream().map(AdaptivePracticeFocusConceptResponse::concept).toList()));
        session.setSessionState(QuizSessionStateUtils.withQuiz(adaptiveQuiz, session.getSessionState()));
        session.setCompletedAt(null);
    }

    private void markSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setCompletedAt(null);
    }

    private QuickReviewAdaptiveQuizResponse toAdaptiveResponse(
        QuickReviewSessionEntity session,
        StudyPackEntity studyPack
    ) {
        return toAdaptiveResponse(session, studyPack, null);
    }

    private QuickReviewAdaptiveQuizResponse toAdaptiveResponse(
        QuickReviewSessionEntity session,
        StudyPackEntity studyPack,
        NoteCollectionEntity sourceCollection
    ) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        String message = switch (session.getStatus()) {
            case GENERATING -> ADAPTIVE_GENERATING_MESSAGE;
            case FAILED -> ADAPTIVE_GENERATION_FAILED_MESSAGE;
            default -> FOCUS_MESSAGE;
        };
        return new QuickReviewAdaptiveQuizResponse(
            session.getId().toString(),
            session.getStatus(),
            session.getStudyPackId() == null ? null : session.getStudyPackId().toString(),
            session.getNoteId() == null ? null : session.getNoteId().toString(),
            sourceCollection == null ? requireStudyPack(studyPack).getTitle() : sourceCollection.getTitle(),
            extractFocusConcepts(session, studyPack),
            quiz,
            message
        );
    }

    private StudyPackEntity requireStudyPack(StudyPackEntity studyPack) {
        if (studyPack == null) {
            throw new QuickReviewSessionAnchorException();
        }
        return studyPack;
    }

    private List<AdaptivePracticeFocusConceptResponse> toFocusEntries(StudyPackEntity studyPack, AdaptiveFocus focus) {
        return focus.concepts().stream()
                .map(concept -> new AdaptivePracticeFocusConceptResponse(
                        concept,
                        studyPack.getId().toString(),
                        studyPack.getTitle(),
                        resolveSelectionReason(concept, focus.selectionReasons())
                ))
                .toList();
    }

    private List<AdaptivePracticeFocusConceptResponse> extractFocusConcepts(
            QuickReviewSessionEntity session,
            StudyPackEntity fallbackStudyPack
    ) {
        Object raw = session.getSessionState() == null
                ? null
                : session.getSessionState().get(SESSION_STATE_FOCUS_CONCEPTS);
        if (raw == null && session.getSessionMetadata() != null) {
            raw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        }
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<AdaptivePracticeFocusConceptResponse> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof AdaptivePracticeFocusConceptResponse focus) {
                result.add(focus);
            } else if (value instanceof Map<?, ?> map && map.get("concept") instanceof String concept) {
                result.add(new AdaptivePracticeFocusConceptResponse(
                        concept,
                        map.get("sourceStudyPackId") instanceof String id
                                ? id
                                : fallbackStudyPack == null ? null : fallbackStudyPack.getId().toString(),
                        map.get("sourceTitle") instanceof String title
                                ? title
                                : fallbackStudyPack == null ? null : fallbackStudyPack.getTitle(),
                        map.get("selectionReason") instanceof String reason ? reason : null
                ));
            } else if (value instanceof String concept && !concept.isBlank()) {
                result.add(new AdaptivePracticeFocusConceptResponse(
                        concept.trim(),
                        fallbackStudyPack == null ? null : fallbackStudyPack.getId().toString(),
                        fallbackStudyPack == null ? null : fallbackStudyPack.getTitle(),
                        null));
            }
        }
        return List.copyOf(result);
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(weakConceptsRaw instanceof List<?> rawList)) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object raw : rawList) {
            if (!(raw instanceof String value)) {
                continue;
            }
            String concept = value.trim();
            if (!concept.isBlank()) {
                normalized.add(concept);
            }
        }
        return new ArrayList<>(normalized);
    }

    private AdaptiveFocus resolveAdaptiveFocus(
        UUID userId,
        UUID studyPackId,
        StudyPackEntity studyPack,
        List<String> weakConcepts,
        OffsetDateTime now
    ) {
        List<String> dueConcepts = conceptHealthService.getDueConcepts(
            userId,
            studyPackId,
            getKeyConcepts(studyPack),
            now
        );
        LinkedHashSet<String> focusConcepts = new LinkedHashSet<>(dueConcepts);
        Map<String, ConceptSelectionReason> selectionReasons = new LinkedHashMap<>();
        dueConcepts.forEach(concept -> selectionReasons.put(concept, ConceptSelectionReason.DUE));
        for (String weakConcept : weakConcepts) {
            focusConcepts.add(weakConcept);
            selectionReasons.compute(
                weakConcept,
                (ignored, existingReason) -> existingReason == ConceptSelectionReason.DUE
                    ? ConceptSelectionReason.BOTH
                    : ConceptSelectionReason.WEAK
            );
        }
        return new AdaptiveFocus(new ArrayList<>(focusConcepts), selectionReasons);
    }

    private String resolveSelectionReason(
        String concept,
        Map<String, ConceptSelectionReason> selectionReasons
    ) {
        if (concept == null || concept.isBlank()) {
            return null;
        }
        ConceptSelectionReason reason = selectionReasons.get(concept.trim());
        return reason == null ? null : reason.name();
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private int resolveAdaptiveQuestionCount(int weakConceptCount) {
        if (weakConceptCount <= 2) {
            return BASE_QUESTION_COUNT;
        }
        if (weakConceptCount <= 4) {
            return MID_QUESTION_COUNT;
        }
        return HIGH_QUESTION_COUNT;
    }

    private StudyPackGenerationContext buildQuizGenerationContext(UUID userId, StudyPackEntity studyPack) {
        return generationContextResolver.resolveForStudyPack(userId, studyPack);
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

    private void assertAdaptivePracticeQuotaAvailable(UUID userId, PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int monthlyLimit = properties.getPricing().resolveMonthlyAdaptivePracticeLimit(planType);
        if (monthlyLimit <= 0) {
            throw new AppException(
                PREMIUM_FEATURE_REQUIRED_CODE,
                PREMIUM_FEATURE_REQUIRED_MESSAGE,
                HttpStatus.FORBIDDEN
            );
        }

        long usedThisMonth = userUsageService.getMonthlyUsage(userId, now).adaptiveQuizGenerations();
        if (usedThisMonth < monthlyLimit) {
            return;
        }

        throw new AppException(
            MONTHLY_LIMIT_REACHED_CODE,
            MONTHLY_LIMIT_REACHED_MESSAGE,
            HttpStatus.FORBIDDEN
        );
    }

    private QuickReviewSessionEntity resolveLatestAdaptiveSourceSession(UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity latestQuickReview = fetchLatestCompletedSession(
            userId,
            studyPackId,
            QuickReviewSessionMode.QUICK_REVIEW
        );
        QuickReviewSessionEntity latestChallenge = fetchLatestCompletedSession(
            userId,
            studyPackId,
            QuickReviewSessionMode.CHALLENGE
        );

        if (latestQuickReview == null) {
            return latestChallenge;
        }
        if (latestChallenge == null) {
            return latestQuickReview;
        }

        OffsetDateTime quickReviewCompletedAt = latestQuickReview.getCompletedAt();
        OffsetDateTime challengeCompletedAt = latestChallenge.getCompletedAt();
        if (quickReviewCompletedAt == null) {
            return latestChallenge;
        }
        if (challengeCompletedAt == null) {
            return latestQuickReview;
        }

        return challengeCompletedAt.isAfter(quickReviewCompletedAt) ? latestChallenge : latestQuickReview;
    }

    private QuickReviewSessionEntity fetchLatestCompletedSession(
        UUID userId,
        UUID studyPackId,
        QuickReviewSessionMode sessionMode
    ) {
        return quickReviewSessionRepository
            .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                studyPackId,
                sessionMode,
                PageRequest.of(0, 1)
            ).stream()
            .findFirst()
            .orElse(null);
    }

    private record AdaptiveFocus(
        List<String> concepts,
        Map<String, ConceptSelectionReason> selectionReasons
    ) {
    }

    private record FocusKey(UUID studyPackId, String concept) {
    }

    private record CollectionCandidate(UUID noteId, String bucket, int position) {
    }

    private enum ConceptSelectionReason {
        DUE,
        WEAK,
        BOTH
    }
}
