package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CourseProgramCatalogService {
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;

    public List<CourseProgramCatalogItemResponse> list() {
        return courseProgramCatalogRepository.findAll();
    }
}
