package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.service.QuickReviewStudyTipService;

/** Quick Review class-B entry point for preregistration sections 3, 6 and 7, Revision 2.2. */
final class QuickReviewStudyTipAdapter extends AbstractClassBAdapter {
    QuickReviewStudyTipAdapter(ClassBCaseManifest.Case item) {
        super(item, ClassBCaseManifest.Workflow.STUDY_TIP);
    }

    @Override protected void configure(StudySnapProperties properties) {
        var tip = properties.getQuickReview().getStudyTip();
        // Benchmark only: application.yaml defaults enabled=false; the owner confirms production runs it.
        tip.setEnabled(true);
        // Benchmark only: application.yaml default minimum is 1 incorrect question.
        tip.setMinIncorrectCount(1);
        // Benchmark only: application.yaml default maximum is 3 questions.
        tip.setMaxQuestions(3);
    }

    @Override protected void validate() {
        if (item.incorrectQuestions() == null || item.incorrectQuestions().isEmpty()
                || item.incorrectQuestions().size() > 3
                || item.incorrectQuestions().stream().anyMatch(question -> question == null
                        || blank(question.question()) || blank(question.correctAnswer())
                        || blank(question.explanation())))
            throw new InputDefect("Study tip needs one to three incorrect questions");
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }

    @Override protected void prepare(ClassBServiceHarness harness) { harness.studyPack(); }

    @Override protected String invoke(ClassBServiceHarness harness) {
        return harness.service(QuickReviewStudyTipService.class).generateStudyTip(
                harness.sourceId.toString(), harness.userId,
                new QuickReviewStudyTipRequest(item.incorrectQuestions())).studyTip();
    }
}
