package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NotificationResponse;
import com.studysnap.backend.dto.NotificationUnreadCountResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> listInbox(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return notificationService.listInbox(user.userId(), limit);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return new NotificationUnreadCountResponse(notificationService.countActionableUnread(user.userId()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return notificationService.markRead(user.userId(), id);
    }

    @PostMapping("/{id}/dismiss")
    public NotificationResponse dismiss(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return notificationService.dismiss(user.userId(), id);
    }
}
