package com.studysnap.backend.util;

import java.util.List;

/**
 * The read semantics every discovery surface uses, in one place: <em>joined catalog programs first
 * when a note has any, otherwise its personal free-text program</em>.
 *
 * <p>This mirrors the SQL that discovery already runs — {@code EXISTS(join rows) OR (NOT EXISTS(join
 * rows) AND legacy matches)} — for the Java consumers that read a note entity directly. Those
 * consumers were blind to the join through {@code v0.71.0} (finding M3): they read
 * {@code notes.course_program} alone, so a curated note with a null string looked programme-less and
 * they silently degraded.
 *
 * <p>Read semantics never consult ownership (`ADR-001`), so this helper deliberately takes no user.
 */
public final class NoteEffectivePrograms {
    private NoteEffectivePrograms() {
    }

    /**
     * @param joinedProgramNames catalog names from {@code note_course_program}; may be null or empty
     * @param legacyCourseProgram the note's personal {@code course_program} string; may be null or blank
     * @return every program the note is discoverable under, newest semantics first; never null
     */
    public static List<String> resolve(List<String> joinedProgramNames, String legacyCourseProgram) {
        if (joinedProgramNames != null && !joinedProgramNames.isEmpty()) {
            return List.copyOf(joinedProgramNames);
        }
        if (legacyCourseProgram == null || legacyCourseProgram.isBlank()) {
            return List.of();
        }
        return List.of(legacyCourseProgram);
    }

    /**
     * The single program name a surface should show when it has room for exactly one. Returns null
     * rather than picking arbitrarily when a note is applicable to several — a caller that must render
     * one value should say "N programs" instead, the way the note cards do.
     */
    public static String resolveSingle(List<String> joinedProgramNames, String legacyCourseProgram) {
        List<String> programs = resolve(joinedProgramNames, legacyCourseProgram);
        return programs.size() == 1 ? programs.getFirst() : null;
    }
}
