package com.studysnap.backend.controller;

import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/course-programs")
@RequiredArgsConstructor
public class CourseProgramController {

    private final NoteService noteService;

    @GetMapping
    public List<String> listCoursePrograms(
            @RequestParam(value = "scope", defaultValue = "public") String scope,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if ("mine".equalsIgnoreCase(scope)) {
            if (user == null) {
                throw new AppException("AUTHENTICATION_REQUIRED", "Authentication required.", HttpStatus.UNAUTHORIZED);
            }
            return noteService.listMineCoursePrograms(user.userId());
        }
        if ("public".equalsIgnoreCase(scope)) {
            return noteService.listPublicCoursePrograms();
        }
        throw new AppException("INVALID_COURSE_PROGRAM_SCOPE", "Invalid course/program scope.", HttpStatus.BAD_REQUEST);
    }
}
