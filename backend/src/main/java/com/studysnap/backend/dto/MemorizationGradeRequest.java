package com.studysnap.backend.dto;

import com.studysnap.backend.entity.MemorizationGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemorizationGradeRequest(
    @NotBlank String concept,
    @NotNull MemorizationGrade grade
) {
}
