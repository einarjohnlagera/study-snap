package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStudyPackRequest(
		@NotBlank(message = "Please add notes text before generating a studyPack.")
		@Size(max = 12000, message = "Notes are too long. Please shorten and try again.")
		String notesText
) {
}

