package com.studysnap.backend.service;

import com.studysnap.backend.entity.StudyPackEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LongExamPlanSourceSamplerTest {
    private final LongExamPlanSourceSampler sampler = new LongExamPlanSourceSampler();

    @Test
    void sample_spreadsAcrossCoverageBucketsBeforeExhaustingOneBucket() {
        UUID primaryId = UUID.randomUUID();
        List<LongExamPlanSourceSampler.EligiblePlanSource> pool = List.of(
                source(primaryId, "A", 0),
                source(UUID.randomUUID(), "A", 1),
                source(UUID.randomUUID(), "A", 2),
                source(UUID.randomUUID(), "A", 3),
                source(UUID.randomUUID(), "B", 4),
                source(UUID.randomUUID(), "C", 5)
        );

        List<LongExamPlanSourceSampler.EligiblePlanSource> sampled = sampler.sample(
                pool, primaryId, 4, UUID.fromString("00000000-0000-0000-0000-000000000105")
        );

        // ⚠️ The size assertion is what makes this test discriminate. Without it, returning the WHOLE
        // 6-item pool also "contains A, B, C" — so removing the sampleLimit clamp entirely left the
        // suite green, in the release whose whole point is that cap.
        assertThat(sampled).hasSize(4);
        assertThat(sampled).extracting(LongExamPlanSourceSampler.EligiblePlanSource::sectionLabel)
                .contains("A", "B", "C");
    }

    @Test
    void sample_neverReturnsMoreThanTheSampleLimit() {
        UUID primaryId = UUID.randomUUID();
        List<LongExamPlanSourceSampler.EligiblePlanSource> pool = new java.util.ArrayList<>();
        pool.add(source(primaryId, "A", 0));
        for (int index = 1; index < 77; index++) {
            pool.add(source(UUID.randomUUID(), "S" + (index % 6), index));
        }

        // A 77-member plan against the College cap of 8 must yield exactly 8, never the pool.
        assertThat(sampler.sample(pool, primaryId, 8, UUID.randomUUID())).hasSize(8);
        // And a limit above the pool size is bounded by the pool, not padded.
        assertThat(sampler.sample(pool, primaryId, 200, UUID.randomUUID())).hasSize(77);
    }

    @Test
    void sample_forceIncludesCallerPrimaryAtIndexZero() {
        UUID primaryId = UUID.randomUUID();
        List<LongExamPlanSourceSampler.EligiblePlanSource> pool = List.of(
                source(primaryId, "A", 0), source(UUID.randomUUID(), "B", 1), source(UUID.randomUUID(), "C", 2)
        );

        List<LongExamPlanSourceSampler.EligiblePlanSource> sampled = sampler.sample(pool, primaryId, 2, UUID.randomUUID());

        assertThat(sampled.getFirst().studyPack().getId()).isEqualTo(primaryId);
    }

    @Test
    void sample_isDeterministicForSessionId() {
        UUID primaryId = UUID.randomUUID();
        List<LongExamPlanSourceSampler.EligiblePlanSource> pool = List.of(
                source(primaryId, "A", 0), source(UUID.randomUUID(), "A", 1), source(UUID.randomUUID(), "B", 2),
                source(UUID.randomUUID(), "C", 3), source(UUID.randomUUID(), null, 4)
        );
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000105");

        List<UUID> first = sampler.sample(pool, primaryId, 4, sessionId).stream()
                .map(source -> source.studyPack().getId()).toList();
        List<UUID> second = sampler.sample(pool, primaryId, 4, sessionId).stream()
                .map(source -> source.studyPack().getId()).toList();

        assertThat(second).isEqualTo(first);
    }

    private LongExamPlanSourceSampler.EligiblePlanSource source(UUID id, String label, int position) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(id);
        return new LongExamPlanSourceSampler.EligiblePlanSource(studyPack, label, position);
    }
}
