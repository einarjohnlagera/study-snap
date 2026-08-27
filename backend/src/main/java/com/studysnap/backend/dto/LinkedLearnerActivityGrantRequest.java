package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

public record LinkedLearnerActivityGrantRequest(@NotNull Boolean granted) {
}
