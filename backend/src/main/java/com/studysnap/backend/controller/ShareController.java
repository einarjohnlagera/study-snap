package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.service.ShareService;
import lombok.RequiredArgsConstructor;
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

	@PostMapping("/review/{id}/share")
	public ShareLinkResponse createShare(@PathVariable String id) {
		return shareService.createShareLink(id);
	}

	@GetMapping("/share/{token}")
	public PublicShareResponse getShared(@PathVariable String token) {
		return shareService.getPublicShare(token);
	}
}
