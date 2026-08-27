package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.GuardianConsentRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.dto.LinkedLearnerBirthYearCorrectionPreviewResponse;
import com.studysnap.backend.dto.LinkedLearnerActivityGrantRequest;
import com.studysnap.backend.dto.LinkedLearnerActivityGrantResponse;
import com.studysnap.backend.dto.LinkedLearnerActivityResponse;
import com.studysnap.backend.dto.LinkedLearnerProgressResponse;
import com.studysnap.backend.dto.LinkedLearnerInvitationResponse;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.RecordLinkedLearnerBirthYearRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.LinkedLearnerService;
import com.studysnap.backend.service.LinkedLearnerActivityService;
import com.studysnap.backend.service.LinkedLearnerGrantService;
import com.studysnap.backend.service.LinkedLearnerProgressService;
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
@RequestMapping("/linked-learners")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class LinkedLearnerController {
    private final LinkedLearnerService linkedLearnerService;
    private final LinkedLearnerProgressService linkedLearnerProgressService;
    private final LinkedLearnerGrantService linkedLearnerGrantService;
    private final LinkedLearnerActivityService linkedLearnerActivityService;

    @PostMapping("/invite")
    public SimpleMessageResponse invite(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InviteLinkedLearnerRequest request
    ) {
        return linkedLearnerService.invite(user.userId(), request);
    }

    @GetMapping("/invitations")
    public List<LinkedLearnerInvitationResponse> listInvitations(@AuthenticationPrincipal AuthenticatedUser user) {
        return linkedLearnerService.listInvitations(user.userId());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public LinkedLearnerResponse acceptInvitation(
            @PathVariable UUID invitationId,
            @Valid @RequestBody AcceptLinkedLearnerRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return linkedLearnerService.acceptInvitation(invitationId, user.userId(), request);
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public SimpleMessageResponse revokeInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return linkedLearnerService.revokeInvitation(invitationId, user.userId());
    }

    @GetMapping
    public List<LinkedLearnerResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return linkedLearnerService.list(user.userId());
    }

    @PostMapping("/birth-year/correction-preview")
    public LinkedLearnerBirthYearCorrectionPreviewResponse previewBirthYearCorrection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecordLinkedLearnerBirthYearRequest request
    ) {
        return linkedLearnerService.previewBirthYearCorrection(user.userId(), request.birthYear());
    }

    @PutMapping("/birth-year")
    public List<LinkedLearnerResponse> correctBirthYear(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecordLinkedLearnerBirthYearRequest request
    ) {
        return linkedLearnerService.correctBirthYear(user.userId(), request.birthYear());
    }

    @GetMapping("/{relationshipId}/progress")
    public LinkedLearnerProgressResponse getProgress(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return linkedLearnerProgressService.getProgress(user.userId(), relationshipId);
    }

    @PutMapping("/{relationshipId}/grants/activity")
    public LinkedLearnerActivityGrantResponse setActivityGrant(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody LinkedLearnerActivityGrantRequest request
    ) {
        return linkedLearnerGrantService.setActivityGrant(
                user.userId(), relationshipId, request.granted());
    }

    @GetMapping("/{relationshipId}/activity")
    public LinkedLearnerActivityResponse getActivity(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return linkedLearnerActivityService.getActivity(user.userId(), relationshipId);
    }

    @PostMapping("/{relationshipId}/accept")
    public LinkedLearnerResponse accept(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AcceptLinkedLearnerRequest request
    ) {
        return linkedLearnerService.accept(relationshipId, user.userId(), request);
    }

    @PostMapping("/{relationshipId}/birth-year")
    public LinkedLearnerResponse recordBirthYear(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecordLinkedLearnerBirthYearRequest request
    ) {
        return linkedLearnerService.recordBirthYear(relationshipId, user.userId(), request.birthYear());
    }

    @PostMapping("/{relationshipId}/guardian-consent")
    public LinkedLearnerResponse recordGuardianConsent(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody GuardianConsentRequest request
    ) {
        return linkedLearnerService.recordGuardianConsent(relationshipId, user.userId());
    }

    @PostMapping("/{relationshipId}/revoke")
    public LinkedLearnerResponse revoke(
            @PathVariable UUID relationshipId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return linkedLearnerService.revoke(relationshipId, user.userId());
    }
}
