package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private EmailLogRepository emailLogRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;

    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl("https://www.notelib.app");
        retentionService = new RetentionService(
                properties,
                userRepository,
                noteRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                emailLogRepository,
                emailTemplateService,
                emailService
        );
    }

    @Test
    void findInactiveUsers_returnsEligibleUsersAndRespectsCooldown() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity inactiveUser = verifiedUser();
        inactiveUser.setLastLoginAt(now.minusDays(8));

        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(inactiveUser));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                inactiveUser.getId(),
                RetentionEmailType.INACTIVITY,
                now.minusDays(7)
        )).thenReturn(false);

        List<RetentionService.InactiveUserReminder> candidates = retentionService.findInactiveUsers(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().dashboardUrl()).isEqualTo("https://www.notelib.app/dashboard");
    }

    @Test
    void findUsersWithWeakConcepts_returnsRemainingWeakTopicsWithoutAdaptiveFollowUp() {
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
                now.minusDays(1),
                Map.of("weakConcepts", List.of("Mitosis", "DNA replication"))
        );
        challengeSession.setStudyPackId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        challengeSession.setNoteId(UUID.fromString("00000000-0000-0000-0000-000000000202"));
        when(quickReviewSessionRepository.findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                QuickReviewSessionMode.CHALLENGE,
                Pageable.ofSize(1).withPage(0)
        )).thenReturn(List.of(challengeSession));

        QuickReviewSessionEntity adaptiveSession = completedSession(
                user.getId(),
                QuickReviewSessionMode.ADAPTIVE,
                now.minusHours(12),
                Map.of("weakConcepts", List.of("Mitosis"))
        );
        adaptiveSession.setStudyPackId(challengeSession.getStudyPackId());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                user.getId(),
                challengeSession.getStudyPackId(),
                QuickReviewSessionMode.ADAPTIVE,
                Pageable.ofSize(10).withPage(0)
        )).thenReturn(List.of(adaptiveSession));

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(challengeSession.getStudyPackId());
        studyPack.setTitle("Cell Biology");
        when(studyPackRepository.findById(challengeSession.getStudyPackId())).thenReturn(Optional.of(studyPack));

        List<RetentionService.WeakConceptReminder> candidates = retentionService.findUsersWithWeakConcepts(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().weakConcepts()).containsExactly("DNA replication");
    }

    @Test
    void findUsersWithUnfinishedNotes_returnsOldDraftNotes() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.UNFINISHED_NOTE,
                now.minusDays(3)
        )).thenReturn(false);

        NoteEntity note = new NoteEntity();
        note.setId(UUID.fromString("00000000-0000-0000-0000-000000000303"));
        note.setOwnerUserId(user.getId());
        note.setTitle("Stoichiometry");
        note.setStatus(NoteStatus.DRAFT);
        note.setCreatedAt(now.minusDays(4));
        note.setUpdatedAt(now.minusDays(1));
        when(noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(List.of(note));

        List<RetentionService.UnfinishedNoteReminder> candidates = retentionService.findUsersWithUnfinishedNotes(now);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().noteTitle()).isEqualTo("Stoichiometry");
        assertThat(candidates.getFirst().noteUrl()).isEqualTo("https://www.notelib.app/notes/" + note.getId());
    }

    @Test
    void sendDueEmails_logsSentEmails() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();
        user.setLastLoginAt(now.minusDays(8));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(any(UUID.class), any(RetentionEmailType.class), any(OffsetDateTime.class)))
                .thenReturn(false);
        when(noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(List.of());
        when(emailTemplateService.render(eq("retention-inactivity-reminder"), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                        "Continue your study where you left off",
                        "<p>Body</p>",
                        "Body"
                ));

        RetentionService.RetentionDispatchSummary summary = retentionService.sendDueEmails(now);

        assertThat(summary.inactivitySent()).isEqualTo(1);
        verify(emailService).sendEmail(any(EmailMessage.class));
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEmailType()).isEqualTo(RetentionEmailType.INACTIVITY);
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void sendDueEmails_doesNotLogWhenTemplateSendFails() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        UserEntity user = verifiedUser();
        user.setLastLoginAt(now.minusDays(8));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(any(UUID.class), any(RetentionEmailType.class), any(OffsetDateTime.class)))
                .thenReturn(false);
        when(noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(List.of());
        when(emailTemplateService.render(eq("retention-inactivity-reminder"), any()))
                .thenThrow(new RuntimeException("Template missing"));

        RetentionService.RetentionDispatchSummary summary = retentionService.sendDueEmails(now);

        assertThat(summary.inactivitySent()).isZero();
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
}
