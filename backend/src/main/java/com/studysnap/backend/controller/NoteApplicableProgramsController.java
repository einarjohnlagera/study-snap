package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.dto.NoteApplicableProgramsResponse;
import com.studysnap.backend.dto.ReplaceApplicableProgramsRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteApplicableProgramsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notes/{noteId}/applicable-programs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class NoteApplicableProgramsController {
    private final NoteApplicableProgramsService noteApplicableProgramsService;

    @GetMapping
    public NoteApplicableProgramsResponse get(
            @PathVariable String noteId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteApplicableProgramsService.get(noteId, user.userId());
    }

    @PutMapping
    public List<ApplicableProgramResponse> replace(
            @PathVariable String noteId,
            @Valid @RequestBody ReplaceApplicableProgramsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteApplicableProgramsService.replace(noteId, request.courseProgramIds(), user.userId());
    }
}
