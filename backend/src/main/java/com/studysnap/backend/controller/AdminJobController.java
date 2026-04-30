package com.studysnap.backend.controller;

import com.studysnap.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobController {
    private final SubscriptionService subscriptionService;

    @PostMapping("/subscription-expiry/{subscriptionId}")
    public ResponseEntity<Void> expireSubscription(@PathVariable UUID subscriptionId) {
        subscriptionService.expireSubscriptionAndDowngradeToFree(subscriptionId);
        return ResponseEntity.ok().build();
    }
}
