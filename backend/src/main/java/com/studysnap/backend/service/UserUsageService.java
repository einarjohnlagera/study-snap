package com.studysnap.backend.service;

import com.studysnap.backend.repository.UserUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserUsageService {
    private final UserUsageRepository userUsageRepository;

    @Transactional(readOnly = true)
    public MonthlyUsage getMonthlyUsage(UUID userId, OffsetDateTime referenceTime) {
        MonthKey monthKey = MonthKey.from(referenceTime);
        return userUsageRepository.findByUserIdAndYearAndMonth(userId, monthKey.year(), monthKey.month())
                .map(usage -> new MonthlyUsage(
                        usage.getStudyPackGenerations(),
                        usage.getChallengeQuizGenerations(),
                        usage.getAdaptiveQuizGenerations()
                ))
                .orElse(MonthlyUsage.zero());
    }

    public void incrementStudyPackGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 1, 0, 0);
    }

    public void incrementChallengeQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 1, 0);
    }

    public void incrementAdaptiveQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 1);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta
    ) {
        MonthKey monthKey = MonthKey.from(occurredAt);
        userUsageRepository.incrementUsage(
                userId,
                monthKey.year(),
                monthKey.month(),
                studyPackDelta,
                challengeDelta,
                adaptiveDelta,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public record MonthlyUsage(
            int studyPackGenerations,
            int challengeQuizGenerations,
            int adaptiveQuizGenerations
    ) {
        public static MonthlyUsage zero() {
            return new MonthlyUsage(0, 0, 0);
        }
    }

    private record MonthKey(int year, int month) {
        private static MonthKey from(OffsetDateTime referenceTime) {
            OffsetDateTime utcTime = referenceTime == null ? OffsetDateTime.now(ZoneOffset.UTC) : referenceTime.withOffsetSameInstant(ZoneOffset.UTC);
            return new MonthKey(utcTime.getYear(), utcTime.getMonthValue());
        }
    }
}
