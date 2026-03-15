package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.dto.ShareRemixResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ShareController {
	private final ShareService shareService;

	@PostMapping("/study-packs/{id}/share")
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ShareLinkResponse createShare(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		return shareService.createShareLink(id, user.userId());
	}

	@GetMapping("/share/{token}")
	public PublicShareResponse getShared(@PathVariable String token) {
		return shareService.getPublicShare(token);
	}

	@GetMapping("/p/{token}")
	public PublicShareResponse getSharedByPublicToken(@PathVariable String token) {
		return shareService.getPublicShare(token);
	}

	@PostMapping("/p/{token}/remix")
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ShareRemixResponse remixSharedStudyPack(
			@PathVariable String token,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		return shareService.remixSharedStudyPack(token, user.userId());
	}
}
