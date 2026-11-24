package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic receipts only; the FRB fixture builder is reused without its private file helpers. */
class OperationalCostReviewTest {
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String CASE = "case-a";
    private static final String TIP_CASE = "tip";
    private static final String TRIGGERED_FLAG = "COST_REVIEW_TRIGGERED:";
    private static final String PASS_BLOCKED_TEXT = "BENCHMARK_PASS (production switch blocked: COST_REVIEW_TRIGGERED:";
    private static final String PARAMETERS_FILE = "parameters.json";
    private static final String PARAMETERS_DIGEST_FILE = "parameters.json.sha256";
    private static final String MAPPING_FILE = "mapping.json";
    private static final String MAPPING_DIGEST_FILE = "mapping.json.sha256";
    private static final String BILLED_FIELD = "billedUsd";
    private static final String CONSERVATIVE_FIELD = "conservativeUsd";
    private static final String NOT_TRIGGERED = "NOT_TRIGGERED";
    private static final Path MAPPING = Path.of(
            "src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json");
    private static final Path PARAMETERS = Path.of(
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;

    private static Stage1Receipt.CaseRow row(String id, String workflow, String conservative,
            String billed, boolean delivered) {
        return new Stage1Receipt.CaseRow(id, workflow, "hash",
                delivered ? CaseArmOutcome.State.COMPLETE : CaseArmOutcome.State.FAILED,
                delivered, delivered, delivered ? null : "FAILED",
                new BigDecimal(conservative), new BigDecimal(billed), List.of(), List.of());
    }

    private static FreezeRecord freeze(Set<String> paidWorkflows) {
        return new FreezeRecord(null, null, null, null, null,
                Map.of(PAID, new FreezeRecord.Arm("paid", List.copyOf(paidWorkflows))),
                null, null, Set.of());
    }

    private Map<String, OperationalCostReview.Result> review(Map<String, String> primary,
            Map<String, Stage1Receipt.CaseRow> mini, Map<String, Stage1Receipt.CaseRow> paid,
            Map<String, Stage1Receipt.CaseRow> candidate, EvidenceMapping mapping,
            Set<String> paidWorkflows) throws Exception {
        Path copy = temp.resolve(PARAMETERS_FILE);
        Files.write(copy, Files.readAllBytes(PARAMETERS));
        Files.writeString(temp.resolve(PARAMETERS_DIGEST_FILE), FreezeRecord.sha256(copy));
        return OperationalCostReview.review(FrozenGateParameters.load(copy), freeze(paidWorkflows),
                mapping, primary, mini, paid, candidate);
    }

    private OperationalCostReview.Result one(String baseline, String candidate) throws Exception {
        return review(Map.of(CASE, NOTE), Map.of(CASE, row(CASE, NOTE, baseline, baseline, true)),
                Map.of(), Map.of(CASE, row(CASE, NOTE, candidate, candidate, true)),
                EvidenceMapping.load(MAPPING), Set.of()).get(NOTE);
    }

    @Test void exactMultiplierIsOkAndOneDecimalUnitMoreTriggers() throws Exception {
        assertThat(one("1", "1.25").status()).isEqualTo(OperationalCostReview.Status.OK);
        assertThat(one("1", "1.25").ratio()).isEqualByComparingTo("1.25");
        assertThat(one("1", "1.2500000000000000000000000000000001").status())
                .isEqualTo(OperationalCostReview.Status.TRIGGERED);
        assertThat(one("0.00018", "0.000226").status())
                .isEqualTo(OperationalCostReview.Status.TRIGGERED);
    }

    @Test void zeroAndEachUndeliveredArmAreUndetermined() throws Exception {
        assertThat(one("0", "1").status()).isEqualTo(OperationalCostReview.Status.UNDETERMINED);
        var mini = Map.of(CASE, row(CASE, NOTE, "9", "9", false));
        var candidate = Map.of(CASE, row(CASE, NOTE, "1", "1", true));
        var first = review(Map.of(CASE, NOTE), mini, Map.of(), candidate,
                EvidenceMapping.load(MAPPING), Set.of()).get(NOTE);
        assertThat(first.status()).isEqualTo(OperationalCostReview.Status.UNDETERMINED);
        assertThat(first.baselineDelivered()).isZero();
        var second = review(Map.of(CASE, NOTE), candidate, Map.of(), mini,
                EvidenceMapping.load(MAPPING), Set.of()).get(NOTE);
        assertThat(second.status()).isEqualTo(OperationalCostReview.Status.UNDETERMINED);
        assertThat(second.candidateDelivered()).isZero();
    }

    @Test void deliveredOnlyMeansAndWorkflowBaselineSelection() throws Exception {
        Map<String, String> primary = Map.of("a", NOTE, "b", NOTE, "c", TIP);
        var mini = Map.of("a", row("a", NOTE, "1", "1", true),
                "b", row("b", NOTE, "99", "99", false),
                "c", row("c", TIP, "100", "100", true));
        var paid = Map.of("c", row("c", TIP, "2", "2", true));
        var candidate = Map.of("a", row("a", NOTE, "1.3", "1.3", true),
                "b", row("b", NOTE, "99", "99", false),
                "c", row("c", TIP, "2", "2", true));
        var result = review(primary, mini, paid, candidate, EvidenceMapping.load(MAPPING), Set.of(TIP));
        assertThat(result.get(NOTE).baselineMeanUsd()).isEqualByComparingTo("1");
        assertThat(result.get(NOTE).candidateDelivered()).isEqualTo(1);
        assertThat(result.get(NOTE).status()).isEqualTo(OperationalCostReview.Status.TRIGGERED);
        assertThat(result.get(TIP).baselineMeanUsd()).isEqualByComparingTo("2");
        assertThat(result.get(TIP).status()).isEqualTo(OperationalCostReview.Status.OK);
    }

    @Test void meanRatherThanSingleExpensiveCaseControlsReview() throws Exception {
        Map<String, String> primary = new java.util.LinkedHashMap<>();
        Map<String, Stage1Receipt.CaseRow> mini = new java.util.LinkedHashMap<>();
        Map<String, Stage1Receipt.CaseRow> candidate = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 11; i++) {
            String id = "n" + i;
            primary.put(id, NOTE);
            mini.put(id, row(id, NOTE, "1", "1", true));
            candidate.put(id, row(id, NOTE, i == 10 ? "2" : "0.1", i == 10 ? "2" : "0.1", true));
        }
        var result = review(primary, mini, Map.of(), candidate, EvidenceMapping.load(MAPPING), Set.of()).get(NOTE);
        assertThat(result.candidateDelivered()).isEqualTo(11);
        assertThat(result.candidateMeanUsd()).isEqualByComparingTo("0.2727272727272727272727272727272727");
        assertThat(result.status()).isEqualTo(OperationalCostReview.Status.OK);
    }

    @Test void shippedBilledFigureAndConservativeVariantAreHonored() throws Exception {
        var baseline = Map.of(CASE, row(CASE, NOTE, "1", "1", true));
        var candidate = Map.of(CASE, row(CASE, NOTE, "2", "1", true));
        EvidenceMapping billed = EvidenceMapping.load(MAPPING);
        assertThat(review(Map.of(CASE, NOTE), baseline, Map.of(), candidate, billed, Set.of())
                .get(NOTE).candidateMeanUsd()).isEqualByComparingTo("1");
        Path variant = temp.resolve(MAPPING_FILE);
        Files.writeString(variant, Files.readString(MAPPING).replace(BILLED_FIELD, CONSERVATIVE_FIELD));
        Files.writeString(temp.resolve(MAPPING_DIGEST_FILE), FreezeRecord.sha256(variant));
        EvidenceMapping conservative = EvidenceMapping.load(variant);
        assertThat(review(Map.of(CASE, NOTE), baseline, Map.of(), candidate, conservative, Set.of())
                .get(NOTE).candidateMeanUsd()).isEqualByComparingTo("2");
    }

    @Test void triggerMultiplierIsReadFromMappingBytes() throws Exception {
        Path variant = temp.resolve(MAPPING_FILE);
        Files.writeString(variant, Files.readString(MAPPING).replace("1.25", "1.50"));
        Files.writeString(temp.resolve(MAPPING_DIGEST_FILE), FreezeRecord.sha256(variant));
        var baseline = Map.of(CASE, row(CASE, NOTE, "1", "1", true));
        var candidate = Map.of(CASE, row(CASE, NOTE, "1.3", "1.3", true));
        assertThat(review(Map.of(CASE, NOTE), baseline, Map.of(), candidate,
                EvidenceMapping.load(MAPPING), Set.of()).get(NOTE).status())
                .isEqualTo(OperationalCostReview.Status.TRIGGERED);
        var amended = review(Map.of(CASE, NOTE), baseline, Map.of(), candidate,
                EvidenceMapping.load(variant), Set.of()).get(NOTE);
        assertThat(amended.status()).isEqualTo(OperationalCostReview.Status.OK);
        assertThat(amended.triggerMultiplier()).isEqualByComparingTo("1.50");
    }

    @Test void malformedPrimaryRowThrowsNamedError() throws Exception {
        var malformed = Map.of(CASE, row(CASE, TIP, "1", "1", true));
        EvidenceMapping mapping = EvidenceMapping.load(MAPPING);
        var primary = Map.of(CASE, NOTE);
        Map<String, Stage1Receipt.CaseRow> absent = Map.of();
        Set<String> noPaid = Set.of();
        assertThatThrownBy(() -> review(primary, malformed, absent, malformed,
                mapping, noPaid)).isInstanceOf(InvalidCostReview.class);
    }

    private FrbPipelineTest.Fixture fixture() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        return builder.fixture(false, 0);
    }

    private static void setCost(Path receipt, String id, String billed) throws Exception {
        ObjectNode body = (ObjectNode) JSON.readTree(receipt.toFile());
        for (JsonNode item : body.path("cases")) if (id.equals(item.path("id").asText())) {
            ((ObjectNode) item).put(BILLED_FIELD, new BigDecimal(billed));
            ((ObjectNode) item).put(CONSERVATIVE_FIELD, new BigDecimal(billed));
        }
        JSON.writeValue(receipt.toFile(), body);
    }

    @Test void frbReportAndOptInClassificationKeepQualityVerdict() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "0.02");
        var plain = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        String report = Files.readString(plain.report());
        assertThat(report).contains("operationalCostReview", "candidateMeanUsd", "baselineMeanUsd",
                "candidateDelivered", "baselineDelivered", "ratio", "TRIGGERED", "triggerMultiplier");
        var optIn = new ResultsPipeline.FrbInput(fixture.frb().bundle(), fixture.frb().reserveRecordFile(),
                fixture.frb().verification(), fixture.frb().provenance(), fixture.frb().routeClassifications(), true);
        var flagged = ResultsPipeline.runFrb(fixture.run(), optIn);
        assertThat(flagged.routes()).isEqualTo(plain.routes());
        assertThat(Files.readString(flagged.report())).contains(TRIGGERED_FLAG + TIP);
        var before = ResultsPipeline.classifyFrbRoutes(plain.routes(), fixture.frb().routeClassifications(), Set.of());
        var after = ResultsPipeline.classifyFrbRoutes(plain.routes(), fixture.frb().routeClassifications(), Set.of(),
                OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(fixture.run()),
                        FrozenGateParameters.load(fixture.run().parameters())));
        assertThat(after.get(ROUTE).classification()).isEqualTo(before.get(ROUTE).classification());
        assertThat(after.get(ROUTE).blocksProductionSwitch()).isTrue();
        assertThat(before.get(ROUTE).flags()).doesNotContain(TRIGGERED_FLAG + TIP);
        assertThat(ResultsPipeline.operationalCostReview(fixture.run()).get(TIP).baselineMeanUsd())
                .isEqualByComparingTo("0.01");
        var supplied = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), Map.of("head", "hash"));
        var passing = ResultsPipeline.classifyFrbRoutes(plain.routes(), Map.of(ROUTE, supplied), Set.of(),
                OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(fixture.run()),
                        FrozenGateParameters.load(fixture.run().parameters()))).get(ROUTE);
        assertThat(passing.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(passing.blocksProductionSwitch()).isTrue();
        assertThat(ResultsPipeline.renderFrbClassification(passing))
                .contains(PASS_BLOCKED_TEXT + TIP);
    }

    @Test void candidateCostAloneCannotChangeAnyT3RouteReport() throws Exception {
        var fixture = fixture();
        var before = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "100");
        var after = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes();
        // The former per-case ceiling made this candidate-only T3; the quality report now stays identical.
        assertThat(after).isEqualTo(before);
        assertThat(after.get(ROUTE).workflows().get(TIP).candidateComponents()).doesNotContainKey("COST");
    }

    @Test void undeterminedReviewAlsoBlocksWhenFrbMergeIsEnabled() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().baseline41().receipt(), TIP_CASE, "0");
        var ordinary = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        var optIn = new ResultsPipeline.FrbInput(fixture.frb().bundle(), fixture.frb().reserveRecordFile(),
                fixture.frb().verification(), fixture.frb().provenance(), fixture.frb().routeClassifications(), true);
        var merged = ResultsPipeline.runFrb(fixture.run(), optIn);
        assertThat(merged.routes()).isEqualTo(ordinary.routes());
        assertThat(Files.readString(merged.report())).contains("COST_REVIEW_UNDETERMINED:" + TIP);
        var flags = OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(fixture.run()),
                FrozenGateParameters.load(fixture.run().parameters()));
        var classification = ResultsPipeline.classifyFrbRoutes(merged.routes(), optIn.routeClassifications(),
                Set.of(), flags).get(ROUTE);
        assertThat(classification.flags()).contains("COST_REVIEW_UNDETERMINED:" + TIP);
        assertThat(classification.blocksProductionSwitch()).isTrue();
    }

    @Test void reserveSpendDoesNotEnterPrimaryMean() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        var fixture = builder.fixture(true, 0);
        var before = ResultsPipeline.operationalCostReview(fixture.run());
        setCost(fixture.run().reserves().get(TIP).candidate().receipt(), "reserve-0", "100");
        assertThat(ResultsPipeline.operationalCostReview(fixture.run())).isEqualTo(before);
    }

    @Test void routeFlagsIncludeOnlyTheTriggeredOrUndeterminedWorkflows() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "0.02");
        var parameters = FrozenGateParameters.load(fixture.run().parameters());
        var flags = OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(fixture.run()), parameters);
        assertThat(flags.get(ROUTE)).containsExactly(TRIGGERED_FLAG + TIP);
        setCost(fixture.run().baselineMini().receipt(), "a-one", "0");
        setCost(fixture.run().baselineMini().receipt(), "a-two", "0");
        flags = OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(fixture.run()), parameters);
        assertThat(flags.get(ROUTE)).containsExactly(TRIGGERED_FLAG + TIP,
                "COST_REVIEW_UNDETERMINED:" + NOTE);
    }

    @Test void derivedCostFlagAloneBlocksCleanBenchmarkPass() throws Exception {
        var fixture = fixture();
        var report = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes().get(ROUTE);
        var constructor = ReviewComposition.ComposedReviewCheck.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var workload = new KeyDefectWorkload.Parameters(BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE);
        var estimate = new KeyDefectWorkload.Estimate(0, Map.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, 0, 0, false, List.of());
        Map<String, String> heads = Map.of("head", "hash");
        var check = (ReviewComposition.ComposedReviewCheck) constructor.newInstance(ROUTE, Set.of(),
                Set.of(NOTE, TIP), Set.of(), heads, Map.of(), workload, Set.of("LUNA"),
                Set.of(MINI, PAID), estimate, report.parameterSha256());
        var input = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), heads, check);
        Map<String, RouteVerdictCalculator.RouteReport> routes = Map.of(ROUTE, report);
        Map<String, FrbClassificationInput> inputs = Map.of(ROUTE, input);
        var plain = ResultsPipeline.classifyFrbRoutes(routes, inputs, Set.of()).get(ROUTE);
        assertThat(plain.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(plain.flags()).isEmpty();
        assertThat(plain.blocksProductionSwitch()).isFalse();
        SortedSet<String> costFlags = new TreeSet<>(Set.of(TRIGGERED_FLAG + TIP));
        var flagged = ResultsPipeline.classifyFrbRoutes(routes, inputs, Set.of(),
                Map.of(ROUTE, costFlags)).get(ROUTE);
        assertThat(flagged.classification()).isEqualTo(plain.classification());
        assertThat(flagged.flags()).containsExactly(TRIGGERED_FLAG + TIP);
        assertThat(flagged.blocksProductionSwitch()).isTrue();
        assertThat(ResultsPipeline.renderFrbClassification(flagged))
                .contains(PASS_BLOCKED_TEXT + TIP);
    }

    @Test void flagsFollowEachFrozenWorkflowRoute() throws Exception {
        Path copy = temp.resolve(PARAMETERS_FILE);
        Files.write(copy, Files.readAllBytes(PARAMETERS));
        Files.writeString(temp.resolve(PARAMETERS_DIGEST_FILE), FreezeRecord.sha256(copy));
        var parameters = FrozenGateParameters.load(copy);
        var triggered = new OperationalCostReview.Result(BigDecimal.TWO, BigDecimal.ONE,
                1, 1, BigDecimal.TWO, OperationalCostReview.Status.TRIGGERED, new BigDecimal("1.25"));
        var undetermined = new OperationalCostReview.Result(BigDecimal.ONE, BigDecimal.ZERO,
                1, 1, null, OperationalCostReview.Status.UNDETERMINED, new BigDecimal("1.25"));
        var grouped = OperationalCostReview.flagsByRoute(Map.of(TIP, triggered,
                "INTERVIEW_GENERATION", undetermined), parameters);
        assertThat(grouped.get(ROUTE)).containsExactly(TRIGGERED_FLAG + TIP);
        assertThat(grouped.get("PAID_INTERACTIVE"))
                .containsExactly("COST_REVIEW_UNDETERMINED:INTERVIEW_GENERATION");
    }

    @Test void switchRecomputationIsOptInAndPermanentReasonsRemain() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "0.02");
        var report = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes().get(ROUTE);
        var old = ProductionSwitchEligibility.check(ROUTE, report, null, fixture.run(), fixture.frb(),
                Map.of(), Map.of(), Set.of());
        var defaultOptions = ProductionSwitchEligibility.checkWithOptions(ROUTE, report, null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, false));
        assertThat(defaultOptions).isEqualTo(old);
        assertThat(old.reasons()).doesNotContain(ProductionSwitchEligibility.COST_REVIEW_REQUIRED,
                ProductionSwitchEligibility.COST_REVIEW_RECOMPUTATION_FAILED);
        assertThat(ProductionSwitchEligibility.check(ROUTE, report, null, fixture.run(), fixture.frb(),
                Map.of(), Map.of(), Set.of(), null)).isEqualTo(old);
        assertThat(ProductionSwitchEligibility.check(ROUTE, report, null, fixture.run(), fixture.frb(),
                Map.of(), Map.of(), Set.of(), null, false)).isEqualTo(old);
        var required = ProductionSwitchEligibility.checkWithOptions(ROUTE, report, null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, false, true));
        assertThat(required.eligible()).isFalse();
        assertThat(required.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_REQUIRED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED,
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        setCost(fixture.run().baseline41().receipt(), TIP_CASE, "0");
        var undetermined = ProductionSwitchEligibility.checkWithOptions(ROUTE, report, null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, false, true));
        assertThat(undetermined.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_REQUIRED);
        Files.writeString(fixture.run().freeze(), "corrupt");
        var failed = ProductionSwitchEligibility.checkWithOptions(ROUTE, report, null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, false, true));
        assertThat(failed.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_RECOMPUTATION_FAILED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }

    @Test void malformedReceiptSurfacesAsInvalidInputAndSwitchRecomputationFailure() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "-1");
        var run = fixture.run();
        var frb = fixture.frb();
        assertThatThrownBy(() -> ResultsPipeline.runFrb(run, frb))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasCauseInstanceOf(InvalidCostReview.class);
        var decision = ProductionSwitchEligibility.checkWithOptions(ROUTE, null, null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, false, true));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_RECOMPUTATION_FAILED);
    }

    @Test void missingRowsAreNotCountedAsFreeDeliveries() throws Exception {
        Map<String, String> primary = Map.of(CASE, NOTE, "second-case", NOTE);
        Map<String, Stage1Receipt.CaseRow> baseline = Map.of(CASE, row(CASE, NOTE, "1", "1", true),
                "second-case", row("second-case", NOTE, "1", "1", true));
        Map<String, Stage1Receipt.CaseRow> candidate = Map.of(CASE, row(CASE, NOTE, "1.25", "1.25", true));
        var result = review(primary, Map.of(), baseline, candidate, EvidenceMapping.load(MAPPING),
                Set.of(NOTE)).get(NOTE);
        assertThat(result.candidateDelivered()).isEqualTo(1);
        assertThat(result.candidateMeanUsd()).isEqualByComparingTo("1.25");
        assertThat(result.baselineDelivered()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(OperationalCostReview.Status.OK);
    }

    @Test void aRowKeyedUnderAnotherCaseIdIsRefused() throws Exception {
        var mismatched = Map.of(CASE, row("other-case", NOTE, "1", "1", true));
        var good = Map.of(CASE, row(CASE, NOTE, "1", "1", true));
        EvidenceMapping mapping = EvidenceMapping.load(MAPPING);
        var primary = Map.of(CASE, NOTE);
        Map<String, Stage1Receipt.CaseRow> absent = Map.of();
        Set<String> noPaid = Set.of();
        assertThatThrownBy(() -> review(primary, absent, absent, mismatched, mapping, noPaid))
                .isInstanceOf(InvalidCostReview.class);
        assertThatThrownBy(() -> review(primary, mismatched, absent, good, mapping, noPaid))
                .isInstanceOf(InvalidCostReview.class);
    }

    @Test void everyFrozenWorkflowWithoutAPrimaryCaseIsUndetermined() throws Exception {
        var result = review(Map.of(CASE, NOTE), Map.of(CASE, row(CASE, NOTE, "1", "1", true)), Map.of(),
                Map.of(CASE, row(CASE, NOTE, "1", "1", true)), EvidenceMapping.load(MAPPING), Set.of());
        assertThat(result.get(TIP).status()).isEqualTo(OperationalCostReview.Status.UNDETERMINED);
        assertThat(result.get(TIP).candidateDelivered()).isZero();
        assertThat(result.get(TIP).baselineDelivered()).isZero();
    }

    @Test void derivedCostFlagsMustNameKnownRoutes() throws Exception {
        var fixture = fixture();
        var plain = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        var unknown = Map.of("NO_SUCH_ROUTE", (java.util.SortedSet<String>) new java.util.TreeSet<>(Set.of("X")));
        var routes = plain.routes();
        var inputs = fixture.frb().routeClassifications();
        assertThatThrownBy(() -> ResultsPipeline.classifyFrbRoutes(routes, inputs, Set.of(), unknown))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void theDefaultFrbInputNeverMergesCostFlagsEvenWhenTheReviewTriggers() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "0.02");
        var plain = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        String report = Files.readString(plain.report());
        assertThat(report).contains("operationalCostReview").contains("TRIGGERED");
        assertThat(report).doesNotContain(TRIGGERED_FLAG + TIP);
    }

    @Test void theSwitchReasonIsScopedToTheQueriedRoute() throws Exception {
        var fixture = fixture();
        setCost(fixture.run().candidate().receipt(), TIP_CASE, "0.02");
        var routes = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes();
        var options = new ProductionSwitchEligibility.SwitchOptions(null, false, false, true);
        var triggeredRoute = ProductionSwitchEligibility.checkWithOptions(ROUTE, routes.get(ROUTE), null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(), options);
        assertThat(triggeredRoute.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_REQUIRED);
        // The synthetic FRB fixture declares one route, so another route name carries no flagged workflow.
        var otherRoute = ProductionSwitchEligibility.checkWithOptions("PAID_INTERACTIVE", routes.get(ROUTE), null,
                fixture.run(), fixture.frb(), Map.of(), Map.of(), Set.of(), options);
        assertThat(otherRoute.reasons()).doesNotContain(ProductionSwitchEligibility.COST_REVIEW_REQUIRED,
                ProductionSwitchEligibility.COST_REVIEW_RECOMPUTATION_FAILED);
    }

    @Test void aDeliveredRowWithAnyMissingCostFigureIsRefused() throws Exception {
        var noConservative = new Stage1Receipt.CaseRow(CASE, NOTE, "hash", CaseArmOutcome.State.COMPLETE,
                true, true, null, null, new BigDecimal("1"), List.of(), List.of());
        var noBilled = new Stage1Receipt.CaseRow(CASE, NOTE, "hash", CaseArmOutcome.State.COMPLETE,
                true, true, null, new BigDecimal("1"), null, List.of(), List.of());
        var good = Map.of(CASE, row(CASE, NOTE, "1", "1", true));
        EvidenceMapping mapping = EvidenceMapping.load(MAPPING);
        var primary = Map.of(CASE, NOTE);
        Map<String, Stage1Receipt.CaseRow> absent = Map.of();
        Set<String> noPaid = Set.of();
        assertThatThrownBy(() -> review(primary, good, absent, Map.of(CASE, noConservative), mapping, noPaid))
                .isInstanceOf(InvalidCostReview.class);
        assertThatThrownBy(() -> review(primary, good, absent, Map.of(CASE, noBilled), mapping, noPaid))
                .isInstanceOf(InvalidCostReview.class);
    }
}
