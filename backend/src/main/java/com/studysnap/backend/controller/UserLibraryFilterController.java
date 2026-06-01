package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateSavedLibraryFilterRequest;
import com.studysnap.backend.dto.SavedLibraryFilterResponse;
import com.studysnap.backend.exception.SavedFilterNotFoundException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.UserLibraryFilterService;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/library-filters")
@RequiredArgsConstructor
public class UserLibraryFilterController {

    private final UserLibraryFilterService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<SavedLibraryFilterResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.list(user.userId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<SavedLibraryFilterResponse> create(
            @RequestBody CreateSavedLibraryFilterRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID filterId = UuidParsingUtils.parseUuidOrThrow(id, SavedFilterNotFoundException::new);
        service.delete(filterId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
