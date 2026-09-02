package com.studysnap.backend.service;

import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.LongExamPrimarySourceNotEligibleException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Draws a bounded, representative Long Exam sample from the uncapped eligible plan pool.
 *
 * <p>Sections provide spread only. They are deliberately never weights: a large section has no
 * entitlement to more questions than a small section merely because it contains more notes.
 */
@Service
public class LongExamPlanSourceSampler {
    private static final String UNSECTIONED_BUCKET = "__unsectioned__";

    public List<EligiblePlanSource> sample(
            List<EligiblePlanSource> eligiblePool,
            UUID primaryStudyPackId,
            int sampleLimit,
            UUID sessionId
    ) {
        // ⚠️ A NAMED EXCEPTION, NOT A BARE orElseThrow. The primary is absent from the eligible pool when
        // its own Study Pack is not READY — findOwnedStudyPackForGenerationOrThrow does not filter on
        // status, so such a primary passes the start check and then vanishes here. A bare orElseThrow
        // surfaced as NoSuchElementException: a generic 500 with no error code, on a paid path.
        EligiblePlanSource primary = eligiblePool.stream()
                .filter(source -> source.studyPack().getId().equals(primaryStudyPackId))
                .findFirst()
                .orElseThrow(LongExamPrimarySourceNotEligibleException::new);
        int limit = Math.min(eligiblePool.size(), sampleLimit);
        if (limit <= 1) {
            return List.of(primary);
        }

        Random random = new Random(sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits());
        Map<String, List<EligiblePlanSource>> buckets = new LinkedHashMap<>();
        eligiblePool.stream()
                .filter(source -> !source.studyPack().getId().equals(primaryStudyPackId))
                .sorted(Comparator.comparingInt(EligiblePlanSource::position)
                        .thenComparing(source -> source.studyPack().getId()))
                .forEach(source -> buckets.computeIfAbsent(normalizeBucket(source.sectionLabel()), ignored -> new ArrayList<>())
                        .add(source));
        buckets.values().forEach(bucket -> java.util.Collections.shuffle(bucket, random));

        List<String> bucketOrder = new ArrayList<>(buckets.keySet());
        java.util.Collections.shuffle(bucketOrder, random);
        List<EligiblePlanSource> sampled = new ArrayList<>(limit);
        sampled.add(primary);

        // Round-robin across buckets before taking a second source from any bucket.
        while (sampled.size() < limit) {
            boolean added = false;
            for (String bucketName : bucketOrder) {
                List<EligiblePlanSource> bucket = buckets.get(bucketName);
                if (!bucket.isEmpty() && sampled.size() < limit) {
                    sampled.add(bucket.removeFirst());
                    added = true;
                }
            }
            if (!added) {
                break;
            }
        }
        return List.copyOf(sampled);
    }

    private String normalizeBucket(String label) {
        return label == null || label.isBlank() ? UNSECTIONED_BUCKET : label.trim();
    }

    public record EligiblePlanSource(StudyPackEntity studyPack, String sectionLabel, int position) {
    }
}
