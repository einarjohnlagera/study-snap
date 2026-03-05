package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateReviewRequest;
import com.studysnap.backend.dto.ReviewResponse;
import com.studysnap.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {
	private final ReviewService reviewService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ReviewResponse createFromText(@Valid @RequestBody CreateReviewRequest request) {
		return reviewService.createFromText(request);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object createFromImage(
			@RequestPart("image") MultipartFile image,
			@RequestParam(value = "subject", required = false) String subject
	) {
		return reviewService.createFromImage(image, subject);
	}

	@PostMapping("/confirm-text")
	public ReviewResponse confirmText(@Valid @RequestBody ConfirmTextRequest request) {
		return reviewService.confirmExtractedText(request);
	}

	@GetMapping("/{id}")
	public ReviewResponse getById(@PathVariable String id) {
		return reviewService.getById(id);
	}
}
