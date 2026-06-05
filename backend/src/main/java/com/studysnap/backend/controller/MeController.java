package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.MePlanService;
import com.studysnap.backend.service.ProgressReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {
    private final MePlanService mePlanService;
    private final ProgressReportService progressReportService;
    private final UserRepository userRepository;

    @GetMapping("/plan")
    @PreAuthorize("isAuthenticated()")
    public MePlanResponse getPlan(@AuthenticationPrincipal AuthenticatedUser user) {
        return mePlanService.getPlan(user.userId());
    }

    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public ProgressReportResponse getProgress(@AuthenticationPrincipal AuthenticatedUser user) {
        UserEntity userEntity = userRepository.findById(user.userId())
                .orElseThrow(UserNotFoundException::new);
        ProgressReportResponse response = progressReportService.getProgressReport(user.userId(), userEntity.getStudyGoal(), OffsetDateTime.now());
        return new ProgressReportResponse(
                response.subjects(),
                response.goalSummary(),
                response.userCoursePrograms(),
                userEntity.getProfileType() == null ? null : userEntity.getProfileType().name()
        );
    }
}
