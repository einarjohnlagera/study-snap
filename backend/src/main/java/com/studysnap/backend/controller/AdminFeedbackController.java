package com.studysnap.backend.controller;

import com.studysnap.backend.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {
    private final FeedbackService feedbackService;

    @GetMapping("/{feedbackId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID feedbackId) {
        FeedbackService.FeedbackImageData image = feedbackService.getImageForAdmin(feedbackId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }
}
