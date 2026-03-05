package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedReviewContent;

public interface LlmReviewService {
	GeneratedReviewContent generateReview(String normalizedNotesText);
}
