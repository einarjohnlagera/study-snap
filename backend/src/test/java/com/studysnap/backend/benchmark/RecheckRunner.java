package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Complete same-input T1 executions for preregistration sections 1, 5 and 8. */
final class RecheckRunner {
    static final class InvalidRecheck extends RuntimeException {
        InvalidRecheck(String message) { super(message); }
    }
    private record RunFile(Path stagePlan, Path originalReceipt, Path originalOutputs,
                           Path stageLedger, Path outputDir, Map<String, BigDecimal> reservations) {}
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test void recheckRun() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.recheckRun"), "opt-in recheck skipped");
        Path configPath = Path.of(requiredProperty("llm.benchmark.recheckRun.plan"));
        DeliveredOutputs.guard(configPath);
        RunFile file = JSON.readValue(configPath.toFile(), RunFile.class);
        if (file.stagePlan() == null || file.originalReceipt() == null || file.originalOutputs() == null
                || file.stageLedger() == null || file.outputDir() == null || file.reservations() == null)
            throw new InvalidRecheck("Incomplete recheck run file");
        Stage1RunPlan plan = new Stage1RunPlan(Stage1RunPlan.readConfig(file.stagePlan(),
                System.getenv("LLM_API_KEY")));
        plan.verifyFresh();
        Stage1Receipt original = Stage1Receipt.read(file.originalReceipt());
        Map<StageCapLedger.Stage, BigDecimal> caps = StageRunner.parseCaps(
                requiredProperty("llm.benchmark.stageCaps"));
        try (StageCapLedger ledger = StageCapLedger.open(file.stageLedger(), caps, false)) {
            run(plan, original, file.originalOutputs(), ledger, file.reservations(), file.outputDir());
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new InvalidRecheck(name + " is required");
        return value;
    }

    static RecheckReceipt run(Stage1RunPlan plan, Stage1Receipt original, Path originalOutputs,
                              StageCapLedger ledger, Map<String, BigDecimal> executionReservations,
                              Path outputRoot) throws Exception {
        FreezeGuard.Verified verified = plan.verifyFresh();
        if (verified == null || !verified.real())
            throw new FreezeRecord.InvalidFreeze("Real recheck requires verified freeze record");
        return execute(plan.input(), plan::cases, plan.currentManifestHashes(), original,
                originalOutputs, ledger, executionReservations, outputRoot, verified, null);
    }

    static RecheckReceipt runOffline(StageRunner.RunInput input,
                                     Supplier<List<StageRunner.CaseCall>> cases,
                                     Map<String, String> currentManifestHashes,
                                     Stage1Receipt original, Path originalOutputs,
                                     StageCapLedger ledger, Map<String, BigDecimal> executionReservations,
                                     Path outputRoot, ClientHttpRequestFactory transport,
                                     FreezeGuard.Verified syntheticFreeze) throws Exception {
        if (syntheticFreeze == null || syntheticFreeze.real() || transport == null
                || !input.key().startsWith("fake-"))
            throw new InvalidRecheck("Offline recheck requires synthetic freeze, stub transport and fake key");
        return execute(input, cases, currentManifestHashes, original, originalOutputs, ledger,
                executionReservations, outputRoot, syntheticFreeze, transport);
    }

    private static RecheckReceipt execute(StageRunner.RunInput input,
                                           Supplier<List<StageRunner.CaseCall>> cases,
                                           Map<String, String> currentManifestHashes,
                                           Stage1Receipt original,
                                           Path originalOutputs, StageCapLedger ledger,
                                           Map<String, BigDecimal> reservations, Path outputRoot,
                                           FreezeGuard.Verified verified,
                                           ClientHttpRequestFactory transport) throws Exception {
        DeliveredOutputs.guard(outputRoot);
        if (ledger.stopped()) throw new StageCapLedger.SpendStop(ledger.stopReason());
        FreezeRecord.Arm frozenArm = verified.record().arms().get(original.arm());
        if (frozenArm == null || !frozenArm.model().equals(input.model())
                || !original.freezeSha256().equals(verified.sha256())
                || !original.head().equals(verified.head()))
            throw new InvalidRecheck("Original receipt differs from verified arm or freeze");
        Files.createDirectories(outputRoot);
        RecheckPlan.Decision decision = RecheckPlan.build(original, originalOutputs,
                verified.record(), currentManifestHashes);
        List<RecheckReceipt.Case> resultCases = new ArrayList<>();
        for (RecheckPlan.Eligible item : decision.eligible()) {
            StageRunner.CaseCall adapter = find(cases.get(), item.caseId());
            BigDecimal one = reservations.get(item.caseId());
            if (one == null || one.signum() <= 0)
                throw new InvalidRecheck("Missing positive execution reservation for " + item.caseId());
            StageCapLedger.Reservation budget = ledger.reserve(input.stage(), adapter.cellId(),
                    one.multiply(BigDecimal.TWO));
            List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
            try {
                for (int recheckIndex = 1; recheckIndex <= 2; recheckIndex++) {
                StageRunner.CaseCall fresh = find(cases.get(), item.caseId());
                Path directory = outputRoot.resolve(item.caseId()).resolve("recheck-" + recheckIndex)
                        .toAbsolutePath().normalize();
                if (!directory.startsWith(outputRoot.toAbsolutePath().normalize())
                        || ".".equals(item.caseId()) || "..".equals(item.caseId()))
                    throw new InvalidRecheck("Case identifier escapes recheck directory");
                StageRunner.RunInput repeatInput = new StageRunner.RunInput(input.model(), input.key(),
                        input.stage(), input.approvedCaps(), input.forecasts(), input.priceIn(),
                        input.priceOut(), input.inputPath(), directory);
                StageRunner.RunSummary run = transport == null
                        ? StageRunner.runVerified(repeatInput, List.of(fresh), ledger, verified, budget)
                        : StageRunner.run(repeatInput, transport, List.of(fresh), ledger, budget);
                CaseArmOutcome outcome = run.outcomes().getFirst();
                DeliveredOutputs.Written delivered = DeliveredOutputs.write(
                        directory.resolve("delivered-outputs.jsonl"), original.arm(), recheckIndex,
                        run.outcomes(), Map.of(item.caseId(), item.workflow()),
                        Map.of(item.caseId(), item.manifestRowSha256()), run.deliveredByCase());
                Stage1Receipt check = Stage1Receipt.fromRun(verified, original.arm(), input.model(),
                        input.stage(), Instant.now(), original.caseSetSha256(),
                        Map.of(item.caseId(), item.manifestRowSha256()),
                        Map.of(item.caseId(), item.workflow()), run, delivered);
                Stage1Receipt.CaseComparison comparison = Stage1Receipt.compare(original, check).stream()
                        .filter(result -> result.caseId().equals(item.caseId())).findFirst()
                        .orElseThrow(() -> new InvalidRecheck("Missing recheck case " + item.caseId()));
                String deviation = comparison.result() == Stage1Receipt.Comparison.MATCH ? null
                        : comparison.deviation().getMessage();
                RouteVerdictCalculator.Evidence evidence = deviation != null
                        ? RouteVerdictCalculator.Evidence.MISSING
                        : outcome.state() == CaseArmOutcome.State.COMPLETE
                            ? RouteVerdictCalculator.Evidence.SCORED
                            : outcome.state() == CaseArmOutcome.State.INCONCLUSIVE
                                ? RouteVerdictCalculator.Evidence.MISSING
                                : RouteVerdictCalculator.Evidence.UNSCORABLE;
                repeats.add(new RecheckReceipt.Repeat(recheckIndex, outcome.failureClass(), evidence,
                        deviation, delivered.lineSha256().get(item.caseId()), delivered.fileSha256()));
                }
            } finally {
                if (!budget.closed()) budget.abandon();
            }
            resultCases.add(new RecheckReceipt.Case(item.caseId(), item.originalFailureClass(),
                    List.copyOf(repeats)));
        }
        RecheckReceipt receipt = new RecheckReceipt(verified.head(), verified.sha256(), original.arm(),
                original.model(), decision.freezeEligibleFailureClasses(), List.copyOf(resultCases));
        receipt.write(outputRoot.resolve("recheck-receipt.json"));
        return receipt;
    }

    private static StageRunner.CaseCall find(List<StageRunner.CaseCall> calls, String id) {
        return calls.stream().filter(call -> call.id().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidRecheck("Frozen case is unreachable: " + id));
    }
}
