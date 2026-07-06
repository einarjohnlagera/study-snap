package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.UpdateExamDateRequest;
import com.studysnap.backend.dto.UpdateFocusSubjectsRequest;
import com.studysnap.backend.dto.UpdateStudyGoalRequest;
import com.studysnap.backend.dto.UpdateStudyDaysPerWeekRequest;
import com.studysnap.backend.dto.UpdatePublicProfileVisibilityRequest;
import com.studysnap.backend.dto.UpdateUserProfileRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final AuthService authService;

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return authService.updateUserProfile(user.userId(), request);
    }

    @PutMapping("/profile/exam-date")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateExamDate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody UpdateExamDateRequest request
    ) {
        return authService.updateExamDate(user.userId(), request);
    }

    @PutMapping("/profile/goal")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateStudyGoal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody UpdateStudyGoalRequest request
    ) {
        return authService.updateStudyGoal(user.userId(), request);
    }

    @PutMapping("/profile/focus-subjects")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateFocusSubjects(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody UpdateFocusSubjectsRequest request
    ) {
        return authService.updateFocusSubjects(user.userId(), request);
    }

    @PutMapping("/profile/public-visibility")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updatePublicProfileVisibility(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdatePublicProfileVisibilityRequest request
    ) {
        return authService.updatePublicProfileVisibility(user.userId(), request);
    }

    @PutMapping("/profile/study-days-per-week")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateStudyDaysPerWeek(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateStudyDaysPerWeekRequest request
    ) {
        return authService.updateStudyDaysPerWeek(user.userId(), request);
    }
}
