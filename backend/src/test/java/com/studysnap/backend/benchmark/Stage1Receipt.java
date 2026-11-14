package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sections 5, 7 and 10 private, content-free execution and paired-input receipts. */
record Stage1Receipt(String head, String freezeSha256, String arm, String model,
                     StageCapLedger.Stage stage, Instant startedAt, Instant endedAt,
                     int ledgerRecordCount, boolean unreconciled, String caseSetSha256,
                     List<CaseRow> cases, Map<String, RouteVerdictCalculator.Tier> triggers,
                     String deliveredOutputsFileSha256) {
    Stage1Receipt(String head, String freezeSha256, String arm, String model,
                  StageCapLedger.Stage stage, Instant startedAt, Instant endedAt,
                  int ledgerRecordCount, boolean unreconciled, String caseSetSha256,
                  List<CaseRow> cases, Map<String, RouteVerdictCalculator.Tier> triggers) {
        this(head, freezeSha256, arm, model, stage, startedAt, endedAt, ledgerRecordCount,
                unreconciled, caseSetSha256, cases, triggers, null);
    }
    record Signal(String outcome, int attempt, long latencyMs, int httpStatus) {
        Signal(String outcome, int attempt, long latencyMs) { this(outcome, attempt, latencyMs, 0); }
    }
    record CaseRow(String id, String workflow, String manifestRowSha256,
                   CaseArmOutcome.State state, boolean firstAttemptClean, boolean finalSuccess,
                   String failureClass, BigDecimal conservativeUsd, BigDecimal billedUsd,
                   List<Signal> llmCalls, List<String> attempt1BodyHashes,
                   String deliveredOutputSha256) {
        CaseRow(String id, String workflow, String manifestRowSha256,
                CaseArmOutcome.State state, boolean firstAttemptClean, boolean finalSuccess,
                String failureClass, BigDecimal conservativeUsd, BigDecimal billedUsd,
                List<Signal> llmCalls, List<String> attempt1BodyHashes) {
            this(id, workflow, manifestRowSha256, state, firstAttemptClean, finalSuccess,
                    failureClass, conservativeUsd, billedUsd, llmCalls, attempt1BodyHashes, null);
        }
    }
    enum Comparison { MATCH, INCONCLUSIVE }
    record CaseComparison(String caseId, Comparison result, ProtocolDeviation deviation) {}
    private record IndexedHash(String group, int index, String hash) {}
    static final class ProtocolDeviation extends RuntimeException {
        ProtocolDeviation(String message) { super(message); }
    }

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final String DEVIATION_CASE = "Protocol deviation case ";

    static Stage1Receipt fromRun(FreezeGuard.Verified freeze, String arm, String model,
                                 StageCapLedger.Stage stage, Instant started,
                                 String caseSetHash, Map<String, String> rowHashes,
                                 Map<String, String> workflows, StageRunner.RunSummary run,
                                 DeliveredOutputs.Written delivered) {
        List<CaseRow> rows = new ArrayList<>();
        for (CaseArmOutcome outcome : run.outcomes()) {
            List<ArmAssertingInterceptor.CallReceipt> calls = run.callsByCase()
                    .getOrDefault(outcome.caseArmId(), List.of());
            List<Signal> signals = new ArrayList<>();
            List<IndexedHash> indexedHashes = new ArrayList<>();
            for (int index = 0; index < calls.size(); index++) {
                var call = calls.get(index);
                AttemptOutcome attempt = outcome.attempts().get(index);
                String signal = attempt.parseSchemaFailure() ? "SCHEMA_OR_PARSE"
                        : attempt.kind() == AttemptOutcome.Kind.OK ? "SUCCESS" : attempt.kind().name();
                signals.add(new Signal(signal, attempt.attempt(), call.call().latencyMs(), call.call().status()));
                if (attempt.attempt() == 1)
                    indexedHashes.add(new IndexedHash(attempt.generationId(), index, call.modelRemovedDigest()));
            }
            List<String> bodyHashes = indexedHashes.stream()
                    .sorted(java.util.Comparator.comparing(IndexedHash::group)
                            .thenComparingInt(IndexedHash::index))
                    .map(IndexedHash::hash).toList();
            rows.add(new CaseRow(outcome.caseArmId(), workflows.get(outcome.caseArmId()),
                    rowHashes.get(outcome.caseArmId()), outcome.state(), outcome.firstAttemptClean(),
                    outcome.finalSuccess(), outcome.failureClass(), outcome.conservativeUsd(),
                    outcome.billedUsd(), List.copyOf(signals), bodyHashes,
                    delivered.lineSha256().get(outcome.caseArmId())));
        }
        return new Stage1Receipt(freeze.head(), freeze.sha256(), arm, model, stage, started,
                Instant.now(), run.ledger().recordCount(), run.ledger().unreconciled(),
                caseSetHash, List.copyOf(rows), Map.of(), delivered.fileSha256());
    }

    static Stage1Receipt fromRun(FreezeGuard.Verified freeze, String arm, String model,
                                 StageCapLedger.Stage stage, Instant started,
                                 String caseSetHash, Map<String, String> rowHashes,
                                 Map<String, String> workflows, StageRunner.RunSummary run) {
        return fromRun(freeze, arm, model, stage, started, caseSetHash, rowHashes, workflows, run,
                new DeliveredOutputs.Written(null, null, Map.of()));
    }

    boolean complete() {
        return !unreconciled && !cases.isEmpty() && cases.stream()
                .allMatch(row -> row.state() == CaseArmOutcome.State.COMPLETE && row.finalSuccess()
                        && row.manifestRowSha256() != null && !row.attempt1BodyHashes().isEmpty());
    }

    void write(Path output) throws IOException {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        LlmModelBenchmark.Config.rejectInsideRepoForTest(output, repo);
        if (!Files.isDirectory(output.getParent())) throw new IllegalArgumentException("Receipt directory missing");
        JSON.writeValue(output.toFile(), this);
    }

    static Stage1Receipt read(Path input) {
        try {
            Stage1Receipt receipt = JSON.readValue(input.toFile(), Stage1Receipt.class);
            if (receipt.head() == null || receipt.freezeSha256() == null || receipt.arm() == null
                    || receipt.model() == null || receipt.stage() == null
                    || receipt.startedAt() == null || receipt.endedAt() == null
                    || receipt.ledgerRecordCount() < 0 || receipt.caseSetSha256() == null
                    || receipt.cases() == null || receipt.triggers() == null
                    || receipt.cases().stream().anyMatch(row -> row == null || row.id() == null
                        || row.workflow() == null || row.state() == null
                        || row.conservativeUsd() == null || row.billedUsd() == null
                        || row.llmCalls() == null || row.attempt1BodyHashes() == null))
                throw new ProtocolDeviation("Malformed baseline receipt " + input);
            return receipt;
        } catch (IOException exception) {
            throw new ProtocolDeviation("Missing or malformed baseline receipt " + input);
        }
    }

    static void requireCompleteBaselines(List<Stage1Receipt> baselines, String caseSetSha256,
                                         Map<String, String> plannedHashes, FreezeRecord freeze,
                                         String freezeHash, String head) {
        Map<String, String> covered = new LinkedHashMap<>();
        for (Stage1Receipt receipt : baselines) {
            if (!receipt.arm().startsWith("BASELINE") || !receipt.complete()
                    || !caseSetSha256.equals(receipt.caseSetSha256())
                    || !freezeHash.equals(receipt.freezeSha256()) || !head.equals(receipt.head())
                    || !freeze.arms().containsKey(receipt.arm())
                    || !freeze.arms().get(receipt.arm()).model().equals(receipt.model()))
                throw new ProtocolDeviation("LUNA requires complete matching baseline receipt");
            for (CaseRow row : receipt.cases()) {
                if (!freeze.arms().get(receipt.arm()).workflows().contains(row.workflow())
                        || !row.manifestRowSha256().equals(plannedHashes.get(row.id())))
                    throw new ProtocolDeviation("Baseline receipt case differs from frozen manifest: " + row.id());
                if (covered.putIfAbsent(row.id(), receipt.arm()) != null)
                    throw new ProtocolDeviation("Duplicate baseline case " + row.id());
            }
        }
        if (!covered.keySet().equals(plannedHashes.keySet()))
            throw new ProtocolDeviation("LUNA baseline receipts do not cover the planned case set");
    }

    static List<CaseComparison> compare(Stage1Receipt baseline, Stage1Receipt candidate) {
        Map<String, CaseRow> left = new LinkedHashMap<>();
        baseline.cases().forEach(row -> left.put(row.id(), row));
        List<CaseComparison> results = new ArrayList<>();
        boolean setMismatch = !baseline.caseSetSha256().equals(candidate.caseSetSha256())
                || !baseline.freezeSha256().equals(candidate.freezeSha256());
        Set<String> seen = new java.util.HashSet<>();
        for (CaseRow right : candidate.cases()) {
            seen.add(right.id());
            if (setMismatch) {
                results.add(new CaseComparison(right.id(), Comparison.INCONCLUSIVE,
                        new ProtocolDeviation(DEVIATION_CASE + right.id() + " call index 0: frozen set differs")));
                continue;
            }
            CaseRow prior = left.get(right.id());
            if (prior == null) {
                results.add(new CaseComparison(right.id(), Comparison.INCONCLUSIVE,
                        new ProtocolDeviation("Missing baseline case " + right.id())));
                continue;
            }
            if (prior.attempt1BodyHashes().isEmpty() || right.attempt1BodyHashes().isEmpty()
                    || !prior.manifestRowSha256().equals(right.manifestRowSha256())) {
                results.add(new CaseComparison(right.id(), Comparison.INCONCLUSIVE,
                        new ProtocolDeviation(DEVIATION_CASE + right.id() + " call index 0")));
                continue;
            }
            int max = Math.max(prior.attempt1BodyHashes().size(), right.attempt1BodyHashes().size());
            String deviation = null;
            for (int index = 0; index < max; index++) {
                if (index >= prior.attempt1BodyHashes().size() || index >= right.attempt1BodyHashes().size()
                        || !prior.attempt1BodyHashes().get(index).equals(right.attempt1BodyHashes().get(index))) {
                    deviation = DEVIATION_CASE + right.id() + " call index " + index;
                    break;
                }
            }
            results.add(new CaseComparison(right.id(), deviation == null ? Comparison.MATCH : Comparison.INCONCLUSIVE,
                    deviation == null ? null : new ProtocolDeviation(deviation)));
        }
        for (String missing : left.keySet())
            if (!seen.contains(missing)) results.add(new CaseComparison(missing, Comparison.INCONCLUSIVE,
                    new ProtocolDeviation("Missing candidate case " + missing)));
        return List.copyOf(results);
    }
}
