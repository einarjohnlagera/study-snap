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
import com.studysnap.backend.repository.ChallengeQuizQuestionBankOwnerProjection;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.NoteOwnerVisibilityProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackOwnerProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
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
    private static final String OFFICIAL_AUTHOR_EMAIL = "einar.lagera@gmail.com";

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

    /**
     * ⚠️ THREE QUERIES OVER THE WHOLE CATALOG, NEVER TWO PER NOTE. This used to load every public
     * {@code NoteEntity} (~1,442 rows, each carrying {@code content}) and every matching
     * {@code StudyPackEntity}, then run {@code userRepository.findById} AND
     * {@code existsByUserIdAndStudyPackId} INSIDE the loop — ~2,884 queries for one admin action.
     *
     * <p>⚠️ WHICH NOTES ARE ELIGIBLE IS UNCHANGED — only how they are found. The shape legs and the
     * Official-author legs are the same predicate the entity path uses
     * ({@link #matchesOfficialTemplateShape}, {@link #isOfficialAuthorAccount}), and the bank check
     * is still asked only about notes that already passed both, exactly as the old {@code ||}
     * short-circuit did. {@code queued}/{@code skipped}/{@code rejected} therefore mean what they
     * meant before.
     *
     * <p>⚠️ AND IT IS NOT PAGED. The response is three counts over the whole catalog; a page would
     * change what those numbers mean. The queue order stays {@code updated_at desc}, because it
     * decides which packs seed first.
     */
    public AdminSeedOfficialChallengeQuizTemplatesResponse queueBackfill() {
        int queued = 0;
        int skipped = 0;
        int rejected = 0;
        List<NoteOwnerVisibilityProjection> publicNotes = noteRepository
                .findOwnerVisibilityProjectionsByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);
        if (publicNotes.isEmpty()) {
            return new AdminSeedOfficialChallengeQuizTemplatesResponse(queued, skipped, rejected);
        }

        Map<UUID, StudyPackOwnerProjection> packsByNoteId = studyPackRepository.findOwnerProjectionsByNoteIdIn(
                publicNotes.stream().map(NoteOwnerVisibilityProjection::id).toList()
        ).stream().collect(Collectors.toMap(StudyPackOwnerProjection::noteId, studyPack -> studyPack));
        Set<UUID> officialAuthorIds = resolveOfficialAuthorIds(
                publicNotes.stream().map(NoteOwnerVisibilityProjection::ownerUserId).toList()
        );

        List<OfficialTemplateCandidate> candidates = new ArrayList<>();
        for (NoteOwnerVisibilityProjection note : publicNotes) {
            StudyPackOwnerProjection studyPack = packsByNoteId.get(note.id());
            if (studyPack == null
                    || !matchesOfficialTemplateShape(
                            note.id(), note.ownerUserId(), note.visibility(),
                            studyPack.noteId(), studyPack.ownerUserId())
                    || !officialAuthorIds.contains(note.ownerUserId())) {
                // ⚠️ AN EXPLICIT COUNTER, NOT `publicNotes.size() - candidates.size()`. The
                // subtraction is only equal to the old loop's semantics if the projection returns
                // exactly one row per note; it does today (no join, and `uq_study_packs_note_id`
                // keeps the pack map one-to-one), but counting here is equal BY CONSTRUCTION and
                // does not quietly depend on that.
                skipped++;
                continue;
            }
            candidates.add(new OfficialTemplateCandidate(note.id(), note.ownerUserId(), studyPack.id()));
        }
        if (candidates.isEmpty()) {
            return new AdminSeedOfficialChallengeQuizTemplatesResponse(queued, skipped, rejected);
        }

        Set<ChallengeQuizQuestionBankOwnerProjection> alreadySeeded = new HashSet<>(
                questionBankRepository.findOwnerStudyPackPairsByStudyPackIdIn(
                        candidates.stream().map(OfficialTemplateCandidate::studyPackId).toList()
                )
        );
        for (OfficialTemplateCandidate candidate : candidates) {
            if (alreadySeeded.contains(new ChallengeQuizQuestionBankOwnerProjection(
                    candidate.ownerUserId(), candidate.studyPackId()))) {
                skipped++;
                continue;
            }
            if (dispatchSeedAfterCommit(candidate.noteId(), candidate.studyPackId())) {
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
     * Official author's learner level; copied rows are tagged with the adopter note's effective
     * curriculum level.
     */
    public List<QuizItem> copyTemplateQuestions(
            UUID userId,
            UUID callerStudyPackId,
            LearnerLevel effectiveCurriculumLevel,
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
                copy.setLearnerLevel(effectiveCurriculumLevel == null ? null : effectiveCurriculumLevel.name());
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
            // Joins the CALLER's transaction — see ChallengeQuizQuestionBankService.persistGeneratedQuestions
            // for why the REQUIRES_NEW isolation attempted in v0.81.0 was reverted (FK to an uncommitted
            // quick_review_sessions row). All-or-nothing is fine: the caller recomputes `shortfall` and
            // generates fresh questions for the remainder.
            try {
                questionBankRepository.saveAll(copies);
            } catch (RuntimeException exception) {
                log.warn("Official Challenge template copy failed for userId={}, studyPackId={}",
                        userId, callerStudyPackId, exception);
                return List.of();
            }
            return List.copyOf(copiedQuestions);
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
                // v0.100.0 item 6. The authoring domain is a property of the MATERIAL, not of whoever
                // is reading it, so this must stay the note owner. It cannot currently drift: this
                // method takes no caller identity at all, and isEligibleOfficialTemplate additionally
                // requires note owner == studyPack owner. Recorded rather than pinned by a test,
                // because a test here could only assert ownerId == ownerId -- vacuous, and this repo
                // has paid for tests that pass for the wrong reason. If a caller id is ever threaded
                // into this method, that test becomes both possible and required.
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
            ).quizItems();
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
                        StudyPackGenerationContextResolver.effectiveCurriculumLevel(target.context()),
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
                && matchesOfficialTemplateShape(
                        note.getId(), note.getOwnerUserId(), note.getVisibility(),
                        studyPack.getNoteId(), studyPack.getOwnerUserId())
                && isOfficialAuthor(note.getOwnerUserId());
    }

    /**
     * The non-author half of {@link #isEligibleOfficialTemplate}, over scalars.
     *
     * <p>⚠️ ONE PREDICATE, TWO CALLERS. The entity path and the backfill's projection path must
     * decide eligibility identically; a second copy of these legs is exactly how the two would
     * drift, and {@code v0.125.0}'s anti-drift is that eligibility does not change.
     */
    private static boolean matchesOfficialTemplateShape(
            UUID noteId,
            UUID noteOwnerUserId,
            NoteVisibility visibility,
            UUID studyPackNoteId,
            UUID studyPackOwnerUserId
    ) {
        return noteId != null
                && noteId.equals(studyPackNoteId)
                && visibility == NoteVisibility.PUBLIC
                && noteOwnerUserId != null
                && noteOwnerUserId.equals(studyPackOwnerUserId);
    }

    private boolean isOfficialAuthor(UUID userId) {
        return userId != null && !AccountPurgeService.DELETED_USER_ID.equals(userId)
                && userRepository.findById(userId)
                .map(OfficialChallengeQuizTemplateService::isOfficialAuthorAccount)
                .orElse(false);
    }

    /**
     * The batched form of {@link #isOfficialAuthor(UUID)}: one {@code findAllById} for the distinct
     * owner set instead of one {@code findById} per note. The {@code DELETED_USER_ID} and null legs
     * are applied here, before the lookup, so a purged owner can never enter the result set.
     */
    private Set<UUID> resolveOfficialAuthorIds(Collection<UUID> ownerUserIds) {
        Set<UUID> lookupIds = ownerUserIds.stream()
                .filter(id -> id != null && !AccountPurgeService.DELETED_USER_ID.equals(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (lookupIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> officialAuthorIds = new LinkedHashSet<>();
        for (UserEntity user : userRepository.findAllById(lookupIds)) {
            if (isOfficialAuthorAccount(user)) {
                officialAuthorIds.add(user.getId());
            }
        }
        return officialAuthorIds;
    }

    private static boolean isOfficialAuthorAccount(UserEntity user) {
        return user != null
                && (user.getRole() == UserRole.ADMIN || OFFICIAL_AUTHOR_EMAIL.equalsIgnoreCase(user.getEmail()));
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

    /** One public note that already passed the shape and Official-author legs of the predicate. */
    private record OfficialTemplateCandidate(UUID noteId, UUID ownerUserId, UUID studyPackId) {
    }

    private record OfficialTemplateSeedTarget(
            UUID ownerUserId,
            StudyPackEntity studyPack,
            StudyPackGenerationContext context
    ) {
    }
}
