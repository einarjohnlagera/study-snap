package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.security.SecurityUserContext;
import com.studysnap.backend.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ShareController {
	private final ShareService shareService;
	private final SecurityUserContext securityUserContext;

	@PostMapping("/studyPack/{id}/share")
	public ShareLinkResponse createShare(
			@PathVariable String id,
			Authentication authentication
	) {
		UUID userId = securityUserContext.requireUserId(authentication);
		return shareService.createShareLink(id, userId);
	}

	@GetMapping("/share/{token}")
	public PublicShareResponse getShared(@PathVariable String token) {
		return shareService.getPublicShare(token);
	}
}

