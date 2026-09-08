package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.exception.InvalidProgramFamilyNameException;
import com.studysnap.backend.exception.ProgramFamilyNameConflictException;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.ProgramFamilyResponse;
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

    public List<ProgramFamilyResponse> listProgramFamilies() {
        return courseProgramCatalogRepository.findAllProgramFamilies();
    }

    /**
     * Creates a Program Family.
     *
     * <p>⚠️ THIS EXISTS SO THAT A NEW FAMILY NO LONGER REQUIRES A MIGRATION. Before it, assigning a
     * family was possible from the admin surface but CREATING one was not, so every new family cost a
     * migration -- {@code V106} seeded Engineering and {@code V142} seeded Education for exactly that
     * reason. A third would have been a third migration.
     *
     * <p>⚠️ A FAMILY IS CREATED EMPTY AND THAT IS CORRECT. Membership is set on the program, through
     * the existing {@code programFamilyId} on create. Do not add member selection here -- the family is
     * a name plus a nullable FK, and the authoring expansion derives membership from the catalog.
     */
    @Transactional
    public ProgramFamilyResponse createProgramFamily(CreateProgramFamilyRequest request) {
        String name = CourseProgramNormalizationUtils.normalizeForStorage(request.name());
        if (name.length() > 120) {
            throw new InvalidProgramFamilyNameException();
        }
        String normalizedName = normalizeName(name);
        courseProgramCatalogRepository.findProgramFamilyByNormalizedName(normalizedName)
                .ifPresent(existing -> {
                    throw new ProgramFamilyNameConflictException(existing.name());
                });
        try {
            return courseProgramCatalogRepository.insertProgramFamily(name);
        } catch (DataIntegrityViolationException ignored) {
            // uk_program_families_name lost a race with a concurrent create. Resolve to the winner
            // rather than surfacing a constraint violation, matching the course-program create path.
            ProgramFamilyResponse existing = courseProgramCatalogRepository
                    .findProgramFamilyByNormalizedName(normalizedName)
                    .orElseThrow(CourseProgramCatalogWriteConflictException::new);
            throw new ProgramFamilyNameConflictException(existing.name());
        }
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
