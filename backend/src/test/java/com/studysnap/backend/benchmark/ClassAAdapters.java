package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Frozen manifest to production-call bridge for preregistration sections 3 and 6. */
final class ClassAAdapters {
    private ClassAAdapters() {}

    static List<StageRunner.CaseCall> load(Path manifest) throws IOException {
        return ClassACaseManifest.load(manifest).stream().map(ClassAAdapters::from).toList();
    }

    static StageRunner.CaseCall from(ClassACaseManifest.Case item) {
        return switch (item.workflow()) {
            case NOTE_ADAPTIVE -> new NoteAdaptiveAdapter(item);
            case PLAN_ADAPTIVE -> new PlanAdaptiveAdapter(item);
            case TEACHER_QUIZ -> new TeacherQuizAdapter(item);
            case BOARD_EXAM_DIRECT -> new BoardExamDirectAdapter(item);
            case INTERVIEW_PRACTICE -> new InterviewPracticeAdapter(item);
            case LONG_EXAM -> switch (item.subgroup()) {
                case DIRECT -> new LongExamDirectAdapter(item);
                case LONG_EXAM_POOL -> new LongExamPoolAdapter(item);
                case BOARD_EXAM_POOL -> new BoardExamPoolAdapter(item);
            };
        };
    }
}
