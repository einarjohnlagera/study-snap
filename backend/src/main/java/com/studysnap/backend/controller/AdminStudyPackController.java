package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.service.AdminStudyPackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/study-packs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudyPackController {
    private final AdminStudyPackService adminStudyPackService;

    @PostMapping("/regenerate-summaries")
    public AdminRegenerateSummariesResponse regenerateSummaries() {
        return adminStudyPackService.regenerateOfficialSummaries();
    }
}
