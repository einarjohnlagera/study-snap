package com.studysnap.backend.benchmark;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blind-output and deterministic-event bridge for preregistration sections 7 and 8, Revision 2.2. */
final class ClassBOutcomeBridge {
    static final class UnscorableOutput extends RuntimeException {
        UnscorableOutput(String message) { super(message); }
    }
    record Prepared(ClassBCaseManifest.Workflow workflow, ClassBCaseManifest.Case fixture,
                    String blindedOutput, RouteVerdictCalculator.ArmResult structural) {
        ClassBBlindPacketExport.Output forArm(ClassBScoreAggregator.Arm arm) {
            if (blindedOutput == null) throw new UnscorableOutput("No delivered output for blind packet");
            return new ClassBBlindPacketExport.Output(fixture.fixtureId(), arm, blindedOutput);
        }
    }

    private ClassBOutcomeBridge() {}

    static Prepared prepare(ClassBCaseManifest.Case fixture, AbstractClassBAdapter adapter,
                            CaseArmOutcome outcome) {
        if (!fixture.fixtureId().equals(adapter.id()) || !fixture.fixtureId().equals(outcome.caseArmId()))
            throw new UnscorableOutput("Fixture and outcome do not match");
        String delivered = outcome.state() == CaseArmOutcome.State.COMPLETE ? adapter.deliveredOutput() : null;
        if (delivered != null && delivered.isBlank()) delivered = null;
        RouteVerdictCalculator.Evidence evidence = switch (outcome.state()) {
            case COMPLETE -> RouteVerdictCalculator.Evidence.SCORED;
            case INCONCLUSIVE -> RouteVerdictCalculator.Evidence.MISSING;
            case FAILED -> outcome.attempts().stream().anyMatch(a -> a.kind() == AttemptOutcome.Kind.UNMETERED)
                    ? RouteVerdictCalculator.Evidence.UNMETERED : RouteVerdictCalculator.Evidence.UNSCORABLE;
        };
        Set<String> components = new HashSet<>();
        for (AttemptOutcome attempt : outcome.attempts()) {
            if (attempt.parseSchemaFailure()) components.add("SCHEMA_OR_PARSE");
            if (attempt.kind() == AttemptOutcome.Kind.TIMEOUT) components.add("TIMEOUT");
            if (attempt.kind() == AttemptOutcome.Kind.HTTP_ERROR) components.add("HTTP_ERROR");
            if (attempt.kind() == AttemptOutcome.Kind.EMPTY) components.add("EMPTY");
        }
        if ("ASK_COMPANION_UNAVAILABLE".equals(outcome.failureClass())) components.add("UNAVAILABLE");
        String finalFailure = outcome.state() == CaseArmOutcome.State.COMPLETE ? null : outcome.failureClass();
        var event = new RouteVerdictCalculator.ArmResult(evidence, components, false,
                finalFailure, List.of(), false, outcome.state() != CaseArmOutcome.State.COMPLETE);
        return new Prepared(fixture.workflow(), fixture, delivered, event);
    }
}
