package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.StudyPackStatus;
import lombok.experimental.UtilityClass;

import java.util.List;

/** Derives learning capability from a Study Pack's own artifacts, independently of Note lifecycle. */
@UtilityClass
public class StudyPackArtifactFacts {
    public boolean hasQuizQuestions(List<QuizItem> quiz) {
        return quiz != null && !quiz.isEmpty();
    }

    public boolean hasKeyConcepts(List<String> keyConcepts) {
        return keyConcepts != null && !keyConcepts.isEmpty();
    }

    public boolean isDone(StudyPackStatus status) {
        return status == StudyPackStatus.DONE;
    }
}
