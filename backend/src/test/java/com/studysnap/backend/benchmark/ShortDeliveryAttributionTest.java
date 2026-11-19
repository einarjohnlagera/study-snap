package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Per-arm decisions and synthetic FRB report/cause integration. */
class ShortDeliveryAttributionTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String CASE = "a-three";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String BASELINE_CAUSE = "SHORT_DELIVERY_BASELINE";
    private static final String CANDIDATE_CAUSE = "SHORT_DELIVERY_CANDIDATE";
    private static final int WIDTH = 2;
    private static final String SHORT_DELIVERY = "SHORT_DELIVERY";
    private static final String MISSING_ARM_OUTPUT = "MISSING_ARM_OUTPUT";
    private static final String CANDIDATE_ROLE = "CANDIDATE";
    private static final String CASES_FIELD = "cases";
    private static final String FAILURE_CLASS_FIELD = "failureClass";
    private static final String DELIVERED_COUNT_FIELD = "deliveredQuestionCount";
    private static final String SCHEMA_OR_PARSE = "SCHEMA_OR_PARSE";
    private static final String RECHECK_PREFIX = "recheck-";
    private static final String DELIVERED_OUTPUT_FILE = "delivered-outputs.jsonl";
    private static final String RECEIPT_FILE = "receipt.json";
    private static final String MISSING_CAUSE = "MISSING";
    private static final String EXCLUDED_EVIDENCE_FIELD = "excludedFixtureEvidence";
    private static final String SHORT_DELIVERY_FIELD = "shortDelivery";
    private static final String TREATMENT_FIELD = "treatment";
    private static final String REPRODUCED = "REPRODUCED";
    private static final String INCOMPLETE_RECHECK = "INCOMPLETE_RECHECK";
    private static final String STUDY_TIP = "STUDY_TIP";
    private static final String RECHECKED = "RECHECKED";
    private static final String RECOVERY_OUTCOME_FIELD = "recoveryOutcome";
    private static final String FIRST_CASE = "a-one";
    private static final String ISOLATED_FINAL_FAILURE = "ISOLATED_FINAL_FAILURE";
    private static final String RECOVERED = "RECOVERED";
    @TempDir Path temp;

    private static Stage1Receipt.CaseRow row(String failure, Integer count) {
        return new Stage1Receipt.CaseRow(CASE, NOTE, "hash", CaseArmOutcome.State.FAILED,
                false, false, failure, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), null, count);
    }

    private static List<ResultsPipeline.ShortDeliveryFinding> findings(Stage1Receipt.CaseRow baseline,
            Stage1Receipt.CaseRow candidate, boolean baselineRechecked, boolean candidateRechecked,
            boolean excluded, String reason, int width, boolean synthetic) {
        return ResultsPipeline.shortDeliveryFindings(CASE, NOTE, baseline, candidate,
                baselineRechecked, candidateRechecked, excluded, reason, width, synthetic);
    }

    @Test void decisionsArePerArmAndRejectContradictoryExclusionOrWidth() {
        Stage1Receipt.CaseRow shortRow = row(SHORT_DELIVERY, 1);
        assertThat(findings(null, shortRow, false, true, true, MISSING_ARM_OUTPUT, WIDTH, false))
                .singleElement().satisfies(f -> {
                    assertThat(f.role()).isEqualTo(CANDIDATE_ROLE);
                    assertThat(f.rechecked()).isTrue();
                    assertThat(f.unresolvedCause()).isNull();
                });
        assertThat(findings(shortRow, null, false, true, true, MISSING_ARM_OUTPUT, WIDTH, false))
                .singleElement().extracting(ResultsPipeline.ShortDeliveryFinding::unresolvedCause)
                .isEqualTo(BASELINE_CAUSE);
        assertThat(findings(shortRow, shortRow, false, false, true, MISSING_ARM_OUTPUT, WIDTH, false))
                .extracting(ResultsPipeline.ShortDeliveryFinding::unresolvedCause)
                .containsExactly(BASELINE_CAUSE, CANDIDATE_CAUSE);
        assertThatThrownBy(() -> findings(null, null, false, false, true,
                SHORT_DELIVERY, WIDTH, false)).isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThat(findings(null, null, false, false, true, SHORT_DELIVERY, WIDTH, true)).isEmpty();
        assertThat(findings(null, null, false, false, true, MISSING_ARM_OUTPUT, WIDTH, false)).isEmpty();
        assertThatThrownBy(() -> findings(null, shortRow, false, false, true,
                MISSING_ARM_OUTPUT, 1, false)).isInstanceOf(ResultsPipeline.InvalidInput.class);
        assertThatThrownBy(() -> findings(null, shortRow, false, false, true,
                MISSING_ARM_OUTPUT, 0, false)).isInstanceOf(ResultsPipeline.InvalidInput.class);
        Stage1Receipt.CaseRow legacyShort = row(SHORT_DELIVERY, null);
        assertThat(findings(null, legacyShort, false, false, true,
                MISSING_ARM_OUTPUT, WIDTH, false)).hasSize(1);
        assertThatThrownBy(() -> findings(null, legacyShort, false, false, true,
                MISSING_ARM_OUTPUT, 0, false)).isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    private FrbPipelineTest.Fixture fixture(boolean excluded) throws Exception {
        FrbPipelineTest harness = new FrbPipelineTest();
        Field path = FrbPipelineTest.class.getDeclaredField("temp");
        path.setAccessible(true);
        path.set(harness, temp);
        return harness.fixture(false, 0, WIDTH, false, false, excluded);
    }

    private static void failArm(ResultsPipeline.ArmFiles arm, boolean withCount) throws Exception {
        failArm(arm, CASE, SHORT_DELIVERY, withCount ? 1 : null);
    }

    private static void failArm(ResultsPipeline.ArmFiles arm, String failure, Integer count) throws Exception {
        failArm(arm, CASE, failure, count);
    }

    private static void failArm(ResultsPipeline.ArmFiles arm, String caseId,
            String failure, Integer count) throws Exception {
        ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
        List<String> lines = new ArrayList<>(Files.readAllLines(arm.delivered()));
        for (int index = 0; index < receipt.path(CASES_FIELD).size(); index++) {
            ObjectNode row = (ObjectNode) receipt.path(CASES_FIELD).get(index);
            if (!caseId.equals(row.path("id").asText())) continue;
            row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                    .put(FAILURE_CLASS_FIELD, failure);
            if (count != null) row.put(DELIVERED_COUNT_FIELD, count);
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(caseId, NOTE,
                    receipt.path("arm").asText(), row.path("manifestRowSha256").asText(), 0,
                    CaseArmOutcome.State.FAILED, failure, null));
            lines.set(index, line);
            row.put("deliveredOutputSha256", FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        }
        Files.writeString(arm.delivered(), String.join("\n", lines) + "\n");
        receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(arm.delivered()));
        JSON.writeValue(arm.receipt().toFile(), receipt);
    }

    private ResultsPipeline.FrbRunInput withBaselineRechecks(FrbPipelineTest.Fixture fixture) throws Exception {
        ResultsPipeline.FrbRunInput original = fixture.run();
        ResultsPipeline.ArmFiles baseline = original.baselineMini();
        failArm(baseline, SCHEMA_OR_PARSE, null);
        Stage1Receipt receipt = Stage1Receipt.read(baseline.receipt());
        Stage1Receipt.CaseRow row = receipt.cases().stream().filter(value -> CASE.equals(value.id()))
                .findFirst().orElseThrow();
        Path directory = temp.resolve("baseline-rechecks");
        List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE, NOTE,
                    receipt.arm(), row.manifestRowSha256(), index, CaseArmOutcome.State.FAILED,
                    SCHEMA_OR_PARSE, null));
            Path output = directory.resolve(CASE).resolve(RECHECK_PREFIX + index).resolve(DELIVERED_OUTPUT_FILE);
            Files.createDirectories(output.getParent());
            Files.writeString(output, line + "\n");
            repeats.add(new RecheckReceipt.Repeat(index, SCHEMA_OR_PARSE,
                    RouteVerdictCalculator.Evidence.SCORED, null,
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)), FreezeRecord.sha256(output)));
        }
        Path recheckReceipt = directory.resolve(RECEIPT_FILE);
        new RecheckReceipt(receipt.head(), receipt.freezeSha256(), receipt.arm(), receipt.model(),
                Set.of(SCHEMA_OR_PARSE), List.of(new RecheckReceipt.Case(CASE, SCHEMA_OR_PARSE, repeats)),
                receipt.caseSetSha256()).write(recheckReceipt);
        ResultsPipeline.ArmFiles changed = new ResultsPipeline.ArmFiles(baseline.receipt(), baseline.delivered(),
                recheckReceipt, directory);
        return new ResultsPipeline.FrbRunInput(original.repoRoot(), original.freeze(), original.parameters(),
                original.mapping(), original.manifests(), changed, original.baseline41(), original.candidate(),
                original.reserves(), original.compatibilityReports(), original.headCommitTime(),
                original.ownerDispositions(), original.report(), original.synthetic());
    }

    private ResultsPipeline.FrbRunInput withRecheckedShortCandidate(FrbPipelineTest.Fixture fixture)
            throws Exception {
        return withRecheckedShortCandidate(fixture, CASE, false);
    }

    private ResultsPipeline.FrbRunInput withRecheckedShortCandidate(FrbPipelineTest.Fixture fixture,
            String caseId, boolean recovered) throws Exception {
        ResultsPipeline.FrbRunInput original = fixture.run();
        JsonNode successfulContent = null;
        if (recovered) {
            for (String line : Files.readAllLines(original.candidate().delivered())) {
                JsonNode output = JSON.readTree(line);
                if (caseId.equals(output.path("caseId").asText()))
                    successfulContent = output.path("content").deepCopy();
            }
        }
        failArm(original.candidate(), caseId, SHORT_DELIVERY, 1);
        ObjectNode freeze = (ObjectNode) JSON.readTree(original.freeze().toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode) freeze.path("recheckEligibleFailureClasses"))
                .add(SHORT_DELIVERY);
        JSON.writeValue(original.freeze().toFile(), freeze);
        String freezeHash = FreezeRecord.sha256(original.freeze());
        Files.writeString(IntegritySupport.detached(original.freeze()), freezeHash);
        for (ResultsPipeline.ArmFiles arm : List.of(original.baselineMini(), original.baseline41(),
                original.candidate())) {
            ObjectNode stage = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            stage.put("freezeSha256", freezeHash);
            JSON.writeValue(arm.receipt().toFile(), stage);
        }
        Stage1Receipt receipt = Stage1Receipt.read(original.candidate().receipt());
        Stage1Receipt.CaseRow row = receipt.cases().stream().filter(value -> caseId.equals(value.id()))
                .findFirst().orElseThrow();
        Path directory = temp.resolve("candidate-short-rechecks");
        List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(caseId, NOTE,
                    receipt.arm(), row.manifestRowSha256(), index,
                    recovered ? CaseArmOutcome.State.COMPLETE : CaseArmOutcome.State.FAILED,
                    recovered ? null : SHORT_DELIVERY, recovered ? successfulContent : null));
            Path output = directory.resolve(caseId).resolve(RECHECK_PREFIX + index).resolve(DELIVERED_OUTPUT_FILE);
            Files.createDirectories(output.getParent());
            Files.writeString(output, line + "\n");
            repeats.add(new RecheckReceipt.Repeat(index, recovered ? null : SHORT_DELIVERY,
                    RouteVerdictCalculator.Evidence.SCORED, null,
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)), FreezeRecord.sha256(output)));
        }
        Path recheckReceipt = directory.resolve(RECEIPT_FILE);
        new RecheckReceipt(receipt.head(), freezeHash, receipt.arm(), receipt.model(),
                Set.of(SCHEMA_OR_PARSE, SHORT_DELIVERY),
                List.of(new RecheckReceipt.Case(caseId, SHORT_DELIVERY, repeats)),
                receipt.caseSetSha256()).write(recheckReceipt);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(original.candidate().receipt(),
                original.candidate().delivered(), recheckReceipt, directory);
        return new ResultsPipeline.FrbRunInput(original.repoRoot(), original.freeze(), original.parameters(),
                original.mapping(), original.manifests(), original.baselineMini(), original.baseline41(),
                candidate, original.reserves(), original.compatibilityReports(), original.headCommitTime(),
                original.ownerDispositions(), original.report(), original.synthetic());
    }

    private static RouteVerdictCalculator.WorkflowReport note(ResultsPipeline.Result result) {
        return result.routes().get(ROUTE).workflows().get(NOTE);
    }

    @Test void flattenedShortPairReportsRoleAndNeverKeepsMissing() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        assertThat(fixture.frb().bundle().evidence().excludedReasons())
                .containsEntry(CASE, MISSING_ARM_OUTPUT);
        failArm(fixture.run().candidate(), true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        RouteVerdictCalculator.CaseRecord record = result.caseRecords().get(ROUTE).stream()
                .filter(value -> CASE.equals(value.id())).findFirst().orElseThrow();
        assertThat(record.baseline().unresolvedCause()).isEqualTo(CANDIDATE_CAUSE);
        assertThat(record.candidate().unresolvedCause()).isEqualTo(CANDIDATE_CAUSE);
        assertThat(record.baseline().criticalQuestions()).isZero();
        assertThat(record.candidate().criticalQuestions()).isZero();
        assertThat(record.baseline().unresolvedQuestions()).isEqualTo(WIDTH);
        assertThat(record.candidate().unresolvedQuestions()).isEqualTo(WIDTH);
        assertThat(note(result).unresolvedCaseIdsByCause()).containsKey(CANDIDATE_CAUSE)
                .doesNotContainKey(MISSING_CAUSE);
        assertThat(note(result).unresolvedCaseIdsByCause().get(CANDIDATE_CAUSE)).containsExactly(CASE);
        assertThat(note(result).causes()).contains(CANDIDATE_CAUSE).doesNotContain(MISSING_CAUSE);
        JsonNode report = JSON.readTree(result.report().toFile());
        assertThat(report.path(EXCLUDED_EVIDENCE_FIELD).path(CASE).asText()).isEqualTo(CANDIDATE_CAUSE);
        JsonNode delivery = report.path(SHORT_DELIVERY_FIELD).get(0);
        assertThat(delivery.path(FAILURE_CLASS_FIELD).asText()).isEqualTo(SHORT_DELIVERY);
        assertThat(delivery.path("armRole").asText()).isEqualTo(CANDIDATE_ROLE);
        assertThat(delivery.path("requestedQuestionCount").asInt()).isEqualTo(WIDTH);
        assertThat(delivery.path(DELIVERED_COUNT_FIELD).asInt()).isEqualTo(1);
        assertThat(delivery.path(TREATMENT_FIELD).asText()).isEqualTo("UNRECHECKED");
        assertThat(delivery.path("productionOutcome").asText()).isEqualTo("ACCEPTED_PARTIAL_DELIVERY");
        assertThat(CauseTable.kind(CANDIDATE_CAUSE)).isEqualTo(CauseTable.Kind.RETURN_TO_OWNER);
        FrbClassificationInput classification = new FrbClassificationInput(Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(CASE), false, false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"),
                Map.of("safety", "0:head"));
        assertThat(DeadlineRule.classifyFrb(result.routes().get(ROUTE), classification).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }

    @Test void olderShortReceiptReportsUnavailableAndMissingEvidenceStaysMissing() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.Result missing = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(note(missing).unresolvedCaseIdsByCause()).containsKey(MISSING_CAUSE);
        assertThat(JSON.readTree(missing.report().toFile()).has(SHORT_DELIVERY_FIELD)).isFalse();
        failArm(fixture.run().baselineMini(), false);
        ResultsPipeline.Result shortResult = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(note(shortResult).unresolvedCaseIdsByCause()).containsKey(BASELINE_CAUSE)
                .doesNotContainKey(MISSING_CAUSE);
        assertThat(JSON.readTree(shortResult.report().toFile()).path(SHORT_DELIVERY_FIELD).get(0)
                .path(DELIVERED_COUNT_FIELD).asText()).isEqualTo("UNAVAILABLE");
    }

    @Test void bothShortArmsKeepTheirOwnRolesWhenPairIsFlattened() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        failArm(fixture.run().baselineMini(), true);
        failArm(fixture.run().candidate(), true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(note(result).unresolvedCaseIdsByCause().get(BASELINE_CAUSE)).contains(CASE);
        assertThat(note(result).unresolvedCaseIdsByCause().get(CANDIDATE_CAUSE)).contains(CASE);
        assertThat(note(result).unresolvedCaseIdsByCause()).doesNotContainKey(MISSING_CAUSE);
        assertThat(JSON.readTree(result.report().toFile()).path(SHORT_DELIVERY_FIELD)).hasSize(2);
        assertThat(JSON.readTree(result.report().toFile()).path(EXCLUDED_EVIDENCE_FIELD)
                .path(CASE).asText()).contains(BASELINE_CAUSE, CANDIDATE_CAUSE);
    }

    @Test void recheckedShortIsReportedWithoutAutomaticShortCause() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.FrbRunInput run = withRecheckedShortCandidate(fixture);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, fixture.frb());
        RouteVerdictCalculator.CaseRecord record = result.caseRecords().get(ROUTE).stream()
                .filter(value -> CASE.equals(value.id())).findFirst().orElseThrow();
        assertThat(record.candidate().unresolvedCause()).isNull();
        assertThat(note(result).causes()).doesNotContain(CANDIDATE_CAUSE);
        JsonNode delivery = JSON.readTree(result.report().toFile()).path(SHORT_DELIVERY_FIELD).get(0);
        assertThat(delivery.path(TREATMENT_FIELD).asText()).isEqualTo(RECHECKED);
        assertThat(delivery.path(RECOVERY_OUTCOME_FIELD).asText()).isEqualTo(REPRODUCED);
        assertThat(delivery.path(DELIVERED_COUNT_FIELD).asInt()).isEqualTo(1);
    }

    @Test void recoveredShortReportMatchesTheCalculatorT3Component() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(false);
        ResultsPipeline.FrbRunInput run = withRecheckedShortCandidate(fixture, FIRST_CASE, true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, fixture.frb());
        assertThat(note(result).candidateComponents()).containsEntry(ISOLATED_FINAL_FAILURE, 1);
        assertThat(note(result).causes()).isNullOrEmpty();
        JsonNode delivery = JSON.readTree(result.report().toFile()).path(SHORT_DELIVERY_FIELD).get(0);
        assertThat(delivery.path(TREATMENT_FIELD).asText()).isEqualTo(RECHECKED);
        assertThat(delivery.path(RECOVERY_OUTCOME_FIELD).asText()).isEqualTo(RECOVERED);
        assertThat(delivery.path(DELIVERED_COUNT_FIELD).asInt()).isEqualTo(1);
    }

    @Test void mixedRecheckedOtherArmDoesNotEraseUnrecheckedShortCause() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.FrbRunInput run = withBaselineRechecks(fixture);
        failArm(run.candidate(), true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, fixture.frb());
        Map<String, java.util.SortedSet<String>> causes = note(result).unresolvedCaseIdsByCause();
        assertThat(causes.get(CANDIDATE_CAUSE)).contains(CASE);
        assertThat(causes).doesNotContainKeys(MISSING_CAUSE, INCOMPLETE_RECHECK);
        assertThat(note(result).causes()).contains(CANDIDATE_CAUSE).doesNotContain(MISSING_CAUSE);
    }

    @Test void providerRejectionRebuildKeepsShortCauseWhenBaselineOriginalFailed() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.FrbRunInput run = withBaselineRechecks(fixture);
        failArm(run.candidate(), true);
        ObjectNode receipt = (ObjectNode) JSON.readTree(run.candidate().receipt().toFile());
        for (JsonNode entry : receipt.path(CASES_FIELD)) if (CASE.equals(entry.path("id").asText()))
            ((ObjectNode) entry.path("llmCalls").get(0)).put("httpStatus", 400);
        JSON.writeValue(run.candidate().receipt().toFile(), receipt);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, fixture.frb());
        RouteVerdictCalculator.CaseRecord record = result.caseRecords().get(ROUTE).stream()
                .filter(value -> CASE.equals(value.id())).findFirst().orElseThrow();
        assertThat(record.candidate().unresolvedCause()).isEqualTo(CANDIDATE_CAUSE);
        assertThat(record.candidate().providerRejected()).isFalse();
        assertThat(note(result).unresolvedCaseIdsByCause()).containsKey(CANDIDATE_CAUSE)
                .doesNotContainKey(INCOMPLETE_RECHECK);
    }

    private static RouteVerdictCalculator.ArmResult result(String failure,
            List<RouteVerdictCalculator.Recheck> rechecks, String cause, boolean rejected) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, failure, rechecks, rejected, false, 0, WIDTH, cause);
    }

    private static RouteVerdictCalculator.Recheck repeat(String failure) {
        return new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, failure, 0, 0);
    }

    private static RouteVerdictCalculator.ArmResult scored() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 0, 0);
    }

    private static RouteVerdictCalculator.RouteReport calculate(FrbPipelineTest.Fixture fixture,
            ResultsPipeline.Result original, RouteVerdictCalculator.ArmResult baseline,
            RouteVerdictCalculator.ArmResult candidate) {
        List<RouteVerdictCalculator.CaseRecord> records = new ArrayList<>();
        for (RouteVerdictCalculator.CaseRecord old : original.caseRecords().get(ROUTE))
            records.add(CASE.equals(old.id()) ? new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(),
                    old.route(), old.subgroup(), old.baselineCaseId(), old.candidateCaseId(),
                    baseline, candidate, WIDTH) : old);
        return RouteVerdictCalculator.calculate(FrozenGateParameters.load(fixture.run().parameters()), ROUTE,
                Map.of(NOTE, Set.of(FIRST_CASE, "a-two", CASE), STUDY_TIP, Set.of("tip")), records,
                Map.of(), Map.of(NOTE, true, STUDY_TIP, true), List.of(),
                fixture.frb().bundle().reserveDrawnByWorkflow(),
                fixture.frb().bundle().requestedQuestionsByCaseId());
    }

    @Test void recheckLabelsMatchCalculatorT1T3AndUnresolvedEvidence() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.Result original = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        RouteVerdictCalculator.ArmResult reproduced = result(SHORT_DELIVERY,
                List.of(repeat(SHORT_DELIVERY), repeat(SHORT_DELIVERY)), null, false);
        RouteVerdictCalculator.RouteReport t1 = calculate(fixture, original, scored(), reproduced);
        assertThat(RouteVerdictCalculator.recheckTreatment(reproduced, 2, true, true)).isEqualTo(REPRODUCED);
        assertThat(t1.workflows().get(NOTE).candidateOnlyT1()).isEqualTo(1);
        assertThat(t1.workflows().get(NOTE).causes()).contains("CONFIRMED_T1")
                .doesNotContain(CANDIDATE_CAUSE);
        RouteVerdictCalculator.ArmResult recovered = result(SHORT_DELIVERY,
                List.of(repeat(null), repeat(null)), null, false);
        RouteVerdictCalculator.RouteReport t3 = calculate(fixture, original, scored(), recovered);
        assertThat(RouteVerdictCalculator.recheckTreatment(recovered, 2, true, true)).isEqualTo(RECOVERED);
        assertThat(t3.workflows().get(NOTE).candidateComponents()).containsEntry(ISOLATED_FINAL_FAILURE, 1);
        assertThat(t3.workflows().get(NOTE).causes()).isNullOrEmpty();
        RouteVerdictCalculator.ArmResult unresolved = result(SHORT_DELIVERY, List.of(repeat(null)), null, false);
        RouteVerdictCalculator.RouteReport unknown = calculate(fixture, original, scored(), unresolved);
        assertThat(RouteVerdictCalculator.recheckTreatment(unresolved, 2, true, true)).isEqualTo("UNRESOLVED");
        assertThat(unknown.workflows().get(NOTE).unresolvedCaseIdsByCause())
                .containsKey(INCOMPLETE_RECHECK);
    }

    @Test void mixedArmDoesNotFallBackToIncompleteRecheckAndProviderCausePrecedesShort() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture(true);
        ResultsPipeline.Result original = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        RouteVerdictCalculator.ArmResult shortBaseline = result(SHORT_DELIVERY, List.of(), BASELINE_CAUSE, false);
        RouteVerdictCalculator.ArmResult recoveredOther = result(SCHEMA_OR_PARSE,
                List.of(repeat(null), repeat(null)), null, false);
        RouteVerdictCalculator.RouteReport mixed = calculate(fixture, original, shortBaseline, recoveredOther);
        assertThat(mixed.workflows().get(NOTE).unresolvedCaseIdsByCause()).containsKey(BASELINE_CAUSE)
                .doesNotContainKeys(MISSING_CAUSE, INCOMPLETE_RECHECK);
        RouteVerdictCalculator.ArmResult rejected = result(SHORT_DELIVERY, List.of(),
                CANDIDATE_CAUSE, true);
        RouteVerdictCalculator.RouteReport provider = calculate(fixture, original, scored(), rejected);
        assertThat(provider.workflows().get(NOTE).unresolvedCaseIdsByCause())
                .containsKey("RECHECK_UNRESOLVED").doesNotContainKey(CANDIDATE_CAUSE);
        RouteVerdictCalculator.RouteReport absentArm = calculate(fixture, original, null, scored());
        assertThat(absentArm.workflows().get(NOTE).unresolvedCaseIdsByCause()).containsKey(MISSING_CAUSE);
    }
}
