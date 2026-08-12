package com.studysnap.backend.service;

import com.studysnap.backend.exception.OfficialStudyPlanWishlistProgramRequiredException;
import com.studysnap.backend.repository.OfficialStudyPlanWishlistRepository;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfficialStudyPlanWishlistService {
    private final OfficialStudyPlanWishlistRepository wishlistRepository;

    @Transactional
    public boolean requestPlan(UUID userId, String courseProgram) {
        String storedProgram = courseProgram == null ? null : courseProgram.trim();
        String normalizedProgram = CourseProgramNormalizationUtils.normalizeForLookup(courseProgram);
        requireProgram(storedProgram, normalizedProgram);

        if (wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, normalizedProgram)) {
            return true;
        }

        wishlistRepository.insertIfAbsent(
                UUID.randomUUID(),
                userId,
                storedProgram,
                normalizedProgram,
                OffsetDateTime.now()
        );
        return true;
    }

    @Transactional(readOnly = true)
    public boolean hasRequestedPlan(UUID userId, String courseProgram) {
        String normalizedProgram = CourseProgramNormalizationUtils.normalizeForLookup(courseProgram);
        if (normalizedProgram.isBlank()) {
            return false;
        }
        return wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, normalizedProgram);
    }

    private void requireProgram(String storedProgram, String normalizedProgram) {
        if (storedProgram == null || storedProgram.isBlank() || normalizedProgram.isBlank()) {
            throw new OfficialStudyPlanWishlistProgramRequiredException();
        }
    }
}
