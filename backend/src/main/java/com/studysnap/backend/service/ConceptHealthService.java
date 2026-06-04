package com.studysnap.backend.service;

import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConceptHealthService {
    static final int DUE_THRESHOLD_DAYS = 3;

    private final ConceptHealthRepository conceptHealthRepository;
    private final StudyPackRepository studyPackRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;

    @Transactional
    public void recordCorrectAnswers(
        UUID userId,
        UUID studyPackId,
        List<String> correctConceptNames,
        OffsetDateTime now
    ) {
        if (correctConceptNames == null || correctConceptNames.isEmpty()) {
            return;
        }

        for (String rawConcept : correctConceptNames) {
            String concept = normalizeConcept(rawConcept);
            if (concept == null) {
                continue;
            }

            ConceptHealthEntity entity = conceptHealthRepository
                .findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, concept)
                .orElseGet(() -> buildConceptHealth(userId, studyPackId, concept, now));
            entity.setLastCorrectAt(now);
            entity.setUpdatedAt(now);
            conceptHealthRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public List<ConceptHealthEntryResponse> getConceptHealthForOwnedStudyPack(
        UUID userId,
        String studyPackIdRaw,
        OffsetDateTime now
    ) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);
        return getConceptHealth(userId, studyPackId, getKeyConcepts(studyPack), now);
    }

    @Transactional(readOnly = true)
    public List<ConceptHealthEntryResponse> getConceptHealth(
        UUID userId,
        UUID studyPackId,
        List<String> allConcepts,
        OffsetDateTime now
    ) {
        if (allConcepts == null || allConcepts.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptHealthEntity> healthByConcept = healthByConcept(userId, studyPackId);
        return allConcepts.stream()
            .map(this::normalizeConcept)
            .filter(Objects::nonNull)
            .map(concept -> toResponse(concept, healthByConcept.get(concept), now))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getDueConcepts(
        UUID userId,
        UUID studyPackId,
        List<String> allConcepts,
        OffsetDateTime now
    ) {
        if (allConcepts == null || allConcepts.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptHealthEntity> healthByConcept = healthByConcept(userId, studyPackId);
        return allConcepts.stream()
            .map(this::normalizeConcept)
            .filter(Objects::nonNull)
            .distinct()
            .filter(concept -> isDue(resolveLastCorrectAt(healthByConcept.get(concept)), now))
            .sorted(Comparator.comparing(
                concept -> resolveLastCorrectAt(healthByConcept.get(concept)),
                Comparator.nullsLast(Comparator.naturalOrder())
            ))
            .toList();
    }

    private ConceptHealthEntity buildConceptHealth(
        UUID userId,
        UUID studyPackId,
        String concept,
        OffsetDateTime now
    ) {
        ConceptHealthEntity entity = new ConceptHealthEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStudyPackId(studyPackId);
        entity.setConcept(concept);
        entity.setCreatedAt(now);
        return entity;
    }

    private Map<String, ConceptHealthEntity> healthByConcept(UUID userId, UUID studyPackId) {
        Map<String, ConceptHealthEntity> healthByConcept = new HashMap<>();
        for (ConceptHealthEntity entity : conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)) {
            String concept = normalizeConcept(entity.getConcept());
            if (concept != null) {
                healthByConcept.put(concept, entity);
            }
        }
        return healthByConcept;
    }

    private ConceptHealthEntryResponse toResponse(
        String concept,
        ConceptHealthEntity entity,
        OffsetDateTime now
    ) {
        OffsetDateTime lastCorrectAt = resolveLastCorrectAt(entity);
        return new ConceptHealthEntryResponse(
            concept,
            lastCorrectAt,
            isDue(lastCorrectAt, now),
            daysSinceReview(lastCorrectAt, now)
        );
    }

    boolean isDue(OffsetDateTime lastCorrectAt, OffsetDateTime now) {
        return lastCorrectAt == null || !lastCorrectAt.isAfter(now.minusDays(DUE_THRESHOLD_DAYS));
    }

    private Integer daysSinceReview(OffsetDateTime lastCorrectAt, OffsetDateTime now) {
        if (lastCorrectAt == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(lastCorrectAt.toLocalDate(), now.toLocalDate()));
    }

    private OffsetDateTime resolveLastCorrectAt(ConceptHealthEntity entity) {
        return entity == null ? null : entity.getLastCorrectAt();
    }

    private String normalizeConcept(String rawConcept) {
        if (rawConcept == null) {
            return null;
        }
        String concept = rawConcept.trim();
        return concept.isBlank() ? null : concept;
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }
}
