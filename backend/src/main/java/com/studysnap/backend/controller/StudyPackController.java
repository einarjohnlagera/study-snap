package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.MemorizationCardResponse;
import com.studysnap.backend.dto.MemorizationGradeRequest;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.QuickReviewActivityRequest;
import com.studysnap.backend.dto.StudyPackListPageResponse;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.dto.UpdateStudyPackMetadataRequest;
import com.studysnap.backend.dto.UpdateStudyPackTagsRequest;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.MemorizationCardService;
import com.studysnap.backend.service.PostSessionNextStepService;
import com.studysnap.backend.service.StudyPackService;
import com.studysnap.backend.util.UuidParsingUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/study-packs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class StudyPackController {
	private final AuthService authService;
	private final StudyPackService studyPackService;
	private final ConceptHealthService conceptHealthService;
	private final MemorizationCardService memorizationCardService;
	private final PostSessionNextStepService postSessionNextStepService;

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

	@GetMapping("/{id}/concept-health")
	public List<ConceptHealthEntryResponse> getConceptHealth(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return conceptHealthService.getConceptHealthForOwnedStudyPack(
				userId,
				id,
				OffsetDateTime.now(ZoneOffset.UTC)
		);
	}

	@GetMapping("/{id}/memorization")
	public List<MemorizationCardResponse> getMemorizationCards(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return memorizationCardService.listForOwnedStudyPack(userId, id);
	}

	@PostMapping("/{id}/memorization/grade")
	public MemorizationCardResponse gradeMemorizationCard(
			@PathVariable String id,
			@Valid @RequestBody MemorizationGradeRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return memorizationCardService.recordGradeForOwnedStudyPack(
				userId,
				id,
				request.concept(),
				request.grade(),
				OffsetDateTime.now(ZoneOffset.UTC)
		);
	}

	@GetMapping("/{id}/next-step")
	public NextStepResponse getNextStep(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(id, StudyPackNotFoundException::new);
		return postSessionNextStepService.getNextStep(userId, studyPackId);
	}

	@GetMapping
	public StudyPackListPageResponse listMine(
			@RequestParam(value = "limit", required = false) Integer limit,
			@RequestParam(value = "cursor", required = false) String cursor,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return studyPackService.listMine(userId, limit, cursor);
	}

	@DeleteMapping("/{id}")
	public void deleteMine(
			@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		studyPackService.deleteMine(id, userId);
	}

	@PutMapping("/{id}/tags")
	public StudyPackResponse updateTags(
			@PathVariable String id,
			@Valid @RequestBody UpdateStudyPackTagsRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return studyPackService.updateTags(id, userId, request.tags());
	}

	@PutMapping("/{id}/metadata")
	public StudyPackResponse updateMetadata(
			@PathVariable String id,
			@Valid @RequestBody UpdateStudyPackMetadataRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		return studyPackService.updateMetadata(id, userId, request.title(), request.subject());
	}

	@PostMapping("/{id}/quick-review/activity")
	public void recordQuickReviewActivity(
			@PathVariable String id,
			@Valid @RequestBody QuickReviewActivityRequest request,
			@AuthenticationPrincipal AuthenticatedUser user
	) {
		UUID userId = user.userId();
		studyPackService.recordQuickReviewActivity(id, userId, request.activityType());
	}
}
