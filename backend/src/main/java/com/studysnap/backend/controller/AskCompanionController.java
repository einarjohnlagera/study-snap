package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AskCompanionQuestionRequest;
import com.studysnap.backend.dto.AskCompanionSessionResponse;
import com.studysnap.backend.exception.AskCompanionSessionNotFoundException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AskCompanionService;
import com.studysnap.backend.util.UuidParsingUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AskCompanionController {
    private final AskCompanionService service;

    @PostMapping("/collections/{collectionId}/ask-companion/sessions")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AskCompanionSessionResponse startOrResume(
            @PathVariable String collectionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID parsedCollectionId = UuidParsingUtils.parseUuidOrThrow(
                collectionId,
                CollectionNotFoundException::new
        );
        return service.startOrResume(parsedCollectionId, user.userId());
    }

    @GetMapping("/collections/{collectionId}/ask-companion/sessions/active")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<AskCompanionSessionResponse> getActive(
            @PathVariable String collectionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID parsedCollectionId = UuidParsingUtils.parseUuidOrThrow(
                collectionId,
                CollectionNotFoundException::new
        );
        return service.getActive(parsedCollectionId, user.userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/ask-companion/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AskCompanionSessionResponse askQuestion(
            @PathVariable String sessionId,
            @Valid @RequestBody AskCompanionQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID parsedSessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionId,
                AskCompanionSessionNotFoundException::new
        );
        return service.askQuestion(parsedSessionId, user.userId(), request);
    }
}
