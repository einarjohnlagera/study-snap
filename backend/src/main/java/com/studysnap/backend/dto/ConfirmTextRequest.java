package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmTextRequest(
		@NotBlank(message = "Draft id is required.")
		String draftId,

		@NotBlank(message = "Please provide notes text to continue.")
		@Size(max = 12000, message = "Notes are too long. Please shorten and try again.")
		String notesText
) {
}
