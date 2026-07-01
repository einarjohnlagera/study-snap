package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.AdoptStudyPlanResponse;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.PlanReadinessResponse;
import com.studysnap.backend.dto.GoalCollectionDetailResponse;
import com.studysnap.backend.dto.SetNoteCollectionParentRequest;
import com.studysnap.backend.dto.SetNoteCollectionChildrenOrderRequest;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.UpdateCollectionVisibilityRequest;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteCollectionService;
import com.studysnap.backend.util.UuidParsingUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/collections")
@RequiredArgsConstructor
public class NoteCollectionController {

    private final NoteCollectionService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteCollectionSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.list(user.userId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<NoteCollectionDetailResponse> create(
            @RequestBody CreateNoteCollectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping("/public")
    public List<NoteCollectionSummaryResponse> listPublic(
            @RequestParam(value = "courseProgram", required = false) String courseProgram
    ) {
        return service.listPublic(courseProgram);
    }

    @GetMapping("/public/{id}")
    public NoteCollectionDetailResponse getPublic(
            @PathVariable String id
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.getPublic(collectionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteCollectionDetailResponse get(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.get(collectionId, user.userId());
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PlanReadinessResponse getReadiness(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.getReadiness(collectionId, user.userId());
    }

    @GetMapping("/{id}/goal")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public GoalCollectionDetailResponse getGoal(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.getGoal(collectionId, user.userId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteCollectionDetailResponse updateMetadata(
            @PathVariable String id,
            @RequestBody UpdateNoteCollectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.updateMetadata(collectionId, user.userId(), request);
    }

    @PatchMapping("/{id}/parent")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteCollectionDetailResponse updateParent(
            @PathVariable String id,
            @RequestBody SetNoteCollectionParentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.updateParent(collectionId, user.userId(), request);
    }

    @PutMapping("/{id}/children/order")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public GoalCollectionDetailResponse setChildrenOrder(
            @PathVariable String id,
            @RequestBody SetNoteCollectionChildrenOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.setChildrenOrder(collectionId, user.userId(), request);
    }

    @PostMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public NoteCollectionDetailResponse updateVisibility(
            @PathVariable String id,
            @Valid @RequestBody UpdateCollectionVisibilityRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.updateVisibility(collectionId, user.userId(), request.visibility());
    }

    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AdoptStudyPlanResponse adopt(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.adopt(collectionId, user.userId());
    }

    @PostMapping("/{id}/adopt-goal")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AdoptGoalResponse adoptGoal(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.adoptGoal(collectionId, user.userId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        service.delete(collectionId, user.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteCollectionDetailResponse addItems(
            @PathVariable String id,
            @RequestBody AddNoteCollectionItemsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.addItems(collectionId, user.userId(), request);
    }

    @DeleteMapping("/{id}/items/{noteId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> removeItem(
            @PathVariable String id,
            @PathVariable String noteId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        UUID parsedNoteId = UuidParsingUtils.parseUuidOrThrow(noteId, CollectionNotFoundException::new);
        service.removeItem(collectionId, user.userId(), parsedNoteId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/items/order")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteCollectionDetailResponse setOrder(
            @PathVariable String id,
            @RequestBody SetNoteCollectionOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID collectionId = UuidParsingUtils.parseUuidOrThrow(id, CollectionNotFoundException::new);
        return service.setOrder(collectionId, user.userId(), request);
    }
}
