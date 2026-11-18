package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRejectionEvidencePipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String CASE = "a-one";
    private static final String ARM = "LUNA";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String DELIVERED_CAUSE = "REJECTION_REPRODUCED_WITH_DELIVERED_RECOVERY";
    private static final String MIXED_CAUSE = "RECHECK_MIXED";
    private static final String RETRIED_SUCCESS_KIND = "RETRIED_SUCCESS";
    private static final String SAME_KIND = "SAME";
    private static final String PARSE_FAILURE = "SCHEMA_OR_PARSE";
    private static final String REPEATS_FIELD = "repeats";
    private static final String CASES_FIELD = "cases";
    private static final String REJECTED_CALLS_FIELD = "rejectedCalls";
    private static final String STATUS_FIELD = "status";
    private static final String DIGEST_FIELD = "modelRemovedDigest";
    private static final String ERROR_CODE_FIELD = "errorCode";
    private static final String ERROR_TYPE_FIELD = "errorType";
    private static final String EQUIVALENCE_REASON_FIELD = "equivalenceReason";
    private static final String REASON_FIELD = "reason";
    private static final String FREEZE_SHA_FIELD = "freezeSha256";
    private static final String NOT_SHOWN = "NOT_SHOWN";
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    private static final String NOT_EQUIVALENT = "NOT_EQUIVALENT";
    private static final String EQUIVALENT = "EQUIVALENT";
    @TempDir Path temp;
    private int fixtureIndex;

    private record Prepared(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
                            Path recheckRoot) {}

    /** Reuses the established real-file fixture without extending the permitted existing-test edits. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Prepared prepared(String first, String second) throws Exception {
        ProviderRejectionFrbPipelineTest builder = new ProviderRejectionFrbPipelineTest();
        Field directory = ProviderRejectionFrbPipelineTest.class.getDeclaredField("temp");
        directory.setAccessible(true);
        directory.set(builder, temp.resolve("fixture-" + ++fixtureIndex));
        Class<?> kind = Class.forName(ProviderRejectionFrbPipelineTest.class.getName() + "$RepeatKind");
        Method method = ProviderRejectionFrbPipelineTest.class.getDeclaredMethod("prepared", kind, kind);
        method.setAccessible(true);
        Object fixture = method.invoke(builder, Enum.valueOf((Class<? extends Enum>) kind, first),
                Enum.valueOf((Class<? extends Enum>) kind, second));
        Class<?> type = fixture.getClass();
        Method run = type.getDeclaredMethod("run");
        Method frb = type.getDeclaredMethod("frb");
        Method root = type.getDeclaredMethod("recheckRoot");
        run.setAccessible(true);
        frb.setAccessible(true);
        root.setAccessible(true);
        return new Prepared((ResultsPipeline.FrbRunInput) run.invoke(fixture),
                (ResultsPipeline.FrbInput) frb.invoke(fixture), (Path) root.invoke(fixture));
    }

    private static void makeDeliveredEquivalent(Prepared prepared, int index) throws Exception {
        Path file = prepared.run().candidate().recheckReceipt();
        ObjectNode receipt = (ObjectNode) JSON.readTree(file.toFile());
        ObjectNode repeat = (ObjectNode) receipt.path(CASES_FIELD).get(0).path(REPEATS_FIELD).get(index);
        Stage1Receipt original = Stage1Receipt.read(prepared.run().candidate().receipt());
        String digest = original.cases().getFirst().llmCalls().getFirst().modelRemovedDigest();
        ArrayNode calls = repeat.putArray(REJECTED_CALLS_FIELD);
        calls.addObject().put("attempt", 1).put(STATUS_FIELD, 400).put(ERROR_CODE_FIELD, "C")
                .put(ERROR_TYPE_FIELD, "T").put(DIGEST_FIELD, digest);
        JSON.writeValue(file.toFile(), receipt);
    }

    private static RouteVerdictCalculator.CaseRecord caseRecord(ResultsPipeline.Result result) {
        return result.caseRecords().get(ROUTE).stream().filter(row -> CASE.equals(row.id())).findFirst().orElseThrow();
    }

    private static JsonNode report(Prepared prepared) throws Exception {
        return JSON.readTree(prepared.run().report().toFile()).path("frb")
                .path("rejectionRechecks").path(CASE).path(ARM);
    }

    @Test void twoEquivalentDeliveredRecoveriesReturnToOwnerAndStayUnscorable() throws Exception {
        Prepared prepared = prepared(RETRIED_SUCCESS_KIND, RETRIED_SUCCESS_KIND);
        makeDeliveredEquivalent(prepared, 0);
        makeDeliveredEquivalent(prepared, 1);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        var route = result.routes().get(ROUTE);
        assertThat(route.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(route.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(route.workflows().get(WORKFLOW).causes()).contains(DELIVERED_CAUSE);
        assertThat(route.workflows().get(WORKFLOW).candidateOnlyT3()).isZero();
        assertThat(caseRecord(result).candidate().rechecks()).allSatisfy(check -> {
            assertThat(check.evidence()).isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
            assertThat(check.deliveredAfterRejection()).isTrue();
            assertThat(check.rejectionEquivalence()).isEqualTo(EQUIVALENT);
            assertThat(check.critical()).isFalse();
            assertThat(check.t3Components()).isEmpty();
        });
        assertThat(report(prepared).path(REASON_FIELD).asText()).isEqualTo(DELIVERED_CAUSE);
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path("deliveredAfterRejection").asBoolean()).isTrue();
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path("equivalence").asText())
                .isEqualTo(EQUIVALENT);
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path(EQUIVALENCE_REASON_FIELD).asText())
                .isEqualTo("MATCH");
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path("statuses").get(0).asInt()).isEqualTo(400);
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path("errorCodes").get(0).asText()).isEqualTo("C");
        assertThat(report(prepared).path(REPEATS_FIELD).get(0).path("errorTypes").get(0).asText()).isEqualTo("T");
        FrozenGateParameters parameters = FrozenGateParameters.load(prepared.run().parameters());
        Map<String, String> expansion = new java.util.LinkedHashMap<>();
        for (String workflow : parameters.routes.get(ROUTE).workflows())
            expansion.put(workflow, "NOT_TRIGGERED");
        FrbClassificationInput classificationInput = new FrbClassificationInput(Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), false, false, Set.of(), expansion,
                Map.of("safety", "0:head"));
        var classification = DeadlineRule.classifyFrb(route, classificationInput);
        assertThat(classification.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(classification.causes()).contains(DELIVERED_CAUSE);
        assertThat(SolRule.justified(parameters, new SolRule.Comparison(route, route, 0,
                Map.of(), Map.of(), Set.of(), Set.of(), true, true, true))).isFalse();
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(prepared.run().candidate().receipt()),
                prepared.run().candidate().delivered(), RecheckReceipt.read(prepared.run().candidate().recheckReceipt()),
                prepared.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).doesNotContain(CASE);
    }

    @Test void sameAndDeliveredIsSpecificCauseButDeliveredAndSuccessIsMixed() throws Exception {
        Prepared same = prepared(SAME_KIND, RETRIED_SUCCESS_KIND);
        makeDeliveredEquivalent(same, 1);
        ResultsPipeline.Result sameResult = ResultsPipeline.runFrb(same.run(), same.frb());
        assertThat(sameResult.routes().get(ROUTE).workflows().get(WORKFLOW).causes())
                .contains(DELIVERED_CAUSE);
        assertThat(sameResult.routes().get(ROUTE).workflows().get(WORKFLOW).recheckOutcomesByCaseId())
                .containsEntry(CASE, "candidate:SAME_REJECTION+SAME_REJECTION_DELIVERED");
        Prepared mixed = prepared(RETRIED_SUCCESS_KIND, "SUCCESS");
        makeDeliveredEquivalent(mixed, 0);
        ResultsPipeline.Result mixedResult = ResultsPipeline.runFrb(mixed.run(), mixed.frb());
        assertThat(mixedResult.routes().get(ROUTE).workflows().get(WORKFLOW).causes()).contains(MIXED_CAUSE);
        assertThat(mixedResult.routes().get(ROUTE).workflows().get(WORKFLOW).recheckOutcomesByCaseId())
                .containsEntry(CASE, "candidate:SAME_REJECTION_DELIVERED+SUCCESS");
    }

    @Test void everyRejectedCallMismatchPreventsT1AndEquivalenceIsNeverNull() throws Exception {
        for (String field : List.of(STATUS_FIELD, ERROR_CODE_FIELD, ERROR_TYPE_FIELD, DIGEST_FIELD)) {
            Prepared prepared = prepared(SAME_KIND, SAME_KIND);
            Path file = prepared.run().candidate().recheckReceipt();
            ObjectNode receipt = (ObjectNode) JSON.readTree(file.toFile());
            for (JsonNode repeat : receipt.path(CASES_FIELD).get(0).path(REPEATS_FIELD)) {
                ObjectNode call = (ObjectNode) repeat.path(REJECTED_CALLS_FIELD).get(0);
                if (STATUS_FIELD.equals(field)) call.put(field, 422);
                else call.put(field, "different");
            }
            JSON.writeValue(file.toFile(), receipt);
            ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
            assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
            assertThat(result.routes().get(ROUTE).failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
            assertThat(caseRecord(result).candidate().rechecks()).allSatisfy(check ->
                    assertThat(check.rejectionEquivalence()).isEqualTo(NOT_EQUIVALENT));
            assertThat(report(prepared).path(REPEATS_FIELD).get(0).path(EQUIVALENCE_REASON_FIELD).asText())
                    .isEqualTo(DIGEST_FIELD.equals(field) ? "REQUEST_DIGEST_DIFFERS"
                            : "REJECTION_DETAILS_DIFFER");
        }
    }

    @Test void oldRejectedReceiptStaysUnresolvedWithOldReportReason() throws Exception {
        Prepared prepared = prepared(RETRIED_SUCCESS_KIND, RETRIED_SUCCESS_KIND);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(caseRecord(result).candidate().rechecks()).allSatisfy(check ->
                assertThat(check.rejectionEquivalence()).isEqualTo(NOT_SHOWN));
        assertThat(report(prepared).path(REASON_FIELD).asText()).isEqualTo("REJECTION_REPRODUCED_THEN_RETRIED");
    }

    @Test void oldFailedRejectionReceiptNeverConfirmsT1() throws Exception {
        Prepared prepared = prepared(SAME_KIND, SAME_KIND);
        Path file = prepared.run().candidate().recheckReceipt();
        ObjectNode receipt = (ObjectNode) JSON.readTree(file.toFile());
        for (JsonNode repeat : receipt.path(CASES_FIELD).get(0).path(REPEATS_FIELD))
            ((ObjectNode) repeat).remove(REJECTED_CALLS_FIELD);
        JSON.writeValue(file.toFile(), receipt);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(result.routes().get(ROUTE).failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(result.routes().get(ROUTE).workflows().get(WORKFLOW).causes())
                .contains("RECHECK_UNRESOLVED");
        assertThat(caseRecord(result).candidate().rechecks()).allSatisfy(check ->
                assertThat(check.rejectionEquivalence()).isEqualTo(NOT_SHOWN));
    }

    @Test void baselineFailureCannotMakeDeliveredRecoveryScoredOnGenericPath() throws Exception {
        Prepared prepared = prepared(RETRIED_SUCCESS_KIND, RETRIED_SUCCESS_KIND);
        makeDeliveredEquivalent(prepared, 0);
        makeDeliveredEquivalent(prepared, 1);
        Method fail = ProviderRejectionFrbPipelineTest.class.getDeclaredMethod("failOriginal",
                ResultsPipeline.ArmFiles.class);
        fail.setAccessible(true);
        fail.invoke(null, prepared.run().baselineMini());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        var candidate = caseRecord(result).candidate();
        assertThat(candidate.providerRejected()).isFalse();
        assertThat(candidate.evidence()).isNotEqualTo(RouteVerdictCalculator.Evidence.SCORED);
        assertThat(candidate.criticalQuestions()).isZero();
        assertThat(candidate.rechecks()).allSatisfy(check -> {
            assertThat(check.evidence()).isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
            assertThat(check.deliveredAfterRejection()).isTrue();
        });
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(result.routes().get(ROUTE).frb().candidateCriticalLowByCaseId().get(CASE)).isZero();
        assertThat(result.routes().get(ROUTE).frb().candidateCriticalHighByCaseId().get(CASE))
                .isEqualTo(caseRecord(result).requestedQuestions());
    }

    @Test void nonRejectionOriginalWithHttp400KeepsDeliveredRepeatSuccess() throws Exception {
        Prepared prepared = prepared(RETRIED_SUCCESS_KIND, RETRIED_SUCCESS_KIND);
        ResultsPipeline.FrbRunInput run = prepared.run();
        FreezeRecord old = FreezeRecord.load(run.freeze());
        FreezeRecord changed = new FreezeRecord(old.gitCommit(), old.preregistrationSha256(),
                old.parametersSha256(), old.manifests(), old.prompts(), old.arms(),
                old.baselineModelsByRoute(), old.ownerApprovalDate(),
                Set.of(RecheckPlan.PROVIDER_REJECTION, PARSE_FAILURE), old.evidenceMappingSha256(),
                old.compatibilitySuites(), old.costForecastUsdPerDeliveredCase());
        JSON.writeValue(run.freeze().toFile(), changed);
        Files.writeString(run.freeze().resolveSibling(run.freeze().getFileName() + ".sha256"),
                FreezeRecord.sha256(run.freeze()));
        String freezeHash = FreezeRecord.sha256(run.freeze());
        for (ResultsPipeline.ArmFiles arm : List.of(run.baselineMini(), run.baseline41(), run.candidate())) {
            ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            receipt.put(FREEZE_SHA_FIELD, freezeHash);
            if (arm == run.candidate()) {
                ObjectNode row = (ObjectNode) receipt.path(CASES_FIELD).get(0);
                row.put("failureClass", PARSE_FAILURE);
                List<String> lines = Files.readAllLines(arm.delivered());
                String failed = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE, WORKFLOW, ARM,
                        row.path("manifestRowSha256").asText(), 0, CaseArmOutcome.State.FAILED,
                        PARSE_FAILURE, null));
                lines.set(0, failed);
                Files.write(arm.delivered(), lines);
                row.put("deliveredOutputSha256", FreezeRecord.sha256(failed.getBytes(StandardCharsets.UTF_8)));
                receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(arm.delivered()));
            }
            JSON.writeValue(arm.receipt().toFile(), receipt);
        }
        ObjectNode rechecks = (ObjectNode) JSON.readTree(run.candidate().recheckReceipt().toFile());
        rechecks.put(FREEZE_SHA_FIELD, freezeHash);
        ((ObjectNode) rechecks.path(CASES_FIELD).get(0)).put("originalFailureClass", PARSE_FAILURE);
        ((ArrayNode) rechecks.path("recheckEligibleFailureClasses")).add(PARSE_FAILURE);
        JSON.writeValue(run.candidate().recheckReceipt().toFile(), rechecks);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, prepared.frb());
        assertThat(caseRecord(result).candidate().rechecks()).allSatisfy(check -> {
            assertThat(check.rejectionEquivalence()).isEqualTo(NOT_APPLICABLE);
            assertThat(check.deliveredAfterRejection()).isFalse();
            assertThat(check.evidence()).isEqualTo(RouteVerdictCalculator.Evidence.SCORED);
        });
        assertThat(result.routes().get(ROUTE).workflows().get(WORKFLOW).recheckOutcomesByCaseId())
                .containsEntry(CASE, "candidate:SUCCESS+SUCCESS");
    }
}
