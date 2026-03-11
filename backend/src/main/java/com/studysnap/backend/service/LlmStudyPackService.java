package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedStudyPackContent;

import java.util.List;

public interface LlmStudyPackService {
	GeneratedStudyPackContent generateStudyPack(String normalizedNotesText);

	String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries);
}

