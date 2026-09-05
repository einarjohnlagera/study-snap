package com.studysnap.backend.service;

import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The two consequence counts a curator is owed before committing a bulk regeneration, and the
 * single-Note form the driver uses to record what an item actually did.
 *
 * <p>⚠️ Both counts are EXACT, not estimates. {@code uq_generated_quizzes_note_id} gives at most one
 * generated quiz per note, so counting notes whose quiz still carries a live share link is a count of
 * links that will really be turned off.
 *
 * <p>⚠️ ONE IMPLEMENTATION, TWO CALLERS, for the same reason as
 * {@link NoteRegenerationReadinessService}: the number the preflight modal shows and the fact the
 * receipt records must come from the same read, or the receipt contradicts the confirmation.
 */
@Service
@RequiredArgsConstructor
public class NoteRegenerationConsequenceService {
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizShareLinkRepository quizShareLinkRepository;

    /**
     * ⚠️ Scope-gated to match the single-Note primitive exactly: only combined regeneration replaces
     * the Note content a shared quiz was built from, so Study-Pack-only regeneration deactivates
     * nothing and must not be described as if it did.
     */
    public int countSharedQuizzesToDeactivate(
            UUID ownerUserId,
            List<UUID> noteIds,
            NoteRegenerationScope scope
    ) {
        return notesWithLiveShareLink(ownerUserId, noteIds, scope).size();
    }

    public boolean hasLiveShareLink(UUID ownerUserId, UUID noteId, NoteRegenerationScope scope) {
        return noteId != null
                && notesWithLiveShareLink(ownerUserId, List.of(noteId), scope).contains(noteId);
    }

    public int countPublicNotes(List<NoteEntity> notes) {
        return (int) notes.stream()
                .filter(note -> note.getVisibility() == NoteVisibility.PUBLIC)
                .count();
    }

    @Transactional(readOnly = true)
    public Set<UUID> notesWithLiveShareLink(
            UUID ownerUserId,
            List<UUID> noteIds,
            NoteRegenerationScope scope
    ) {
        if (scope != NoteRegenerationScope.NOTE_AND_STUDY_PACK || noteIds == null || noteIds.isEmpty()) {
            return Set.of();
        }
        List<GeneratedQuizEntity> quizzes =
                generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(ownerUserId, List.copyOf(noteIds));
        if (quizzes.isEmpty()) {
            return Set.of();
        }
        // ⚠️ ANY active link, not just the newest: createShareLink mints a new row over an inactive one
        // and findActiveLink accepts any active token, so a quiz can carry several live links.
        return quizzes.stream()
                .filter(quiz -> quizShareLinkRepository
                        .findByGeneratedQuizIdAndOwnerUserId(quiz.getId(), ownerUserId)
                        .stream()
                        .anyMatch(NoteRegenerationConsequenceService::isActive))
                .map(GeneratedQuizEntity::getNoteId)
                .collect(Collectors.toSet());
    }

    private static boolean isActive(QuizShareLinkEntity link) {
        return link != null && Boolean.TRUE.equals(link.getActive());
    }
}
