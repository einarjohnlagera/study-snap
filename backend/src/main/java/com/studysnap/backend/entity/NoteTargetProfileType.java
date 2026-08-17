package com.studysnap.backend.entity;

public enum NoteTargetProfileType {
    STUDENT,
    BOARD_TAKER,
    PROFESSIONAL;

    /**
     * Derives the stored audience value from the note owner's profile.
     *
     * <p>Target Audience was removed from authoring, display and discovery in v0.83.0, but
     * {@code notes.target_profile_type} is NOT NULL with no database default and is retained as
     * migration evidence until phase 4 drops it. Every path that persists a NoteEntity therefore
     * still has to supply a valid value, and it derives that value here rather than hardcoding one:
     * SPEC.md documents the contract as "derives a constrained non-null value from the owner's
     * profile", and a constant would quietly write an audience the owner never chose.
     *
     * <p>Total by construction — every ProfileType maps, so this can never return null and can never
     * violate the CHECK constraint. TEACHER, PARENT and STUDENT all fall through to STUDENT, which
     * is the behaviour note creation has had in production since before the audience field existed.
     *
     * <p>This must never be used as a runtime fallback for Authored Depth. It is one-time migration
     * evidence, not a depth signal.
     */
    public static NoteTargetProfileType forOwnerProfile(ProfileType profileType) {
        if (profileType == ProfileType.BOARD_EXAM) {
            return BOARD_TAKER;
        }
        if (profileType == ProfileType.PROFESSIONAL) {
            return PROFESSIONAL;
        }
        return STUDENT;
    }
}
