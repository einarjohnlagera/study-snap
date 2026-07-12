package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.ConceptReadinessStatus;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConceptHealthServiceTest {
    private static final String OHMS_LAW_CONCEPT = "Ohm's Law";
    private static final String RECURSION_CONCEPT = "Recursion";
    private static final String CAPACITANCE_CONCEPT = "Capacitance";
    private static final String INDEXES_CONCEPT = "Indexes";
    private static final String OLDEST_CONCEPT = "Oldest";
    private static final String OLDER_CONCEPT = "Older";
    private static final String CURRENT_CONCEPT = "Current";
    private static final String NEVER_SEEN_CONCEPT = "Never Seen";
    private static final String SECOND_PACK_NEVER_SEEN_CONCEPT = "Second Pack Never Seen";
    private static final String MISSED_CONCEPT = "Missed Concept";
    private static final String FREE_FORM_LABEL = "Free-form label";

    @Mock
    private ConceptHealthRepository conceptHealthRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;

    private ConceptHealthService conceptHealthService;

    @BeforeEach
    void setUp() {
        conceptHealthService = new ConceptHealthService(
            conceptHealthRepository,
            studyPackRepository,
            subscriptionService,
            featureGateService
        );
    }

    @Test
    void recordCorrectAnswers_insertsNewRecordsForFirstTimeConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, OHMS_LAW_CONCEPT))
            .thenReturn(Optional.empty());

        conceptHealthService.recordCorrectAnswers(userId, studyPackId, List.of(OHMS_LAW_CONCEPT), now);

        ArgumentCaptor<ConceptHealthEntity> captor = ArgumentCaptor.forClass(ConceptHealthEntity.class);
        verify(conceptHealthRepository).save(captor.capture());
        ConceptHealthEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStudyPackId()).isEqualTo(studyPackId);
        assertThat(saved.getConcept()).isEqualTo(OHMS_LAW_CONCEPT);
        assertThat(saved.getLastCorrectAt()).isEqualTo(now);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void recordCorrectAnswers_updatesLastCorrectAtForExistingRecord() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime previous = OffsetDateTime.of(2026, 5, 20, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity existing = conceptHealth(userId, studyPackId, RECURSION_CONCEPT, previous);
        existing.setLastIncorrectAt(now.minusDays(1));

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, RECURSION_CONCEPT))
            .thenReturn(Optional.of(existing));

        conceptHealthService.recordCorrectAnswers(userId, studyPackId, List.of(RECURSION_CONCEPT), now);

        verify(conceptHealthRepository).save(existing);
        assertThat(existing.getLastCorrectAt()).isEqualTo(now);
        assertThat(existing.getLastIncorrectAt()).isEqualTo(now.minusDays(1));
        assertThat(existing.getUpdatedAt()).isEqualTo(now);
        assertThat(existing.getCreatedAt()).isEqualTo(previous);
    }

    @Test
    void recordIncorrectAnswers_setsLastIncorrectAtWithoutTouchingLastCorrectAt() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime previous = OffsetDateTime.of(2026, 5, 20, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity existing = conceptHealth(userId, studyPackId, MISSED_CONCEPT, previous);

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, MISSED_CONCEPT))
            .thenReturn(Optional.of(existing));

        conceptHealthService.recordIncorrectAnswers(userId, studyPackId, List.of(MISSED_CONCEPT), now);

        verify(conceptHealthRepository).save(existing);
        assertThat(existing.getLastCorrectAt()).isEqualTo(previous);
        assertThat(existing.getLastIncorrectAt()).isEqualTo(now);
        assertThat(existing.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void recordIncorrectAnswers_insertsMissOnlyRecordWithNullLastCorrectAt() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, MISSED_CONCEPT))
            .thenReturn(Optional.empty());

        conceptHealthService.recordIncorrectAnswers(userId, studyPackId, List.of(MISSED_CONCEPT), now);

        ArgumentCaptor<ConceptHealthEntity> captor = ArgumentCaptor.forClass(ConceptHealthEntity.class);
        verify(conceptHealthRepository).save(captor.capture());
        ConceptHealthEntity saved = captor.getValue();
        assertThat(saved.getLastCorrectAt()).isNull();
        assertThat(saved.getLastIncorrectAt()).isEqualTo(now);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void recordCorrectAnswers_skipsBlankAndNullConceptNames() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);

        conceptHealthService.recordCorrectAnswers(userId, studyPackId, List.of(" ", ""), now);
        conceptHealthService.recordCorrectAnswers(userId, studyPackId, Collections.singletonList(null), now);

        verify(conceptHealthRepository, never()).findByUserIdAndStudyPackIdAndConcept(any(), any(), any());
        verify(conceptHealthRepository, never()).save(any());
    }

    @Test
    void recordCorrectAnswersForKnownConcepts_recordsOnlyConceptsPresentInKeyConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, OHMS_LAW_CONCEPT))
            .thenReturn(Optional.empty());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, RECURSION_CONCEPT))
            .thenReturn(Optional.empty());

        conceptHealthService.recordCorrectAnswersForKnownConcepts(
            userId,
            studyPackId,
            List.of(" " + OHMS_LAW_CONCEPT + " ", FREE_FORM_LABEL, RECURSION_CONCEPT, RECURSION_CONCEPT),
            List.of(OHMS_LAW_CONCEPT, " " + RECURSION_CONCEPT + " "),
            now
        );

        ArgumentCaptor<ConceptHealthEntity> captor = ArgumentCaptor.forClass(ConceptHealthEntity.class);
        verify(conceptHealthRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(ConceptHealthEntity::getConcept)
            .containsExactly(OHMS_LAW_CONCEPT, RECURSION_CONCEPT);
    }

    @Test
    void recordIncorrectAnswersForKnownConcepts_recordsOnlyConceptsPresentInKeyConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, OHMS_LAW_CONCEPT))
            .thenReturn(Optional.empty());

        conceptHealthService.recordIncorrectAnswersForKnownConcepts(
            userId,
            studyPackId,
            List.of(" " + OHMS_LAW_CONCEPT + " ", FREE_FORM_LABEL),
            List.of(OHMS_LAW_CONCEPT),
            now
        );

        ArgumentCaptor<ConceptHealthEntity> captor = ArgumentCaptor.forClass(ConceptHealthEntity.class);
        verify(conceptHealthRepository).save(captor.capture());
        ConceptHealthEntity saved = captor.getValue();
        assertThat(saved.getConcept()).isEqualTo(OHMS_LAW_CONCEPT);
        assertThat(saved.getLastIncorrectAt()).isEqualTo(now);
        assertThat(saved.getLastCorrectAt()).isNull();
    }

    @Test
    void recordCorrectAnswersForKnownConcepts_noopsWhenNoCorrectConceptMatchesKeyConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);

        conceptHealthService.recordCorrectAnswersForKnownConcepts(
            userId,
            studyPackId,
            List.of(FREE_FORM_LABEL),
            List.of(OHMS_LAW_CONCEPT),
            now
        );

        verify(conceptHealthRepository, never()).findByUserIdAndStudyPackIdAndConcept(any(), any(), any());
        verify(conceptHealthRepository, never()).save(any());
    }

    @Test
    void getConceptHealth_marksConceptStrugglingWhenLastIncorrectIsNewerThanLastCorrect() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, MISSED_CONCEPT, now.minusDays(2));
        health.setLastIncorrectAt(now.minusHours(1));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of(MISSED_CONCEPT),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.lastIncorrectAt()).isEqualTo(now.minusHours(1));
            assertThat(entry.isStruggling()).isTrue();
        });
    }

    @Test
    void getConceptHealth_clearsStrugglingWhenLastCorrectIsNewerThanLastIncorrect() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, CURRENT_CONCEPT, now.minusHours(1));
        health.setLastIncorrectAt(now.minusDays(1));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of(CURRENT_CONCEPT),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> assertThat(entry.isStruggling()).isFalse());
    }

    @Test
    void getConceptHealth_marksMissOnlyConceptStrugglingAndDue() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealthWithSignals(userId, studyPackId, MISSED_CONCEPT, null, now.minusHours(1), now);

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of(MISSED_CONCEPT),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.lastCorrectAt()).isNull();
            assertThat(entry.lastIncorrectAt()).isEqualTo(now.minusHours(1));
            assertThat(entry.isStruggling()).isTrue();
            assertThat(entry.isDue()).isTrue();
            assertThat(entry.daysSinceReview()).isNull();
        });
    }

    @Test
    void getConceptHealth_marksConceptExactlyThreeDaysOldAsDue() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, CAPACITANCE_CONCEPT, now.minusDays(3));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of(CAPACITANCE_CONCEPT),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.isDue()).isTrue();
            assertThat(entry.daysSinceReview()).isEqualTo(3);
        });
    }

    @Test
    void canViewConceptHealth_allowsPaidPlanWithAdaptiveFeature() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(featureGateService.hasFeatureAccess(PlanType.PLUS, Feature.ADAPTIVE_QUIZ)).thenReturn(true);

        boolean result = conceptHealthService.canViewConceptHealth(userId);

        assertThat(result).isTrue();
    }

    @Test
    void canViewConceptHealth_rejectsFreeEvenWhenAdaptiveFeatureIsAvailable() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        boolean result = conceptHealthService.canViewConceptHealth(userId);

        assertThat(result).isFalse();
        verify(featureGateService, never()).hasFeatureAccess(any(PlanType.class), any(Feature.class));
    }

    @Test
    void canViewConceptHealth_rejectsPaidPlanWithoutAdaptiveFeature() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(featureGateService.hasFeatureAccess(PlanType.PLUS, Feature.ADAPTIVE_QUIZ)).thenReturn(false);

        boolean result = conceptHealthService.canViewConceptHealth(userId);

        assertThat(result).isFalse();
    }

    @Test
    void getConceptHealthForOwnedStudyPack_returnsDueSignalWithLastCorrectAtForFreePlan() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        StudyPackEntity studyPack = studyPack(studyPackId, userId, List.of(CAPACITANCE_CONCEPT, NEVER_SEEN_CONCEPT));
        ConceptHealthEntity staleHealth = conceptHealth(userId, studyPackId, CAPACITANCE_CONCEPT, now.minusDays(4));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(staleHealth));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealthForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            now
        );

        assertThat(response).hasSize(2);
        assertThat(response.getFirst()).satisfies(entry -> {
            assertThat(entry.concept()).isEqualTo(CAPACITANCE_CONCEPT);
            assertThat(entry.readinessStatus()).isEqualTo(ConceptReadinessStatus.DUE);
            assertThat(entry.isDue()).isTrue();
            assertThat(entry.lastCorrectAt()).isEqualTo(now.minusDays(4));
            assertThat(entry.lastIncorrectAt()).isNull();
            assertThat(entry.daysSinceReview()).isNull();
        });
        assertThat(response.get(1).readinessStatus()).isEqualTo(ConceptReadinessStatus.NOT_STARTED);
        verify(featureGateService, never()).checkFeatureAccess(any(PlanType.class), any(Feature.class));
        verify(featureGateService, never()).hasFeatureAccess(any(PlanType.class), any(Feature.class));
    }

    @Test
    void getConceptHealthForOwnedStudyPack_returnsReviewTimingForPaidPlan() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        StudyPackEntity studyPack = studyPack(studyPackId, userId, List.of(CAPACITANCE_CONCEPT));
        ConceptHealthEntity staleHealth = conceptHealth(userId, studyPackId, CAPACITANCE_CONCEPT, now.minusDays(4));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(featureGateService.hasFeatureAccess(PlanType.PLUS, Feature.ADAPTIVE_QUIZ)).thenReturn(true);
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(staleHealth));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealthForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.readinessStatus()).isEqualTo(ConceptReadinessStatus.DUE);
            assertThat(entry.lastCorrectAt()).isEqualTo(now.minusDays(4));
            assertThat(entry.daysSinceReview()).isEqualTo(4);
        });
    }

    @Test
    void getConceptHealth_marksRecentlyReviewedConceptAsCurrent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, INDEXES_CONCEPT, now.minusDays(2));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of(INDEXES_CONCEPT),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.isDue()).isFalse();
            assertThat(entry.daysSinceReview()).isEqualTo(2);
        });
    }

    @Test
    void getConceptHealth_marksNeverReviewedConceptAsDueWithoutDaysSinceReview() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of());

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of("Load Balancing"),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.isDue()).isTrue();
            assertThat(entry.daysSinceReview()).isNull();
            assertThat(entry.lastCorrectAt()).isNull();
        });
    }

    @Test
    void getDueConcepts_sortsOldestReviewedFirstAndNeverSeenLast() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity oldest = conceptHealth(userId, studyPackId, OLDEST_CONCEPT, now.minusDays(12));
        ConceptHealthEntity older = conceptHealth(userId, studyPackId, OLDER_CONCEPT, now.minusDays(5));
        ConceptHealthEntity current = conceptHealth(userId, studyPackId, CURRENT_CONCEPT, now.minusDays(1));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId))
            .thenReturn(List.of(current, older, oldest));

        List<String> dueConcepts = conceptHealthService.getDueConcepts(
            userId,
            studyPackId,
            List.of(NEVER_SEEN_CONCEPT, CURRENT_CONCEPT, OLDER_CONCEPT, OLDEST_CONCEPT),
            now
        );

        assertThat(dueConcepts).containsExactly(OLDEST_CONCEPT, OLDER_CONCEPT, NEVER_SEEN_CONCEPT);
    }

    @Test
    void getDueConceptsByStudyPackIdsLoadsHealthOnceAndPreservesPerPackOrdering() {
        UUID userId = UUID.randomUUID();
        UUID firstStudyPackId = UUID.randomUUID();
        UUID secondStudyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity oldest = conceptHealth(userId, firstStudyPackId, OLDEST_CONCEPT, now.minusDays(10));
        ConceptHealthEntity current = conceptHealth(userId, firstStudyPackId, CURRENT_CONCEPT, now.minusDays(1));
        Map<UUID, List<String>> conceptsByStudyPackId = new LinkedHashMap<>();
        conceptsByStudyPackId.put(firstStudyPackId, List.of(NEVER_SEEN_CONCEPT, CURRENT_CONCEPT, OLDEST_CONCEPT));
        conceptsByStudyPackId.put(secondStudyPackId, List.of(SECOND_PACK_NEVER_SEEN_CONCEPT));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(
            userId,
            List.of(firstStudyPackId, secondStudyPackId)
        )).thenReturn(List.of(current, oldest));

        Map<UUID, List<String>> result = conceptHealthService.getDueConceptsByStudyPackIds(
            userId,
            conceptsByStudyPackId,
            now
        );

        assertThat(result.get(firstStudyPackId)).containsExactly(OLDEST_CONCEPT, NEVER_SEEN_CONCEPT);
        assertThat(result.get(secondStudyPackId)).containsExactly(SECOND_PACK_NEVER_SEEN_CONCEPT);
        verify(conceptHealthRepository, times(1)).findByUserIdAndStudyPackIdIn(
            userId,
            List.of(firstStudyPackId, secondStudyPackId)
        );
        verify(conceptHealthRepository, never()).findByUserIdAndStudyPackId(any(), any());
    }

    private ConceptHealthEntity conceptHealth(
        UUID userId,
        UUID studyPackId,
        String concept,
        OffsetDateTime lastCorrectAt
    ) {
        return conceptHealthWithSignals(userId, studyPackId, concept, lastCorrectAt, null, lastCorrectAt);
    }

    private ConceptHealthEntity conceptHealthWithSignals(
        UUID userId,
        UUID studyPackId,
        String concept,
        OffsetDateTime lastCorrectAt,
        OffsetDateTime lastIncorrectAt,
        OffsetDateTime createdAt
    ) {
        ConceptHealthEntity entity = new ConceptHealthEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStudyPackId(studyPackId);
        entity.setConcept(concept);
        entity.setLastCorrectAt(lastCorrectAt);
        entity.setLastIncorrectAt(lastIncorrectAt);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return entity;
    }

    private StudyPackEntity studyPack(UUID studyPackId, UUID userId, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setKeyConcepts(keyConcepts);
        return studyPack;
    }
}
