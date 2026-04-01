package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.service.PublicProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/profile")
@RequiredArgsConstructor
public class PublicProfileController {

    private final PublicProfileService publicProfileService;

    @GetMapping("/{userId}")
    public PublicProfileResponse getByUserId(@PathVariable String userId) {
        return publicProfileService.getByUserId(userId);
    }
}
