package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.QuickReviewSessionMetadataProjection;
import com.studysnap.backend.repository.QuickReviewSessionSummaryProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {
    private static final int SESSION_LOOKBACK_LIMIT = 10;
    private static final String SESSION_STATE_FOCUS_CONCEPTS = "focusConcepts";
    private static final String FOCUS_SOURCE_STUDY_PACK_ID_KEY = "sourceStudyPackId";
    private static final String FOCUS_CONCEPT_KEY = "concept";
    private static final int KNOWLEDGE_IMPACT_WINDOW_DAYS = 30;
    private static final String FIRST_NAME_PARAMETER = "firstName";
    private static final String DASHBOARD_URL_PARAMETER = "dashboardUrl";
    private static final String DASHBOARD_PATH = "/dashboard";
    private static final String QUICK_REVIEW_PATH_SUFFIX = "/quick-review?source=due-concepts-digest";
    private static final String DEFAULT_STUDY_PACK_TITLE = "your study pack";
    private static final String KNOWLEDGE_IMPACT_DIGEST_TEMPLATE = "knowledge-impact-digest";
    private static final String PUBLIC_CREATOR_PATH_PREFIX = "/public/creator/";
    private static final String PUBLIC_PROFILE_PATH_PREFIX = "/public/profile/";
    private static final String IMPACT_SECTION_FRAGMENT = "#your-impact-heading";
    private static final ZoneId EMAIL_BUDGET_ZONE = ZoneId.of("Asia/Manila");
    private static final List<QuickReviewSessionMode> WEEKLY_SUMMARY_QUIZ_MODES = List.of(
            QuickReviewSessionMode.QUICK_REVIEW,
            QuickReviewSessionMode.CHALLENGE
    );

    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final EmailUnsubscribeLinkService emailUnsubscribeLinkService;
    private final ConceptHealthService conceptHealthService;

    @Transactional(readOnly = true)
    public List<InactiveUserReminder> findInactiveUsers() {
        return findInactiveUsers(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public boolean isReturningAfterInactivity(UUID userId) {
        return isReturningAfterInactivity(userId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<WeakConceptReminder> findUsersWithWeakConcepts() {
        return findUsersWithWeakConcepts(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<WeeklySummaryReminder> findWeeklySummaryUsers() {
        return findWeeklySummaryUsers(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<DueConceptsDigestReminder> findDueConceptsDigestUsers() {
        return findDueConceptsDigestUsers(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeImpactDigestReminder> findKnowledgeImpactDigestUsers() {
        return findKnowledgeImpactDigestUsers(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public DailyRetentionDispatchSummary sendDailyEmails() {
        return sendDailyEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int sendInactiveUserEmails() {
        return dispatchInactivityEmails(findInactiveUsers(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int sendWeakConceptEmails() {
        return dispatchWeakConceptEmails(findUsersWithWeakConcepts(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public WeeklyRetentionDispatchSummary sendWeeklySummaryEmails() {
        return sendWeeklySummaryEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int sendDueConceptsDigestEmails() {
        return sendDueConceptsDigestEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int sendKnowledgeImpactDigestEmails() {
        return sendKnowledgeImpactDigestEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    List<InactiveUserReminder> findInactiveUsers(OffsetDateTime now) {
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> isReturningAfterInactivity(user.getId(), now))
                .filter(user -> cooldownElapsed(
                        user.getId(),
                        RetentionEmailType.INACTIVITY,
                        properties.getRetention().getInactivityCooldownDays(),
                        now
                ))
                .map(user -> new InactiveUserReminder(
                        user.getId(),
                        user.getEmail(),
                        resolveFirstName(user),
                        buildAbsoluteUrl(DASHBOARD_PATH)
                ))
                .toList();
    }

    boolean isReturningAfterInactivity(UUID userId, OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getInactivityDays());
        return hasRecordedStudyActivity(userId) && !hasRecentMeaningfulActivity(userId, cutoff);
    }

    List<WeakConceptReminder> findUsersWithWeakConcepts(OffsetDateTime now) {
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .map(user -> findWeakConceptReminderForUser(user, now))
                .flatMap(Optional::stream)
                .toList();
    }

    List<WeeklySummaryReminder> findWeeklySummaryUsers(OffsetDateTime now) {
        OffsetDateTime weekStart = now.minusDays(7);
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeeklySummaryRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> hasRecordedStudyActivity(user.getId()))
                .filter(user -> cooldownElapsed(
                        user.getId(),
                        RetentionEmailType.WEEKLY_SUMMARY,
                        properties.getRetention().getWeeklyCooldownDays(),
                        now
                ))
                .map(user -> buildWeeklySummaryReminder(user, weekStart, now))
                .toList();
    }

    List<DueConceptsDigestReminder> findDueConceptsDigestUsers(OffsetDateTime now) {
        DayOfWeek dispatchDay = now.atZoneSameInstant(EMAIL_BUDGET_ZONE).getDayOfWeek();
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> isEligibleReviewDay(user, dispatchDay))
                .map(user -> findDueConceptsDigestReminderForUser(user, now))
                .flatMap(Optional::stream)
                .toList();
    }

    List<KnowledgeImpactDigestReminder> findKnowledgeImpactDigestUsers(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusDays(KNOWLEDGE_IMPACT_WINDOW_DAYS);
        return userRepository
                .findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(UserStatus.ACTIVE)
                .stream()
                .filter(user -> cooldownElapsed(
                        user.getId(),
                        RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST,
                        properties.getRetention().getKnowledgeImpactDigestCooldownDays(),
                        now
                ))
                .map(user -> buildKnowledgeImpactDigestReminder(user, windowStart))
                .flatMap(Optional::stream)
                .toList();
    }

    DailyRetentionDispatchSummary sendDailyEmails(OffsetDateTime now) {
        InactivityDispatchResult inactivityDispatchResult = dispatchBudgetedInactivityEmails(now);
        int weakConceptSent = dispatchWeakConceptEmails(findUsersWithWeakConcepts(now), now);
        return new DailyRetentionDispatchSummary(
                inactivityDispatchResult.sent(),
                weakConceptSent,
                inactivityDispatchResult.budget(),
                inactivityDispatchResult.sentToday(),
                inactivityDispatchResult.attempted(),
                inactivityDispatchResult.skippedForBudget()
        );
    }

    WeeklyRetentionDispatchSummary sendWeeklySummaryEmails(OffsetDateTime now) {
        int weeklySummarySent = dispatchWeeklySummaryEmails(findWeeklySummaryUsers(now), now);
        return new WeeklyRetentionDispatchSummary(weeklySummarySent);
    }

    int sendDueConceptsDigestEmails(OffsetDateTime now) {
        return dispatchDueConceptsDigestEmails(findDueConceptsDigestUsers(now), now);
    }

    int sendKnowledgeImpactDigestEmails(OffsetDateTime now) {
        return dispatchKnowledgeImpactDigestEmails(findKnowledgeImpactDigestUsers(now), now);
    }

    private Optional<WeakConceptReminder> findWeakConceptReminderForUser(UserEntity user, OffsetDateTime now) {
        if (!cooldownElapsed(
                user.getId(),
                RetentionEmailType.WEAK_CONCEPT,
                properties.getRetention().getWeakConceptCooldownDays(),
                now
        )) {
            return Optional.empty();
        }

        QuickReviewSessionMetadataProjection latestChallenge = quickReviewSessionRepository
                .findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                        user.getId(),
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        if (latestChallenge == null || latestChallenge.completedAt() == null) {
            return Optional.empty();
        }

        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getWeakConceptInactivityDays());
        if (latestChallenge.completedAt().isAfter(cutoff)) {
            return Optional.empty();
        }

        LinkedHashSet<String> remainingWeakConcepts = new LinkedHashSet<>(
                extractWeakConcepts(latestChallenge.sessionMetadata())
        );
        if (remainingWeakConcepts.isEmpty()) {
            return Optional.empty();
        }

        quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndStudyPackIdAndSessionModeOrderByCompletedAtDesc(
                user.getId(),
                latestChallenge.studyPackId(),
                QuickReviewSessionMode.ADAPTIVE,
                PageRequest.of(0, SESSION_LOOKBACK_LIMIT)
        ).forEach(session -> {
            if (session.completedAt() == null || session.completedAt().isBefore(latestChallenge.completedAt())) {
                return;
            }
            extractWeakConcepts(session.sessionMetadata()).forEach(remainingWeakConcepts::remove);
        });

        // ⚠️ v0.113.0. The query above is keyed on study_pack_id, which a PLAN-SCOPED Adaptive session
        // does not have -- so without this a learner who cleared these concepts through
        // "Practice Across This Plan" would still be nagged about them.
        // ⚠️ Concepts are matched by their SOURCE-PACK STAMP, never by name alone: concept strings are
        // free text scoped per pack, and treating two packs' "Shear Force" as one concept would be the
        // cross-pack canonical identity claim that stays ADR-sized and out of scope.
        removeConceptsPractisedInPlanScopedSessions(
                user.getId(),
                latestChallenge.studyPackId(),
                latestChallenge.completedAt(),
                remainingWeakConcepts
        );

        if (remainingWeakConcepts.isEmpty()) {
            return Optional.empty();
        }

        String noteTitle = studyPackRepository.findById(latestChallenge.studyPackId())
                .map(StudyPackEntity::getTitle)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_STUDY_PACK_TITLE);

        return Optional.of(new WeakConceptReminder(
                user.getId(),
                user.getEmail(),
                resolveFirstName(user),
                latestChallenge.noteId(),
                noteTitle,
                List.copyOf(remainingWeakConcepts).stream().limit(3).toList(),
                buildAbsoluteUrl("/notes/" + latestChallenge.noteId() + "/adaptive-practice")
        ));
    }

    private WeeklySummaryReminder buildWeeklySummaryReminder(UserEntity user, OffsetDateTime weekStart, OffsetDateTime now) {
        int studyPacksCreated = (int) studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                user.getId(),
                weekStart,
                now
        );

        List<UserActivityEventEntity> weeklyEvents = activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                user.getId(),
                ActivityType.MEANINGFUL_STUDY_ACTIVITIES,
                weekStart
        ).stream()
                .filter(event -> event.getCreatedAt() != null && event.getCreatedAt().isBefore(now))
                .toList();

        int quizzesTaken = countEventsByType(weeklyEvents, ActivityType.COMPLETED_QUICK_REVIEW)
                + countEventsByType(weeklyEvents, ActivityType.COMPLETED_CHALLENGE_QUIZ);
        int adaptiveSessions = countEventsByType(weeklyEvents, ActivityType.STARTED_ADAPTIVE_PRACTICE);
        int averageQuizScore = calculateAverageQuizScore(user.getId(), weekStart, now);

        return new WeeklySummaryReminder(
                user.getId(),
                user.getEmail(),
                resolveFirstName(user),
                studyPacksCreated,
                quizzesTaken,
                adaptiveSessions,
                averageQuizScore,
                buildAbsoluteUrl(DASHBOARD_PATH)
        );
    }

    private Optional<DueConceptsDigestReminder> findDueConceptsDigestReminderForUser(UserEntity user, OffsetDateTime now) {
        if (!cooldownElapsed(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                properties.getRetention().getDueConceptsDigestCooldownDays(),
                now
        )) {
            return Optional.empty();
        }

        List<StudyPackEntity> studyPacks = studyPackRepository
                .findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged());
        Map<UUID, List<String>> conceptsByStudyPackId = new LinkedHashMap<>();
        Map<UUID, String> titlesByStudyPackId = new LinkedHashMap<>();
        for (StudyPackEntity studyPack : studyPacks) {
            if (studyPack.getId() != null && studyPack.getKeyConcepts() != null && !studyPack.getKeyConcepts().isEmpty()) {
                conceptsByStudyPackId.put(studyPack.getId(), studyPack.getKeyConcepts());
                titlesByStudyPackId.put(studyPack.getId(), resolveStudyPackTitle(studyPack));
            }
        }
        Map<UUID, List<String>> dueConceptsByStudyPackId = conceptHealthService.getDueConceptsByStudyPackIds(
                user.getId(),
                conceptsByStudyPackId,
                now
        );
        int dueConceptCount = dueConceptsByStudyPackId.values().stream().mapToInt(List::size).sum();
        if (dueConceptCount == 0) {
            return Optional.empty();
        }
        List<String> studyPackTitles = dueConceptsByStudyPackId.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                .map(entry -> titlesByStudyPackId.getOrDefault(entry.getKey(), DEFAULT_STUDY_PACK_TITLE))
                .limit(3)
                .toList();
        UUID targetStudyPackId = dueConceptsByStudyPackId.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        String actionPath = studyPacks.stream()
                .filter(studyPack -> Objects.equals(targetStudyPackId, studyPack.getId()))
                .map(StudyPackEntity::getNoteId)
                .filter(Objects::nonNull)
                .filter(noteId -> noteRepository.findByIdAndOwnerUserId(noteId, user.getId()).isPresent())
                .map(noteId -> "/notes/" + noteId + QUICK_REVIEW_PATH_SUFFIX)
                .findFirst()
                .orElse(DASHBOARD_PATH);
        return Optional.of(new DueConceptsDigestReminder(
                user.getId(), user.getEmail(), resolveFirstName(user), dueConceptCount, studyPackTitles,
                buildAbsoluteUrl(actionPath)
        ));
    }

    private boolean isEligibleReviewDay(UserEntity user, DayOfWeek dispatchDay) {
        String[] reviewDays = user.getReviewDays();
        if (reviewDays == null || reviewDays.length == 0) {
            return true;
        }
        return Arrays.stream(reviewDays).anyMatch(dispatchDay.name()::equals);
    }

    private Optional<KnowledgeImpactDigestReminder> buildKnowledgeImpactDigestReminder(
            UserEntity user,
            OffsetDateTime windowStart
    ) {
        long newLearnersCount = noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(
                user.getId(),
                windowStart
        );
        if (newLearnersCount == 0) {
            return Optional.empty();
        }
        return Optional.of(new KnowledgeImpactDigestReminder(
                user.getId(),
                user.getEmail(),
                resolveFirstName(user),
                newLearnersCount,
                buildPublicProfileImpactUrl(user)
        ));
    }

    private String resolveStudyPackTitle(StudyPackEntity studyPack) {
        return studyPack.getTitle() == null || studyPack.getTitle().isBlank()
                ? DEFAULT_STUDY_PACK_TITLE
                : studyPack.getTitle();
    }

    private int dispatchInactivityEmails(List<InactiveUserReminder> candidates, OffsetDateTime now) {
        int sent = 0;
        for (InactiveUserReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(),
                    candidate.email(),
                    RetentionEmailType.INACTIVITY,
                    "retention-inactivity-reminder",
                    Map.of(
                            FIRST_NAME_PARAMETER, candidate.firstName(),
                            "resumeUrl", candidate.resumeUrl()
                    ),
                    UnsubscribeCategory.STUDY_REMINDERS,
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private InactivityDispatchResult dispatchBudgetedInactivityEmails(OffsetDateTime now) {
        if (!properties.getEmail().isReengagementEnabled()) {
            log.info("retention.email.inactivity disabled");
            return new InactivityDispatchResult(0, countEmailsSentToday(now), 0, 0, 0);
        }

        long sentToday = countEmailsSentToday(now);
        int budget = resolveReengagementBudget(sentToday);
        List<InactiveUserReminder> candidates = findInactiveUsers(now);
        int attempted = Math.min(candidates.size(), budget);
        int skippedForBudget = Math.max(0, candidates.size() - attempted);
        int sent = dispatchInactivityEmails(candidates.stream().limit(attempted).toList(), now);
        log.info(
                "retention.email.inactivity.dispatch budget={} sentToday={} attempted={} sent={} skippedForBudget={}",
                budget,
                sentToday,
                attempted,
                sent,
                skippedForBudget
        );
        return new InactivityDispatchResult(budget, sentToday, attempted, sent, skippedForBudget);
    }

    private long countEmailsSentToday(OffsetDateTime now) {
        OffsetDateTime startOfDay = now.atZoneSameInstant(EMAIL_BUDGET_ZONE)
                .toLocalDate()
                .atStartOfDay(EMAIL_BUDGET_ZONE)
                .toOffsetDateTime();
        return emailLogRepository.countBySentAtGreaterThanEqual(startOfDay);
    }

    private int resolveReengagementBudget(long sentToday) {
        int dailyLimit = Math.max(0, properties.getEmail().getDailyLimit());
        int transactionalReserve = Math.max(0, properties.getEmail().getTransactionalReserve());
        long budget = (long) dailyLimit - transactionalReserve - sentToday;
        return (int) Math.clamp(budget, 0L, Integer.MAX_VALUE);
    }

    private int dispatchWeakConceptEmails(List<WeakConceptReminder> candidates, OffsetDateTime now) {
        int sent = 0;
        for (WeakConceptReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(),
                    candidate.email(),
                    RetentionEmailType.WEAK_CONCEPT,
                    "retention-weak-concept-reminder",
                    Map.of(
                            FIRST_NAME_PARAMETER, candidate.firstName(),
                            "weakConceptList", formatWeakConcepts(candidate.weakConcepts()),
                            "adaptivePracticeUrl", candidate.adaptivePracticeUrl()
                    ),
                    UnsubscribeCategory.WEAK_CONCEPT,
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private int dispatchWeeklySummaryEmails(List<WeeklySummaryReminder> candidates, OffsetDateTime now) {
        int sent = 0;
        for (WeeklySummaryReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(),
                    candidate.email(),
                    RetentionEmailType.WEEKLY_SUMMARY,
                    "retention-weekly-summary",
                    Map.of(
                            FIRST_NAME_PARAMETER, candidate.firstName(),
                            "studyPacksCreated", Integer.toString(candidate.studyPacksCreated()),
                            "quizzesTaken", Integer.toString(candidate.quizzesTaken()),
                            "adaptiveSessions", Integer.toString(candidate.adaptiveSessions()),
                            "averageQuizScore", Integer.toString(candidate.averageQuizScore()),
                            DASHBOARD_URL_PARAMETER, candidate.dashboardUrl()
                    ),
                    UnsubscribeCategory.WEEKLY_SUMMARY,
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private int dispatchDueConceptsDigestEmails(List<DueConceptsDigestReminder> candidates, OffsetDateTime now) {
        int sent = 0;
        for (DueConceptsDigestReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(), candidate.email(), RetentionEmailType.DUE_CONCEPTS_DIGEST,
                    "retention-due-concepts-digest",
                    Map.of(
                            FIRST_NAME_PARAMETER, candidate.firstName(),
                            "dueConceptCount", Integer.toString(candidate.dueConceptCount()),
                            "studyPackList", formatStudyPackTitles(candidate.studyPackTitles()),
                            DASHBOARD_URL_PARAMETER, candidate.dashboardUrl()
                    ),
                    UnsubscribeCategory.DUE_CONCEPTS_DIGEST,
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private int dispatchKnowledgeImpactDigestEmails(
            List<KnowledgeImpactDigestReminder> candidates,
            OffsetDateTime now
    ) {
        int sent = 0;
        for (KnowledgeImpactDigestReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(),
                    candidate.email(),
                    RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST,
                    KNOWLEDGE_IMPACT_DIGEST_TEMPLATE,
                    Map.of(
                            FIRST_NAME_PARAMETER, candidate.firstName(),
                            "newLearnersCount", Long.toString(candidate.newLearnersCount()),
                            "learnerLabel", candidate.newLearnersCount() == 1 ? "learner" : "learners",
                            "impactUrl", candidate.impactUrl()
                    ),
                    UnsubscribeCategory.KNOWLEDGE_IMPACT_DIGEST,
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private boolean sendRetentionEmail(
            UUID userId,
            String email,
            RetentionEmailType emailType,
            String templateName,
            Map<String, String> parameters,
            UnsubscribeCategory unsubscribeCategory,
            OffsetDateTime now
    ) {
        try {
            EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext unsubscribeContext = buildUnsubscribeContext(
                    userId,
                    emailType,
                    unsubscribeCategory
            );
            Map<String, String> templateParameters = new java.util.LinkedHashMap<>(parameters);
            templateParameters.put("unsubscribeUrl", unsubscribeContext.unsubscribeUrl());
            templateParameters.put("unsubscribeFooterHtml", unsubscribeContext.htmlFooter());
            templateParameters.put("unsubscribeFooterText", unsubscribeContext.textFooter());
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(templateName, templateParameters);
            boolean sent = emailService.sendEmail(new EmailMessage(
                    email,
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.textBody(),
                    unsubscribeContext.headers()
            ));
            if (!sent) {
                return false;
            }
            logEmailSent(userId, emailType, now);
            return true;
        } catch (RuntimeException ex) {
            log.warn("retention.email.send failed userId={} emailType={} message={}", userId, emailType, ex.getMessage());
            return false;
        }
    }

    private EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext buildUnsubscribeContext(
            UUID userId,
            RetentionEmailType emailType,
            UnsubscribeCategory unsubscribeCategory
    ) {
        try {
            return emailUnsubscribeLinkService.buildContext(userId, unsubscribeCategory);
        } catch (RuntimeException ex) {
            log.warn(
                    "retention.email.unsubscribe_link failed userId={} emailType={} message={}",
                    userId,
                    emailType,
                    ex.getMessage()
            );
            return EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext.empty();
        }
    }

    private void logEmailSent(UUID userId, RetentionEmailType emailType, OffsetDateTime now) {
        EmailLogEntity entity = new EmailLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setEmailType(emailType);
        entity.setSentAt(now);
        emailLogRepository.save(entity);
    }

    private boolean cooldownElapsed(UUID userId, RetentionEmailType emailType, int cooldownDays, OffsetDateTime now) {
        OffsetDateTime cooldownCutoff = now.minusDays(cooldownDays);
        return !emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(userId, emailType, cooldownCutoff);
    }

    private boolean hasRecordedStudyActivity(UUID userId) {
        return activityEventRepository.existsByUserIdAndActivityTypeIn(userId, ActivityType.MEANINGFUL_STUDY_ACTIVITIES);
    }

    private boolean hasRecentMeaningfulActivity(UUID userId, OffsetDateTime cutoff) {
        return activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                userId,
                ActivityType.MEANINGFUL_STUDY_ACTIVITIES,
                cutoff
        );
    }

    private int calculateAverageQuizScore(UUID userId, OffsetDateTime fromInclusive, OffsetDateTime toExclusive) {
        List<QuickReviewSessionSummaryProjection> completedQuizSessions = quickReviewSessionRepository
                .findCompletedSessionSummariesByUserIdAndSessionModeInAndCompletedAtBetweenOrderByCompletedAtDesc(
                        userId,
                        WEEKLY_SUMMARY_QUIZ_MODES,
                        fromInclusive,
                        toExclusive
                ).stream()
                .filter(session -> session.scorePercentage() != null)
                .toList();
        if (completedQuizSessions.isEmpty()) {
            return 0;
        }

        BigDecimal totalScore = completedQuizSessions.stream()
                .map(QuickReviewSessionSummaryProjection::scorePercentage)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalScore
                .divide(BigDecimal.valueOf(completedQuizSessions.size()), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int countEventsByType(Collection<UserActivityEventEntity> events, ActivityType activityType) {
        return (int) events.stream()
                .filter(event -> event.getActivityType() == activityType)
                .count();
    }

    private List<String> extractWeakConcepts(Map<String, Object> sessionMetadata) {
        if (sessionMetadata == null) {
            return List.of();
        }
        Object raw = sessionMetadata.get("weakConcepts");
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<String> concepts = new ArrayList<>(rawList.size());
        for (Object value : rawList) {
            if (!(value instanceof String concept)) {
                continue;
            }
            String normalized = concept.trim();
            if (!normalized.isBlank()) {
                concepts.add(normalized);
            }
        }
        return concepts;
    }

    private String formatWeakConcepts(List<String> weakConcepts) {
        return weakConcepts.stream()
                .map(concept -> "- " + concept)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatStudyPackTitles(List<String> studyPackTitles) {
        return studyPackTitles.stream().map(title -> "- " + title).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String resolveFirstName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return "there";
    }

    private String buildAbsoluteUrl(String path) {
        String baseUrl = properties.getEmail().getAppBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }

    private String buildPublicProfileImpactUrl(UserEntity user) {
        String path = user.getUsername() == null || user.getUsername().isBlank()
                ? PUBLIC_PROFILE_PATH_PREFIX + user.getId()
                : PUBLIC_CREATOR_PATH_PREFIX + user.getUsername();
        return buildAbsoluteUrl(path + IMPACT_SECTION_FRAGMENT);
    }

    public record InactiveUserReminder(
            UUID userId,
            String email,
            String firstName,
            String resumeUrl
    ) {
    }

    public record WeakConceptReminder(
            UUID userId,
            String email,
            String firstName,
            UUID noteId,
            String noteTitle,
            List<String> weakConcepts,
            String adaptivePracticeUrl
    ) {
    }

    public record WeeklySummaryReminder(
            UUID userId,
            String email,
            String firstName,
            int studyPacksCreated,
            int quizzesTaken,
            int adaptiveSessions,
            int averageQuizScore,
            String dashboardUrl
    ) {
    }

    public record DueConceptsDigestReminder(
            UUID userId,
            String email,
            String firstName,
            int dueConceptCount,
            List<String> studyPackTitles,
            String dashboardUrl
    ) {
    }

    public record KnowledgeImpactDigestReminder(
            UUID userId,
            String email,
            String firstName,
            long newLearnersCount,
            String impactUrl
    ) {
    }

    public record DailyRetentionDispatchSummary(
            int inactivitySent,
            int weakConceptSent,
            int inactivityBudget,
            long sentToday,
            int inactivityAttempted,
            int inactivitySkippedForBudget
    ) {
    }

    private record InactivityDispatchResult(
            int budget,
            long sentToday,
            int attempted,
            int sent,
            int skippedForBudget
    ) {
    }

    public record WeeklyRetentionDispatchSummary(
            int weeklySummarySent
    ) {
    }

    /**
     * Removes the weak concepts a learner already practised through a PLAN-SCOPED Adaptive session.
     *
     * <p>Those sessions carry no {@code study_pack_id}, so the pack-keyed lookup above cannot see them.
     * Each focus concept is stamped with the pack it came from, and only concepts stamped with
     * {@code studyPackId} are removed -- matching on the concept STRING alone would silently equate two
     * packs' identically-named concepts.
     */
    private void removeConceptsPractisedInPlanScopedSessions(
            UUID userId,
            UUID studyPackId,
            OffsetDateTime challengeCompletedAt,
            Set<String> remainingWeakConcepts
    ) {
        if (studyPackId == null || remainingWeakConcepts.isEmpty()) {
            return;
        }
        for (QuickReviewSessionEntity session : quickReviewSessionRepository
                .findByUserIdAndStatusAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionStatus.COMPLETED,
                        List.of(QuickReviewSessionMode.ADAPTIVE)
                )) {
            if (session.getSourceCollectionId() == null) {
                continue;
            }
            if (session.getCompletedAt() == null || session.getCompletedAt().isBefore(challengeCompletedAt)) {
                continue;
            }
            for (String concept : extractFocusConceptsForStudyPack(session, studyPackId)) {
                remainingWeakConcepts.remove(concept);
            }
        }
    }

    private List<String> extractFocusConceptsForStudyPack(QuickReviewSessionEntity session, UUID studyPackId) {
        Map<String, Object> state = session.getSessionState();
        if (state == null || !(state.get(SESSION_STATE_FOCUS_CONCEPTS) instanceof List<?> rawEntries)) {
            return List.of();
        }
        String targetStudyPackId = studyPackId.toString();
        List<String> concepts = new ArrayList<>();
        for (Object rawEntry : rawEntries) {
            if (!(rawEntry instanceof Map<?, ?> entry)) {
                continue;
            }
            if (!targetStudyPackId.equals(entry.get(FOCUS_SOURCE_STUDY_PACK_ID_KEY))
                    || !(entry.get(FOCUS_CONCEPT_KEY) instanceof String concept)
                    || concept.isBlank()) {
                continue;
            }
            concepts.add(concept);
        }
        return concepts;
    }

}
