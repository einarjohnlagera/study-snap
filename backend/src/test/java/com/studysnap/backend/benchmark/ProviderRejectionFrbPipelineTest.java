package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRejectionFrbPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String CASE = "a-one";
    private static final String ARM = "LUNA";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String FAILURE = "HTTP_ERROR";
    private static final String TOKEN = RecheckPlan.PROVIDER_REJECTION;
    private static final String R12_REASON = "REJECTION_REPRODUCED_THEN_RETRIED";
    private static final String ERROR_CODE = "C";
    private static final String ERROR_TYPE = "T";
    private enum RepeatKind { SAME, OTHER_FAILURE, SUCCESS, RETRIED_SUCCESS }
    private record Prepared(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
                            Path recheckRoot) {}
    @TempDir Path temp;
    private int fixtureIndex;

    private FrbPipelineTest.Fixture fixture() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        Field directory = FrbPipelineTest.class.getDeclaredField("temp");
        directory.setAccessible(true);
        directory.set(builder, temp.resolve("fixture-" + ++fixtureIndex));
        return builder.fixture(false, 0);
    }

    private static void freezeToken(ResultsPipeline.FrbRunInput run) throws Exception {
        FreezeRecord old = FreezeRecord.load(run.freeze());
        FreezeRecord changed = new FreezeRecord(old.gitCommit(), old.preregistrationSha256(),
                old.parametersSha256(), old.manifests(), old.prompts(), old.arms(),
                old.baselineModelsByRoute(), old.ownerApprovalDate(), Set.of(TOKEN),
                old.evidenceMappingSha256(), old.compatibilitySuites(), old.costForecastUsdPerDeliveredCase());
        JSON.writeValue(run.freeze().toFile(), changed);
        Files.writeString(run.freeze().resolveSibling(run.freeze().getFileName() + ".sha256"),
                FreezeRecord.sha256(run.freeze()));
        String hash = FreezeRecord.sha256(run.freeze());
        for (ResultsPipeline.ArmFiles arm : List.of(run.baselineMini(), run.baseline41(), run.candidate())) {
            ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            receipt.put("freezeSha256", hash);
            JSON.writeValue(arm.receipt().toFile(), receipt);
        }
    }

    private static JsonNode failOriginal(ResultsPipeline.ArmFiles files) throws Exception {
        ObjectNode receipt = (ObjectNode) JSON.readTree(files.receipt().toFile());
        ObjectNode row = null;
        for (JsonNode candidate : receipt.path("cases"))
            if (CASE.equals(candidate.path("id").asText())) row = (ObjectNode) candidate;
        if (row == null) throw new ResultsPipeline.InvalidInput("Synthetic rejection case missing");
        String hash = row.path("manifestRowSha256").asText();
        row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                .put("failureClass", FAILURE);
        ObjectNode signal = (ObjectNode) row.path("llmCalls").get(0);
        signal.put("outcome", FAILURE).put("httpStatus", 400);
        signal.put("errorCode", ERROR_CODE).put("errorType", ERROR_TYPE)
                .put("modelRemovedDigest", row.path("attempt1BodyHashes").get(0).asText());
        List<String> lines = new ArrayList<>(Files.readAllLines(files.delivered()));
        JsonNode successful = null;
        for (int index = 0; index < lines.size(); index++) {
            JsonNode line = JSON.readTree(lines.get(index));
            if (!CASE.equals(line.path("caseId").asText())) continue;
            successful = line.path("content");
            String failed = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE, WORKFLOW,
                    receipt.path("arm").asText(), hash, 0, CaseArmOutcome.State.FAILED, FAILURE, null));
            lines.set(index, failed);
            row.put("deliveredOutputSha256", FreezeRecord.sha256(failed.getBytes(StandardCharsets.UTF_8)));
            break;
        }
        Files.write(files.delivered(), lines);
        receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(files.delivered()));
        JSON.writeValue(files.receipt().toFile(), receipt);
        return successful;
    }

    private Prepared prepared(RepeatKind first, RepeatKind second) throws Exception {
        FrbPipelineTest.Fixture fixture = fixture();
        ResultsPipeline.FrbRunInput original = fixture.run();
        freezeToken(original);
        JsonNode questions = failOriginal(original.candidate());
        Stage1Receipt receipt = Stage1Receipt.read(original.candidate().receipt());
        String hash = receipt.cases().stream().filter(row -> CASE.equals(row.id())).findFirst()
                .orElseThrow().manifestRowSha256();
        Path root = original.candidate().receipt().getParent().resolve("rechecks");
        List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            RepeatKind kind = index == 1 ? first : second;
            String failure = kind == RepeatKind.SAME ? FAILURE
                    : kind == RepeatKind.OTHER_FAILURE ? "SCHEMA_OR_PARSE" : null;
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE, WORKFLOW, ARM,
                    hash, index, failure == null ? CaseArmOutcome.State.COMPLETE : CaseArmOutcome.State.FAILED,
                    failure, failure == null ? questions : null));
            Path output = root.resolve(CASE).resolve("recheck-" + index).resolve("delivered-outputs.jsonl");
            Files.createDirectories(output.getParent());
            Files.writeString(output, line + "\n");
            String lineHash = FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8));
            String fileHash = FreezeRecord.sha256(output);
            repeats.add(kind == RepeatKind.SAME
                    ? new RecheckReceipt.Repeat(index, failure, RouteVerdictCalculator.Evidence.SCORED,
                            null, lineHash, fileHash, true,
                            List.of(new RecheckReceipt.RejectedCall(1, 400, ERROR_CODE, ERROR_TYPE,
                                    receipt.cases().stream().filter(row -> CASE.equals(row.id()))
                                            .findFirst().orElseThrow().attempt1BodyHashes().getFirst())))
                    : new RecheckReceipt.Repeat(index, failure, RouteVerdictCalculator.Evidence.SCORED,
                            null, lineHash, fileHash, kind == RepeatKind.RETRIED_SUCCESS));
        }
        Path checkFile = root.resolve("recheck-receipt.json");
        new RecheckReceipt(receipt.head(), receipt.freezeSha256(), ARM, receipt.model(), Set.of(TOKEN),
                List.of(new RecheckReceipt.Case(CASE, FAILURE, repeats)), receipt.caseSetSha256())
                .write(checkFile);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(original.candidate().receipt(),
                original.candidate().delivered(), checkFile, root);
        ResultsPipeline.FrbRunInput run = new ResultsPipeline.FrbRunInput(original.repoRoot(),
                original.freeze(), original.parameters(), original.mapping(), original.manifests(),
                original.baselineMini(), original.baseline41(), candidate, original.reserves(),
                original.compatibilityReports(), original.headCommitTime(), original.ownerDispositions(),
                original.report(), original.synthetic());
        return new Prepared(run, fixture.frb(), root);
    }

    private static RouteVerdictCalculator.CaseRecord caseRecord(ResultsPipeline.Result result) {
        return result.caseRecords().get(ROUTE).stream().filter(row -> CASE.equals(row.id())).findFirst()
                .orElseThrow();
    }

    private static Object armEvidence(Prepared prepared, Stage1Receipt.CaseRow row) throws Exception {
        Class<?> evidenceType = Class.forName(ResultsPipeline.class.getName() + "$ArmEvidence");
        var evidenceConstructor = evidenceType.getDeclaredConstructor(Stage1Receipt.class, Map.class,
                Map.class, RecheckReceipt.class, Path.class);
        evidenceConstructor.setAccessible(true);
        return evidenceConstructor.newInstance(Stage1Receipt.read(prepared.run().candidate().receipt()),
                Map.of(CASE, row), Map.of(), RecheckReceipt.read(prepared.run().candidate().recheckReceipt()),
                prepared.recheckRoot());
    }

    private static boolean usable(Prepared prepared, Stage1Receipt.CaseRow row) throws Exception {
        Class<?> frozenType = Class.forName(ResultsPipeline.class.getName() + "$FrozenCase");
        var frozenConstructor = frozenType.getDeclaredConstructor(String.class, String.class, int.class, String.class);
        frozenConstructor.setAccessible(true);
        Object frozen = frozenConstructor.newInstance(WORKFLOW, null, 1, row.manifestRowSha256());
        Class<?> evidenceType = Class.forName(ResultsPipeline.class.getName() + "$ArmEvidence");
        Object evidence = armEvidence(prepared, row);
        Method method = ResultsPipeline.class.getDeclaredMethod("usableOutput", String.class,
                frozenType, evidenceType);
        method.setAccessible(true);
        return (boolean) method.invoke(null, CASE, frozen, evidence);
    }

    @Test void repeatedRejectionsConfirmT1AndFailedFirstRepeatDoesNotBreakPacketLoad() throws Exception {
        Prepared prepared = prepared(RepeatKind.SAME, RepeatKind.SAME);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        assertThat(result.routes().get(ROUTE).failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(caseRecord(result).candidate().rechecks())
                .extracting(RouteVerdictCalculator.Recheck::providerRejected).containsExactly(true, true);
        JsonNode flags = JSON.readTree(result.report().toFile()).path("frb").path("rejectionRechecks")
                .path(CASE).path(ARM).path("repeats");
        assertThat(flags.get(0).path("providerRejected").asBoolean()).isTrue();
        assertThat(flags.get(1).path("providerRejected").asBoolean()).isTrue();
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(prepared.run().candidate().receipt()),
                prepared.run().candidate().delivered(), RecheckReceipt.read(prepared.run().candidate().recheckReceipt()),
                prepared.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).doesNotContain(CASE);
    }

    @Test void twoCleanSuccessesAreTransientAndFailedFirstRepeatYieldsSecondOutput() throws Exception {
        Prepared success = prepared(RepeatKind.SUCCESS, RepeatKind.SUCCESS);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(success.run(), success.frb());
        assertThat(result.routes().get(ROUTE).failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(result.routes().get(ROUTE).workflows().get(WORKFLOW).recheckOutcomesByCaseId())
                .containsEntry(CASE, "candidate:SUCCESS+SUCCESS");
        Prepared mixed = prepared(RepeatKind.SAME, RepeatKind.SUCCESS);
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(mixed.run().candidate().receipt()),
                mixed.run().candidate().delivered(), RecheckReceipt.read(mixed.run().candidate().recheckReceipt()),
                mixed.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).contains(CASE);
    }

    @Test void packetLoadSkipsFailedScoredRepeatWithoutARejectionFlag() throws Exception {
        Prepared mixed = prepared(RepeatKind.OTHER_FAILURE, RepeatKind.SUCCESS);
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(mixed.run().candidate().receipt()),
                mixed.run().candidate().delivered(), RecheckReceipt.read(mixed.run().candidate().recheckReceipt()),
                mixed.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).contains(CASE);
        Prepared failed = prepared(RepeatKind.OTHER_FAILURE, RepeatKind.OTHER_FAILURE);
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(failed.run().candidate().receipt()),
                failed.run().candidate().delivered(), RecheckReceipt.read(failed.run().candidate().recheckReceipt()),
                failed.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).doesNotContain(CASE);
    }

    @Test void mixedPairIsInconclusive() throws Exception {
        Prepared prepared = prepared(RepeatKind.SAME, RepeatKind.SUCCESS);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(result.routes().get(ROUTE).workflows().get(WORKFLOW).causes())
                .contains("RECHECK_MIXED");
    }

    @Test void reproducedThenRetriedIsUnresolvedAndNeverSuppliesScorableOutput() throws Exception {
        Prepared prepared = prepared(RepeatKind.RETRIED_SUCCESS, RepeatKind.RETRIED_SUCCESS);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(result.routes().get(ROUTE).workflows().get(WORKFLOW).causes())
                .contains("RECHECK_UNRESOLVED");
        assertThat(caseRecord(result).candidate().rechecks())
                .extracting(RouteVerdictCalculator.Recheck::evidence)
                .containsExactly(RouteVerdictCalculator.Evidence.MISSING, RouteVerdictCalculator.Evidence.MISSING);
        assertThat(JSON.readTree(result.report().toFile()).path("frb").path("rejectionRechecks")
                .path(CASE).path(ARM).path("reason").asText()).isEqualTo(R12_REASON);
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(prepared.run().candidate().receipt()),
                prepared.run().candidate().delivered(), RecheckReceipt.read(prepared.run().candidate().recheckReceipt()),
                prepared.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).doesNotContain(CASE);
    }

    @Test void calculatorAloneFailsClosedOnUnevaluatedReproducedThenRetried() throws Exception {
        Method method = RouteVerdictCalculator.class.getDeclaredMethod("rejectionOutcome",
                RouteVerdictCalculator.Recheck.class);
        method.setAccessible(true);
        RouteVerdictCalculator.Recheck repeated = new RouteVerdictCalculator.Recheck(
                RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, null, 0, 0, true);
        assertThat(method.invoke(null, repeated).toString()).isEqualTo("OTHER");
    }

    @Test void usableOutputExcludesR12OnlyForRejectionOriginals() throws Exception {
        Prepared retried = prepared(RepeatKind.RETRIED_SUCCESS, RepeatKind.RETRIED_SUCCESS);
        Stage1Receipt.CaseRow rejection = Stage1Receipt.read(retried.run().candidate().receipt()).cases()
                .stream().filter(row -> CASE.equals(row.id())).findFirst().orElseThrow();
        assertThat(usable(retried, rejection)).isFalse();
        Stage1Receipt.CaseRow parse = new Stage1Receipt.CaseRow(rejection.id(), rejection.workflow(),
                rejection.manifestRowSha256(), rejection.state(), false, false, "SCHEMA_OR_PARSE",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new Stage1Receipt.Signal("SCHEMA_OR_PARSE", 1, 1, 200)),
                rejection.attempt1BodyHashes(), rejection.deliveredOutputSha256());
        assertThat(usable(retried, parse)).isTrue();
        Prepared clean = prepared(RepeatKind.SUCCESS, RepeatKind.SUCCESS);
        Stage1Receipt.CaseRow cleanOriginal = Stage1Receipt.read(clean.run().candidate().receipt()).cases()
                .stream().filter(row -> CASE.equals(row.id())).findFirst().orElseThrow();
        assertThat(usable(clean, cleanOriginal)).isTrue();
    }

    @Test void eligibleFailureDetectionRequiresTokenForRejectedOriginal() throws Exception {
        Prepared prepared = prepared(RepeatKind.SAME, RepeatKind.SAME);
        Stage1Receipt.CaseRow row = Stage1Receipt.read(prepared.run().candidate().receipt()).cases()
                .stream().filter(candidate -> CASE.equals(candidate.id())).findFirst().orElseThrow();
        Object evidence = armEvidence(prepared, row);
        Class<?> evidenceType = Class.forName(ResultsPipeline.class.getName() + "$ArmEvidence");
        Method method = ResultsPipeline.class.getDeclaredMethod("hasEligibleFailureWithRechecks",
                String.class, evidenceType, FreezeRecord.class);
        method.setAccessible(true);
        FreezeRecord token = FreezeRecord.load(prepared.run().freeze());
        FreezeRecord legacy = new FreezeRecord(token.gitCommit(), token.preregistrationSha256(),
                token.parametersSha256(), token.manifests(), token.prompts(), token.arms(),
                token.baselineModelsByRoute(), token.ownerApprovalDate(), Set.of(),
                token.evidenceMappingSha256(), token.compatibilitySuites(),
                token.costForecastUsdPerDeliveredCase());
        assertThat(method.invoke(null, CASE, evidence, token)).isEqualTo(true);
        assertThat(method.invoke(null, CASE, evidence, legacy)).isEqualTo(false);
    }

    @Test void nonRejectionOriginalKeepsSuccessfulRepeatEvenIfItContainsARejection() throws Exception {
        Prepared prepared = prepared(RepeatKind.RETRIED_SUCCESS, RepeatKind.RETRIED_SUCCESS);
        ResultsPipeline.FrbRunInput run = prepared.run();
        FreezeRecord old = FreezeRecord.load(run.freeze());
        FreezeRecord changed = new FreezeRecord(old.gitCommit(), old.preregistrationSha256(),
                old.parametersSha256(), old.manifests(), old.prompts(), old.arms(),
                old.baselineModelsByRoute(), old.ownerApprovalDate(), Set.of(TOKEN, "SCHEMA_OR_PARSE"),
                old.evidenceMappingSha256(), old.compatibilitySuites(), old.costForecastUsdPerDeliveredCase());
        JSON.writeValue(run.freeze().toFile(), changed);
        Files.writeString(run.freeze().resolveSibling(run.freeze().getFileName() + ".sha256"),
                FreezeRecord.sha256(run.freeze()));
        String freezeHash = FreezeRecord.sha256(run.freeze());
        for (ResultsPipeline.ArmFiles arm : List.of(run.baselineMini(), run.baseline41(), run.candidate())) {
            ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            receipt.put("freezeSha256", freezeHash);
            if (arm == run.candidate()) {
                ObjectNode row = (ObjectNode) receipt.path("cases").get(0);
                row.put("failureClass", "SCHEMA_OR_PARSE");
                ((ObjectNode) row.path("llmCalls").get(0)).put("httpStatus", 200);
                List<String> lines = new ArrayList<>(Files.readAllLines(arm.delivered()));
                String failed = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE, WORKFLOW, ARM,
                        row.path("manifestRowSha256").asText(), 0, CaseArmOutcome.State.FAILED,
                        "SCHEMA_OR_PARSE", null));
                lines.set(0, failed);
                Files.write(arm.delivered(), lines);
                row.put("deliveredOutputSha256", FreezeRecord.sha256(failed.getBytes(StandardCharsets.UTF_8)));
                receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(arm.delivered()));
            }
            JSON.writeValue(arm.receipt().toFile(), receipt);
        }
        ObjectNode rechecks = (ObjectNode) JSON.readTree(run.candidate().recheckReceipt().toFile());
        rechecks.put("freezeSha256", freezeHash);
        ((ObjectNode) rechecks.path("cases").get(0)).put("originalFailureClass", "SCHEMA_OR_PARSE");
        ((com.fasterxml.jackson.databind.node.ArrayNode) rechecks.path("recheckEligibleFailureClasses"))
                .add("SCHEMA_OR_PARSE");
        JSON.writeValue(run.candidate().recheckReceipt().toFile(), rechecks);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(run, prepared.frb());
        assertThat(caseRecord(result).candidate().rechecks())
                .extracting(RouteVerdictCalculator.Recheck::evidence)
                .containsExactly(RouteVerdictCalculator.Evidence.SCORED, RouteVerdictCalculator.Evidence.SCORED);
        assertThat(ClassABlindPacketExport.loadScored(Stage1Receipt.read(run.candidate().receipt()),
                run.candidate().delivered(), RecheckReceipt.read(run.candidate().recheckReceipt()),
                prepared.recheckRoot())).extracting(ClassABlindPacketExport.Output::caseId).contains(CASE);
    }

    @Test void legacyGateRefusesTokenBeforeAnyScoring() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture();
        freezeToken(fixture.run());
        ResultsPipeline.FrbRunInput run = fixture.run();
        Path placeholder = temp.resolve("unused");
        ResultsPipeline.Input legacy = new ResultsPipeline.Input(run.repoRoot(), run.freeze(),
                run.parameters(), run.mapping(), run.manifests(), run.baselineMini(), run.baseline41(),
                run.candidate(), new ScoreEvidence.FilesA(placeholder, placeholder, placeholder,
                    placeholder, placeholder), new ScoreEvidence.FilesB(placeholder, placeholder, Map.of()),
                Map.of(), run.compatibilityReports(), Instant.EPOCH, null,
                temp.resolve("legacy-report.json"), temp.resolve("legacy-trigger.json"), true);
        assertThatThrownBy(() -> ResultsPipeline.run(legacy))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining(TOKEN);
        assertThat(Files.exists(legacy.report())).isFalse();
    }

    @Test void baselineFailureClearsCandidateRejectionFlagAsExistingPipelineDoes() throws Exception {
        Prepared prepared = prepared(RepeatKind.SAME, RepeatKind.SAME);
        failOriginal(prepared.run().baselineMini());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(prepared.run(), prepared.frb());
        assertThat(caseRecord(result).candidate().providerRejected()).isFalse();
        assertThat(caseRecord(result).candidate().rechecks())
                .extracting(RouteVerdictCalculator.Recheck::providerRejected).containsExactly(true, true);
    }
}
