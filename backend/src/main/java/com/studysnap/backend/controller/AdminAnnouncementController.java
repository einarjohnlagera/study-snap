package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AnnouncementPublishResponse;
import com.studysnap.backend.dto.AnnouncementResponse;
import com.studysnap.backend.dto.UpsertAnnouncementRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

/**
 * Admin "What's New".
 *
 * <p>⚠️ THE CLASS-LEVEL {@code hasRole('ADMIN')} GATES EVERY HANDLER, and no method may weaken it —
 * {@code AdminAnnouncementControllerTest} enumerates the mapped methods and asserts none carries its
 * own {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/admin/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<List<AnnouncementResponse>> list() {
        return ResponseEntity.ok(announcementService.list());
    }

    @PostMapping
    public ResponseEntity<AnnouncementResponse> create(
            @Valid @RequestBody UpsertAnnouncementRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(announcementService.create(request, user.userId()));
    }

    /**
     * ⚠️ DRAFTS ONLY. Once published, content is immutable and this refuses with a named exception
     * rather than quietly accepting an edit that would never reach anybody.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertAnnouncementRequest request
    ) {
        return ResponseEntity.ok(announcementService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<AnnouncementPublishResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(announcementService.publish(id));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<AnnouncementResponse> end(@PathVariable UUID id) {
        return ResponseEntity.ok(announcementService.end(id));
    }
}
