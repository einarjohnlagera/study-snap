package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.ProgramFamilyResponse;
import com.studysnap.backend.dto.UpdateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.UpdateProgramFamilyRequest;
import com.studysnap.backend.exception.UnknownProgramFamilyException;
import com.studysnap.backend.exception.CourseProgramNotFoundException;
import com.studysnap.backend.service.CourseProgramCatalogService;
import com.studysnap.backend.util.UuidParsingUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/course-program-catalog")
@RequiredArgsConstructor
@Validated
public class CourseProgramCatalogController {
    private final CourseProgramCatalogService courseProgramCatalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<CourseProgramCatalogItemResponse> list() {
        return courseProgramCatalogService.list();
    }

    @GetMapping("/similar")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CourseProgramCatalogItemResponse> similar(@RequestParam @NotBlank String name) {
        return courseProgramCatalogService.findSimilar(name);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CourseProgramCatalogItemResponse create(@Valid @RequestBody CreateCourseProgramCatalogRequest request) {
        return courseProgramCatalogService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseProgramCatalogItemResponse update(
            @PathVariable String id,
            @RequestBody UpdateCourseProgramCatalogRequest request
    ) {
        UUID programId = UuidParsingUtils.parseUuidOrThrow(id, CourseProgramNotFoundException::new);
        return courseProgramCatalogService.updateProgramFamilies(programId, request);
    }

    // ⚠️ A families READ is required, not optional: a newly created family has no members yet, and the
    // admin form derives its family options from the catalog. Without this endpoint a family created
    // today would vanish from the picker on refresh until a program was assigned to it.
    @GetMapping("/families")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProgramFamilyResponse> listProgramFamilies() {
        return courseProgramCatalogService.listProgramFamilies();
    }

    @PostMapping("/families")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramFamilyResponse createProgramFamily(@Valid @RequestBody CreateProgramFamilyRequest request) {
        return courseProgramCatalogService.createProgramFamily(request);
    }

    @PatchMapping("/families/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramFamilyResponse updateProgramFamily(
            @PathVariable String id,
            @Valid @RequestBody UpdateProgramFamilyRequest request
    ) {
        UUID familyId = UuidParsingUtils.parseUuidOrThrow(id, UnknownProgramFamilyException::new);
        return courseProgramCatalogService.updateProgramFamily(familyId, request);
    }
}
