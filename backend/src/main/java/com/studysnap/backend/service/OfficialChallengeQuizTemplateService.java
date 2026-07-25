package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminSeedOfficialChallengeQuizTemplatesResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Seeds one read-only Challenge Quiz template for Official content and copies it into an adopter's
 * ordinary per-user bank when their own bank cannot satisfy a normal Challenge request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialChallengeQuizTemplateService {
    private static final String TEMPLATE_DIFFICULTY = "medium";
    private static final String OUTCOME_UNANSWERED = "UNANSWERED";

    private final ChallengeQuizQuestionBankRepository questionBankRepository;
    private final ChallengeQuizQuestionBankService questionBankService;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final QuizGenerationService quizGenerationService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final TransactionOperations studyPackGenerationTransactionOperations;
    @Qualifier("llmParallelTaskExecutor")
    private final AsyncTaskExecutor llmParallelTaskExecutor;

    /**
     * Queues seeding only for a currently public Study Pack owned by an Official author. The task
     * is dispatched after the caller's surrounding write transaction commits.
     */
    public void queueSeedIfEligible(NoteEntity note, StudyPackEntity studyPack) {
        try {
            if (!isEligibleOfficialTemplate(note, studyPack)
                    || questionBankRepository.existsByUserIdAndStudyPackId(note.getOwnerUserId(), studyPack.getId())) {
                return;
            }
            dispatchSeedAfterCommit(note.getId(), studyPack.getId());
        } catch (RuntimeException exception) {
            log.warn("Official Challenge template queue failed for noteId={}, studyPackId={}",
                    note == null ? null : note.getId(), studyPack == null ? null : studyPack.getId(), exception);
        }
    }

    public AdminSeedOfficialChallengeQuizTemplatesResponse queueBackfill() {
        int queued = 0;
        int skipped = 0;
        int rejected = 0;
        List<NoteEntity> publicNotes = noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);
        if (publicNotes.isEmpty()) {
            return new AdminSeedOfficialChallengeQuizTemplatesResponse(queued, skipped, rejected);
        }

        java.util.Map<UUID, StudyPackEntity> packsByNoteId = studyPackRepository.findByNoteIdIn(
                publicNotes.stream().map(NoteEntity::getId).toList()
        ).stream().collect(java.util.stream.Collectors.toMap(StudyPackEntity::getNoteId, studyPack -> studyPack));
        for (NoteEntity note : publicNotes) {
            StudyPackEntity studyPack = packsByNoteId.get(note.getId());
            if (!isEligibleOfficialTemplate(note, studyPack)
                    || questionBankRepository.existsByUserIdAndStudyPackId(note.getOwnerUserId(), studyPack.getId())) {
                skipped++;
                continue;
            }
            if (dispatchSeedAfterCommit(note.getId(), studyPack.getId())) {
                queued++;
            } else {
                rejected++;
            }
        }
        return new AdminSeedOfficialChallengeQuizTemplatesResponse(queued, skipped, rejected);
    }

    /**
     * Resolves exactly one adopted-note hop. A copied community note never inherits a template by
     * chasing its own source further upstream.
     */
    public Optional<OfficialTemplateKey> resolveTemplate(UUID callerStudyPackId) {
        try {
            StudyPackEntity callerStudyPack = studyPackRepository.findById(callerStudyPackId).orElse(null);
            if (callerStudyPack == null || callerStudyPack.getNoteId() == null) {
                return Optional.empty();
            }
            NoteEntity callerNote = noteRepository.findById(callerStudyPack.getNoteId()).orElse(null);
            if (callerNote == null || callerNote.getCopiedFromNoteId() == null) {
                return Optional.empty();
            }
            NoteEntity sourceNote = noteRepository.findById(callerNote.getCopiedFromNoteId()).orElse(null);
            if (sourceNote == null || sourceNote.getVisibility() != NoteVisibility.PUBLIC
                    || !isOfficialAuthor(sourceNote.getOwnerUserId())) {
                return Optional.empty();
            }
            StudyPackEntity sourceStudyPack = studyPackRepository.findByNoteId(sourceNote.getId()).orElse(null);
            if (sourceStudyPack == null || !sourceNote.getOwnerUserId().equals(sourceStudyPack.getOwnerUserId())) {
                return Optional.empty();
            }
            return Optional.of(new OfficialTemplateKey(sourceNote.getOwnerUserId(), sourceStudyPack.getId()));
        } catch (RuntimeException exception) {
            log.warn("Official Challenge template resolution failed for studyPackId={}", callerStudyPackId, exception);
            return Optional.empty();
        }
    }

    /**
     * Copies template rows into the caller's own bank. Template reads deliberately ignore the
     * Official author's learner level; copied rows are tagged with the caller's current level.
     */
    public List<QuizItem> copyTemplateQuestions(
            UUID userId,
            UUID callerStudyPackId,
            LearnerLevel learnerLevel,
            UUID sessionId,
            Set<String> disallowedQuestionKeys,
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }
        try {
            Optional<OfficialTemplateKey> template = resolveTemplate(callerStudyPackId);
            if (template.isEmpty()) {
                return List.of();
            }
            Set<String> excluded = disallowedQuestionKeys == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(disallowedQuestionKeys);
            excluded.addAll(questionBankRepository.findQuestionKeysByUserIdAndStudyPackId(userId, callerStudyPackId));
            List<ChallengeQuizQuestionBankEntity> sourceRows = questionBankRepository
                    .findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(
                            template.get().ownerUserId(),
                            template.get().studyPackId()
                    );
            List<ChallengeQuizQuestionBankEntity> copies = new ArrayList<>(count);
            List<QuizItem> copiedQuestions = new ArrayList<>(count);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            for (ChallengeQuizQuestionBankEntity source : sourceRows) {
                String questionKey = source.getQuestionKey();
                if (questionKey == null || questionKey.isBlank() || !excluded.add(questionKey)) {
                    continue;
                }
                ChallengeQuizQuestionBankEntity copy = new ChallengeQuizQuestionBankEntity();
                copy.setId(UUID.randomUUID());
                copy.setUserId(userId);
                copy.setStudyPackId(callerStudyPackId);
                copy.setOriginSessionId(sessionId);
                copy.setQuestionKey(questionKey);
                copy.setQuestion(source.getQuestion());
                copy.setLearnerLevel(learnerLevel == null ? null : learnerLevel.name());
                copy.setLastKnownOutcome(OUTCOME_UNANSWERED);
                copy.setClaimedSessionId(sessionId);
                copy.setGeneratedAt(now);
                copies.add(copy);
                copiedQuestions.add(source.getQuestion());
                if (copies.size() == count) {
                    break;
                }
            }
            if (copies.isEmpty()) {
                return List.of();
            }
            List<QuizItem> persistedQuestions = new ArrayList<>(copies.size());
            for (int index = 0; index < copies.size(); index++) {
                try {
                    questionBankRepository.saveAll(List.of(copies.get(index)));
                    persistedQuestions.add(copiedQuestions.get(index));
                } catch (DataIntegrityViolationException exception) {
                    log.warn("Official Challenge template copy skipped duplicate row for userId={}, studyPackId={}",
                            userId, callerStudyPackId, exception);
                }
            }
            return List.copyOf(persistedQuestions);
        } catch (RuntimeException exception) {
            log.warn("Official Challenge template copy failed for userId={}, studyPackId={}",
                    userId, callerStudyPackId, exception);
            return List.of();
        }
    }

    void seedTemplateAsync(UUID noteId, UUID studyPackId) {
        try {
            OfficialTemplateSeedTarget target = studyPackGenerationTransactionOperations.execute(status -> {
                NoteEntity note = noteRepository.findById(noteId).orElse(null);
                StudyPackEntity studyPack = studyPackRepository.findById(studyPackId).orElse(null);
                if (!isEligibleOfficialTemplate(note, studyPack)
                        || questionBankRepository.existsByUserIdAndStudyPackId(note.getOwnerUserId(), studyPack.getId())) {
                    return null;
                }
                StudyPackGenerationContext context = generationContextResolver.resolveForStudyPack(
                        note.getOwnerUserId(), studyPack
                );
                return new OfficialTemplateSeedTarget(note.getOwnerUserId(), studyPack, context);
            });
            if (target == null) {
                return;
            }
            List<QuizItem> generated = quizGenerationService.generateChallengeQuiz(
                    target.studyPack().getTitle(),
                    target.studyPack().getSummary(),
                    target.studyPack().getKeyConcepts() == null ? List.of() : target.studyPack().getKeyConcepts(),
                    List.of(),
                    ChallengeQuizService.MAX_CHALLENGE_QUIZ_QUESTIONS,
                    TEMPLATE_DIFFICULTY,
                    target.context()
            );
            studyPackGenerationTransactionOperations.execute(status -> {
                NoteEntity note = noteRepository.findById(noteId).orElse(null);
                StudyPackEntity studyPack = studyPackRepository.findById(studyPackId).orElse(null);
                if (!isEligibleOfficialTemplate(note, studyPack)
                        || questionBankRepository.existsByUserIdAndStudyPackId(note.getOwnerUserId(), studyPack.getId())) {
                    return null;
                }
                questionBankService.persistGeneratedQuestions(
                        target.ownerUserId(),
                        target.studyPack().getId(),
                        null,
                        target.context().learnerLevel(),
                        generated
                );
                return null;
            });
        } catch (RuntimeException exception) {
            log.warn("Official Challenge template seed failed for noteId={}, studyPackId={}", noteId, studyPackId, exception);
        }
    }

    private boolean isEligibleOfficialTemplate(NoteEntity note, StudyPackEntity studyPack) {
        return note != null
                && studyPack != null
                && note.getId() != null
                && note.getId().equals(studyPack.getNoteId())
                && note.getVisibility() == NoteVisibility.PUBLIC
                && note.getOwnerUserId() != null
                && note.getOwnerUserId().equals(studyPack.getOwnerUserId())
                && isOfficialAuthor(note.getOwnerUserId());
    }

    private boolean isOfficialAuthor(UUID userId) {
        return userId != null && !AccountPurgeService.DELETED_USER_ID.equals(userId)
                && userRepository.findById(userId)
                .map(user -> user.getRole() == UserRole.ADMIN)
                .orElse(false);
    }

    /**
     * Seeding runs on the bulk LLM fan-out pool, never on the live Study Pack generation pool, so a
     * large backfill can never starve or reject a user-facing generation request. Returns whether an
     * immediate dispatch was accepted; a transaction-deferred dispatch always reports {@code true}
     * because its outcome is only known after the caller has already returned.
     */
    private boolean dispatchSeedAfterCommit(UUID noteId, UUID studyPackId) {
        Runnable seedTask = () -> seedTemplateAsync(noteId, studyPackId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitSeedTask(seedTask, noteId, studyPackId);
                }
            });
            return true;
        }
        return submitSeedTask(seedTask, noteId, studyPackId);
    }

    private boolean submitSeedTask(Runnable seedTask, UUID noteId, UUID studyPackId) {
        try {
            llmParallelTaskExecutor.execute(seedTask);
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("Official Challenge template seed dispatch rejected for noteId={}, studyPackId={} "
                    + "— executor queue full", noteId, studyPackId);
            return false;
        }
    }

    public record OfficialTemplateKey(UUID ownerUserId, UUID studyPackId) {
    }

    private record OfficialTemplateSeedTarget(
            UUID ownerUserId,
            StudyPackEntity studyPack,
            StudyPackGenerationContext context
    ) {
    }
}
