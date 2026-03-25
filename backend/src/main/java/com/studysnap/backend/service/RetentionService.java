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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {
    private static final int SESSION_LOOKBACK_LIMIT = 10;

    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
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
    public List<UnfinishedNoteReminder> findUsersWithUnfinishedNotes() {
        return findUsersWithUnfinishedNotes(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public RetentionDispatchSummary sendDueEmails() {
        return sendDueEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    List<InactiveUserReminder> findInactiveUsers(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getInactivityDays());
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> user.getLastLoginAt() != null && !user.getLastLoginAt().isAfter(cutoff))
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

    List<UnfinishedNoteReminder> findUsersWithUnfinishedNotes(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(properties.getRetention().getUnfinishedNoteDays());
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus.ACTIVE).stream()
                .map(user -> findUnfinishedNoteReminderForUser(user, cutoff, now))
                .flatMap(Optional::stream)
                .toList();
    }

    RetentionDispatchSummary sendDueEmails(OffsetDateTime now) {
        int inactivitySent = dispatchInactivityEmails(findInactiveUsers(now), now);
        int weakConceptSent = dispatchWeakConceptEmails(findUsersWithWeakConcepts(now), now);
        int unfinishedNoteSent = dispatchUnfinishedNoteEmails(findUsersWithUnfinishedNotes(now), now);
        return new RetentionDispatchSummary(inactivitySent, weakConceptSent, unfinishedNoteSent);
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
            remainingWeakConcepts.removeAll(extractWeakConcepts(session));
        });

        if (remainingWeakConcepts.isEmpty()) {
            return Optional.empty();
        }

        String noteTitle = studyPackRepository.findById(latestChallenge.getStudyPackId())
                .map(StudyPackEntity::getTitle)
                .filter(value -> value != null && !value.isBlank())
                .orElse("your study pack");

        return Optional.of(new WeakConceptReminder(
                user.getId(),
                user.getEmail(),
                resolveFirstName(user),
                latestChallenge.getNoteId(),
                noteTitle,
                List.copyOf(remainingWeakConcepts),
                buildAbsoluteUrl("/notes/" + latestChallenge.getNoteId() + "/adaptive-practice")
        ));
    }

    private Optional<UnfinishedNoteReminder> findUnfinishedNoteReminderForUser(
            UserEntity user,
            OffsetDateTime cutoff,
            OffsetDateTime now
    ) {
        if (!cooldownElapsed(
                user.getId(),
                RetentionEmailType.UNFINISHED_NOTE,
                properties.getRetention().getUnfinishedNoteCooldownDays(),
                now
        )) {
            return Optional.empty();
        }

        return noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(note -> note.getStatus() == NoteStatus.DRAFT)
                .filter(note -> !note.getCreatedAt().isAfter(cutoff))
                .findFirst()
                .map(note -> new UnfinishedNoteReminder(
                        user.getId(),
                        user.getEmail(),
                        resolveFirstName(user),
                        note.getId(),
                        resolveNoteTitle(note),
                        buildAbsoluteUrl("/notes/" + note.getId())
                ));
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
                            "name", candidate.firstName(),
                            "dashboardUrl", candidate.dashboardUrl()
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
                            "name", candidate.firstName(),
                            "noteTitle", candidate.noteTitle(),
                            "weakConcepts", String.join(", ", candidate.weakConcepts()),
                            "adaptivePracticeUrl", candidate.adaptivePracticeUrl()
                    ),
                    now
            )) {
                sent += 1;
            }
        }
        return sent;
    }

    private int dispatchUnfinishedNoteEmails(List<UnfinishedNoteReminder> candidates, OffsetDateTime now) {
        int sent = 0;
        for (UnfinishedNoteReminder candidate : candidates) {
            if (sendRetentionEmail(
                    candidate.userId(),
                    candidate.email(),
                    RetentionEmailType.UNFINISHED_NOTE,
                    "retention-unfinished-note-reminder",
                    Map.of(
                            "name", candidate.firstName(),
                            "noteTitle", candidate.noteTitle(),
                            "noteUrl", candidate.noteUrl()
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

    private String resolveFirstName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return "there";
    }

    private String resolveNoteTitle(NoteEntity note) {
        if (note.getTitle() != null && !note.getTitle().isBlank()) {
            return note.getTitle().trim();
        }
        return "your note";
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
            String dashboardUrl
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

    public record UnfinishedNoteReminder(
            UUID userId,
            String email,
            String firstName,
            UUID noteId,
            String noteTitle,
            String noteUrl
    ) {
    }

    public record RetentionDispatchSummary(
            int inactivitySent,
            int weakConceptSent,
            int unfinishedNoteSent
    ) {
    }
}
