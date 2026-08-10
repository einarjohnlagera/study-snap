package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.service.CourseProgramCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/course-program-catalog")
@RequiredArgsConstructor
public class CourseProgramCatalogController {
    private final CourseProgramCatalogService courseProgramCatalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<CourseProgramCatalogItemResponse> list() {
        return courseProgramCatalogService.list();
    }
}
