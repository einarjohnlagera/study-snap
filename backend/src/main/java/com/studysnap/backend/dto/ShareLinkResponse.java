package com.studysnap.backend.dto;

public record ShareLinkResponse(
		String token,
		String shareUrl
) {
}
