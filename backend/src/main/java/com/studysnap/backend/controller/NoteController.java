package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public NoteResponse create(
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.create(request, userId);
    }

    @PutMapping("/{id}")
    public NoteResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.update(id, request, userId);
    }

    @GetMapping("/{id}")
    public NoteResponse getById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.getById(id, userId);
    }

    @PostMapping("/{id}/clone")
    public NoteResponse cloneNote(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.cloneNote(id, userId);
    }

    @GetMapping
    public List<NoteListItemResponse> listMine(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.listMine(userId);
    }
}
