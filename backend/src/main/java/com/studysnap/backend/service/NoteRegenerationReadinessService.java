package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The ONE implementation of "can this Note be regenerated right now, under this scope?", with exactly
 * two callers: the bulk preflight endpoint and the bulk driver at the moment each item starts.
 *
 * <p>⚠️ ONE IMPLEMENTATION, TWO CALLERS, AND THAT IS THE WHOLE POINT. If preflight and the driver can
 * ever disagree about whether a Note is ready, the feature is built wrong: the curator would confirm a
 * batch described one way and receive a different one. Preflight is DISCLOSURE; the item-start
 * evaluation is ENFORCEMENT; both read this class.
 *
 * <p>⚠️ THE PREFLIGHT SNAPSHOT IS NEVER TRUSTED. A Note that passed preflight can be edited, deleted,
 * or put into {@code GENERATING} by a single-Note regeneration before its turn comes (failure matrix
 * row 8), so the driver re-runs this at item start and a now-blocked Note takes {@code BLOCKED} with
 * its reason rather than being forced through or silently skipped.
 *
 * <p>⚠️ EVERY SIGNAL HERE IS DETERMINISTIC. There is no metadata-quality score, no classifier and no
 * "is this good enough" judgement — a Note with a NULL Domain Context and one joined program is fully
 * generation-ready, so calling it "review recommended" would mean judging metadata quality, which the
 * release forbids.
 *
 * <p>⚠️ THE STATE SET IS DERIVED FROM WHAT THE DRIVER CAN ACTUALLY PRODUCE, not from a table written
 * before the single-Note primitive shipped. Two consequences, both deliberate:
 * <ul>
 *   <li>{@code NOTE_REGENERATION_STUDY_PACK_REQUIRED} and {@code NOTE_REGENERATION_TOPIC_REQUIRED} are
 *       included even though the audit's preflight table omits them — they are reachable in
 *       {@code StudyPackService.startAsyncNoteAndStudyPackRegeneration} and omitting them would create
 *       exactly the preflight/driver disagreement above.</li>
 *   <li>"Blocked — no course/program resolvable" is deliberately ABSENT. The audit anchors it to
 *       {@code CourseProgramSelectionRequiredException}, which is thrown by
 *       {@code NoteGenerationService.resolveAuthoringContext} — a path BOTH regeneration entry points
 *       bypass by passing an already-resolved context. Adding it would invent a refusal that does not
 *       exist today, so a Note that regenerates fine one at a time would be refused in bulk.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NoteRegenerationReadinessService {
    static final String NOT_ELIGIBLE_CODE = "NOTE_NOT_FOUND";
    static final String NOT_ELIGIBLE_MESSAGE = "This note is no longer in your Library.";
    static final String EMPTY_CONTENT_CODE = "EMPTY_NOTES";
    static final String EMPTY_CONTENT_MESSAGE =
            "This note has no content to build a Study Pack from.";

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final NoteCourseProgramRepository noteCourseProgramRepository;

    /**
     * Evaluated in the same order the corresponding {@code StudyPackService} entry point throws, so the
     * reported reason is the one the curator would have seen from a single-Note attempt.
     */
    @Transactional(readOnly = true)
    public Verdict evaluate(UUID noteId, UUID ownerUserId, NoteRegenerationScope scope) {
        NoteEntity note = noteId == null
                ? null
                : noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId).orElse(null);
        if (note == null) {
            return Verdict.notEligible();
        }
        return evaluate(note, ownerUserId, scope);
    }

    /**
     * Overload for callers that already hold the Note. Ownership is re-asserted rather than assumed —
     * a caller passing someone else's Note must not be able to buy a READY verdict with it.
     */
    @Transactional(readOnly = true)
    public Verdict evaluate(NoteEntity note, UUID ownerUserId, NoteRegenerationScope scope) {
        if (note == null || note.getOwnerUserId() == null || !note.getOwnerUserId().equals(ownerUserId)) {
            return Verdict.notEligible();
        }

        NoteStatus status = note.getStatus() == null ? NoteStatus.DRAFT : note.getStatus();
        if (status == NoteStatus.GENERATING) {
            return Verdict.blocked(
                    "NOTE_GENERATION_IN_PROGRESS",
                    "A Study Pack is already being generated for this note."
            );
        }

        if (scope == NoteRegenerationScope.NOTE_AND_STUDY_PACK) {
            if (studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, note.getId()).isEmpty()) {
                return Verdict.blocked(
                        "NOTE_REGENERATION_STUDY_PACK_REQUIRED",
                        "This note has no Study Pack yet. Generate one first, then you can regenerate both together."
                );
            }
            if (note.getTitle() == null || note.getTitle().trim().isEmpty()) {
                return Verdict.blocked(
                        "NOTE_REGENERATION_TOPIC_REQUIRED",
                        "Add a title to this note before regenerating it. The title is the topic we write from."
                );
            }
        }

        if (note.getDomainContext() == null
                && noteCourseProgramRepository.findIdsByNoteId(note.getId()).size() > 1) {
            return Verdict.blocked(
                    "MULTI_PROGRAM_DOMAIN_CONTEXT_REQUIRED",
                    "A note shared across several programs needs a Domain Context, so the AI knows which"
                            + " academic domain to write in."
            );
        }

        // Study-Pack-only regeneration reads the note's EXISTING body as its source text, so a blank
        // body is a hard blocker there. Combined regeneration writes a new body first, so the same
        // note is perfectly regenerable under that scope — which is why this is scope-gated.
        if (scope == NoteRegenerationScope.STUDY_PACK
                && (note.getContent() == null || note.getContent().isBlank())) {
            return Verdict.blocked(EMPTY_CONTENT_CODE, EMPTY_CONTENT_MESSAGE);
        }

        return Verdict.ready();
    }

    /**
     * How many note-generation units one item of this scope spends.
     *
     * <p>⚠️ SCOPE-AWARE ON PURPOSE. {@code startAsyncGenerationFromNote} asserts and charges only the
     * Study Pack meter, so a Study-Pack-only batch spends ZERO note-generation units and the hard
     * note-generation rejection must not fire for it. Only combined regeneration spends both.
     */
    public int noteGenerationUnitsPerItem(NoteRegenerationScope scope) {
        return scope == NoteRegenerationScope.NOTE_AND_STUDY_PACK ? 1 : 0;
    }

    public record Verdict(NoteRegenerationReadiness readiness, String reasonCode, String reason) {
        static Verdict ready() {
            return new Verdict(NoteRegenerationReadiness.READY, null, null);
        }

        static Verdict blocked(String reasonCode, String reason) {
            return new Verdict(NoteRegenerationReadiness.BLOCKED, reasonCode, reason);
        }

        static Verdict notEligible() {
            return new Verdict(NoteRegenerationReadiness.NOT_ELIGIBLE, NOT_ELIGIBLE_CODE, NOT_ELIGIBLE_MESSAGE);
        }

        public boolean isReady() {
            return readiness == NoteRegenerationReadiness.READY;
        }
    }

    /**
     * ⚠️ {@code NOT_ELIGIBLE} and {@code BLOCKED} are kept apart because they mean different things to
     * the curator: a not-eligible Note is not theirs (or no longer exists) and will never become
     * regenerable, while a blocked one becomes regenerable as soon as the named condition changes.
     */
    public enum NoteRegenerationReadiness {
        READY,
        BLOCKED,
        NOT_ELIGIBLE
    }
}
