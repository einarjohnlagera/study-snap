package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.StudyPackListItemResponse;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.StudyPackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/studyPack")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class StudyPackController {
	private final AuthService authService;
	private final StudyPackService studyPackService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public StudyPackResponse createFromText(
			@Valid @RequestBody CreateStudyPackRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		authService.requireEmailVerified(userId);
		return studyPackService.createFromText(request, userId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object createFromImage(
			@RequestPart("image") MultipartFile image,
			@RequestParam(value = "subject", required = false) String subject,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		authService.requireEmailVerified(userId);
		return studyPackService.createFromImage(image, subject, userId);
	}

	@PostMapping("/confirm-text")
	public StudyPackResponse confirmText(
			@Valid @RequestBody ConfirmTextRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		authService.requireEmailVerified(userId);
		return studyPackService.confirmExtractedText(request, userId);
	}

	@GetMapping("/{id}")
	public StudyPackResponse getById(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return studyPackService.getById(id, userId);
	}

	@GetMapping
	public List<StudyPackListItemResponse> listMine(
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return studyPackService.listMine(userId);
	}

	@DeleteMapping("/{id}")
	public void deleteMine(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		studyPackService.deleteMine(id, userId);
	}
}

