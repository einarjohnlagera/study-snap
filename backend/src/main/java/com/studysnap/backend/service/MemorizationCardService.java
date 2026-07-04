package com.studysnap.backend.service;

import com.studysnap.backend.dto.MemorizationCardResponse;
import com.studysnap.backend.entity.MemorizationCardEntity;
import com.studysnap.backend.entity.MemorizationGrade;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.exception.MemorizationNotAvailableException;
import com.studysnap.backend.repository.MemorizationCardRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemorizationCardService {
    private static final BigDecimal INITIAL_EASE_FACTOR = BigDecimal.valueOf(2.50).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal MIN_EASE_FACTOR = BigDecimal.valueOf(1.30).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal AGAIN_EASE_PENALTY = BigDecimal.valueOf(0.20).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HARD_INTERVAL_MULTIPLIER = BigDecimal.valueOf(1.20).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HARD_EASE_PENALTY = BigDecimal.valueOf(0.15).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal EASY_INTERVAL_BONUS = BigDecimal.valueOf(1.30).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal EASY_EASE_BONUS = BigDecimal.valueOf(0.15).setScale(2, RoundingMode.HALF_UP);
    private static final int GOOD_FIRST_INTERVAL_DAYS = 1;
    private static final int EASY_FIRST_INTERVAL_DAYS = 4;

    private final MemorizationCardRepository memorizationCardRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MemorizationCardResponse> listForOwnedStudyPack(
        UUID userId,
        String studyPackIdRaw
    ) {
        assertProfileCanUseMemorization(userId);
        UUID studyPackId = resolveOwnedStudyPackId(userId, studyPackIdRaw);
        return memorizationCardRepository.findByUserIdAndStudyPackIdOrderByDueAtAscConceptAsc(userId, studyPackId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public MemorizationCardResponse recordGradeForOwnedStudyPack(
        UUID userId,
        String studyPackIdRaw,
        String rawConcept,
        MemorizationGrade grade,
        OffsetDateTime now
    ) {
        assertProfileCanUseMemorization(userId);
        UUID studyPackId = resolveOwnedStudyPackId(userId, studyPackIdRaw);
        String concept = normalizeConcept(rawConcept);
        MemorizationCardEntity entity = memorizationCardRepository
            .findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, concept)
            .orElseGet(() -> buildNewCard(userId, studyPackId, concept, now));
        applyGrade(entity, grade, now);
        return toResponse(memorizationCardRepository.save(entity));
    }

    private UUID resolveOwnedStudyPackId(UUID userId, String studyPackIdRaw) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
        return studyPackId;
    }

    private void assertProfileCanUseMemorization(UUID userId) {
        boolean teacherMode = userRepository.findById(userId)
            .map(user -> user.getProfileType() == ProfileType.TEACHER)
            .orElse(false);
        if (teacherMode) {
            throw new MemorizationNotAvailableException();
        }
    }

    private MemorizationCardEntity buildNewCard(UUID userId, UUID studyPackId, String concept, OffsetDateTime now) {
        MemorizationCardEntity entity = new MemorizationCardEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStudyPackId(studyPackId);
        entity.setConcept(concept);
        entity.setIntervalDays(0);
        entity.setEaseFactor(INITIAL_EASE_FACTOR);
        entity.setRepetitions(0);
        entity.setDueAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyGrade(MemorizationCardEntity entity, MemorizationGrade grade, OffsetDateTime now) {
        int previousIntervalDays = entity.getIntervalDays();
        int previousRepetitions = entity.getRepetitions();
        BigDecimal previousEaseFactor = entity.getEaseFactor();

        switch (grade) {
            case AGAIN -> {
                entity.setRepetitions(0);
                entity.setIntervalDays(0);
                entity.setEaseFactor(floorEase(previousEaseFactor.subtract(AGAIN_EASE_PENALTY)));
                entity.setDueAt(now);
            }
            case HARD -> {
                entity.setRepetitions(previousRepetitions + 1);
                entity.setIntervalDays(Math.max(1, round(previousIntervalDays, HARD_INTERVAL_MULTIPLIER)));
                entity.setEaseFactor(floorEase(previousEaseFactor.subtract(HARD_EASE_PENALTY)));
                entity.setDueAt(now.plusDays(entity.getIntervalDays()));
            }
            case GOOD -> {
                entity.setRepetitions(previousRepetitions + 1);
                int intervalDays = previousRepetitions == 0
                    ? GOOD_FIRST_INTERVAL_DAYS
                    : round(previousIntervalDays, previousEaseFactor);
                entity.setIntervalDays(intervalDays);
                entity.setDueAt(now.plusDays(intervalDays));
            }
            case EASY -> {
                entity.setRepetitions(previousRepetitions + 1);
                int intervalDays = previousRepetitions == 0
                    ? EASY_FIRST_INTERVAL_DAYS
                    : round(previousIntervalDays, previousEaseFactor, EASY_INTERVAL_BONUS);
                entity.setIntervalDays(intervalDays);
                entity.setEaseFactor(previousEaseFactor.add(EASY_EASE_BONUS).setScale(2, RoundingMode.HALF_UP));
                entity.setDueAt(now.plusDays(intervalDays));
            }
        }
        entity.setLastReviewedAt(now);
        entity.setLastGrade(grade);
        entity.setUpdatedAt(now);
    }

    private int round(int intervalDays, BigDecimal... multipliers) {
        BigDecimal result = BigDecimal.valueOf(intervalDays);
        for (BigDecimal multiplier : multipliers) {
            result = result.multiply(multiplier);
        }
        return Math.toIntExact(Math.round(result.doubleValue()));
    }

    private BigDecimal floorEase(BigDecimal easeFactor) {
        return easeFactor.max(MIN_EASE_FACTOR).setScale(2, RoundingMode.HALF_UP);
    }

    private MemorizationCardResponse toResponse(MemorizationCardEntity entity) {
        return new MemorizationCardResponse(
            entity.getConcept(),
            entity.getIntervalDays(),
            entity.getEaseFactor(),
            entity.getRepetitions(),
            entity.getDueAt(),
            entity.getLastReviewedAt(),
            entity.getLastGrade()
        );
    }

    private String normalizeConcept(String rawConcept) {
        if (rawConcept == null) {
            return null;
        }
        String concept = rawConcept.trim().toLowerCase(Locale.ROOT);
        return concept.isBlank() ? null : concept;
    }
}
