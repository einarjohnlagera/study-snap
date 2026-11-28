package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The FRB operator flow: runs the anchor check, the file-backed assembly, the binding check, the
 * per-route classification-input build, one runFrb and the switch-time check in one fixed,
 * fail-closed order, and returns the result as data. The FRB operator flow is runnable by
 * FrbOperator and returns advice only.
 *
 * Honest limits. (1) This flow never opens an arm key. The keys are opened by the caller BEFORE the
 * flow (through the logged gate in ArmKeyAccess), so LockAnchors.requireBeforeOpen is an operator
 * step outside this class. The flow verifies EVERY entry of EVERY access log that the provenance
 * lists; it does not verify an open recorded into any other log, and it does not link the keys
 * opened for assembly to any log entry (the binding check binds no access log, reserve seal or
 * reserve lock). (2) The code cannot prove WHEN the owner noted the anchors; the anchor texts, the
 * fresh heads, the owner facts and the cost approvals are owner-supplied trusted inputs.
 * (3) The decisions are CLASSIFICATION-TIME advice, not switch-time evidence: the ledger check
 * requires the noted heads to equal the current store heads, and within one run call the ledgers
 * cannot change, so in a clean run the fresh heads equal the classification heads; a later attended
 * switch needs a NEW run with newly noted heads. (4) The switch check re-derives its remaining
 * assertions when its opt-in gates pass. An eligible decision is advice for the owner's attended
 * decision, and a completed Outcome alone is not an eligible switch. (5) The flow deploys
 * nothing, changes no environment variable and switches no model. (6) The per-log anchor sets are
 * NARROWER than what FrbProvenance accepts (it lets an entry name any supplied lock): a primary log
 * may name only its own primary lock and the RESERVE-kind lock for primary opening of every drawn
 * reserve, and a reserve log only its lock for reserve opening. That narrowing is intentional. Two
 * drawn workflows whose reserve exports share one log are refused as a duplicate log, also on
 * purpose: a shared reserve log is not part of the preregistered shape.
 *
 * The report. runFrb overwrites the file at the run's report path, so the flow refuses to start
 * when that file already exists and calls runFrb exactly once, after the inputs are built. After a
 * failure at or after RUN_FRB the file left at that path is INVALID and must not be read; the flow
 * does not delete it. Outcome.report is null unless every step completed.
 */
final class FrbOperator {
    private static final String CLASSIFICATION = "classification";
    private static final String DEADLINE = "deadlineClassification";
    private static final String HEADS = "ledgerHeadHashes";
    private static final String CAUSES = "causes";
    private static final String FLAGS = "flags";
    private static final String BLOCKS = "blocksProductionSwitch";
    private static final String DRIFT = "Classification drift: ";
    private static final String INVALID_REPORT = "; the report on disk is INVALID and must not be read";
    private static final String SEPARATOR = ": ";

    private FrbOperator() {}

    /** Precondition failures only; every step failure is an Outcome. */
    static final class InvalidOperatorInputs extends RuntimeException {
        InvalidOperatorInputs(String message) { super(message); }
    }

    /** A refusal raised by this flow's own checks inside a step (anchor sets, logs, drift). */
    static final class OperatorRefusal extends RuntimeException {
        OperatorRefusal(String message) { super(message); }
    }

    enum Step { ANCHORS, ASSEMBLE, BIND, BUILD_INPUTS, RUN_FRB, SWITCH_CHECK }

    /**
     * The flow's inputs. The frb supplies ONLY the provenance, the reserve record and the
     * verification: its bundle and its route classifications are ignored and replaced by the flow.
     * costLedgers may be null (no ledger-backed cost evidence).
     */
    record Sources(FrbBundleAssembler.Sources bundleSources, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<Path, String> notedAnchorTextByAccessLog,
            RouteClassificationInputs.RouteLedgers ledgers,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            RouteClassificationInputs.OwnerFacts owner,
            List<ProductionSwitchEligibility.CostLedgerInput> costLedgers) {
        Sources {
            if (bundleSources == null || run == null || frb == null || notedAnchorTextByAccessLog == null
                    || ledgers == null || freshNotedHeadsByLockSha256 == null || owner == null
                    || run.report() == null)
                throw new InvalidOperatorInputs("Every operator source except the cost ledgers is required");
            if (notedAnchorTextByAccessLog.isEmpty())
                throw new InvalidOperatorInputs("At least one noted anchor text is required");
            if (notedAnchorTextByAccessLog.keySet().stream().anyMatch(Objects::isNull)
                    || notedAnchorTextByAccessLog.values().stream().anyMatch(Objects::isNull)
                    || freshNotedHeadsByLockSha256.keySet().stream().anyMatch(Objects::isNull)
                    || freshNotedHeadsByLockSha256.values().stream().anyMatch(Objects::isNull))
                throw new InvalidOperatorInputs("Noted texts and fresh heads must not contain null");
            if (!freshNotedHeadsByLockSha256.keySet().equals(ledgers.ledgersByLockSha256().keySet()))
                throw new InvalidOperatorInputs("Fresh noted heads must name exactly the ledger locks");
            notedAnchorTextByAccessLog = Collections.unmodifiableMap(new LinkedHashMap<>(notedAnchorTextByAccessLog));
            freshNotedHeadsByLockSha256 = Collections.unmodifiableMap(new TreeMap<>(freshNotedHeadsByLockSha256));
            costLedgers = costLedgers == null ? null : Collections.unmodifiableList(new ArrayList<>(costLedgers));
        }
    }

    /**
     * failedStep and failure are null exactly when every step completed. On a failure every field
     * produced by the failed or a later step is null or empty, and report is null.
     */
    record Outcome(List<Step> completedSteps, Step failedStep, String failure,
            FrbBundleAssembler.Assembled assembled, RouteClassificationInputs.Built built,
            Map<String, FrbClassification> classifications,
            Map<String, ProductionSwitchEligibility.Decision> decisions, Path report) {
        Outcome {
            completedSteps = List.copyOf(completedSteps);
            classifications = classifications == null ? null
                    : Collections.unmodifiableMap(new TreeMap<>(classifications));
            decisions = decisions == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(decisions));
        }
    }

    /** Test-only seam; other callers must use run(Sources). */
    interface FrbRunner {
        ResultsPipeline.Result run(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb);
    }

    /** Test-only seam; other callers must use run(Sources). */
    interface SwitchChecker {
        ProductionSwitchEligibility.Decision check(String route, RouteVerdictCalculator.RouteReport report,
                FrbClassificationInput input, ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
                Map<String, FindingsLedger> ledgers, Map<String, FindingsLedger.ExternalHeads> freshHeads,
                Set<String> pendingRoutes, ProductionSwitchEligibility.SwitchOptions options);
    }

    static Outcome run(Sources sources) {
        return run(sources, ResultsPipeline::runFrb, ProductionSwitchEligibility::checkWithOptions);
    }

    /** Test-only seam overload; other callers must use run(Sources). */
    static Outcome run(Sources sources, FrbRunner runner, SwitchChecker checker) {
        if (sources == null || runner == null || checker == null)
            throw new InvalidOperatorInputs("Sources, runner and checker are required");
        if (Files.exists(sources.run().report()))
            throw new InvalidOperatorInputs("The report path already exists; a report left by an earlier run "
                    + "must not be read or overwritten");
        List<Step> done = new ArrayList<>();

        try {
            verifyAnchors(sources.frb().provenance(), sources.notedAnchorTextByAccessLog());
        } catch (Exception exception) {
            return failed(done, Step.ANCHORS, exception, null, null, null);
        }
        done.add(Step.ANCHORS);

        FrbBundleAssembler.Assembled assembled;
        try {
            assembled = FrbBundleAssembler.assemble(sources.bundleSources());
        } catch (Exception exception) {
            return failed(done, Step.ASSEMBLE, exception, null, null, null);
        }
        done.add(Step.ASSEMBLE);

        // A BIND failure means run or frb names different files from bundleSources: a change after
        // assembly cannot happen inside one call.
        ResultsPipeline.FrbInput frb = sources.frb();
        ResultsPipeline.FrbInput frbBound;
        try {
            frbBound = new ResultsPipeline.FrbInput(assembled.bundle(), frb.reserveRecordFile(),
                    frb.verification(), frb.provenance(), null, true);
            FrbBundleAssembler.requireBound(assembled, sources.run(), frbBound);
        } catch (Exception exception) {
            return failed(done, Step.BIND, exception, assembled, null, null);
        }
        done.add(Step.BIND);

        RouteClassificationInputs.Built built;
        try {
            built = RouteClassificationInputs.build(assembled, sources.run(), frbBound, sources.ledgers(),
                    sources.owner(), sources.costLedgers());
        } catch (Exception exception) {
            return failed(done, Step.BUILD_INPUTS, exception, assembled, null, null);
        }
        done.add(Step.BUILD_INPUTS);

        ResultsPipeline.FrbInput frbFinal;
        Map<String, FrbClassification> classifications;
        try {
            classifications = RouteClassificationInputs.classify(built);
            frbFinal = new ResultsPipeline.FrbInput(assembled.bundle(), frb.reserveRecordFile(),
                    frb.verification(), frb.provenance(), built.inputs(), true);
            ResultsPipeline.Result result = runner.run(sources.run(), frbFinal);
            JsonNode written = IntegritySupport.JSON.readTree(sources.run().report().toFile());
            requireNoDrift(classifications, written, result, built.reports(), built.inputs());
        } catch (Exception exception) {
            return new Outcome(done, Step.RUN_FRB, describe(exception) + INVALID_REPORT,
                    assembled, built, null, null, null);
        }
        done.add(Step.RUN_FRB);

        Map<String, ProductionSwitchEligibility.Decision> decisions = new TreeMap<>();
        try {
            ProductionSwitchEligibility.SwitchOptions options =
                    new ProductionSwitchEligibility.SwitchOptions(sources.costLedgers(), true, true, true,
                            sources.bundleSources(), true);
            for (String route : new TreeSet<>(built.reports().keySet())) {
                ProductionSwitchEligibility.Decision decision = checker.check(route, built.reports().get(route),
                        built.inputs().get(route), sources.run(), frbFinal, sources.ledgers().ledgersByLockSha256(),
                        sources.freshNotedHeadsByLockSha256(), built.pendingRoutes(), options);
                if (decision == null) throw new OperatorRefusal("Switch check returned no decision: " + route);
                decisions.put(route, decision);
            }
        } catch (Exception exception) {
            return failed(done, Step.SWITCH_CHECK, exception, assembled, built, classifications);
        }
        done.add(Step.SWITCH_CHECK);
        return new Outcome(done, null, null, assembled, built, classifications, decisions, sources.run().report());
    }

    private static Outcome failed(List<Step> done, Step step, Exception exception,
            FrbBundleAssembler.Assembled assembled, RouteClassificationInputs.Built built,
            Map<String, FrbClassification> classifications) {
        return new Outcome(done, step, describe(exception), assembled, built, classifications, null, null);
    }

    private static String describe(Exception exception) {
        return exception.getClass().getName() + SEPARATOR + exception.getMessage();
    }

    // ---- ANCHORS ----

    private record LogSpec(Path logFile, Map<Path, Path> lockToSeal) {}

    private static void verifyAnchors(ResultsPipeline.ProvenanceLogs provenance, Map<Path, String> notes) {
        if (provenance == null || provenance.primaries() == null || provenance.reserves() == null)
            throw new OperatorRefusal("Provenance primaries and reserves are required");
        Map<Path, LogSpec> expected = new TreeMap<>();
        for (ResultsPipeline.PrimaryExport export : provenance.primaries()) {
            if (export == null || export.accessLog() == null || export.lockFile() == null || export.sealFile() == null)
                throw new OperatorRefusal("Incomplete primary export");
            Map<Path, Path> locks = new LinkedHashMap<>();
            locks.put(export.lockFile(), export.sealFile());
            for (ResultsPipeline.ReserveExport reserve : provenance.reserves().values()) {
                if (reserve == null || reserve.lockForPrimaryOpening() == null || reserve.sealFile() == null)
                    throw new OperatorRefusal("Incomplete reserve export");
                locks.put(reserve.lockForPrimaryOpening(), reserve.sealFile());
            }
            addLog(expected, export.accessLog(), locks);
        }
        for (ResultsPipeline.ReserveExport reserve : provenance.reserves().values()) {
            if (reserve == null || reserve.reserveKeyAccessLog() == null || reserve.lockForReserveOpening() == null
                    || reserve.sealFile() == null)
                throw new OperatorRefusal("Incomplete reserve export");
            Map<Path, Path> locks = new LinkedHashMap<>();
            locks.put(reserve.lockForReserveOpening(), reserve.sealFile());
            addLog(expected, reserve.reserveKeyAccessLog(), locks);
        }
        Map<Path, String> noted = new TreeMap<>();
        for (Map.Entry<Path, String> entry : notes.entrySet())
            if (noted.put(normalized(entry.getKey()), entry.getValue()) != null)
                throw new OperatorRefusal("Two notes name the same access log");
        for (Path logFile : noted.keySet())
            if (!expected.containsKey(logFile))
                throw new OperatorRefusal("A note names an access log that the provenance does not list");
        for (Path logFile : expected.keySet())
            if (!noted.containsKey(logFile))
                throw new OperatorRefusal("A provenance access log has no noted anchors");
        for (LogSpec spec : expected.values()) verifyLog(spec, noted.get(spec.logFile()));
    }

    private static void addLog(Map<Path, LogSpec> expected, Path logFile, Map<Path, Path> locks) {
        Path normal = normalized(logFile);
        if (expected.put(normal, new LogSpec(normal, locks)) != null)
            throw new OperatorRefusal("Duplicate access log in the provenance");
    }

    private static void verifyLog(LogSpec spec, String noteText) {
        LockAnchors.Noted noted = LockAnchors.parse(noteText);
        LockAnchors.verifyLocksOnDisk(noted, spec.lockToSeal());
        Set<LockAnchors.Anchor> wanted = new HashSet<>();
        spec.lockToSeal().forEach((lock, seal) -> wanted.add(LockAnchors.anchorOf(lock, seal)));
        if (!new HashSet<>(noted.anchors()).equals(wanted))
            throw new OperatorRefusal("Noted anchor set differs from the set expected for this access log");
        LockAnchors.verifyAfterOpen(noted, spec.logFile());
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    // ---- RUN_FRB drift ----

    /** Strict: a missing node or a wrong node type is drift; no boolean or text defaults. */
    static void requireNoDrift(Map<String, FrbClassification> expected, JsonNode report,
            ResultsPipeline.Result result, Map<String, RouteVerdictCalculator.RouteReport> builtReports,
            Map<String, FrbClassificationInput> builtInputs) {
        if (expected == null || report == null || result == null || builtReports == null || builtInputs == null)
            throw new OperatorRefusal(DRIFT + "inputs required");
        JsonNode classification = object(report, CLASSIFICATION);
        JsonNode deadline = object(report, DEADLINE);
        if (!names(classification).equals(expected.keySet()) || !names(deadline).equals(expected.keySet())
                || !builtReports.keySet().equals(expected.keySet()))
            throw new OperatorRefusal(DRIFT + "route sets differ");
        for (Map.Entry<String, FrbClassification> entry : expected.entrySet()) {
            String route = entry.getKey();
            FrbClassification want = entry.getValue();
            JsonNode node = object(classification, route);
            JsonNode name = node.get(CLASSIFICATION);
            if (name == null || !name.isTextual() || !name.textValue().equals(want.classification().name()))
                throw new OperatorRefusal(DRIFT + "classification differs: " + route);
            if (!strings(node, CAUSES).equals(new ArrayList<>(want.causes())))
                throw new OperatorRefusal(DRIFT + "causes differ: " + route);
            if (!strings(node, FLAGS).equals(new ArrayList<>(want.flags())))
                throw new OperatorRefusal(DRIFT + "flags differ: " + route);
            JsonNode blocks = node.get(BLOCKS);
            if (blocks == null || !blocks.isBoolean() || blocks.booleanValue() != want.blocksProductionSwitch())
                throw new OperatorRefusal(DRIFT + "switch block differs: " + route);
            JsonNode text = deadline.get(route);
            if (text == null || !text.isTextual()
                    || !text.textValue().equals(ResultsPipeline.renderFrbClassification(want)))
                throw new OperatorRefusal(DRIFT + "deadline classification differs: " + route);
        }
        requireHeads(report, builtInputs);
        if (result.routes() == null || !result.routes().equals(builtReports))
            throw new OperatorRefusal(DRIFT + "result routes differ from the built reports");
    }

    private static void requireHeads(JsonNode report, Map<String, FrbClassificationInput> builtInputs) {
        Map<String, Map<String, String>> wanted = new TreeMap<>();
        builtInputs.forEach((route, input) -> {
            if (input != null && input.ledgerHeadHashes() != null) wanted.put(route, input.ledgerHeadHashes());
        });
        if (!report.has(HEADS)) throw new OperatorRefusal(DRIFT + "ledger head node missing");
        JsonNode heads = report.get(HEADS);
        if (wanted.isEmpty()) {
            if (!heads.isNull()) throw new OperatorRefusal(DRIFT + "ledger heads differ");
            return;
        }
        if (!heads.isObject() || !names(heads).equals(wanted.keySet()))
            throw new OperatorRefusal(DRIFT + "ledger heads differ");
        for (Map.Entry<String, Map<String, String>> entry : wanted.entrySet()) {
            JsonNode node = heads.get(entry.getKey());
            if (!node.isObject() || !names(node).equals(entry.getValue().keySet()))
                throw new OperatorRefusal(DRIFT + "ledger heads differ: " + entry.getKey());
            for (Map.Entry<String, String> head : entry.getValue().entrySet()) {
                JsonNode value = node.get(head.getKey());
                if (!value.isTextual() || !value.textValue().equals(head.getValue()))
                    throw new OperatorRefusal(DRIFT + "ledger heads differ: " + entry.getKey());
            }
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) throw new OperatorRefusal(DRIFT + "missing or non-object node: " + field);
        return node;
    }

    private static Set<String> names(JsonNode node) {
        Set<String> result = new TreeSet<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) result.add(iterator.next());
        return result;
    }

    private static List<String> strings(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) throw new OperatorRefusal(DRIFT + "missing or non-array node: " + field);
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new OperatorRefusal(DRIFT + "non-text value in: " + field);
            result.add(value.textValue());
        }
        return result;
    }
}
