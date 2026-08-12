package com.studysnap.backend.service;

import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPackQuizMasteryService {
    private static final Logger log = LoggerFactory.getLogger(StudyPackQuizMasteryService.class);

    private final QuickReviewSessionRepository quickReviewSessionRepository;

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public StudyPackQuizMastery resolve(UUID userId, StudyPackEntity studyPack) {
        return tryResolve(userId, studyPack).orElseGet(StudyPackQuizMastery::notMastered);
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
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
