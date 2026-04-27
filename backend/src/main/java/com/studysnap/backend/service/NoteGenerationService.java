package com.studysnap.backend.service;

import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteGenerationService {
    private final UserRepository userRepository;
    private final LlmStudyPackService llmStudyPackService;

    public GenerateNoteFromTopicResponse generateFromTopic(GenerateNoteFromTopicRequest request, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        String normalizedTopic = request.topic().trim();
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                user.getLearnerLevel(),
                CourseProgramNormalizationUtils.normalizeForStorage(user.getCourseProgram()),
                null,
                List.of()
        );
        String generatedContent = llmStudyPackService.generateNoteFromTopic(normalizedTopic, context);
        return new GenerateNoteFromTopicResponse(generatedContent);
    }
}
