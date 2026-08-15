package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.exception.CourseProgramCatalogNameConflictException;
import com.studysnap.backend.exception.InvalidExamGoalSlugException;
import com.studysnap.backend.exception.InvalidCourseProgramCatalogNameException;
import com.studysnap.backend.exception.CourseProgramCatalogWriteConflictException;
import com.studysnap.backend.exception.UnknownProgramFamilyException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CourseProgramCatalogService {
    private static final Set<String> ALLOWED_EXAM_GOAL_SLUGS = Set.of("ale", "pnle", "let", "cpale");

    private final CourseProgramCatalogRepository courseProgramCatalogRepository;

    public List<CourseProgramCatalogItemResponse> list() {
        return courseProgramCatalogRepository.findAll();
    }

    public List<CourseProgramCatalogItemResponse> findSimilar(String name) {
        String normalizedName = normalizeName(name);
        if (normalizedName.isEmpty()) {
            return List.of();
        }
        return courseProgramCatalogRepository.findSimilar(normalizedName);
    }

    @Transactional
    public CourseProgramCatalogItemResponse create(CreateCourseProgramCatalogRequest request) {
        String name = CourseProgramNormalizationUtils.normalizeForStorage(request.name());
        if (name.length() > 120) {
            throw new InvalidCourseProgramCatalogNameException();
        }
        String normalizedName = normalizeName(name);
        courseProgramCatalogRepository.findByNormalizedName(normalizedName)
                .ifPresent(existing -> {
                    throw new CourseProgramCatalogNameConflictException(existing.name());
                });

        String familyName = null;
        if (request.programFamilyId() != null) {
            familyName = courseProgramCatalogRepository.findProgramFamilyName(request.programFamilyId())
                    .orElseThrow(UnknownProgramFamilyException::new);
        }

        String examGoalSlug = normalizeExamGoalSlug(request.examGoalSlug());
        try {
            return courseProgramCatalogRepository.insert(name, request.programFamilyId(), familyName, examGoalSlug);
        } catch (DataIntegrityViolationException ignored) {
            CourseProgramCatalogItemResponse existing = courseProgramCatalogRepository.findByNormalizedName(normalizedName)
                    .orElse(null);
            if (existing != null) {
                throw new CourseProgramCatalogNameConflictException(existing.name());
            }
            throw new CourseProgramCatalogWriteConflictException();
        }
    }

    private String normalizeName(String name) {
        return CourseProgramNormalizationUtils.normalizeForLookup(name);
    }

    private String normalizeExamGoalSlug(String examGoalSlug) {
        if (examGoalSlug == null || examGoalSlug.isBlank()) {
            return null;
        }
        String normalizedSlug = examGoalSlug.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXAM_GOAL_SLUGS.contains(normalizedSlug)) {
            throw new InvalidExamGoalSlugException();
        }
        return normalizedSlug;
    }
}
