package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.service.StudyPackService;
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
@RequestMapping("/studyPack")
@RequiredArgsConstructor
public class StudyPackController {
	private final StudyPackService studyPackService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public StudyPackResponse createFromText(@Valid @RequestBody CreateStudyPackRequest request) {
		return studyPackService.createFromText(request);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object createFromImage(
			@RequestPart("image") MultipartFile image,
			@RequestParam(value = "subject", required = false) String subject
	) {
		return studyPackService.createFromImage(image, subject);
	}

	@PostMapping("/confirm-text")
	public StudyPackResponse confirmText(@Valid @RequestBody ConfirmTextRequest request) {
		return studyPackService.confirmExtractedText(request);
	}

	@GetMapping("/{id}")
	public StudyPackResponse getById(@PathVariable String id) {
		return studyPackService.getById(id);
	}
}

