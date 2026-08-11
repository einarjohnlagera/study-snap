package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteApplicableProgramsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notes/applicable-programs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminNoteApplicableProgramsController {
    private static final String DEFAULT_PAGE = "0";
    private static final String DEFAULT_SIZE = "25";

    private final NoteApplicableProgramsService noteApplicableProgramsService;

    @GetMapping
    public AdminNoteApplicableProgramsPageResponse list(
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteApplicableProgramsService.getAdminPage(page, size, user.userId());
    }
}
