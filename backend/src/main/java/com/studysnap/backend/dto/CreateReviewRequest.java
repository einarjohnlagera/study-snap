package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
		@NotBlank(message = "Please add notes text before generating a review.")
		@Size(max = 12000, message = "Notes are too long. Please shorten and try again.")
		String notesText
) {
}
