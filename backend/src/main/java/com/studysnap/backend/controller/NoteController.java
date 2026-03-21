package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse create(
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.create(request, userId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.update(id, request, userId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse getById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.getById(id, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public void deleteById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        noteService.deleteById(id, userId);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse copyNote(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.copyNote(id, userId);
    }

    @PostMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse updateVisibility(
            @PathVariable String id,
            @Valid @RequestBody UpdateNoteVisibilityRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.updateVisibility(id, request.visibility(), userId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteListItemResponse> listMine(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.listMine(userId);
    }

    @GetMapping("/public")
    public List<NoteListItemResponse> listPublic(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID viewerUserId = user == null ? null : user.userId();
        return noteService.listPublic(viewerUserId);
    }

    @GetMapping("/public/{id}")
    public PublicNoteDetailResponse getPublicById(
            @PathVariable String id
    ) {
        return noteService.getPublicById(id);
    }
}
