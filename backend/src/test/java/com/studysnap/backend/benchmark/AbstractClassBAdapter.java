package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.AskCompanionUnavailableException;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.RestClient;

/** Production-call outcome bridge for preregistration sections 3, 6 and 7, Revision 2.2. */
abstract class AbstractClassBAdapter implements StageRunner.CaseCall {
    private static final String EMPTY_DELIVERED_OUTPUT = "EMPTY_DELIVERED_OUTPUT";
    static final class InputDefect extends RuntimeException {
        InputDefect(String message) { super(message); }
    }
    protected final ClassBCaseManifest.Case item;
    private String deliveredOutput;
    private List<ProductionCallSignals.Signal> signals = List.of();
    private List<String> requestBodyDigests = List.of();
    private ClassBServiceHarness harness;

    AbstractClassBAdapter(ClassBCaseManifest.Case item, ClassBCaseManifest.Workflow workflow) {
        if (item.workflow() != workflow) throw new InputDefect("Wrong class-B workflow");
        this.item = item;
    }

    @Override public String id() { return item.fixtureId(); }
    @Override public String cellId() { return item.workflow() + ":" + item.stratum(); }
    @Override public String generationId() { return item.fixtureId(); }
    String deliveredOutput() { return deliveredOutput; }
    @Override public Object deliveredContent() { return deliveredOutput; }
    List<ProductionCallSignals.Signal> signals() { return signals; }
    List<String> requestBodyDigests() { return requestBodyDigests; }
    ClassBServiceHarness harness() { return harness; }

    @Override public final StageRunner.CallResult execute(RestClient client, StudySnapProperties properties,
            ArmAssertingInterceptor path) {
        configure(properties);
        validate();
        harness = new ClassBServiceHarness(item, client, properties);
        prepare(harness);
        int before = path.receipts().size();
        String failure = null;
        try (ProductionCallSignals production = new ProductionCallSignals()) {
            try {
                deliveredOutput = invoke(harness);
            } catch (RuntimeException ex) {
                failure = ex.getClass().getSimpleName();
            }
            var receipts = path.receipts().subList(before, path.receipts().size());
            requestBodyDigests = receipts.stream().map(ArmAssertingInterceptor.CallReceipt::bodyDigest).toList();
            signals = production.signals();
            if (!usesServiceOutcome() && (deliveredOutput == null || deliveredOutput.isBlank())
                    && !signals.isEmpty()) {
                String terminal = signals.getLast().outcome();
                if (!"SUCCESS".equals(terminal)) failure = terminal;
            }
            if (receipts.isEmpty()) {
                if (failure == null && (deliveredOutput == null || deliveredOutput.isBlank()))
                    throw new InputDefect("Fixture did not reach a model call");
                return new StageRunner.CallResult(false, Set.of(), failure == null
                        ? "INCONCLUSIVE_NO_MODEL_CALL" : failure);
            }
            if (usesServiceOutcome()) return askResult(receipts, failure);
            StageRunner.CallResult result = production.result(generationId(),
                    deliveredOutput != null && !deliveredOutput.isBlank(), failure, receipts);
            if (result.failureClass() != null) return result;
            if (deliveredOutput == null || deliveredOutput.isBlank()) {
                String named = signals.stream().map(ProductionCallSignals.Signal::outcome)
                        .filter(outcome -> !"SUCCESS".equals(outcome)).reduce((first, last) -> last)
                        .orElse(EMPTY_DELIVERED_OUTPUT);
                return new StageRunner.CallResult(false, result.parseSchemaFailures(), named,
                        result.callGroupIds(), result.parseFailureCallIndices(), result.signalKinds(),
                        result.signalAttempts());
            }
            return result;
        }
    }

    private StageRunner.CallResult askResult(List<ArmAssertingInterceptor.CallReceipt> receipts, String failure) {
        if (receipts.size() != 1) return new StageRunner.CallResult(false, Set.of(),
                ProductionCallSignals.SIGNAL_MISMATCH);
        MeteringInterceptor.Call call = receipts.getFirst().call();
        AttemptOutcome attempt = AttemptOutcome.from(call, false);
        boolean delivered = deliveredOutput != null && !deliveredOutput.isBlank() && failure == null
                && attempt.kind() != AttemptOutcome.Kind.UNMETERED;
        String failureClass = delivered ? null : switch (attempt.kind()) {
            case TIMEOUT -> "TIMEOUT";
            case HTTP_ERROR -> "HTTP_ERROR";
            case UNMETERED -> "INCONCLUSIVE_UNMETERED";
            default -> failure != null && failure.equals(AskCompanionUnavailableException.class.getSimpleName())
                    ? "ASK_COMPANION_UNAVAILABLE" : failure == null ? EMPTY_DELIVERED_OUTPUT : failure;
        };
        return new StageRunner.CallResult(delivered, Set.of(), failureClass,
                List.of(generationId()), Set.of(), List.of(attempt.kind()), List.of(1));
    }

    protected void configure(StudySnapProperties properties) {}
    protected void validate() {}
    protected boolean usesServiceOutcome() { return false; }
    protected abstract void prepare(ClassBServiceHarness harness);
    protected abstract String invoke(ClassBServiceHarness harness);
}
