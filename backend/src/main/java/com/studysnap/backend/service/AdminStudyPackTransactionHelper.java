package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminStudyPackTransactionHelper {
    private static final Logger log = LoggerFactory.getLogger(AdminStudyPackTransactionHelper.class);
    private static final String ENRICHED_SUMMARY_MARKER = "|";

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final LlmStudyPackService llmStudyPackService;

    @Transactional
    public void regenerateOnePack(StudyPackEntity pack) {
        if (pack == null || pack.getId() == null || pack.getNoteId() == null) {
            log.warn("Admin summary regeneration skipped study pack with missing identifiers");
            return;
        }

        try {
            StudyPackEntity currentPack = studyPackRepository.findById(pack.getId())
                    .orElse(null);
            if (currentPack == null) {
                log.warn("Admin summary regeneration skipped missing packId={}", pack.getId());
                return;
            }
            if (currentPack.getSummary() != null && currentPack.getSummary().contains(ENRICHED_SUMMARY_MARKER)) {
                return;
            }

            NoteEntity note = noteRepository.findById(currentPack.getNoteId())
                    .orElse(null);
            if (note == null) {
                log.warn("Admin summary regeneration skipped packId={} because source note was not found", currentPack.getId());
                return;
            }

            StudyPackGenerationContext context = buildContext(note);
            String newSummary = llmStudyPackService.regenerateSummary(note.getContent(), context);
            currentPack.setSummary(newSummary);
            studyPackRepository.save(currentPack);
        } catch (Exception ex) {
            log.warn(
                    "Admin summary regeneration failed for packId={} reason={}",
                    pack.getId(),
                    ex.getMessage(),
                    ex
            );
        }
    }

    private StudyPackGenerationContext buildContext(NoteEntity note) {
        List<String> tags = note.getTags() == null ? List.of() : Arrays.asList(note.getTags());
        return userRepository.findById(note.getOwnerUserId())
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        note.getCourseProgram(),
                        note.getSubject(),
                        tags
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        note.getCourseProgram(),
                        note.getSubject(),
                        tags
                ));
    }
}
