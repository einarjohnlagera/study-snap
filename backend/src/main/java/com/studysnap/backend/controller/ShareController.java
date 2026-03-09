package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.service.ShareService;
import com.studysnap.backend.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ShareController {
	private final ShareService shareService;
	private final UserContextService userContextService;

	@PostMapping("/studyPack/{id}/share")
	public ShareLinkResponse createShare(
			@PathVariable String id,
			@RequestHeader(name = "X-User-Id", required = false) String userIdHeader
	) {
		UUID userId = userContextService.requireUserId(userIdHeader);
		return shareService.createShareLink(id, userId);
	}

	@GetMapping("/share/{token}")
	public PublicShareResponse getShared(@PathVariable String token) {
		return shareService.getPublicShare(token);
	}
}

