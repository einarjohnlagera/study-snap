package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import java.util.List;
import org.springframework.web.client.RestClient;

/** Production caller bridge and signal receipt for preregistration sections 3 and 7. */
abstract class AbstractClassAAdapter implements StageRunner.CaseCall {
    protected final ClassACaseManifest.Case item;
    private List<QuizItem> deliveredQuestions = List.of();
    private List<ProductionCallSignals.Signal> signals = List.of();
    private List<String> requestBodyDigests = List.of();
    private ClassAServiceHarness harness;

    AbstractClassAAdapter(ClassACaseManifest.Case item, ClassACaseManifest.Workflow workflow) {
        if (item.workflow() != workflow) throw new IllegalArgumentException("Wrong class-A workflow");
        this.item = item;
    }

    @Override public String id() { return item.caseId(); }
    @Override public String cellId() { return item.workflow().name() + ":" + item.stratum(); }
    @Override public String generationId() { return item.caseId(); }
    List<QuizItem> deliveredQuestions() { return deliveredQuestions; }
    List<ProductionCallSignals.Signal> signals() { return signals; }
    List<String> requestBodyDigests() { return requestBodyDigests; }
    ClassAServiceHarness harness() { return harness; }

    @Override public final StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
            ArmAssertingInterceptor path) {
        harness = new ClassAServiceHarness(item, client, properties);
        int before = path.calls().size();
        int beforeBodies = path.bodyDigests().size();
        int beforeCounts = path.requestQuestionCounts().size();
        String failureClass = null;
        try (ProductionCallSignals production = new ProductionCallSignals()) {
            try {
                deliveredQuestions = List.copyOf(invoke(harness));
            } catch (RuntimeException exception) {
                failureClass = exception.getClass().getSimpleName();
            }
            signals = production.signals();
            requestBodyDigests = path.bodyDigests().subList(beforeBodies, path.bodyDigests().size());
            List<Integer> counts = path.requestQuestionCounts().subList(beforeCounts,
                    path.requestQuestionCounts().size());
            int requested = 0;
            for (int index = 0; index < Math.min(counts.size(), signals.size()); index++)
                if (signals.get(index).attempt() == 1) requested += counts.get(index);
            if (requested != item.requestedQuestionCount()) failureClass = "INPUT_COUNT_MISMATCH";
            return production.result(generationId(), failureClass == null && !deliveredQuestions.isEmpty(),
                    failureClass, path.calls().size() - before);
        }
    }

    protected abstract List<QuizItem> invoke(ClassAServiceHarness harness);
}
