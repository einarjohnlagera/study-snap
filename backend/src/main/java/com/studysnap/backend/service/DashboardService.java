package com.studysnap.backend.service;

import com.studysnap.backend.dto.ContinueStudyingReason;
import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.SummaryPreviewUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {
    private static final BigDecimal PERFECT_SCORE = BigDecimal.valueOf(100);

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;

    public ContinueStudyingResponse getContinueStudyingRecommendation(UUID userId) {
        Optional<ContinueStudyingResponse> lowScoreRecent = resolveLowScoreRecentRecommendation(userId);
        if (lowScoreRecent.isPresent()) {
            return lowScoreRecent.get();
        }

        Optional<ContinueStudyingResponse> recentlyOpened = resolveRecentlyOpenedRecommendation(userId);
        if (recentlyOpened.isPresent()) {
            return recentlyOpened.get();
        }

        Optional<StudyPackEntity> recentlyCreated = studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId);
        if (recentlyCreated.isPresent()) {
            StudyPackEntity studyPack = recentlyCreated.get();
            return toResponse(
                    studyPack,
                    ContinueStudyingReason.RECENTLY_CREATED,
                    null,
                    null,
                    findLastOpenedAt(userId, studyPack.getId()),
                    studyPack.getCreatedAt()
            );
        }

        return new ContinueStudyingResponse(null, null, null, null, null, null, null, null);
    }

    private Optional<ContinueStudyingResponse> resolveLowScoreRecentRecommendation(UUID userId) {
        List<QuickReviewSessionEntity> recentSessions = quickReviewSessionRepository
                .findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, PageRequest.of(0, 50));

        Map<UUID, QuickReviewSessionEntity> latestSessionByStudyPack = new LinkedHashMap<>();
        for (QuickReviewSessionEntity session : recentSessions) {
            UUID studyPackId = session.getStudyPackId();
            if (!latestSessionByStudyPack.containsKey(studyPackId)) {
                latestSessionByStudyPack.put(studyPackId, session);
            }
        }

        for (QuickReviewSessionEntity session : latestSessionByStudyPack.values()) {
            BigDecimal scorePercentage = session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage();
            if (scorePercentage.compareTo(PERFECT_SCORE) >= 0) {
                continue;
            }

            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
            if (studyPack.isEmpty()) {
                continue;
            }

            return Optional.of(toResponse(
                    studyPack.get(),
                    ContinueStudyingReason.LOW_SCORE_RECENT,
                    scorePercentage,
                    session.getCompletedAt(),
                    findLastOpenedAt(userId, session.getStudyPackId()),
                    studyPack.get().getCreatedAt()
            ));
        }

        return Optional.empty();
    }

    private Optional<ContinueStudyingResponse> resolveRecentlyOpenedRecommendation(UUID userId) {
        List<UserActivityEventEntity> recentOpened = activityEventRepository
                .findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                        userId,
                        ActivityType.OPENED_STUDY_PACK,
                        PageRequest.of(0, 30)
                );

        for (UserActivityEventEntity openedEvent : recentOpened) {
            UUID studyPackId = openedEvent.getStudyPackId();
            if (studyPackId == null) {
                continue;
            }

            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId);
            if (studyPack.isEmpty()) {
                continue;
            }

            Optional<QuickReviewSessionEntity> latestSession = quickReviewSessionRepository
                    .findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                            userId,
                            studyPackId,
                            PageRequest.of(0, 1)
                    )
                    .stream()
                    .findFirst();

            return Optional.of(toResponse(
                    studyPack.get(),
                    ContinueStudyingReason.RECENTLY_OPENED,
                    latestSession.map(QuickReviewSessionEntity::getScorePercentage).orElse(null),
                    latestSession.map(QuickReviewSessionEntity::getCompletedAt).orElse(null),
                    openedEvent.getCreatedAt(),
                    studyPack.get().getCreatedAt()
            ));
        }

        return Optional.empty();
    }

    private OffsetDateTime findLastOpenedAt(UUID userId, UUID studyPackId) {
        return activityEventRepository
                .findTopByUserIdAndStudyPackIdAndActivityTypeOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        ActivityType.OPENED_STUDY_PACK
                )
                .map(UserActivityEventEntity::getCreatedAt)
                .orElse(null);
    }

    private ContinueStudyingResponse toResponse(
            StudyPackEntity studyPack,
            ContinueStudyingReason reason,
            BigDecimal lastScorePercentage,
            OffsetDateTime lastReviewedAt,
            OffsetDateTime lastOpenedAt,
            OffsetDateTime createdAt
    ) {
        return new ContinueStudyingResponse(
                studyPack.getId().toString(),
                studyPack.getTitle(),
                SummaryPreviewUtils.buildSummaryPreview(studyPack.getSummary(), 140),
                reason,
                lastScorePercentage,
                lastReviewedAt,
                lastOpenedAt,
                createdAt
        );
    }
}
