package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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
    private NoteRepository noteRepository;
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
    @Mock
    private ConceptHealthService conceptHealthService;

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
        properties.getRetention().setKnowledgeImpactDigestCooldownDays(30);
        retentionService = new RetentionService(
                properties,
                userRepository,
                noteRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                emailLogRepository,
                emailTemplateService,
                emailService,
                emailUnsubscribeLinkService,
                conceptHealthService
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
        lenient().when(emailService.sendEmail(any(EmailMessage.class))).thenReturn(true);
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
    void isReturningAfterInactivity_usesTheSameMeaningfulActivityThresholdAsInactiveUsers() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UUID userId = UUID.randomUUID();
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(userId), anyCollection()))
                .thenReturn(true);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(userId),
                anyCollection(),
                eq(now.minusDays(3))
        )).thenReturn(false, true);

        assertThat(retentionService.isReturningAfterInactivity(userId, now)).isTrue();
        assertThat(retentionService.isReturningAfterInactivity(userId, now)).isFalse();
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
        when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                user.getId(),
                QuickReviewSessionMode.CHALLENGE,
                PageRequest.of(0, 1)
        )).thenReturn(List.of(toMetadataProjection(challengeSession)));

        QuickReviewSessionEntity adaptiveSession = completedSession(
                user.getId(),
                QuickReviewSessionMode.ADAPTIVE,
                now.minusDays(2),
                Map.of("weakConcepts", List.of("Mitosis"))
        );
        adaptiveSession.setStudyPackId(challengeSession.getStudyPackId());
        when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndStudyPackIdAndSessionModeOrderByCompletedAtDesc(
                user.getId(),
                challengeSession.getStudyPackId(),
                QuickReviewSessionMode.ADAPTIVE,
                PageRequest.of(0, 10)
        )).thenReturn(List.of(toMetadataProjection(adaptiveSession)));

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
    void findDueConceptsDigestUsers_sumsAcrossMultipleStudyPacksAndOmitsPacksWithNoDueConcepts() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                now.minusDays(7)
        )).thenReturn(false);

        UUID firstPackId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID secondPackId = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID thirdPackId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        StudyPackEntity firstPack = studyPackWithConcepts(firstPackId, "Cell Biology", List.of("Mitosis", "Meiosis"));
        StudyPackEntity secondPack = studyPackWithConcepts(secondPackId, "Anatomy", List.of("Skeletal System"));
        StudyPackEntity thirdPack = studyPackWithConcepts(thirdPackId, "Nothing Due", List.of("Homeostasis"));
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged()))
                .thenReturn(List.of(firstPack, secondPack, thirdPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(user.getId()), any(), eq(now)))
                .thenReturn(Map.of(
                        firstPackId, List.of("Mitosis", "Meiosis"),
                        secondPackId, List.of("Skeletal System"),
                        thirdPackId, List.of()
                ));

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(candidates).hasSize(1);
        RetentionService.DueConceptsDigestReminder candidate = candidates.getFirst();
        assertThat(candidate.dueConceptCount()).isEqualTo(3);
        assertThat(candidate.studyPackTitles()).containsExactly("Cell Biology", "Anatomy");
        assertThat(candidate.dashboardUrl()).isEqualTo("https://www.notelib.app/dashboard");
    }

    @Test
    void findDueConceptsDigestUsers_nullAndEmptyReviewDaysKeepExistingScheduleEligibility() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity nullScheduleUser = verifiedUser(UUID.randomUUID(), "null@example.com");
        UserEntity emptyScheduleUser = verifiedUser(UUID.randomUUID(), "empty@example.com");
        emptyScheduleUser.setReviewDays(new String[0]);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(nullScheduleUser, emptyScheduleUser));
        UUID packId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPackWithConcepts(packId, "Cell Biology", List.of("Mitosis"));
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(any(), eq(Pageable.unpaged())))
                .thenReturn(List.of(studyPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(any(), any(), eq(now)))
                .thenReturn(Map.of(packId, List.of("Mitosis")));

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(candidates).extracting(RetentionService.DueConceptsDigestReminder::userId)
                .containsExactly(nullScheduleUser.getId(), emptyScheduleUser.getId());
    }

    @Test
    void findDueConceptsDigestUsers_matchesReviewDayInAsiaManila() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-24T18:00:00Z");
        UserEntity chosenDayUser = verifiedUser(UUID.randomUUID(), "chosen@example.com");
        chosenDayUser.setReviewDays(new String[]{"WEDNESDAY"});
        UserEntity unchosenDayUser = verifiedUser(UUID.randomUUID(), "unchosen@example.com");
        unchosenDayUser.setReviewDays(new String[]{"TUESDAY"});
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(chosenDayUser, unchosenDayUser));
        UUID packId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPackWithConcepts(packId, "Cell Biology", List.of("Mitosis"));
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(chosenDayUser.getId(), Pageable.unpaged()))
                .thenReturn(List.of(studyPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(chosenDayUser.getId()), any(), eq(now)))
                .thenReturn(Map.of(packId, List.of("Mitosis")));

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(candidates).extracting(RetentionService.DueConceptsDigestReminder::userId)
                .containsExactly(chosenDayUser.getId());
        verify(studyPackRepository, never())
                .findByOwnerUserIdOrderByCreatedAtDescIdDesc(unchosenDayUser.getId(), Pageable.unpaged());
    }

    @Test
    void findDueConceptsDigestUsers_linksMostDueNoteToQuickReview() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        UUID smallerPackId = UUID.randomUUID();
        UUID targetPackId = UUID.randomUUID();
        UUID smallerNoteId = UUID.randomUUID();
        UUID targetNoteId = UUID.randomUUID();
        StudyPackEntity smallerPack = studyPackWithConcepts(smallerPackId, "Anatomy", List.of("Bones"));
        smallerPack.setNoteId(smallerNoteId);
        StudyPackEntity targetPack = studyPackWithConcepts(targetPackId, "Cell Biology", List.of("Mitosis", "Meiosis"));
        targetPack.setNoteId(targetNoteId);
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged()))
                .thenReturn(List.of(smallerPack, targetPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(user.getId()), any(), eq(now)))
                .thenReturn(Map.of(smallerPackId, List.of("Bones"), targetPackId, List.of("Mitosis", "Meiosis")));
        when(noteRepository.findByIdAndOwnerUserId(targetNoteId, user.getId())).thenReturn(Optional.of(new NoteEntity()));

        RetentionService.DueConceptsDigestReminder reminder = retentionService.findDueConceptsDigestUsers(now).getFirst();

        assertThat(reminder.dashboardUrl()).isEqualTo(
                "https://www.notelib.app/notes/" + targetNoteId + "/quick-review?source=due-concepts-digest"
        );
    }

    @Test
    void findDueConceptsDigestUsers_fallsBackToDashboardWhenTargetNoteCannotBeResolved() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        UUID packId = UUID.randomUUID();
        UUID deletedNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPackWithConcepts(packId, "Cell Biology", List.of("Mitosis"));
        studyPack.setNoteId(deletedNoteId);
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged()))
                .thenReturn(List.of(studyPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(user.getId()), any(), eq(now)))
                .thenReturn(Map.of(packId, List.of("Mitosis")));
        when(noteRepository.findByIdAndOwnerUserId(deletedNoteId, user.getId())).thenReturn(Optional.empty());

        RetentionService.DueConceptsDigestReminder reminder = retentionService.findDueConceptsDigestUsers(now).getFirst();

        assertThat(reminder.dashboardUrl()).isEqualTo("https://www.notelib.app/dashboard");
    }

    @Test
    void findDueConceptsDigestUsers_excludesUserWithZeroDueConcepts() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                now.minusDays(7)
        )).thenReturn(false);

        UUID packId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        StudyPackEntity studyPack = studyPackWithConcepts(packId, "Cell Biology", List.of("Mitosis"));
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged()))
                .thenReturn(List.of(studyPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(user.getId()), any(), eq(now)))
                .thenReturn(Map.of(packId, List.of()));

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findDueConceptsDigestUsers_excludesExistingUserWhosePersistedDigestPreferenceIsDisabled() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity existingUser = verifiedUser();
        existingUser.setDueConceptsDigestRemindersEnabled(false);

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(existingUser.getDueConceptsDigestRemindersEnabled()).isFalse();
        assertThat(candidates).isEmpty();
        verify(studyPackRepository, never()).findByOwnerUserIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void findDueConceptsDigestUsers_respectsCooldown() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                now.minusDays(7)
        )).thenReturn(true);

        List<RetentionService.DueConceptsDigestReminder> candidates = retentionService.findDueConceptsDigestUsers(now);

        assertThat(candidates).isEmpty();
        verify(studyPackRepository, never()).findByOwnerUserIdOrderByCreatedAtDescIdDesc(any(), any());
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
        when(emailLogRepository.countBySentAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(0L);
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
    void sendDailyEmails_exhaustsInactivityBudgetWhenSentTodayAlreadyUsesRetentionPool() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T10:00:00Z");
        UserEntity user = verifiedUser();

        when(emailLogRepository.countBySentAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(70L);
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
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.INACTIVITY,
                now.minusDays(3)
        )).thenReturn(false);

        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails(now);

        assertThat(summary.inactivitySent()).isZero();
        assertThat(summary.inactivityBudget()).isZero();
        assertThat(summary.sentToday()).isEqualTo(70L);
        assertThat(summary.inactivityAttempted()).isZero();
        assertThat(summary.inactivitySkippedForBudget()).isEqualTo(1);
        verify(emailTemplateService, never()).render(eq("retention-inactivity-reminder"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendDailyEmails_capsInactivityBatchWithConfiguredBudget() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl("https://www.notelib.app");
        properties.getEmail().setDailyLimit(80);
        properties.getEmail().setTransactionalReserve(25);
        RetentionService budgetedService = new RetentionService(
                properties,
                userRepository,
                noteRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                emailLogRepository,
                emailTemplateService,
                emailService,
                emailUnsubscribeLinkService,
                conceptHealthService
        );
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T10:00:00Z");
        UserEntity firstUser = verifiedUser(UUID.fromString("00000000-0000-0000-0000-000000000011"), "[email protected]");
        UserEntity secondUser = verifiedUser(UUID.fromString("00000000-0000-0000-0000-000000000012"), "[email protected]");
        UserEntity thirdUser = verifiedUser(UUID.fromString("00000000-0000-0000-0000-000000000013"), "[email protected]");

        when(emailLogRepository.countBySentAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(53L);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(firstUser, secondUser, thirdUser));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());
        for (UserEntity user : List.of(firstUser, secondUser, thirdUser)) {
            when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(user.getId()), anyCollection()))
                    .thenReturn(true);
            when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                    eq(user.getId()),
                    anyCollection(),
                    eq(now.minusDays(3))
            )).thenReturn(false);
            when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                    user.getId(),
                    RetentionEmailType.INACTIVITY,
                    now.minusDays(3)
            )).thenReturn(false);
        }
        when(emailTemplateService.render(eq("retention-inactivity-reminder"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("Subject", "<p>Body</p>", "Body"));

        RetentionService.DailyRetentionDispatchSummary summary = budgetedService.sendDailyEmails(now);

        assertThat(summary.inactivityBudget()).isEqualTo(2);
        assertThat(summary.inactivityAttempted()).isEqualTo(2);
        assertThat(summary.inactivitySent()).isEqualTo(2);
        assertThat(summary.inactivitySkippedForBudget()).isEqualTo(1);
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(EmailLogEntity::getUserId)
                .containsExactly(firstUser.getId(), secondUser.getId());
    }

    @Test
    void sendDailyEmails_withDefaultLimitAndTenSentTodaySendsAtMostFiftyInactivityReminders() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T10:00:00Z");
        List<UserEntity> users = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> verifiedUser(
                        UUID.fromString(String.format("00000000-0000-0000-0000-%012d", index)),
                        "student" + index + "@example.com"
                ))
                .toList();

        when(emailLogRepository.countBySentAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(10L);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(users);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());
        for (UserEntity user : users) {
            when(activityEventRepository.existsByUserIdAndActivityTypeIn(eq(user.getId()), anyCollection()))
                    .thenReturn(true);
            when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                    eq(user.getId()),
                    anyCollection(),
                    eq(now.minusDays(3))
            )).thenReturn(false);
            when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                    user.getId(),
                    RetentionEmailType.INACTIVITY,
                    now.minusDays(3)
            )).thenReturn(false);
        }
        when(emailTemplateService.render(eq("retention-inactivity-reminder"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("Subject", "<p>Body</p>", "Body"));

        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails(now);

        assertThat(summary.inactivityBudget()).isEqualTo(50);
        assertThat(summary.inactivityAttempted()).isEqualTo(50);
        assertThat(summary.inactivitySent()).isEqualTo(50);
        assertThat(summary.inactivitySkippedForBudget()).isEqualTo(1);
        verify(emailLogRepository, org.mockito.Mockito.times(50)).save(any(EmailLogEntity.class));
    }

    @Test
    void sendDailyEmails_killSwitchSkipsInactivityDispatch() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setReengagementEnabled(false);
        RetentionService disabledService = new RetentionService(
                properties,
                userRepository,
                noteRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                emailLogRepository,
                emailTemplateService,
                emailService,
                emailUnsubscribeLinkService,
                conceptHealthService
        );
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T10:00:00Z");

        when(emailLogRepository.countBySentAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(10L);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());

        RetentionService.DailyRetentionDispatchSummary summary = disabledService.sendDailyEmails(now);

        assertThat(summary.inactivitySent()).isZero();
        assertThat(summary.inactivityAttempted()).isZero();
        assertThat(summary.inactivitySkippedForBudget()).isZero();
        verify(userRepository, never()).findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE);
        verify(emailService, never()).sendEmail(any(EmailMessage.class));
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
        when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeInAndCompletedAtBetweenOrderByCompletedAtDesc(
                user.getId(),
                List.of(QuickReviewSessionMode.QUICK_REVIEW, QuickReviewSessionMode.CHALLENGE),
                weekStart,
                now
        )).thenReturn(List.of(
                toSummaryProjection(completedScoredSession(user.getId(), QuickReviewSessionMode.QUICK_REVIEW, now.minusDays(5), "80")),
                toSummaryProjection(completedScoredSession(user.getId(), QuickReviewSessionMode.CHALLENGE, now.minusDays(4), "90"))
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

    @Test
    void sendDueConceptsDigestEmails_logsSentEmailWithDueConceptCount() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-29T10:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                now.minusDays(7)
        )).thenReturn(false);

        UUID packId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        StudyPackEntity studyPack = studyPackWithConcepts(packId, "Cell Biology", List.of("Mitosis", "Meiosis"));
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId(), Pageable.unpaged()))
                .thenReturn(List.of(studyPack));
        when(conceptHealthService.getDueConceptsByStudyPackIds(eq(user.getId()), any(), eq(now)))
                .thenReturn(Map.of(packId, List.of("Mitosis", "Meiosis")));
        when(emailTemplateService.render(eq("retention-due-concepts-digest"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                        "2 concepts are due for review",
                        "<p>Body</p>",
                        "Body"
                ));

        int sent = retentionService.sendDueConceptsDigestEmails(now);

        assertThat(sent).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(eq("retention-due-concepts-digest"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsEntry("dueConceptCount", "2");
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEmailType()).isEqualTo(RetentionEmailType.DUE_CONCEPTS_DIGEST);
    }

    @Test
    void sendDueConceptsDigestEmails_doesNotSendWhenSkippedByCooldown() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-29T10:00:00Z");
        UserEntity user = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.DUE_CONCEPTS_DIGEST,
                now.minusDays(7)
        )).thenReturn(true);

        int sent = retentionService.sendDueConceptsDigestEmails(now);

        assertThat(sent).isZero();
        verify(emailTemplateService, never()).render(eq("retention-due-concepts-digest"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void findKnowledgeImpactDigestUsers_excludesCreatorWithZeroNewLearners() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-29T01:00:00Z");
        UserEntity creator = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(
                UserStatus.ACTIVE
        )).thenReturn(List.of(creator));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                creator.getId(),
                RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST,
                now.minusDays(30)
        )).thenReturn(false);
        when(noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(
                creator.getId(),
                now.minusDays(30)
        )).thenReturn(0L);

        List<RetentionService.KnowledgeImpactDigestReminder> candidates =
                retentionService.findKnowledgeImpactDigestUsers(now);

        assertThat(candidates).isEmpty();
    }

    @Test
    void findKnowledgeImpactDigestUsers_includesOptedInCreatorWithNewLearners() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-29T01:00:00Z");
        UserEntity creator = verifiedUser();
        creator.setUsername("note-creator");

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(
                UserStatus.ACTIVE
        )).thenReturn(List.of(creator));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                creator.getId(),
                RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST,
                now.minusDays(30)
        )).thenReturn(false);
        when(noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(
                creator.getId(),
                now.minusDays(30)
        )).thenReturn(2L);

        List<RetentionService.KnowledgeImpactDigestReminder> candidates =
                retentionService.findKnowledgeImpactDigestUsers(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().newLearnersCount()).isEqualTo(2);
        assertThat(candidates.getFirst().impactUrl())
                .isEqualTo("https://www.notelib.app/public/creator/note-creator#your-impact-heading");
    }

    @Test
    void findKnowledgeImpactDigestUsers_excludesCreatorWithinCooldown() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-29T01:00:00Z");
        UserEntity creator = verifiedUser();

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(
                UserStatus.ACTIVE
        )).thenReturn(List.of(creator));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                creator.getId(),
                RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST,
                now.minusDays(30)
        )).thenReturn(true);

        List<RetentionService.KnowledgeImpactDigestReminder> candidates =
                retentionService.findKnowledgeImpactDigestUsers(now);

        assertThat(candidates).isEmpty();
        verify(noteRepository, never()).countDistinctLearnersHelpedByCreatorUserIdSince(any(), any());
    }

    @Test
    void sendKnowledgeImpactDigestEmails_continuesAfterOneCandidateFails() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-29T01:00:00Z");
        UserEntity firstCreator = verifiedUser(
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                "first@example.com"
        );
        UserEntity secondCreator = verifiedUser(
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                "second@example.com"
        );
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(
                UserStatus.ACTIVE
        )).thenReturn(List.of(firstCreator, secondCreator));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                any(UUID.class),
                eq(RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST),
                eq(now.minusDays(30))
        )).thenReturn(false);
        when(noteRepository.countDistinctLearnersHelpedByCreatorUserIdSince(
                any(UUID.class),
                eq(now.minusDays(30))
        )).thenReturn(1L);
        when(emailTemplateService.render(eq("knowledge-impact-digest"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("Subject", "<p>Body</p>", "Body"));
        when(emailService.sendEmail(any(EmailMessage.class)))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(true);

        int sent = retentionService.sendKnowledgeImpactDigestEmails(now);

        assertThat(sent).isEqualTo(1);
        verify(emailTemplateService, org.mockito.Mockito.times(2)).render(eq("knowledge-impact-digest"), any());
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(secondCreator.getId());
        assertThat(logCaptor.getValue().getEmailType()).isEqualTo(RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST);
    }

    private StudyPackEntity studyPackWithConcepts(UUID id, String title, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(id);
        studyPack.setTitle(title);
        studyPack.setKeyConcepts(keyConcepts);
        return studyPack;
    }

    private UserEntity verifiedUser() {
        return verifiedUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "[email protected]");
    }

    private UserEntity verifiedUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(20));
        user.setInactivityRemindersEnabled(true);
        user.setWeakConceptRemindersEnabled(true);
        user.setWeeklySummaryRemindersEnabled(true);
        user.setDueConceptsDigestRemindersEnabled(true);
        user.setKnowledgeImpactDigestRemindersEnabled(true);
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

    private QuickReviewSessionSummaryProjection toSummaryProjection(QuickReviewSessionEntity session) {
        return new QuickReviewSessionSummaryProjection(
                session.getId(),
                session.getUserId(),
                session.getStudyPackId(),
                session.getNoteId(),
                session.getSessionMode(),
                null,
                null,
                null,
                session.getScorePercentage(),
                null,
                null,
                null,
                session.getCompletedAt()
        );
    }

    private QuickReviewSessionMetadataProjection toMetadataProjection(QuickReviewSessionEntity session) {
        return new QuickReviewSessionMetadataProjection(
                session.getId(),
                session.getUserId(),
                session.getStudyPackId(),
                session.getNoteId(),
                session.getSessionMode(),
                null,
                null,
                null,
                session.getScorePercentage(),
                null,
                null,
                session.getSessionMetadata(),
                null,
                session.getCompletedAt()
        );
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
