package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class NoteController {

    private final AuthService authService;
    private final NoteService noteService;

    @PostMapping
    public NoteResponse create(
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        return noteService.create(request, userId);
    }

    @PutMapping("/{id}")
    public NoteResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        return noteService.update(id, request, userId);
    }
}
