package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.GeneratedQuizService;
import java.util.List;

/** Teacher quiz public generation path for preregistration sections 3 and 7. */
final class TeacherQuizAdapter extends AbstractClassAAdapter {
    TeacherQuizAdapter(ClassACaseManifest.Case item) {
        super(item, ClassACaseManifest.Workflow.TEACHER_QUIZ);
    }

    @Override protected List<QuizItem> invoke(ClassAServiceHarness harness) {
        return harness.service(GeneratedQuizService.class).generate(
                harness.note(0).getId().toString(), harness.userId,
                item.requestedQuestionCount(), item.noteLevel().name()).questions();
    }
}
