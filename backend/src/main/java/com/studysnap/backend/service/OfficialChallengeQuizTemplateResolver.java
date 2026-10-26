package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the single Official source hop shared by template copying and bank eligibility. */
@Component
@RequiredArgsConstructor
public class OfficialChallengeQuizTemplateResolver {
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final ChallengeQuizQuestionBankRepository questionBankRepository;

    public Optional<OfficialChallengeQuizTemplateService.OfficialTemplateKey> resolveTemplate(UUID callerStudyPackId) {
        StudyPackEntity callerPack = studyPackRepository.findById(callerStudyPackId).orElse(null);
        if (callerPack == null || callerPack.getNoteId() == null) {
            return Optional.empty();
        }
        NoteEntity callerNote = noteRepository.findById(callerPack.getNoteId()).orElse(null);
        if (callerNote == null) {
            return Optional.empty();
        }
        if (callerNote.getCopiedFromNoteId() == null) {
            return Optional.empty();
        }
        NoteEntity sourceNote = noteRepository.findById(callerNote.getCopiedFromNoteId()).orElse(null);
        if (sourceNote == null || sourceNote.getVisibility() != NoteVisibility.PUBLIC
                || !isOfficialAuthor(sourceNote.getOwnerUserId())) {
            return Optional.empty();
        }
        StudyPackEntity sourcePack = studyPackRepository.findByNoteId(sourceNote.getId()).orElse(null);
        if (sourcePack == null || !sourceNote.getOwnerUserId().equals(sourcePack.getOwnerUserId())) {
            return Optional.empty();
        }
        return Optional.of(new OfficialChallengeQuizTemplateService.OfficialTemplateKey(
                sourceNote.getOwnerUserId(), sourcePack.getId()));
    }

    public Set<String> templateQuestionKeys(UUID studyPackId) {
        StudyPackEntity pack = studyPackRepository.findById(studyPackId).orElse(null);
        NoteEntity note = pack == null || pack.getNoteId() == null
                ? null : noteRepository.findById(pack.getNoteId()).orElse(null);
        Stream<OfficialChallengeQuizTemplateService.OfficialTemplateKey> ownTemplate = Stream.empty();
        if (note != null && note.getVisibility() == NoteVisibility.PUBLIC
                && note.getOwnerUserId().equals(pack.getOwnerUserId())
                && isOfficialAuthor(note.getOwnerUserId())) {
            ownTemplate = Stream.of(new OfficialChallengeQuizTemplateService.OfficialTemplateKey(
                    note.getOwnerUserId(), pack.getId()));
        }
        return Stream.concat(ownTemplate, resolveTemplate(studyPackId).stream())
                .flatMap(template -> questionBankRepository.findQuestionKeysByUserIdAndStudyPackId(
                        template.ownerUserId(), template.studyPackId()).stream())
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean isOfficialAuthor(UUID userId) {
        return userId != null && !AccountPurgeService.DELETED_USER_ID.equals(userId)
                && userRepository.findById(userId)
                .map(OfficialChallengeQuizTemplateService::isOfficialAuthorAccount).orElse(false);
    }
}
