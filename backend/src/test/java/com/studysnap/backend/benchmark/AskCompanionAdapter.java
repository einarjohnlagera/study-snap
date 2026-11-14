package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.AskCompanionQuestionRequest;
import com.studysnap.backend.entity.AskCompanionSessionEntity;
import com.studysnap.backend.service.AskCompanionService;

/** Ask Companion class-B result-signal entry point for preregistration sections 3, 6 and 7, Revision 2.2. */
final class AskCompanionAdapter extends AbstractClassBAdapter {
    private AskCompanionSessionEntity session;
    AskCompanionAdapter(ClassBCaseManifest.Case item) {
        super(item, ClassBCaseManifest.Workflow.ASK_COMPANION);
    }

    @Override protected void validate() {
        if (item.priorTurns() == null || item.priorTurns().size() >= AskCompanionService.TURN_LIMIT
                || item.companion() == null || item.question() == null || item.question().isBlank())
            throw new InputDefect("Ask Companion session cannot reach a model call");
    }

    @Override protected boolean usesServiceOutcome() { return true; }

    @Override protected void prepare(ClassBServiceHarness harness) {
        session = harness.askSession(item);
        if (session.getTurnCount() >= AskCompanionService.TURN_LIMIT)
            throw new InputDefect("Ask Companion turn limit reached");
    }

    @Override protected String invoke(ClassBServiceHarness harness) {
        var response = harness.service(AskCompanionService.class).askQuestion(
                session.getId(), harness.userId, new AskCompanionQuestionRequest(item.question()));
        if (response.turns().size() != item.priorTurns().size() + 1)
            throw new InputDefect("Ask Companion did not store a new turn");
        return response.turns().getLast().answer();
    }
}
