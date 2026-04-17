package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyPackGenerationContextResolver {
    private final UserRepository userRepository;

    public StudyPackGenerationContext resolve(UUID ownerUserId, NoteEntity note) {
        List<String> tags = note == null || note.getTags() == null
                ? List.of()
                : Arrays.asList(note.getTags());

        return userRepository.findById(ownerUserId)
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        note == null ? user.getCourseProgram() : firstNonBlank(note.getCourseProgram(), user.getCourseProgram()),
                        note == null ? null : note.getSubject(),
                        tags
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        note == null ? null : note.getCourseProgram(),
                        note == null ? null : note.getSubject(),
                        tags
                ));
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
