package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizValidationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminStudyPackTransactionHelper {
    private static final Logger log = LoggerFactory.getLogger(AdminStudyPackTransactionHelper.class);
    private static final String ENRICHED_SUMMARY_MARKER = "|";

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final LlmStudyPackService llmStudyPackService;
    private final RegenerationProgressTracker progressTracker;
    private final ExamQuestionPoolService examQuestionPoolService;

    @Transactional
    public void regenerateOnePack(StudyPackEntity pack) {
        if (pack == null || pack.getId() == null || pack.getNoteId() == null) {
            log.warn("Admin summary regeneration skipped study pack with missing identifiers");
            progressTracker.recordFailure();
            return;
        }

        try {
            StudyPackEntity currentPack = studyPackRepository.findById(pack.getId())
                    .orElse(null);
            if (currentPack == null) {
                log.warn("Admin summary regeneration skipped missing packId={}", pack.getId());
                progressTracker.recordSuccess();
                return;
            }
            if (currentPack.getSummary() != null && currentPack.getSummary().contains(ENRICHED_SUMMARY_MARKER)) {
                progressTracker.recordSuccess();
                return;
            }

            NoteEntity note = noteRepository.findById(currentPack.getNoteId())
                    .orElse(null);
            if (note == null) {
                log.warn("Admin summary regeneration skipped packId={} because source note was not found", currentPack.getId());
                progressTracker.recordSuccess();
                return;
            }

            generationContextResolver.assertGenerationReady(note);
            StudyPackGenerationContext context = buildContext(note);
            String newSummary = llmStudyPackService.regenerateSummary(note.getContent(), context);
            currentPack.setSummary(newSummary);
            studyPackRepository.save(currentPack);
            // Same invalidation StudyPackService's regeneration path uses (v0.143.0) — this repair
            // replaces the pack's summary in place, and the summary is a direct exam-pool generation
            // input, so a READY pool must be reset or it keeps serving questions from the old one.
            // The flush is load-bearing for the same reason it is there: without it, the UPDATE above
            // stays a pending, unflushed change when refreshPool's JPQL @Lock query runs, inverting the
            // study_packs -> exam_question_pool lock order every caller relies on.
            studyPackRepository.flush();
            examQuestionPoolService.refreshPool(currentPack.getId(), ExamQuestionPoolService.MODE_LONG_EXAM);
            examQuestionPoolService.refreshPool(currentPack.getId(), ExamQuestionPoolService.MODE_BOARD_EXAM);
            progressTracker.recordSuccess();
        } catch (Exception ex) {
            progressTracker.recordFailure();
            log.warn(
                    "Admin summary regeneration failed for packId={} reason={}",
                    pack.getId(),
                    ex.getMessage(),
                    ex
            );
        }
    }

    @Transactional
    public void repairMalformedQuiz(StudyPackEntity pack) {
        if (pack == null || pack.getId() == null || pack.getNoteId() == null) {
            log.warn("Admin malformed quiz repair skipped study pack with missing identifiers");
            progressTracker.recordFailure();
            return;
        }

        try {
            StudyPackEntity currentPack = studyPackRepository.findById(pack.getId())
                    .orElse(null);
            if (currentPack == null) {
                log.warn("Admin malformed quiz repair skipped missing packId={}", pack.getId());
                progressTracker.recordSuccess();
                return;
            }
            if (!containsFormatStemMismatch(currentPack.getQuiz())) {
                progressTracker.recordSuccess();
                return;
            }
            if (quickReviewSessionRepository.existsByStudyPackIdAndSessionModeAndStatus(
                    currentPack.getId(),
                    QuickReviewSessionMode.QUICK_REVIEW,
                    QuickReviewSessionStatus.IN_PROGRESS
            )) {
                log.warn("Admin malformed quiz repair skipped packId={} because a Quick Review session is in progress", currentPack.getId());
                progressTracker.recordSuccess();
                return;
            }

            NoteEntity note = noteRepository.findById(currentPack.getNoteId())
                    .orElse(null);
            if (note == null) {
                log.warn("Admin malformed quiz repair skipped packId={} because source note was not found", currentPack.getId());
                progressTracker.recordSuccess();
                return;
            }

            generationContextResolver.assertGenerationReady(note);
            StudyPackGenerationContext context = buildContext(note);
            GeneratedStudyPackContent generated = llmStudyPackService.generateStudyPack(note.getContent(), context);
            currentPack.setQuiz(generated.quiz());
            studyPackRepository.save(currentPack);
            // Same invalidation as regenerateOnePack above, and for the same reason: this replaces the
            // pack's quiz in place, which feeds the pool's answer-key exclusion filter, so a READY pool
            // must be reset. The flush ordering is load-bearing — see the comment above.
            studyPackRepository.flush();
            examQuestionPoolService.refreshPool(currentPack.getId(), ExamQuestionPoolService.MODE_LONG_EXAM);
            examQuestionPoolService.refreshPool(currentPack.getId(), ExamQuestionPoolService.MODE_BOARD_EXAM);
            progressTracker.recordSuccess();
        } catch (Exception ex) {
            progressTracker.recordFailure();
            log.warn(
                    "Admin malformed quiz repair failed for packId={} reason={}",
                    pack.getId(),
                    ex.getMessage(),
                    ex
            );
        }
    }

    private boolean containsFormatStemMismatch(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return false;
        }
        return quiz.stream()
                .anyMatch(item -> QuizValidationUtils.isFormatStemMismatch(
                        item.getQuestion(),
                        item.getChoices(),
                        item.getQuestionFormat()
                ));
    }

    private StudyPackGenerationContext buildContext(NoteEntity note) {
        return generationContextResolver.resolve(note.getOwnerUserId(), note);
    }
}
