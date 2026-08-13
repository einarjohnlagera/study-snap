package com.studysnap.backend.service;

import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPackQuizMasteryService {
    private static final Logger log = LoggerFactory.getLogger(StudyPackQuizMasteryService.class);

    private final QuickReviewSessionRepository quickReviewSessionRepository;

    /**
     * Joins the caller's transaction deliberately. An earlier version used
     * {@code Propagation.NOT_SUPPORTED} to keep a failure here from poisoning the caller, but that
     * suspends the caller's transaction WITHOUT releasing its connection, so this query needs a
     * SECOND connection from the pool. Every call site is already inside a transaction, including
     * the note-detail read — the hottest authenticated route — so under concurrency at or above the
     * pool size (default 10, with 25 Tomcat threads) requests block until timeout, the failure is
     * swallowed here, and every learner silently reads as NOT mastered: unlocked Quiz tabs re-lock
     * and a learner who just scored perfectly is told to review the notes.
     *
     * <p>The suspension also bought nothing. {@code completeSession} calls this BEFORE it sets
     * {@code verifiedCorrectAnswers}, so the in-flight row still holds null and cannot match the
     * quiz size whether or not the query sees the transaction.
     */
    @Transactional(readOnly = true)
    public StudyPackQuizMastery resolve(UUID userId, StudyPackEntity studyPack) {
        return tryResolve(userId, studyPack).orElseGet(StudyPackQuizMastery::notMastered);
    }

    @Transactional(readOnly = true)
    public Optional<StudyPackQuizMastery> tryResolve(UUID userId, StudyPackEntity studyPack) {
        if (userId == null || studyPack == null || studyPack.getId() == null
                || studyPack.getQuiz() == null || studyPack.getQuiz().isEmpty()) {
            return Optional.of(StudyPackQuizMastery.notMastered());
        }

        try {
            int currentQuizSize = studyPack.getQuiz().size();
            OffsetDateTime masteredAt = quickReviewSessionRepository.findQuizMasteredAt(
                    userId,
                    studyPack.getId(),
                    currentQuizSize
            );
            return Optional.of(masteredAt == null
                    ? StudyPackQuizMastery.notMastered()
                    : StudyPackQuizMastery.masteredAt(masteredAt));
        } catch (RuntimeException exception) {
            log.warn(
                    "action=resolve_study_pack_quiz_mastery outcome=failed userId={} studyPackId={}",
                    userId,
                    studyPack.getId(),
                    exception
            );
            return Optional.empty();
        }
    }
}
