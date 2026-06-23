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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailUnsubscribeLinkService emailUnsubscribeLinkService;

    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl("https://www.notelib.app");
        properties.getRetention().setInactivityDays(3);
        properties.getRetention().setInactivityCooldownDays(3);
        properties.getRetention().setWeakConceptInactivityDays(3);
        properties.getRetention().setWeakConceptCooldownDays(5);
        properties.getRetention().setWeeklyCooldownDays(7);
        retentionService = new RetentionService(
                properties,
                userRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                emailLogRepository,
                emailTemplateService,
                emailService,
                emailUnsubscribeLinkService
        );
        lenient().when(emailUnsubscribeLinkService.buildContext(any(UUID.class), any(UnsubscribeCategory.class)))
                .thenReturn(new EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext(
                        "https://www.notelib.app/unsubscribe?token=test-token",
                        "<p>unsubscribe</p>",
                        "unsubscribe",
                        Map.of(
                                "List-Unsubscribe", "<https://www.notelib.app/api/email/unsubscribe?token=test-token>, <mailto:support@mail.notelib.app?subject=unsubscribe>",
                                "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"
                        )
                ));
    }

    @Test
    void findInactiveUsers_returnsEligibleUsersBasedOnMeaningfulActivity() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity inactiveUser = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(inactiveUser));
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(inactiveUser.getId()), anyCollection()))
                .thenReturn(true);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(inactiveUser.getId()),
                anyCollection(),
                eq(now.minusDays(3))
        )).thenReturn(false);
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                inactiveUser.getId(),
                RetentionEmailType.INACTIVITY,
                now.minusDays(3)
        )).thenReturn(false);

        List<RetentionService.InactiveUserReminder> candidates = retentionService.findInactiveUsers(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().resumeUrl()).isEqualTo("https://www.notelib.app/dashboard");
    }

    @Test
    void findUsersWithWeakConcepts_returnsRemainingTopicsAfterAdaptiveFollowUp() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.WEAK_CONCEPT,
                now.minusDays(5)
        )).thenReturn(false);

        QuickReviewSessionEntity challengeSession = completedSession(
                user.getId(),
                QuickReviewSessionMode.CHALLENGE,
                now.minusDays(4),
                Map.of("weakConcepts", List.of("Mitosis", "DNA replication"))
        );
        challengeSession.setStudyPackId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        challengeSession.setNoteId(UUID.fromString("00000000-0000-0000-0000-000000000202"));
        when(quickReviewSessionRepository.findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                QuickReviewSessionMode.CHALLENGE,
                PageRequest.of(0, 1)
        )).thenReturn(List.of(challengeSession));

        QuickReviewSessionEntity adaptiveSession = completedSession(
                user.getId(),
                QuickReviewSessionMode.ADAPTIVE,
                now.minusDays(2),
                Map.of("weakConcepts", List.of("Mitosis"))
        );
        adaptiveSession.setStudyPackId(challengeSession.getStudyPackId());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                challengeSession.getStudyPackId(),
                QuickReviewSessionMode.ADAPTIVE,
                PageRequest.of(0, 10)
        )).thenReturn(List.of(adaptiveSession));

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(challengeSession.getStudyPackId());
        studyPack.setTitle("Cell Biology");
        when(studyPackRepository.findById(challengeSession.getStudyPackId())).thenReturn(Optional.of(studyPack));

        List<RetentionService.WeakConceptReminder> candidates = retentionService.findUsersWithWeakConcepts(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().weakConcepts()).containsExactly("DNA replication");
        assertThat(candidates.getFirst().adaptivePracticeUrl())
                .isEqualTo("https://www.notelib.app/notes/" + challengeSession.getNoteId() + "/adaptive-practice");
    }

    @Test
    void sendDailyEmails_logsSentInactiveEmails() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(user.getId()), anyCollection()))
                .thenReturn(true);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(user.getId()),
                anyCollection(),
                eq(now.minusDays(3))
        )).thenReturn(false);
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(any(UUID.class), any(RetentionEmailType.class), any(OffsetDateTime.class)))
                .thenReturn(false);
        when(emailTemplateService.render(eq("retention-inactivity-reminder"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                        "Continue your study pack 📚",
                        "<p>Body</p>",
                        "Body"
                ));

        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails(now);

        assertThat(summary.inactivitySent()).isEqualTo(1);
        assertThat(summary.weakConceptSent()).isZero();
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).sendEmail(emailCaptor.capture());
        assertThat(emailCaptor.getValue().headers())
                .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
                .containsKey("List-Unsubscribe");
        assertThat(emailCaptor.getValue().headers().get("List-Unsubscribe"))
                .contains("/api/email/unsubscribe?token=test-token");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(eq("retention-inactivity-reminder"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue())
                .containsEntry("unsubscribeUrl", "https://www.notelib.app/unsubscribe?token=test-token")
                .containsEntry("unsubscribeFooterHtml", "<p>unsubscribe</p>")
                .containsEntry("unsubscribeFooterText", "unsubscribe");
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEmailType()).isEqualTo(RetentionEmailType.INACTIVITY);
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void findWeeklySummaryUsers_buildsWeeklyMetrics() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-29T10:00:00Z");
        OffsetDateTime weekStart = now.minusDays(7);
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeeklySummaryRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(user.getId()), anyCollection()))
                .thenReturn(true);
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.WEEKLY_SUMMARY,
                now.minusDays(7)
        )).thenReturn(false);
        when(studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                user.getId(),
                weekStart,
                now
        )).thenReturn(2L);
        when(activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(user.getId()),
                anyCollection(),
                eq(weekStart)
        )).thenReturn(List.of(
                activityEvent(user.getId(), ActivityType.CREATED_STUDY_PACK, now.minusDays(6)),
                activityEvent(user.getId(), ActivityType.COMPLETED_QUICK_REVIEW, now.minusDays(5)),
                activityEvent(user.getId(), ActivityType.COMPLETED_CHALLENGE_QUIZ, now.minusDays(4)),
                activityEvent(user.getId(), ActivityType.STARTED_ADAPTIVE_PRACTICE, now.minusDays(3))
        ));
        when(quickReviewSessionRepository.findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                List.of(QuickReviewSessionMode.QUICK_REVIEW, QuickReviewSessionMode.CHALLENGE)
        )).thenReturn(List.of(
                completedScoredSession(user.getId(), QuickReviewSessionMode.QUICK_REVIEW, now.minusDays(5), "80"),
                completedScoredSession(user.getId(), QuickReviewSessionMode.CHALLENGE, now.minusDays(4), "90")
        ));

        List<RetentionService.WeeklySummaryReminder> candidates = retentionService.findWeeklySummaryUsers(now);

        assertThat(candidates).hasSize(1);
        RetentionService.WeeklySummaryReminder weeklySummary = candidates.getFirst();
        assertThat(weeklySummary.studyPacksCreated()).isEqualTo(2);
        assertThat(weeklySummary.quizzesTaken()).isEqualTo(2);
        assertThat(weeklySummary.adaptiveSessions()).isEqualTo(1);
        assertThat(weeklySummary.averageQuizScore()).isEqualTo(85);
        assertThat(weeklySummary.dashboardUrl()).isEqualTo("https://www.notelib.app/dashboard");
    }

    @Test
    void findWeeklySummaryUsers_excludesUsersWhoHaveNotOptedIn() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-29T10:00:00Z");

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeeklySummaryRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());

        List<RetentionService.WeeklySummaryReminder> candidates = retentionService.findWeeklySummaryUsers(now);

        assertThat(candidates).isEmpty();
        verify(activityEventRepository, never()).existsByUserIdAndActivityTypeIn(any(UUID.class), anyCollection());
    }

    @Test
    void sendWeeklySummaryEmails_respectsCooldownAndDoesNotLogWhenSkipped() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-29T10:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeeklySummaryRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(user.getId()), anyCollection()))
                .thenReturn(true);
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.WEEKLY_SUMMARY,
                now.minusDays(7)
        )).thenReturn(true);

        RetentionService.WeeklyRetentionDispatchSummary summary = retentionService.sendWeeklySummaryEmails(now);

        assertThat(summary.weeklySummarySent()).isZero();
        verify(emailTemplateService, never()).render(eq("retention-weekly-summary"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    private UserEntity verifiedUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        user.setEmail("[email protected]");
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(20));
        user.setInactivityRemindersEnabled(true);
        user.setWeakConceptRemindersEnabled(true);
        user.setWeeklySummaryRemindersEnabled(true);
        return user;
    }

    private QuickReviewSessionEntity completedSession(
            UUID userId,
            QuickReviewSessionMode mode,
            OffsetDateTime completedAt,
            Map<String, Object> metadata
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setSessionMode(mode);
        session.setCompletedAt(completedAt);
        session.setSessionMetadata(metadata);
        return session;
    }

    private QuickReviewSessionEntity completedScoredSession(
            UUID userId,
            QuickReviewSessionMode mode,
            OffsetDateTime completedAt,
            String scorePercentage
    ) {
        QuickReviewSessionEntity session = completedSession(userId, mode, completedAt, Map.of());
        session.setScorePercentage(new BigDecimal(scorePercentage));
        return session;
    }

    private UserActivityEventEntity activityEvent(UUID userId, ActivityType activityType, OffsetDateTime createdAt) {
        UserActivityEventEntity event = new UserActivityEventEntity();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setActivityType(activityType);
        event.setCreatedAt(createdAt);
        return event;
    }
}
