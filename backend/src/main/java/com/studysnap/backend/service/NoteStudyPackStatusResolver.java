package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NoteStudyPackStatusResolver {
    public static final String DRAFT = "DRAFT";
    public static final String GENERATING = "GENERATING";
    public static final String FAILED = "FAILED";
    public static final String STUDY_PACK_READY = "STUDY_PACK_READY";

    public String resolve(NoteEntity note, StudyPackEntity studyPack) {
        NoteStatus noteStatus = resolveStatus(note);
        return resolve(noteStatus, studyPack != null);
    }

    public String resolve(NoteStatus noteStatus, boolean hasStudyPack) {
        NoteStatus resolvedStatus = resolveStatus(noteStatus);
        if (resolvedStatus == NoteStatus.GENERATED) {
            return STUDY_PACK_READY;
        }
        if (resolvedStatus == NoteStatus.GENERATING) {
            return GENERATING;
        }
        if (resolvedStatus == NoteStatus.FAILED) {
            return FAILED;
        }
        if (!hasStudyPack) {
            return DRAFT;
        }
        return STUDY_PACK_READY;
    }

    private NoteStatus resolveStatus(NoteEntity note) {
        return note.getStatus() == null ? NoteStatus.DRAFT : note.getStatus();
    }

    private NoteStatus resolveStatus(NoteStatus noteStatus) {
        return noteStatus == null ? NoteStatus.DRAFT : noteStatus;
    }
}
