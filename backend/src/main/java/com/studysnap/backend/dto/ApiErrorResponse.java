package com.studysnap.backend.dto;

public record ApiErrorResponse(
		String requestId,
		ApiError error
) {
	public record ApiError(
			String code,
			String message,
			String details
	) {
	}
}
