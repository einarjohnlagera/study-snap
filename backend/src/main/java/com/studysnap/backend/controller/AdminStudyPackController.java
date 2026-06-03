package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminRepairMalformedQuizzesResponse;
import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.dto.AdminRegenerationStatusResponse;
import com.studysnap.backend.service.AdminStudyPackService;
import com.studysnap.backend.service.RegenerationProgressTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/study-packs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudyPackController {
    private final AdminStudyPackService adminStudyPackService;
    private final RegenerationProgressTracker progressTracker;

    @PostMapping("/regenerate-summaries")
    public AdminRegenerateSummariesResponse regenerateSummaries() {
        return adminStudyPackService.regenerateOfficialSummaries();
    }

    @PostMapping("/repair-malformed-quizzes")
    public AdminRepairMalformedQuizzesResponse repairMalformedQuizzes() {
        return adminStudyPackService.repairMalformedQuizzes();
    }

    @GetMapping("/regeneration-status")
    public AdminRegenerationStatusResponse regenerationStatus() {
        return progressTracker.getStatus();
    }
}
