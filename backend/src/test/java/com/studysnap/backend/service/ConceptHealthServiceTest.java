package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
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

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, "Ohm's Law"))
            .thenReturn(Optional.empty());

        conceptHealthService.recordCorrectAnswers(userId, studyPackId, List.of("Ohm's Law"), now);

        ArgumentCaptor<ConceptHealthEntity> captor = ArgumentCaptor.forClass(ConceptHealthEntity.class);
        verify(conceptHealthRepository).save(captor.capture());
        ConceptHealthEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStudyPackId()).isEqualTo(studyPackId);
        assertThat(saved.getConcept()).isEqualTo("Ohm's Law");
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
        ConceptHealthEntity existing = conceptHealth(userId, studyPackId, "Recursion", previous);

        when(conceptHealthRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, "Recursion"))
            .thenReturn(Optional.of(existing));

        conceptHealthService.recordCorrectAnswers(userId, studyPackId, List.of("Recursion"), now);

        verify(conceptHealthRepository).save(existing);
        assertThat(existing.getLastCorrectAt()).isEqualTo(now);
        assertThat(existing.getUpdatedAt()).isEqualTo(now);
        assertThat(existing.getCreatedAt()).isEqualTo(previous);
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
    void getConceptHealth_marksConceptExactlyThreeDaysOldAsDue() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, "Capacitance", now.minusDays(3));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of("Capacitance"),
            now
        );

        assertThat(response).singleElement().satisfies(entry -> {
            assertThat(entry.isDue()).isTrue();
            assertThat(entry.daysSinceReview()).isEqualTo(3);
        });
    }

    @Test
    void getConceptHealth_marksRecentlyReviewedConceptAsCurrent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 28, 8, 0, 0, 0, ZoneOffset.UTC);
        ConceptHealthEntity health = conceptHealth(userId, studyPackId, "Indexes", now.minusDays(2));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId)).thenReturn(List.of(health));

        List<ConceptHealthEntryResponse> response = conceptHealthService.getConceptHealth(
            userId,
            studyPackId,
            List.of("Indexes"),
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
        ConceptHealthEntity oldest = conceptHealth(userId, studyPackId, "Oldest", now.minusDays(12));
        ConceptHealthEntity older = conceptHealth(userId, studyPackId, "Older", now.minusDays(5));
        ConceptHealthEntity current = conceptHealth(userId, studyPackId, "Current", now.minusDays(1));

        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPackId))
            .thenReturn(List.of(current, older, oldest));

        List<String> dueConcepts = conceptHealthService.getDueConcepts(
            userId,
            studyPackId,
            List.of("Never Seen", "Current", "Older", "Oldest"),
            now
        );

        assertThat(dueConcepts).containsExactly("Oldest", "Older", "Never Seen");
    }

    private ConceptHealthEntity conceptHealth(
        UUID userId,
        UUID studyPackId,
        String concept,
        OffsetDateTime lastCorrectAt
    ) {
        ConceptHealthEntity entity = new ConceptHealthEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStudyPackId(studyPackId);
        entity.setConcept(concept);
        entity.setLastCorrectAt(lastCorrectAt);
        entity.setCreatedAt(lastCorrectAt);
        entity.setUpdatedAt(lastCorrectAt);
        return entity;
    }
}
