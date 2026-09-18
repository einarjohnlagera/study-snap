package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.UpdateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.UpdateProgramFamilyRequest;
import com.studysnap.backend.exception.CourseProgramNotFoundException;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
     * <p>Membership may be set at creation time because it belongs to the many-to-many relation,
     * rather than to either catalog entity. The family row and its initial membership set are written
     * atomically.
     */
    @Transactional
    public ProgramFamilyResponse createProgramFamily(CreateProgramFamilyRequest request) {
        String name = normalizeProgramFamilyName(request.name());
        String normalizedName = normalizeName(name);
        courseProgramCatalogRepository.findProgramFamilyByNormalizedName(normalizedName)
                .ifPresent(existing -> {
                    throw new ProgramFamilyNameConflictException(existing.name());
                });
        ProgramFamilyResponse family;
        try {
            family = courseProgramCatalogRepository.insertProgramFamily(name);
        } catch (DataIntegrityViolationException ignored) {
            // uk_program_families_name lost a race with a concurrent create. Resolve to the winner
            // rather than surfacing a constraint violation, matching the course-program create path.
            ProgramFamilyResponse existing = courseProgramCatalogRepository
                    .findProgramFamilyByNormalizedName(normalizedName)
                    .orElseThrow(CourseProgramCatalogWriteConflictException::new);
            throw new ProgramFamilyNameConflictException(existing.name());
        }
        List<UUID> programIds = validateAndDeduplicateProgramIds(request.programIds());
        if (!programIds.isEmpty()) {
            courseProgramCatalogRepository.replaceProgramMemberships(family.id(), programIds);
        }
        return family;
    }

    @Transactional
    public ProgramFamilyResponse updateProgramFamily(UUID familyId, UpdateProgramFamilyRequest request) {
        ProgramFamilyResponse current = courseProgramCatalogRepository.findProgramFamilyById(familyId)
                .orElseThrow(UnknownProgramFamilyException::new);

        String updatedName = current.name();
        if (request.name() != null) {
            updatedName = normalizeProgramFamilyName(request.name());
            String normalizedName = normalizeName(updatedName);
            courseProgramCatalogRepository.findOtherProgramFamilyByNormalizedName(normalizedName, familyId)
                    .ifPresent(existing -> {
                        throw new ProgramFamilyNameConflictException(existing.name());
                    });
            if (!updatedName.equals(current.name())) {
                try {
                    courseProgramCatalogRepository.updateProgramFamilyName(familyId, updatedName);
                } catch (DataIntegrityViolationException ignored) {
                    ProgramFamilyResponse existing = courseProgramCatalogRepository
                            .findOtherProgramFamilyByNormalizedName(normalizedName, familyId)
                            .orElseThrow(CourseProgramCatalogWriteConflictException::new);
                    throw new ProgramFamilyNameConflictException(existing.name());
                }
            }
        }

        if (request.programIds() != null) {
            List<UUID> programIds = validateAndDeduplicateProgramIds(request.programIds());
            courseProgramCatalogRepository.replaceProgramMemberships(familyId, programIds);
        }
        return new ProgramFamilyResponse(familyId, updatedName);
    }

    public List<CourseProgramCatalogItemResponse> findSimilar(String name) {
        String normalizedName = normalizeName(name);
        if (normalizedName.isEmpty()) {
            return List.of();
        }
        return courseProgramCatalogRepository.findSimilar(normalizedName);
    }

    @Transactional
    public CourseProgramCatalogItemResponse updateProgramFamilies(
            UUID programId,
            UpdateCourseProgramCatalogRequest request
    ) {
        courseProgramCatalogRepository.findById(programId)
                .orElseThrow(CourseProgramNotFoundException::new);

        if (request.programFamilyIds() != null) {
            List<UUID> familyIds = validateAndDeduplicateFamilyIds(request.programFamilyIds());
            courseProgramCatalogRepository.replaceProgramFamilies(programId, familyIds);
        }
        if (request.isActive() != null) {
            courseProgramCatalogRepository.updateIsActive(programId, request.isActive());
        }
        return courseProgramCatalogRepository.findById(programId)
                .orElseThrow(CourseProgramNotFoundException::new);
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

        List<UUID> familyIds = validateAndDeduplicateFamilyIds(request.effectiveProgramFamilyIds());

        String examGoalSlug = normalizeExamGoalSlug(request.examGoalSlug());
        try {
            UUID id = courseProgramCatalogRepository.insert(name, examGoalSlug);
            courseProgramCatalogRepository.insertProgramFamilies(id, familyIds);
            return courseProgramCatalogRepository.findById(id)
                    .orElseThrow(CourseProgramCatalogWriteConflictException::new);
        } catch (DataIntegrityViolationException ignored) {
            CourseProgramCatalogItemResponse existing = courseProgramCatalogRepository.findByNormalizedName(normalizedName)
                    .orElse(null);
            if (existing != null) {
                throw new CourseProgramCatalogNameConflictException(existing.name());
            }
            throw new CourseProgramCatalogWriteConflictException();
        }
    }

    private List<UUID> validateAndDeduplicateFamilyIds(List<UUID> requestedIds) {
        LinkedHashSet<UUID> familyIds = new LinkedHashSet<>(requestedIds == null ? List.of() : requestedIds);
        familyIds.forEach(id -> courseProgramCatalogRepository.findProgramFamilyName(id)
                .orElseThrow(UnknownProgramFamilyException::new));
        return List.copyOf(familyIds);
    }

    private List<UUID> validateAndDeduplicateProgramIds(List<UUID> requestedIds) {
        LinkedHashSet<UUID> programIds = new LinkedHashSet<>(requestedIds == null ? List.of() : requestedIds);
        programIds.forEach(id -> courseProgramCatalogRepository.findById(id)
                .orElseThrow(CourseProgramNotFoundException::new));
        return List.copyOf(programIds);
    }

    private String normalizeProgramFamilyName(String requestedName) {
        String name = CourseProgramNormalizationUtils.normalizeForStorage(requestedName);
        if (name.isBlank() || name.length() > 120) {
            throw new InvalidProgramFamilyNameException();
        }
        return name;
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
