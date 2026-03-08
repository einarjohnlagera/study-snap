package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedStudyPackContent;

public interface LlmStudyPackService {
	GeneratedStudyPackContent generateStudyPack(String normalizedNotesText);
}

