package com.studysnap.backend.service;

import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.ConceptReadinessStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        recordAnswers(userId, studyPackId, correctConceptNames, now, true);
    }

    @Transactional
    public void recordIncorrectAnswers(
        UUID userId,
        UUID studyPackId,
        List<String> incorrectConceptNames,
        OffsetDateTime now
    ) {
        recordAnswers(userId, studyPackId, incorrectConceptNames, now, false);
    }

    @Transactional
    public void recordCorrectAnswersForKnownConcepts(
        UUID userId,
        UUID studyPackId,
        List<String> correctConceptNames,
        List<String> keyConcepts,
        OffsetDateTime now
    ) {
        List<String> matchedConcepts = filterKnownConcepts(correctConceptNames, keyConcepts);
        if (matchedConcepts.isEmpty()) {
            return;
        }
        recordCorrectAnswers(userId, studyPackId, matchedConcepts, now);
    }

    @Transactional
    public void recordIncorrectAnswersForKnownConcepts(
        UUID userId,
        UUID studyPackId,
        List<String> incorrectConceptNames,
        List<String> keyConcepts,
        OffsetDateTime now
    ) {
        List<String> matchedConcepts = filterKnownConcepts(incorrectConceptNames, keyConcepts);
        if (matchedConcepts.isEmpty()) {
            return;
        }
        recordIncorrectAnswers(userId, studyPackId, matchedConcepts, now);
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
        boolean includeReviewTiming = canViewConceptReviewTiming(planType);
        return getConceptHealth(userId, studyPackId, getKeyConcepts(studyPack), now, includeReviewTiming);
    }

    @Transactional(readOnly = true)
    public boolean canViewConceptHealth(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        return canViewConceptReviewTiming(planType);
    }

    @Transactional(readOnly = true)
    public List<ConceptHealthEntryResponse> getConceptHealth(
        UUID userId,
        UUID studyPackId,
        List<String> allConcepts,
        OffsetDateTime now
    ) {
        return getConceptHealth(userId, studyPackId, allConcepts, now, true);
    }

    @Transactional(readOnly = true)
    public List<ConceptHealthEntryResponse> getConceptHealth(
        UUID userId,
        UUID studyPackId,
        List<String> allConcepts,
        OffsetDateTime now,
        boolean includeReviewTiming
    ) {
        if (allConcepts == null || allConcepts.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptHealthEntity> healthByConcept = healthByConcept(userId, studyPackId);
        return allConcepts.stream()
            .map(this::normalizeConcept)
            .filter(Objects::nonNull)
            .map(concept -> toResponse(concept, healthByConcept.get(concept), now, includeReviewTiming))
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
        return getDueConcepts(allConcepts, healthByConcept, now);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<String>> getDueConceptsByStudyPackIds(
        UUID userId,
        Map<UUID, List<String>> conceptsByStudyPackId,
        OffsetDateTime now
    ) {
        if (conceptsByStudyPackId == null || conceptsByStudyPackId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<String, ConceptHealthEntity>> healthByStudyPackId = new HashMap<>();
        List<UUID> studyPackIds = List.copyOf(conceptsByStudyPackId.keySet());
        for (ConceptHealthEntity entity : conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, studyPackIds)) {
            String concept = normalizeConcept(entity.getConcept());
            if (concept != null) {
                healthByStudyPackId
                    .computeIfAbsent(entity.getStudyPackId(), ignored -> new HashMap<>())
                    .put(concept, entity);
            }
        }

        Map<UUID, List<String>> dueConceptsByStudyPackId = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<String>> entry : conceptsByStudyPackId.entrySet()) {
            dueConceptsByStudyPackId.put(
                entry.getKey(),
                getDueConcepts(
                    entry.getValue(),
                    healthByStudyPackId.getOrDefault(entry.getKey(), Map.of()),
                    now
                )
            );
        }
        return Map.copyOf(dueConceptsByStudyPackId);
    }

    private List<String> getDueConcepts(
        List<String> allConcepts,
        Map<String, ConceptHealthEntity> healthByConcept,
        OffsetDateTime now
    ) {
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
        OffsetDateTime now,
        boolean includeReviewTiming
    ) {
        OffsetDateTime lastCorrectAt = resolveLastCorrectAt(entity);
        OffsetDateTime lastIncorrectAt = resolveLastIncorrectAt(entity);
        ConceptReadinessStatus readinessStatus = resolveReadinessStatus(lastCorrectAt, now);
        return new ConceptHealthEntryResponse(
            concept,
            readinessStatus,
            includeReviewTiming ? lastCorrectAt : null,
            includeReviewTiming ? lastIncorrectAt : null,
            includeReviewTiming && isStruggling(lastCorrectAt, lastIncorrectAt),
            isDue(lastCorrectAt, now),
            includeReviewTiming ? daysSinceReview(lastCorrectAt, now) : null
        );
    }

    private void recordAnswers(
        UUID userId,
        UUID studyPackId,
        List<String> conceptNames,
        OffsetDateTime now,
        boolean correct
    ) {
        if (conceptNames == null || conceptNames.isEmpty()) {
            return;
        }

        for (String rawConcept : conceptNames) {
            String concept = normalizeConcept(rawConcept);
            if (concept == null) {
                continue;
            }

            ConceptHealthEntity entity = conceptHealthRepository
                .findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, concept)
                .orElseGet(() -> buildConceptHealth(userId, studyPackId, concept, now));
            if (correct) {
                entity.setLastCorrectAt(now);
            } else {
                entity.setLastIncorrectAt(now);
            }
            entity.setUpdatedAt(now);
            conceptHealthRepository.save(entity);
        }
    }

    private List<String> filterKnownConcepts(List<String> conceptNames, List<String> keyConcepts) {
        if (conceptNames == null || conceptNames.isEmpty() || keyConcepts == null || keyConcepts.isEmpty()) {
            return List.of();
        }

        Set<String> knownConcepts = keyConcepts.stream()
            .map(this::normalizeConcept)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (knownConcepts.isEmpty()) {
            return List.of();
        }

        return conceptNames.stream()
            .map(this::normalizeConcept)
            .filter(Objects::nonNull)
            .filter(knownConcepts::contains)
            .distinct()
            .toList();
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

    private OffsetDateTime resolveLastIncorrectAt(ConceptHealthEntity entity) {
        return entity == null ? null : entity.getLastIncorrectAt();
    }

    private boolean isStruggling(OffsetDateTime lastCorrectAt, OffsetDateTime lastIncorrectAt) {
        return lastIncorrectAt != null && (lastCorrectAt == null || lastIncorrectAt.isAfter(lastCorrectAt));
    }

    private boolean canViewConceptReviewTiming(PlanType planType) {
        boolean hasPaidPlan = planType == PlanType.PLUS || planType == PlanType.PRO;
        return hasPaidPlan && featureGateService.hasFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);
    }

    private ConceptReadinessStatus resolveReadinessStatus(OffsetDateTime lastCorrectAt, OffsetDateTime now) {
        if (lastCorrectAt == null) {
            return ConceptReadinessStatus.NOT_STARTED;
        }
        return isDue(lastCorrectAt, now) ? ConceptReadinessStatus.DUE : ConceptReadinessStatus.MASTERED;
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
