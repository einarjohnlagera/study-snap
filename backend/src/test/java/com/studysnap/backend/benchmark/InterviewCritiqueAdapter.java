package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.service.InterviewPracticeService;
import com.studysnap.backend.util.QuizSessionStateUtils;

/** Interview critique class-B entry point for preregistration sections 3, 6 and 7, Revision 2.2. */
final class InterviewCritiqueAdapter extends AbstractClassBAdapter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QuickReviewSessionEntity session;
    InterviewCritiqueAdapter(ClassBCaseManifest.Case item) {
        super(item, ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE);
    }

    @Override protected void validate() {
        var question = item.interviewQuestion();
        if (question == null || question.choices() == null || item.selectedChoiceIndex() == null
                || item.selectedChoiceIndex() < 0 || item.selectedChoiceIndex() >= question.choices().size())
            throw new InputDefect("Interview choice is outside the question");
    }

    @Override protected void prepare(ClassBServiceHarness harness) {
        session = harness.interviewSession(item);
        requireNoCachedFeedback(session);
    }

    static void requireNoCachedFeedback(QuickReviewSessionEntity session) {
        if (QuizSessionStateUtils.extractInterviewFeedback(session.getSessionState(), 0).isPresent())
            throw new InputDefect("Interview fixture already contains critique feedback");
    }

    @Override protected String invoke(ClassBServiceHarness harness) {
        var result = harness.service(InterviewPracticeService.class).answerQuestion(
                session.getId(), harness.userId,
                new InterviewPracticeAnswerRequest(0, "ABCD".substring(item.selectedChoiceIndex(),
                        item.selectedChoiceIndex() + 1), 30));
        var recorded = QuizSessionStateUtils.extractInterviewFeedback(session.getSessionState(), 0)
                .orElseThrow(() -> new InputDefect("Interview critique was not recorded"));
        if (!recorded.verdict().equals(result.verdict()) || !recorded.rationale().equals(result.rationale())
                || !recorded.followUp().equals(result.followUp()))
            throw new InputDefect("Interview response and recorded critique differ");
        try {
            return JSON.writeValueAsString(recorded);
        } catch (JsonProcessingException ex) {
            throw new CollectionCompanionAdapter.InvalidDeliveredOutput(ex);
        }
    }
}
