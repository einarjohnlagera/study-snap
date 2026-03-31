package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
    private static final Set<ActivityType> MEANINGFUL_STUDY_ACTIVITIES = EnumSet.of(
            ActivityType.CREATED_STUDY_PACK,
            ActivityType.STARTED_QUICK_REVIEW,
            ActivityType.STARTED_ADAPTIVE_PRACTICE,
            ActivityType.COMPLETED_QUICK_REVIEW,
            ActivityType.COMPLETED_CHALLENGE_QUIZ,
            ActivityType.COMPLETED_ADAPTIVE_QUIZ
    );
    private static final List<QuickReviewSessionMode> WEEKLY_SUMMARY_QUIZ_MODES = List.of(
            QuickReviewSessionMode.QUICK_REVIEW,
            QuickReviewSessionMode.CHALLENGE
    );

    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<InactiveUserReminder> findInactiveUsers() {
        return findInactiveUsers(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<WeakConceptReminder> findUsersWithWeakConcepts() {
        return findUsersWithWeakConcepts(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<WeeklySummaryReminder> findWeeklySummaryUsers() {
        return findWeeklySummaryUsers(OffsetDateTime.now(ZoneOffset.UTC));
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

    List<InactiveUserReminder> findInactiveUsers(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getInactivityDays());
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> hasRecordedStudyActivity(user.getId()))
                .filter(user -> !hasRecentMeaningfulActivity(user.getId(), cutoff))
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
                        buildAbsoluteUrl("/dashboard")
                ))
                .toList();
    }

    List<WeakConceptReminder> findUsersWithWeakConcepts(OffsetDateTime now) {
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .map(user -> findWeakConceptReminderForUser(user, now))
                .flatMap(Optional::stream)
                .toList();
    }

    List<WeeklySummaryReminder> findWeeklySummaryUsers(OffsetDateTime now) {
        OffsetDateTime weekStart = now.minusDays(7);
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE).stream()
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

    DailyRetentionDispatchSummary sendDailyEmails(OffsetDateTime now) {
        int inactivitySent = dispatchInactivityEmails(findInactiveUsers(now), now);
        int weakConceptSent = dispatchWeakConceptEmails(findUsersWithWeakConcepts(now), now);
        return new DailyRetentionDispatchSummary(inactivitySent, weakConceptSent);
    }

    WeeklyRetentionDispatchSummary sendWeeklySummaryEmails(OffsetDateTime now) {
        int weeklySummarySent = dispatchWeeklySummaryEmails(findWeeklySummaryUsers(now), now);
        return new WeeklyRetentionDispatchSummary(weeklySummarySent);
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

        QuickReviewSessionEntity latestChallenge = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        user.getId(),
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        if (latestChallenge == null || latestChallenge.getCompletedAt() == null) {
            return Optional.empty();
        }

        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getWeakConceptInactivityDays());
        if (latestChallenge.getCompletedAt().isAfter(cutoff)) {
            return Optional.empty();
        }

        LinkedHashSet<String> remainingWeakConcepts = new LinkedHashSet<>(extractWeakConcepts(latestChallenge));
        if (remainingWeakConcepts.isEmpty()) {
            return Optional.empty();
        }

        quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                latestChallenge.getStudyPackId(),
                QuickReviewSessionMode.ADAPTIVE,
                PageRequest.of(0, SESSION_LOOKBACK_LIMIT)
        ).forEach(session -> {
            if (session.getCompletedAt() == null || session.getCompletedAt().isBefore(latestChallenge.getCompletedAt())) {
                return;
            }
            extractWeakConcepts(session).forEach(remainingWeakConcepts::remove);
        });

        if (remainingWeakConcepts.isEmpty()) {
            return Optional.empty();
        }

        String noteTitle = studyPackRepository.findById(latestChallenge.getStudyPackId())
                .map(StudyPackEntity::getTitle)
                .filter(value -> !value.isBlank())
                .orElse("your study pack");

        return Optional.of(new WeakConceptReminder(
                user.getId(),
                user.getEmail(),
                resolveFirstName(user),
                latestChallenge.getNoteId(),
                noteTitle,
                List.copyOf(remainingWeakConcepts).stream().limit(3).toList(),
                buildAbsoluteUrl("/notes/" + latestChallenge.getNoteId() + "/adaptive-practice")
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
                MEANINGFUL_STUDY_ACTIVITIES,
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
                buildAbsoluteUrl("/dashboard")
        );
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
                            "firstName", candidate.firstName(),
                            "resumeUrl", candidate.resumeUrl()
                    ),
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
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
                            "firstName", candidate.firstName(),
                            "weakConceptList", formatWeakConcepts(candidate.weakConcepts()),
                            "adaptivePracticeUrl", candidate.adaptivePracticeUrl()
                    ),
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
                            "firstName", candidate.firstName(),
                            "studyPacksCreated", Integer.toString(candidate.studyPacksCreated()),
                            "quizzesTaken", Integer.toString(candidate.quizzesTaken()),
                            "adaptiveSessions", Integer.toString(candidate.adaptiveSessions()),
                            "averageQuizScore", Integer.toString(candidate.averageQuizScore()),
                            "dashboardUrl", candidate.dashboardUrl()
                    ),
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
            OffsetDateTime now
    ) {
        try {
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(templateName, parameters);
            emailService.sendEmail(new EmailMessage(email, rendered.subject(), rendered.htmlBody(), rendered.textBody()));
            logEmailSent(userId, emailType, now);
            return true;
        } catch (RuntimeException ex) {
            log.warn("retention.email.send failed userId={} emailType={} message={}", userId, emailType, ex.getMessage());
            return false;
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
        return activityEventRepository.existsByUserIdAndActivityTypeIn(userId, MEANINGFUL_STUDY_ACTIVITIES);
    }

    private boolean hasRecentMeaningfulActivity(UUID userId, OffsetDateTime cutoff) {
        return activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                userId,
                MEANINGFUL_STUDY_ACTIVITIES,
                cutoff
        );
    }

    private int calculateAverageQuizScore(UUID userId, OffsetDateTime fromInclusive, OffsetDateTime toExclusive) {
        List<QuickReviewSessionEntity> completedQuizSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        WEEKLY_SUMMARY_QUIZ_MODES
                ).stream()
                .filter(session -> session.getCompletedAt() != null)
                .filter(session -> !session.getCompletedAt().isBefore(fromInclusive))
                .filter(session -> session.getCompletedAt().isBefore(toExclusive))
                .filter(session -> session.getScorePercentage() != null)
                .toList();
        if (completedQuizSessions.isEmpty()) {
            return 0;
        }

        BigDecimal totalScore = completedQuizSessions.stream()
                .map(QuickReviewSessionEntity::getScorePercentage)
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

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object raw = session.getSessionMetadata().get("weakConcepts");
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

    public record DailyRetentionDispatchSummary(
            int inactivitySent,
            int weakConceptSent
    ) {
    }

    public record WeeklyRetentionDispatchSummary(
            int weeklySummarySent
    ) {
    }
}
