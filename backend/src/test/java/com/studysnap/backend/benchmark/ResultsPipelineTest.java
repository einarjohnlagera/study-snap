package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Preregistration sections 1, 4, 7, 8 and 9 offline receipts-to-verdict-to-real-reserve-gate checks. */
class ResultsPipelineTest {
    private static final String INTERACTIVE_ROUTE = "INTERACTIVE_QUIZ";
    private static final String PAID_ROUTE = "PAID_INTERACTIVE";
    private static final String PARSE_FAILURE = "SCHEMA_OR_PARSE";
    private static final String RESERVE_NOTE = "reserve-note";
    private static final String CONSERVATIVE_USD_FIELD = "conservativeUsd";
    private static final String DELIVERED_OUTPUTS_FILE = "delivered-outputs.jsonl";
    private static final String ROUTES_FIELD = "routes";
    private static final String MARGIN_FIELD = "margin";
    private static final String WORKFLOWS_FIELD = "workflows";
    private static final String PRIMARY_SIZE_FIELD = "primarySize";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String QUESTION_FORMAT_FIELD = "questionFormat";
    private static final String FREEZE_HASH_FIELD = "freezeSha256";
    private static final String DELIVERED_FILE_HASH_FIELD = "deliveredOutputsFileSha256";
    private static final String DEADLINE_LABEL = "DEADLINE FALLBACK, NOT BENCHMARK PASS";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String INTERVIEW = "INTERVIEW_GENERATION";
    private static final String CASE_NOTE = "case-note";
    private static final String CASE_INTERVIEW = "case-interview";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String SOL_PAID = "SOL_PAID_INTERACTIVE";
    private static final String HEAD = "a".repeat(40);
    private static final String MARKER = "private-note-marker";
    private static final String ANSWER = "secret-answer-marker";
    private static final String PROMPT = "backend/src/main/resources/prompts/study-pack-v1/system.txt";
    private static final String PREREG = "docs/claude-plans/2026-11-12-v0182-llm-migration-preregistration.md";
    private static final String PARAMS = "backend/src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json";
    private static final String MAPPING = "backend/src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json";
    @TempDir Path temp;

    private record Fixture(ResultsPipeline.Input input, FreezeRecord freeze, Path score,
                           Path candidateReceipt, Path candidateOutput) {}

    private Fixture fixture(boolean paidQualityDeficit, boolean noteCritical, boolean excludeNote) throws Exception {
        Path root = temp.resolve("synthetic-repo");
        Path parameter = temp.resolve("parameters.json");
        ObjectNode gates = (ObjectNode) JSON.readTree(Files.readAllBytes(GateTestFixture.SOURCE));
        ((ObjectNode) gates.get(ROUTES_FIELD)).remove("CRITIQUE");
        ((ObjectNode) gates.get(ROUTES_FIELD)).remove(INTERACTIVE_ROUTE);
        ((ObjectNode) gates.get(ROUTES_FIELD)).remove(PAID_ROUTE);
        ObjectNode routes = (ObjectNode) gates.get(ROUTES_FIELD);
        routes.set(INTERACTIVE_ROUTE, JSON.valueToTree(Map.of(MARGIN_FIELD, 0, WORKFLOWS_FIELD, List.of(NOTE))));
        routes.set(PAID_ROUTE, JSON.valueToTree(Map.of(MARGIN_FIELD, 0, WORKFLOWS_FIELD, List.of(INTERVIEW))));
        ObjectNode workflows = (ObjectNode) gates.get(WORKFLOWS_FIELD);
        List<String> names = new ArrayList<>();
        workflows.fieldNames().forEachRemaining(names::add);
        for (String name : names) if (!Set.of(NOTE, INTERVIEW).contains(name)) workflows.remove(name);
        ((ObjectNode) workflows.get(NOTE)).put(PRIMARY_SIZE_FIELD, 1);
        ((ObjectNode) workflows.get(INTERVIEW)).put(PRIMARY_SIZE_FIELD, 1);
        gates.put("reserveSize", 1).put("t2FailCount", 1);
        byte[] gateBytes = JSON.writeValueAsBytes(gates);
        write(parameter, gateBytes);
        Files.writeString(temp.resolve("parameters.json.sha256"), FreezeRecord.sha256(gateBytes));
        write(root.resolve(PARAMS), gateBytes);
        Path mapping = temp.resolve("mapping.json");
        byte[] mappingBytes = Files.readAllBytes(Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json"));
        write(mapping, mappingBytes);
        Files.writeString(temp.resolve("mapping.json.sha256"), FreezeRecord.sha256(mappingBytes));
        write(root.resolve(MAPPING), mappingBytes);
        Path prereg = write(root.resolve(PREREG), "draft");
        Path prompt = write(root.resolve(PROMPT), "prompt");
        Path aPrimary = write(temp.resolve("a-primary.jsonl"), aManifest(CASE_NOTE, NOTE, false)
                + "\n" + aManifest(CASE_INTERVIEW, INTERVIEW, false) + "\n");
        Path aReserve = write(temp.resolve("a-reserve.jsonl"), aManifest(RESERVE_NOTE, NOTE, true)
                + "\n" + aManifest("reserve-interview", INTERVIEW, true) + "\n");
        Path bPrimary = write(temp.resolve("b-primary.jsonl"), "");
        Path bReserve = write(temp.resolve("b-reserve.jsonl"), "");
        Map<String, Path> manifests = Map.of("classA.primary", aPrimary, "classA.reserve", aReserve,
                "classB.primary", bPrimary, "classB.reserve", bReserve);
        Map<String, String> manifestHashes = new HashMap<>();
        manifests.values().forEach(file -> {
            try { manifestHashes.put(file.toAbsolutePath().toString(), FreezeRecord.sha256(file)); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
        });
        Map<String, String> suites = Map.of(
                "QuickReviewAdaptivePracticeServiceTest", NOTE,
                "QuickReviewStudyTipServiceTest", NOTE,
                "GeneratedQuizServiceTest", NOTE,
                "ChallengeQuizServiceTest", NOTE,
                "LongExamServiceTest", NOTE,
                "ExamQuestionPoolServiceTest", NOTE,
                "InterviewPracticeServiceTest", INTERVIEW,
                "NoteCollectionServiceTest", INTERVIEW,
                "OpenAiAskCompanionLlmServiceTest", INTERVIEW,
                "OpenAiLlmStudyPackServiceTest", INTERVIEW);
        FreezeRecord freeze = new FreezeRecord(HEAD, FreezeRecord.sha256(prereg), FreezeRecord.sha256(parameter),
                manifestHashes, Map.of(PROMPT, FreezeRecord.sha256(prompt)), Map.of(
                    MINI, new FreezeRecord.Arm("gpt-4.1-mini", List.of(NOTE)),
                    PAID, new FreezeRecord.Arm("gpt-4.1", List.of(INTERVIEW)),
                    LUNA, new FreezeRecord.Arm("gpt-6-luna", List.of(NOTE, INTERVIEW)),
                    SOL_PAID, new FreezeRecord.Arm("gpt-6.1-sol", List.of(INTERVIEW))),
                Map.of(INTERACTIVE_ROUTE, "gpt-4.1-mini", PAID_ROUTE, "gpt-4.1"),
                LocalDate.of(2026, 11, 14), Set.of(PARSE_FAILURE),
                FreezeRecord.sha256(mapping), suites, Map.of(NOTE, BigDecimal.ONE, INTERVIEW, BigDecimal.ONE));
        Path freezeFile = temp.resolve("freeze.json");
        byte[] freezeBytes = JSON.writeValueAsBytes(freeze);
        write(freezeFile, freezeBytes);
        Files.writeString(temp.resolve("freeze.json.sha256"), FreezeRecord.sha256(freezeBytes));
        String freezeHash = FreezeRecord.sha256(freezeBytes);
        String caseSetHash = caseSetHash(aPrimary);
        ResultsPipeline.ArmFiles mini = arm(MINI, "gpt-4.1-mini", List.of(CASE_NOTE),
                aPrimary, freezeHash, caseSetHash);
        ResultsPipeline.ArmFiles paid = arm(PAID, "gpt-4.1", List.of(CASE_INTERVIEW),
                aPrimary, freezeHash, caseSetHash);
        ResultsPipeline.ArmFiles luna = arm(LUNA, "gpt-6-luna", List.of(CASE_NOTE, CASE_INTERVIEW),
                aPrimary, freezeHash, caseSetHash);
        Path aKey = temp.resolve("class-a-key.jsonl");
        Path aScore = temp.resolve("class-a-primary.jsonl");
        List<String> keys = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        int index = 0;
        for (String id : List.of(CASE_NOTE, CASE_INTERVIEW)) {
            if (excludeNote && CASE_NOTE.equals(id)) continue;
            for (String arm : List.of(CASE_NOTE.equals(id) ? MINI : PAID, LUNA)) {
                String blind = "blind-" + ++index;
                keys.add(JSON.writeValueAsString(Map.of("id", blind, "arm", arm, CASE_ID_FIELD, id,
                        WORKFLOW_FIELD, CASE_NOTE.equals(id) ? NOTE : INTERVIEW, QUESTION_FORMAT_FIELD, "MCQ")));
                scores.add(JSON.writeValueAsString(score(blind, noteCritical && CASE_NOTE.equals(id)
                        && LUNA.equals(arm), paidQualityDeficit && CASE_INTERVIEW.equals(id) && LUNA.equals(arm))));
            }
        }
        write(aKey, String.join("\n", keys) + "\n");
        write(aScore, String.join("\n", scores) + "\n");
        Path aReviews = write(temp.resolve("class-a-reviews.jsonl"), "");
        Path aAdjudications = write(temp.resolve("class-a-adjudications.jsonl"), "");
        Path exclusions = write(temp.resolve("class-a-exclusions.jsonl"), excludeNote
                ? "{\"caseId\":\"" + CASE_NOTE + "\",\"reason\":\"SHORT_DELIVERY\"}\n" : "");
        Path bKey = write(temp.resolve("class-b-key.jsonl"), "");
        Path bScore = write(temp.resolve("class-b-scores.jsonl"), "");
        Path suitesDir = temp.resolve("surefire-reports");
        Files.createDirectories(suitesDir);
        for (String name : suites.keySet()) write(suitesDir.resolve("TEST-com.studysnap.backend.service." + name + ".xml"),
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        ResultsPipeline.Input input = new ResultsPipeline.Input(root, freezeFile, parameter, mapping, manifests,
                mini, paid, luna, new ScoreEvidence.FilesA(aKey, aScore, aReviews, aAdjudications, exclusions),
                new ScoreEvidence.FilesB(bKey, bScore, Map.of("HUMAN_A", ClassBScoreFiles.ScorerKind.HUMAN)),
                Map.of(), suitesDir, Instant.EPOCH, null, temp.resolve("decision-report.json"),
                temp.resolve("t2-trigger.json"), true);
        return new Fixture(input, freeze, aScore, luna.receipt(), luna.delivered());
    }

    private ResultsPipeline.ArmFiles arm(String arm, String model, List<String> ids, Path manifest,
            String freezeHash, String caseSetHash) throws Exception {
        Map<String, String> rowHashes = new HashMap<>();
        for (String line : Files.readAllLines(manifest)) {
            JsonNode row = JSON.readTree(line);
            rowHashes.put(row.path(CASE_ID_FIELD).asText(), FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        }
        Path output = temp.resolve(arm + "-delivered.jsonl");
        List<String> lines = new ArrayList<>();
        List<Stage1Receipt.CaseRow> rows = new ArrayList<>();
        for (String id : ids) {
            String workflow = CASE_NOTE.equals(id) ? NOTE : INTERVIEW;
            ArrayNode questions = JSON.createArrayNode();
            questions.addObject().put("question", MARKER).put("answer", ANSWER);
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(id, workflow, arm,
                    rowHashes.get(id), 0, CaseArmOutcome.State.COMPLETE, null, questions));
            lines.add(line);
            rows.add(new Stage1Receipt.CaseRow(id, workflow, rowHashes.get(id),
                    CaseArmOutcome.State.COMPLETE, true, true, null,
                    new BigDecimal("0.01"), new BigDecimal("0.01"),
                    List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, 200)), List.of("same-body"),
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))));
        }
        write(output, String.join("\n", lines) + "\n");
        Stage1Receipt receipt = new Stage1Receipt(HEAD, freezeHash, arm, model,
                StageCapLedger.Stage.STAGE_1, Instant.now(), Instant.now(), 1, false,
                caseSetHash, rows, Map.of(), FreezeRecord.sha256(output));
        Path receiptFile = temp.resolve(arm + "-receipt.json");
        receipt.write(receiptFile);
        return new ResultsPipeline.ArmFiles(receiptFile, output, null, null);
    }

    private ResultsPipeline.ArmFiles reserveArm(String arm, String model, Path manifest,
            String primaryCaseSet, String freezeHash) throws Exception {
        String line = Files.readAllLines(manifest).getFirst();
        String id = JSON.readTree(line).path(CASE_ID_FIELD).asText();
        String rowHash = FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8));
        ArrayNode questions = JSON.createArrayNode();
        questions.addObject().put("question", MARKER).put("answer", ANSWER);
        String outputLine = JSON.writeValueAsString(new DeliveredOutputs.Line(id, NOTE, arm, rowHash,
                0, CaseArmOutcome.State.COMPLETE, null, questions));
        Path output = write(temp.resolve(arm + "-reserve-output.jsonl"), outputLine + "\n");
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(id, NOTE, rowHash,
                CaseArmOutcome.State.COMPLETE, true, true, null, new BigDecimal("0.01"),
                new BigDecimal("0.01"), List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, 200)),
                List.of("same-body"), FreezeRecord.sha256(outputLine.getBytes(StandardCharsets.UTF_8)));
        Stage1Receipt receipt = new Stage1Receipt(HEAD, freezeHash, arm, model,
                StageCapLedger.Stage.STAGE_2, Instant.now(), Instant.now(), 1, false,
                primaryCaseSet, List.of(row), Map.of(), FreezeRecord.sha256(output));
        Path receiptFile = temp.resolve(arm + "-reserve-evidence.json");
        receipt.write(receiptFile);
        return new ResultsPipeline.ArmFiles(receiptFile, output, null, null);
    }

    private String aManifest(String id, String workflow, boolean reserve) throws Exception {
        var source = new ClassACaseManifest.Source("Cells", MARKER, "Membranes", List.of("membrane"),
                List.of(), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var row = new ClassACaseManifest.Case(id,
                NOTE.equals(workflow) ? ClassACaseManifest.Workflow.NOTE_ADAPTIVE
                        : ClassACaseManifest.Workflow.INTERVIEW_PRACTICE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                List.of(source), 1, null, reserve);
        return JSON.writeValueAsString(row);
    }

    private static ObjectNode score(String id, boolean critical, boolean quality) {
        ObjectNode row = JSON.createObjectNode();
        row.put("id", id).put("reviewerId", "HUMAN_A").put("round", "PRIMARY");
        row.put("factuallyCorrect", "YES").put("grounding", "GROUNDED");
        row.put("critical", critical);
        if (critical) row.put("criticalType", "WRONG_KEY"); else row.putNull("criticalType");
        row.put("distractorPlausibility", 5).put("modeAlignment", 5).put("cognitiveDemand", 5);
        row.put("clarity", quality ? 1 : 5).put("faithfulness", 5);
        row.put("seriousGiveaway", false).putNull("giveawayPattern");
        return row;
    }

    private static String caseSetHash(Path primary) throws Exception {
        StringBuilder signature = new StringBuilder();
        for (String line : Files.readAllLines(primary)) {
            String id = JSON.readTree(line).path(CASE_ID_FIELD).asText();
            signature.append(id).append(':').append(FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return FreezeRecord.sha256(signature.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return path;
    }
    private static Path write(Path path, byte[] bytes) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
        return path;
    }

    private void refreezeMapping(Fixture fixture, String amended) throws Exception {
        ResultsPipeline.Input input = fixture.input();
        byte[] bytes = amended.getBytes(StandardCharsets.UTF_8);
        write(input.mapping(), bytes);
        Files.writeString(input.mapping().resolveSibling(input.mapping().getFileName() + ".sha256"),
                FreezeRecord.sha256(bytes));
        write(input.repoRoot().resolve(MAPPING), bytes);
        FreezeRecord old = fixture.freeze();
        FreezeRecord changed = new FreezeRecord(old.gitCommit(), old.preregistrationSha256(),
                old.parametersSha256(), old.manifests(), old.prompts(), old.arms(),
                old.baselineModelsByRoute(), old.ownerApprovalDate(), old.recheckEligibleFailureClasses(),
                FreezeRecord.sha256(bytes), old.compatibilitySuites(), old.costForecastUsdPerDeliveredCase());
        byte[] freezeBytes = JSON.writeValueAsBytes(changed);
        write(input.freeze(), freezeBytes);
        Files.writeString(input.freeze().resolveSibling(input.freeze().getFileName() + ".sha256"),
                FreezeRecord.sha256(freezeBytes));
        for (ResultsPipeline.ArmFiles arm : List.of(input.baselineMini(), input.baseline41(), input.candidate())) {
            ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            receipt.put(FREEZE_HASH_FIELD, FreezeRecord.sha256(freezeBytes));
            JSON.writeValue(arm.receipt().toFile(), receipt);
        }
    }

    private void failArmCase(ResultsPipeline.ArmFiles files, String caseId, int status) throws Exception {
        ObjectNode receipt = (ObjectNode) JSON.readTree(files.receipt().toFile());
        ArrayNode cases = (ArrayNode) receipt.path("cases");
        List<String> lines = Files.readAllLines(files.delivered());
        for (int index = 0; index < cases.size(); index++) {
            ObjectNode row = (ObjectNode) cases.get(index);
            if (!caseId.equals(row.path("id").asText())) continue;
            row.put("state", "FAILED").put("finalSuccess", false)
                    .put("firstAttemptClean", false).put("failureClass", "HTTP_ERROR");
            ArrayNode calls = (ArrayNode) row.path("llmCalls");
            calls.removeAll();
            calls.addObject().put("outcome", "HTTP_ERROR").put("attempt", 1)
                    .put("latencyMs", 1).put("httpStatus", status);
            String output = JSON.writeValueAsString(new DeliveredOutputs.Line(caseId,
                    row.path(WORKFLOW_FIELD).asText(), receipt.path("arm").asText(),
                    row.path("manifestRowSha256").asText(), 0, CaseArmOutcome.State.FAILED,
                    "HTTP_ERROR", null));
            lines.set(index, output);
            row.put("deliveredOutputSha256", FreezeRecord.sha256(output.getBytes(StandardCharsets.UTF_8)));
        }
        write(files.delivered(), String.join("\n", lines) + "\n");
        receipt.put(DELIVERED_FILE_HASH_FIELD, FreezeRecord.sha256(files.delivered()));
        JSON.writeValue(files.receipt().toFile(), receipt);
    }

    private void removeScoreItem(ResultsPipeline.Input input, String caseId, String arm) throws Exception {
        List<String> keys = Files.readAllLines(input.classA().key());
        String blindId = null;
        List<String> retainedKeys = new ArrayList<>();
        for (String line : keys) {
            JsonNode key = JSON.readTree(line);
            if (caseId.equals(key.path(CASE_ID_FIELD).asText()) && arm.equals(key.path("arm").asText()))
                blindId = key.path("id").asText();
            else retainedKeys.add(line);
        }
        List<String> retainedScores = new ArrayList<>();
        for (String line : Files.readAllLines(input.classA().primary()))
            if (!JSON.readTree(line).path("id").asText().equals(blindId)) retainedScores.add(line);
        write(input.classA().key(), String.join("\n", retainedKeys) + "\n");
        write(input.classA().primary(), String.join("\n", retainedScores) + "\n");
    }

    @Test void cleanPrimaryPairPassesAndReportContainsNoDeliveredContent() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        assertThat(result.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(result.routes().get(PAID_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        String report = Files.readString(result.report());
        assertThat(report).contains("BENCHMARK_PASS", "RegressionEvidenceOnly", "ceilingUsd", "mappingSha256");
        assertThat(report).doesNotContain(MARKER, ANSWER, "output_text", "input_tokens");
    }

    @Test void paidWorkflowUsesPaidBaselineAndCandidateQualityAloneFailsT3() throws Exception {
        Fixture fixture = fixture(true, false, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        assertThat(result.routes().get(PAID_ROUTE).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T3);
        assertThat(result.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(Files.readString(result.report())).contains(DEADLINE_LABEL);
    }

    @Test void candidateOnlyCriticalWritesTriggerAndRealGateIgnoresReceiptTriggerField() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        assertThat(result.triggers().get(NOTE)).containsExactly(CASE_NOTE);
        assertThat(result.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(result.routes().get(INTERACTIVE_ROUTE).undisposedCriticalCaseIds().get(NOTE))
                .contains(CASE_NOTE);
        assertThat(Files.readString(result.report())).contains("undisposedCriticalCaseIds", CASE_NOTE,
                "ownerDispositionCount\":0");
        assertThat(Files.readString(result.report())).doesNotContain(DEADLINE_LABEL);
        var runInput = new StageRunner.RunInput("gpt-4.1-mini", "fake-secret-key",
                StageCapLedger.Stage.STAGE_2, Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN),
                Map.of(NOTE + ":synthetic", new ForecastReservation(100, 100, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE)), BigDecimal.ONE, BigDecimal.ONE, null,
                temp.resolve("reserve-output"));
        Stage1RunPlan plan = new Stage1RunPlan(new Stage1RunPlan.Config(runInput, MINI, null,
                fixture.input().parameters(), fixture.input().manifests(), NOTE, false,
                List.of(fixture.input().baselineMini().receipt(), fixture.input().baseline41().receipt(),
                        fixture.input().candidate().receipt()), temp.resolve("reserve-receipt.json"),
                fixture.freeze(), result.triggerRecord()));
        AtomicBoolean dispatched = new AtomicBoolean();
        Stage1RunPlan.OfflineTransport stub = (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                dispatched.set(true);
                ObjectNode response = JSON.createObjectNode().put("output_text", "{\"questions\":[]}");
                response.putObject("usage").put("input_tokens", 1).put("output_tokens", 1);
                return new MockClientHttpResponse(JSON.writeValueAsBytes(response), 200);
            }
        };
        try (StageCapLedger ledger = StageCapLedger.open(temp.resolve("reserve-ledger.jsonl"),
                Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN), true)) {
            plan.dryRun(stub, ledger);
        }
        assertThat(dispatched).isTrue();
    }

    @Test void exclusionCannotBecomePassAndTamperedScoreOrReceiptClosesGate() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        Files.writeString(fixture.score(), "changed");
        Path triggerPath = result.triggerRecord();
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(triggerPath, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("SHA-256");
        Fixture excluded = fixture(false, false, true);
        ResultsPipeline.Result excludedResult = ResultsPipeline.run(excluded.input());
        assertThat(excludedResult.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(Files.readString(excludedResult.report()))
                .contains("\"excludedFixtureEvidence\":{\"" + CASE_NOTE + "\":\"MISSING\"}");
    }

    @Test void staleCompatibilityReportMakesOnlyGuardedWorkflowInconclusive() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ResultsPipeline.Input original = fixture.input();
        ResultsPipeline.Input stale = new ResultsPipeline.Input(original.repoRoot(), original.freeze(),
                original.parameters(), original.mapping(), original.manifests(), original.baselineMini(),
                original.baseline41(), original.candidate(), original.classA(), original.classB(),
                original.reserves(), original.compatibilityReports(), Instant.now().plusSeconds(3600),
                original.ownerDispositions(), original.report(), original.triggerRecord(), true);
        ResultsPipeline.Result result = ResultsPipeline.run(stale);
        assertThat(result.routes().values()).allSatisfy(route ->
                assertThat(route.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE));
        assertThat(Files.readString(result.report())).contains("NOT_EXECUTED");
    }

    @Test void costAboveOldPerCaseCeilingDoesNotCreateT3Event() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ObjectNode row = (ObjectNode) receipt.path("cases").get(1);
        row.put(CONSERVATIVE_USD_FIELD, 2.00);
        row.put("billedUsd", 1.00);
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        var paid = result.routes().get(PAID_ROUTE);
        assertThat(paid.failedTiers()).isEmpty();
        assertThat(paid.workflows().get(INTERVIEW).candidateComponents()).isEmpty();
        // ceilingUsd remains informational for existing report consumers; it is no longer a gate.
        assertThat(Files.readString(result.report())).contains("\"ceilingUsd\":1.25",
                "\"candidateConservativeUsd\":2.0", "\"candidateBilledUsd\":1.0",
                "\"operationalCostReview\"");
    }

    @Test void refrozenCriticalTypesChangePipelineEvidenceAndCostToleranceNoLongerMovesT3() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ((ObjectNode) receipt.path("cases").get(1)).put(CONSERVATIVE_USD_FIELD, 1.30);
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        ResultsPipeline.Result before = ResultsPipeline.run(fixture.input());
        // D5: cost is no longer a per-case T3 event, so a candidate cost above the old ceiling leaves the
        // paid route's T3 untouched; the tolerance now feeds only the separate operational cost review.
        assertThat(before.routes().get(PAID_ROUTE).failedTiers())
                .doesNotContain(RouteVerdictCalculator.Tier.T3);
        assertThat(before.triggers().get(NOTE)).contains(CASE_NOTE);
        String changed = Files.readString(fixture.input().mapping())
                .replace("1.25", "1.50").replace("\"WRONG_KEY\", ", "");
        refreezeMapping(fixture, changed);
        ResultsPipeline.Result after = ResultsPipeline.run(fixture.input());
        assertThat(after.routes().get(PAID_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(after.triggers()).doesNotContainKey(NOTE);
        assertThat(after.routes().get(INTERACTIVE_ROUTE).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T3);
    }

    @Test void recoveredFirstAttemptParseCountsOnceAndMissingPairNeverPasses() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ObjectNode row = (ObjectNode) receipt.path("cases").get(0);
        row.put("firstAttemptClean", false);
        ArrayNode calls = (ArrayNode) row.path("llmCalls");
        calls.removeAll();
        calls.addObject().put("outcome", PARSE_FAILURE).put("attempt", 1)
                .put("latencyMs", 1).put("httpStatus", 200);
        calls.addObject().put("outcome", "SUCCESS").put("attempt", 2)
                .put("latencyMs", 1).put("httpStatus", 200);
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        ResultsPipeline.Result parse = ResultsPipeline.run(fixture.input());
        assertThat(parse.routes().get(INTERACTIVE_ROUTE).workflows().get(NOTE).candidateComponents())
                .containsEntry("RECOVERED_PARSE", 1);
        assertThat(parse.routes().get(INTERACTIVE_ROUTE).d()).isEqualTo(1);
        List<String> outputLines = Files.readAllLines(fixture.candidateOutput());
        Files.writeString(fixture.candidateOutput(), outputLines.getFirst() + "\n");
        receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ((ArrayNode) receipt.path("cases")).remove(1);
        receipt.put(DELIVERED_FILE_HASH_FIELD, FreezeRecord.sha256(fixture.candidateOutput()));
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        ResultsPipeline.Result missing = ResultsPipeline.run(fixture.input());
        assertThat(missing.routes().get(PAID_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void handMadeTriggerEditedReceiptAndWrongWorkflowAreRefused() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        Path trigger = result.triggerRecord();
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, INTERVIEW))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("No scored primary T2 trigger");
        ObjectNode forged = (ObjectNode) JSON.readTree(trigger.toFile());
        ArrayNode falseClaim = JSON.createArrayNode().add(CASE_INTERVIEW);
        ((ObjectNode) forged.path("candidateOnlyCritical")).set(NOTE, falseClaim);
        byte[] forgedBytes = JSON.writeValueAsBytes(forged);
        Files.write(trigger, forgedBytes);
        Files.writeString(trigger.resolveSibling(trigger.getFileName() + ".sha256"),
                FreezeRecord.sha256(forgedBytes));
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("differ from recomputed");
        ResultsPipeline.run(fixture.input());
        Files.writeString(fixture.candidateReceipt(), Files.readString(fixture.candidateReceipt()) + " ");
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("SHA-256");
        Fixture parameterCase = fixture(false, true, false);
        ResultsPipeline.Result parameterResult = ResultsPipeline.run(parameterCase.input());
        Files.writeString(parameterCase.input().parameters(),
                Files.readString(parameterCase.input().parameters()) + " ");
        Path parameterTrigger = parameterResult.triggerRecord();
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(parameterTrigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("SHA-256");
    }

    @Test void gateRecomputesExtraAndOmittedWorkflowClaimsFromPipelineInputs() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        Path trigger = result.triggerRecord();
        assertThat(result.triggers().get(NOTE)).containsExactly(CASE_NOTE);
        assertThat(T2TriggerRecord.readAndVerify(trigger, NOTE).candidateOnlyCritical())
                .isEqualTo(result.triggers());
        ObjectNode forged = (ObjectNode) JSON.readTree(trigger.toFile());
        ObjectNode claims = (ObjectNode) forged.path("candidateOnlyCritical");
        claims.set(INTERVIEW, JSON.createArrayNode().add(CASE_INTERVIEW));
        byte[] forgedBytes = JSON.writeValueAsBytes(forged);
        Files.write(trigger, forgedBytes);
        Files.writeString(trigger.resolveSibling(trigger.getFileName() + ".sha256"),
                FreezeRecord.sha256(forgedBytes));
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class)
                .hasMessageContaining(INTERVIEW).hasMessageContaining(NOTE)
                .hasMessageContaining("recomputed");
        claims.remove(INTERVIEW);
        claims.remove(NOTE);
        byte[] omittedBytes = JSON.writeValueAsBytes(forged);
        Files.write(trigger, omittedBytes);
        Files.writeString(trigger.resolveSibling(trigger.getFileName() + ".sha256"),
                FreezeRecord.sha256(omittedBytes));
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class)
                .hasMessageContaining("claims {}").hasMessageContaining("recomputed")
                .hasMessageContaining(NOTE);
        ResultsPipeline.run(fixture.input());
        Path registry = T2TriggerRecord.readAndVerify(trigger, NOTE)
                .linkedPath(T2TriggerRecord.SCORER_REGISTRY);
        Files.writeString(registry, Files.readString(registry) + " ");
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(trigger, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class)
                .hasMessageContaining("SHA-256");
    }

    @Test void receiptsMustMatchFrozenFreezeAndCaseSetHashes() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        receipt.put("caseSetSha256", "0".repeat(64));
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        ResultsPipeline.Input input = fixture.input();
        assertThatThrownBy(() -> ResultsPipeline.run(input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("case set");
        receipt.put("caseSetSha256", caseSetHash(fixture.input().manifests().get("classA.primary")));
        receipt.put(FREEZE_HASH_FIELD, "0".repeat(64));
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        assertThatThrownBy(() -> ResultsPipeline.run(input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("freeze");
    }

    @Test void reserveResultsAndOwnerDispositionFeedBackIntoPrimaryVerdict() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Input original = fixture.input();
        String primarySet = caseSetHash(original.manifests().get("classA.primary"));
        String freezeHash = FreezeRecord.sha256(original.freeze());
        ResultsPipeline.ArmFiles baseline = reserveArm(MINI, "gpt-4.1-mini",
                original.manifests().get("classA.reserve"), primarySet, freezeHash);
        ResultsPipeline.ArmFiles candidate = reserveArm(LUNA, "gpt-6-luna",
                original.manifests().get("classA.reserve"), primarySet, freezeHash);
        Path key = write(temp.resolve("reserve-key.jsonl"),
                JSON.writeValueAsString(Map.of("id", "blind-1", "arm", MINI, CASE_ID_FIELD, RESERVE_NOTE,
                        WORKFLOW_FIELD, NOTE, QUESTION_FORMAT_FIELD, "MCQ")) + "\n"
                + JSON.writeValueAsString(Map.of("id", "blind-2", "arm", LUNA, CASE_ID_FIELD, RESERVE_NOTE,
                        WORKFLOW_FIELD, NOTE, QUESTION_FORMAT_FIELD, "MCQ")) + "\n");
        Path score = write(temp.resolve("reserve-scores.jsonl"),
                JSON.writeValueAsString(score("blind-1", false, false)) + "\n"
                + JSON.writeValueAsString(score("blind-2", false, false)) + "\n");
        ScoreEvidence.FilesA classA = new ScoreEvidence.FilesA(key, score,
                write(temp.resolve("reserve-reviews.jsonl"), ""),
                write(temp.resolve("reserve-adjudications.jsonl"), ""),
                write(temp.resolve("reserve-exclusions.jsonl"), ""));
        ScoreEvidence.FilesB classB = new ScoreEvidence.FilesB(
                write(temp.resolve("reserve-b-key.jsonl"), ""),
                write(temp.resolve("reserve-b-scores.jsonl"), ""),
                Map.of("HUMAN_A", ClassBScoreFiles.ScorerKind.HUMAN));
        Path disposition = write(temp.resolve("owner-dispositions.json"),
                "[{\"workflow\":\"" + NOTE + "\",\"caseId\":\"" + CASE_NOTE
                + "\",\"value\":\"DEFECT_ACKNOWLEDGED\",\"reasonCode\":\"ACKNOWLEDGED_FOR_MITIGATION\"}]");
        ResultsPipeline.Input withReserve = new ResultsPipeline.Input(original.repoRoot(), original.freeze(),
                original.parameters(), original.mapping(), original.manifests(), original.baselineMini(),
                original.baseline41(), original.candidate(), original.classA(), original.classB(),
                Map.of(NOTE, new ResultsPipeline.ReserveInput(baseline, candidate, classA, classB)),
                original.compatibilityReports(), original.headCommitTime(), disposition,
                original.report(), original.triggerRecord(), true);
        ResultsPipeline.Result result = ResultsPipeline.run(withReserve);
        var interactive = result.routes().get(INTERACTIVE_ROUTE);
        assertThat(interactive.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(interactive.workflows().get(NOTE).reserveCandidateOnly()).isZero();
        assertThat(interactive.undisposedCriticalCaseIds()).isEmpty();
        assertThat(Files.readString(result.report())).contains(RESERVE_NOTE, "reserveFileSha256");
    }

    @Test void firstSuccessfulRecheckIsScoredAndOriginalFailureCountsOnceInT3() throws Exception {
        Fixture fixture = fixture(false, true, false);
        ResultsPipeline.Input original = fixture.input();
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ObjectNode row = (ObjectNode) receipt.path("cases").get(0);
        String rowHash = row.path("manifestRowSha256").asText();
        row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                .put("failureClass", PARSE_FAILURE);
        ArrayNode calls = (ArrayNode) row.path("llmCalls");
        calls.removeAll();
        calls.addObject().put("outcome", PARSE_FAILURE).put("attempt", 1)
                .put("latencyMs", 1).put("httpStatus", 200);
        String failed = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE_NOTE, NOTE, LUNA,
                rowHash, 0, CaseArmOutcome.State.FAILED, PARSE_FAILURE, null));
        row.put("deliveredOutputSha256", FreezeRecord.sha256(failed.getBytes(StandardCharsets.UTF_8)));
        List<String> originalOutput = Files.readAllLines(fixture.candidateOutput());
        write(fixture.candidateOutput(), failed + "\n" + originalOutput.get(1) + "\n");
        receipt.put(DELIVERED_FILE_HASH_FIELD, FreezeRecord.sha256(fixture.candidateOutput()));
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        Path root = temp.resolve("rechecks");
        String success = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE_NOTE, NOTE, LUNA,
                rowHash, 1, CaseArmOutcome.State.COMPLETE, null,
                JSON.readTree(originalOutput.getFirst()).path("content")));
        Path successFile = write(root.resolve(CASE_NOTE).resolve("recheck-1").resolve(DELIVERED_OUTPUTS_FILE),
                success + "\n");
        String failedAgain = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE_NOTE, NOTE, LUNA,
                rowHash, 2, CaseArmOutcome.State.FAILED, PARSE_FAILURE, null));
        Path failedFile = write(root.resolve(CASE_NOTE).resolve("recheck-2").resolve(DELIVERED_OUTPUTS_FILE),
                failedAgain + "\n");
        RecheckReceipt checks = new RecheckReceipt(HEAD, FreezeRecord.sha256(original.freeze()), LUNA,
                "gpt-6-luna", Set.of(PARSE_FAILURE), List.of(new RecheckReceipt.Case(CASE_NOTE,
                    PARSE_FAILURE, List.of(
                        new RecheckReceipt.Repeat(1, null, RouteVerdictCalculator.Evidence.SCORED,
                                null, FreezeRecord.sha256(success.getBytes(StandardCharsets.UTF_8)),
                                FreezeRecord.sha256(successFile)),
                        new RecheckReceipt.Repeat(2, PARSE_FAILURE, RouteVerdictCalculator.Evidence.SCORED,
                                null, FreezeRecord.sha256(failedAgain.getBytes(StandardCharsets.UTF_8)),
                                FreezeRecord.sha256(failedFile))))),
                caseSetHash(original.manifests().get("classA.primary")));
        Path checkFile = root.resolve("recheck-receipt.json");
        checks.write(checkFile);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(original.candidate().receipt(),
                original.candidate().delivered(), checkFile, root);
        ResultsPipeline.Input withRecheck = new ResultsPipeline.Input(original.repoRoot(), original.freeze(),
                original.parameters(), original.mapping(), original.manifests(), original.baselineMini(),
                original.baseline41(), candidate, original.classA(), original.classB(), original.reserves(),
                original.compatibilityReports(), original.headCommitTime(), original.ownerDispositions(),
                original.report(), original.triggerRecord(), true);
        ResultsPipeline.Result result = ResultsPipeline.run(withRecheck);
        var note = result.routes().get(INTERACTIVE_ROUTE).workflows().get(NOTE);
        assertThat(note.candidateComponents()).containsEntry("ISOLATED_FINAL_FAILURE", 1);
        assertThat(note.candidateOnlyT3()).isEqualTo(1);
        assertThat(note.candidateOnlyT1()).isZero();
        assertThat(result.triggers().get(NOTE)).containsExactly(CASE_NOTE);
        Path triggerFile = result.triggerRecord();
        assertThat(T2TriggerRecord.readAndVerify(triggerFile, NOTE)
                .candidateOnlyCritical().get(NOTE)).containsExactly(CASE_NOTE);
        var reserveRun = new StageRunner.RunInput("gpt-4.1-mini", "fake-secret-key",
                StageCapLedger.Stage.STAGE_2, Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN),
                Map.of(NOTE + ":synthetic", new ForecastReservation(100, 100, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE)), BigDecimal.ONE, BigDecimal.ONE, null,
                temp.resolve("recheck-reserve-output"));
        Stage1RunPlan reservePlan = new Stage1RunPlan(new Stage1RunPlan.Config(reserveRun, MINI, null,
                original.parameters(), original.manifests(), NOTE, false,
                List.of(original.baselineMini().receipt(), original.baseline41().receipt(),
                        original.candidate().receipt()), temp.resolve("recheck-reserve-receipt.json"),
                fixture.freeze(), triggerFile));
        Stage1RunPlan.OfflineTransport stub = (uri, method) -> new MockClientHttpRequest(method, uri) {
            @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                ObjectNode response = JSON.createObjectNode().put("output_text", "{\"questions\":[]}");
                response.putObject("usage").put("input_tokens", 1).put("output_tokens", 1);
                return new MockClientHttpResponse(JSON.writeValueAsBytes(response), 200);
            }
        };
        try (StageCapLedger ledger = StageCapLedger.open(temp.resolve("recheck-reserve-ledger.jsonl"),
                Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN), true)) {
            reservePlan.dryRun(stub, ledger);
        }
        Files.writeString(successFile, Files.readString(successFile) + " ");
        assertThatThrownBy(() -> T2TriggerRecord.readAndVerify(triggerFile, NOTE))
                .isInstanceOf(T2TriggerRecord.InvalidTrigger.class).hasMessageContaining("Recheck SHA-256");
    }

    private ResultsPipeline.Input failedOriginalWithRechecks(Fixture fixture, boolean firstSuccess,
            boolean secondSuccess) throws Exception {
        ResultsPipeline.Input input = fixture.input();
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.candidateReceipt().toFile());
        ObjectNode row = (ObjectNode) receipt.path("cases").get(0);
        String rowHash = row.path("manifestRowSha256").asText();
        row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                .put("failureClass", PARSE_FAILURE);
        String originalLine = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE_NOTE, NOTE, LUNA,
                rowHash, 0, CaseArmOutcome.State.FAILED, PARSE_FAILURE, null));
        row.put("deliveredOutputSha256", FreezeRecord.sha256(originalLine.getBytes(StandardCharsets.UTF_8)));
        List<String> lines = Files.readAllLines(fixture.candidateOutput());
        write(fixture.candidateOutput(), originalLine + "\n" + lines.get(1) + "\n");
        receipt.put(DELIVERED_FILE_HASH_FIELD, FreezeRecord.sha256(fixture.candidateOutput()));
        JSON.writeValue(fixture.candidateReceipt().toFile(), receipt);
        Path directory = temp.resolve("case-rechecks");
        List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            boolean success = index == 1 ? firstSuccess : secondSuccess;
            String delivered = JSON.writeValueAsString(new DeliveredOutputs.Line(CASE_NOTE, NOTE, LUNA,
                    rowHash, index, success ? CaseArmOutcome.State.COMPLETE : CaseArmOutcome.State.FAILED,
                    success ? null : PARSE_FAILURE,
                    success ? JSON.readTree(lines.getFirst()).path("content") : null));
            Path file = write(directory.resolve(CASE_NOTE).resolve("recheck-" + index)
                    .resolve(DELIVERED_OUTPUTS_FILE), delivered + "\n");
            repeats.add(new RecheckReceipt.Repeat(index, success ? null : PARSE_FAILURE,
                    RouteVerdictCalculator.Evidence.SCORED, null,
                    FreezeRecord.sha256(delivered.getBytes(StandardCharsets.UTF_8)), FreezeRecord.sha256(file)));
        }
        Path checkFile = directory.resolve("receipt.json");
        new RecheckReceipt(HEAD, FreezeRecord.sha256(input.freeze()), LUNA, "gpt-6-luna",
                Set.of(PARSE_FAILURE), List.of(new RecheckReceipt.Case(CASE_NOTE, PARSE_FAILURE,
                repeats)), caseSetHash(input.manifests().get("classA.primary"))).write(checkFile);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(input.candidate().receipt(),
                input.candidate().delivered(), checkFile, directory);
        return new ResultsPipeline.Input(input.repoRoot(), input.freeze(), input.parameters(), input.mapping(),
                input.manifests(), input.baselineMini(), input.baseline41(), candidate, input.classA(),
                input.classB(), input.reserves(), input.compatibilityReports(), input.headCommitTime(),
                input.ownerDispositions(), input.report(), input.triggerRecord(), true);
    }

    @Test void confirmedT1ThroughExcludedFailedOriginalReturnsToOwner() throws Exception {
        Fixture fixture = fixture(false, false, true);
        ResultsPipeline.Result result = ResultsPipeline.run(failedOriginalWithRechecks(fixture, false, false));
        var route = result.routes().get(INTERACTIVE_ROUTE);
        assertThat(route.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        assertThat(route.failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(route.workflows().get(NOTE).candidateOnlyT1()).isEqualTo(1);
        assertThat(DeadlineRule.classify(route)).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(Files.readString(result.report())).doesNotContain(DEADLINE_LABEL);
    }

    @Test void oneSuccessfulRecheckScoresOutputAndOriginalFailureOnce() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ResultsPipeline.Result result = ResultsPipeline.run(failedOriginalWithRechecks(fixture, false, true));
        var workflow = result.routes().get(INTERACTIVE_ROUTE).workflows().get(NOTE);
        assertThat(workflow.candidateOnlyT1()).isZero();
        assertThat(workflow.candidateComponents()).containsEntry("ISOLATED_FINAL_FAILURE", 1);
        assertThat(workflow.candidateOnlyT3()).isEqualTo(1);
    }

    @Test void excludedOriginalWithSuccessfulRecheckUsesSupplementalBlindScore() throws Exception {
        Fixture fixture = fixture(false, false, true);
        ResultsPipeline.Input input = fixture.input();
        Path key = input.classA().key();
        Path scores = input.classA().primary();
        for (String arm : List.of(MINI, LUNA)) {
            String blind = "recheck-blind-" + arm;
            Files.writeString(key, JSON.writeValueAsString(Map.of("id", blind, "arm", arm,
                    CASE_ID_FIELD, CASE_NOTE, WORKFLOW_FIELD, NOTE, QUESTION_FORMAT_FIELD, "MCQ"))
                    + "\n", java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(scores, JSON.writeValueAsString(score(blind, false, false)) + "\n",
                    java.nio.file.StandardOpenOption.APPEND);
        }
        ResultsPipeline.Result result = ResultsPipeline.run(failedOriginalWithRechecks(fixture, false, true));
        var workflow = result.routes().get(INTERACTIVE_ROUTE).workflows().get(NOTE);
        assertThat(workflow.candidateOnlyT1()).isZero();
        assertThat(workflow.candidateOnlyT3()).isEqualTo(1);
        assertThat(workflow.candidateComponents()).containsEntry("ISOLATED_FINAL_FAILURE", 1);
    }

    @Test void excludedMissingOutputWithoutRecheckRemainsInconclusive() throws Exception {
        Fixture fixture = fixture(false, false, true);
        Files.writeString(fixture.input().classA().exclusions(),
                "{\"caseId\":\"" + CASE_NOTE + "\",\"reason\":\"MISSING_ARM_OUTPUT\"}\n");
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        assertThat(result.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void eligibleFailureWithoutRecheckReceiptStaysInconclusive() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ResultsPipeline.Input withChecks = failedOriginalWithRechecks(fixture, false, false);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(withChecks.candidate().receipt(),
                withChecks.candidate().delivered(), null, null);
        ResultsPipeline.Input missingChecks = new ResultsPipeline.Input(withChecks.repoRoot(),
                withChecks.freeze(), withChecks.parameters(), withChecks.mapping(), withChecks.manifests(),
                withChecks.baselineMini(), withChecks.baseline41(), candidate, withChecks.classA(),
                withChecks.classB(), withChecks.reserves(), withChecks.compatibilityReports(),
                withChecks.headCommitTime(), withChecks.ownerDispositions(), withChecks.report(),
                withChecks.triggerRecord(), true);
        ResultsPipeline.Result result = ResultsPipeline.run(missingChecks);
        assertThat(result.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void recheckFreezeAndCaseSetMismatchAreRefused() throws Exception {
        Fixture fixture = fixture(false, false, false);
        ResultsPipeline.Input input = failedOriginalWithRechecks(fixture, false, false);
        Path file = input.candidate().recheckReceipt();
        ObjectNode receipt = (ObjectNode) JSON.readTree(file.toFile());
        receipt.put("caseSetSha256", "0".repeat(64));
        JSON.writeValue(file.toFile(), receipt);
        assertThatThrownBy(() -> ResultsPipeline.run(input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Recheck receipt identity differs");
        receipt.put("caseSetSha256", caseSetHash(input.manifests().get("classA.primary")));
        receipt.put(FREEZE_HASH_FIELD, "0".repeat(64));
        JSON.writeValue(file.toFile(), receipt);
        assertThatThrownBy(() -> ResultsPipeline.run(input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Recheck receipt identity differs");
    }

    @Test void candidate400NeedsSameCaseBaselineSuccessToBeProviderRejection() throws Exception {
        Fixture fixture = fixture(false, false, false);
        failArmCase(fixture.input().candidate(), CASE_NOTE, 400);
        removeScoreItem(fixture.input(), CASE_NOTE, LUNA);
        ResultsPipeline.Result rejected = ResultsPipeline.run(fixture.input());
        assertThat(rejected.routes().get(INTERACTIVE_ROUTE).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T1);
        assertThat(Files.readString(rejected.report())).doesNotContain(DEADLINE_LABEL);
        failArmCase(fixture.input().baselineMini(), CASE_NOTE, 500);
        removeScoreItem(fixture.input(), CASE_NOTE, MINI);
        ResultsPipeline.Result bothFailed = ResultsPipeline.run(fixture.input());
        assertThat(bothFailed.routes().get(INTERACTIVE_ROUTE).failedTiers())
                .doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(bothFailed.routes().get(INTERACTIVE_ROUTE).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void solComparisonRequiresOwnerInputsAndUsesTheTwoContentFreeReports() throws Exception {
        Fixture fixture = fixture(true, false, false);
        ResultsPipeline.Input primary = fixture.input();
        ResultsPipeline.run(primary);
        Path lunaReport = temp.resolve("luna-decision-report.json");
        Files.copy(primary.report(), lunaReport);
        ResultsPipeline.ArmFiles solArm = arm(SOL_PAID, "gpt-6.1-sol", List.of(CASE_INTERVIEW),
                primary.manifests().get("classA.primary"), FreezeRecord.sha256(primary.freeze()),
                caseSetHash(primary.manifests().get("classA.primary")));
        Path solKey = write(temp.resolve("sol-key.jsonl"),
                JSON.writeValueAsString(Map.of("id", "blind-1", "arm", PAID, CASE_ID_FIELD, CASE_INTERVIEW,
                        WORKFLOW_FIELD, INTERVIEW, QUESTION_FORMAT_FIELD, "MCQ")) + "\n"
                + JSON.writeValueAsString(Map.of("id", "blind-2", "arm", SOL_PAID,
                        CASE_ID_FIELD, CASE_INTERVIEW, WORKFLOW_FIELD, INTERVIEW,
                        QUESTION_FORMAT_FIELD, "MCQ")) + "\n");
        Path solScores = write(temp.resolve("sol-scores.jsonl"),
                JSON.writeValueAsString(score("blind-1", false, false)) + "\n"
                + JSON.writeValueAsString(score("blind-2", false, false)) + "\n");
        ScoreEvidence.FilesA solClassA = new ScoreEvidence.FilesA(solKey, solScores,
                primary.classA().reviews(), primary.classA().adjudications(), primary.classA().exclusions());
        ResultsPipeline.Input solInput = new ResultsPipeline.Input(primary.repoRoot(), primary.freeze(),
                primary.parameters(), primary.mapping(), primary.manifests(), primary.baselineMini(),
                primary.baseline41(), solArm, solClassA, primary.classB(), Map.of(),
                primary.compatibilityReports(), primary.headCommitTime(), null,
                temp.resolve("sol-decision-report.json"), temp.resolve("sol-t2-trigger.json"), true);
        ResultsPipeline.Result sol = ResultsPipeline.run(solInput);
        assertThat(sol.routes().get(PAID_ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        Path owner = write(temp.resolve("sol-owner-input.json"), JSON.writeValueAsString(Map.of(
                "route", PAID_ROUTE, "lunaReportSha256", FreezeRecord.sha256(lunaReport),
                "solReportSha256", FreezeRecord.sha256(sol.report()),
                "economicsAndOtherGatesPass", true, "investigationComplete", true,
                "samePrimaryInputs", true)));
        Path decision = temp.resolve("sol-justification.json");
        assertThat(ResultsPipeline.compareSol(lunaReport, sol.report(), owner,
                primary.parameters(), decision)).isFalse();
        assertThat(Files.readString(decision)).contains("\"solVersusLunaD\":-1");
    }

    @Test void recoveredLongExamFallbackUsesGroupStructureAndIgnoresProductionRetries() {
        List<Stage1Receipt.Signal> batches = List.of(
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-batch-1"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-batch-2"));
        List<Stage1Receipt.Signal> fallbackCalls = List.of(
                new Stage1Receipt.Signal(PARSE_FAILURE, 1, 1, 200, "long-case-batch-1"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-batch-2"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-seq-1"));
        Stage1Receipt.CaseRow fallback = new Stage1Receipt.CaseRow("long-case", "LONG_EXAM", "hash",
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ZERO, BigDecimal.ZERO,
                fallbackCalls, List.of("same-body"), "hash");
        assertThat(ResultsPipeline.recoveredFallback(fallback)).isTrue();
        List<Stage1Receipt.Signal> fullFallback = List.of(
                new Stage1Receipt.Signal(PARSE_FAILURE, 1, 1, 200, "long-case-batch-1"),
                new Stage1Receipt.Signal(PARSE_FAILURE, 1, 1, 200, "long-case-batch-2"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-seq-1"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-seq-2"));
        assertThat(ResultsPipeline.recoveredFallback(new Stage1Receipt.CaseRow("long-case", "LONG_EXAM", "hash",
                CaseArmOutcome.State.COMPLETE, false, true, null, BigDecimal.ZERO, BigDecimal.ZERO,
                fullFallback, List.of("same-body"), "hash"))).isTrue();
        assertThat(ResultsPipeline.recoveredFallback(new Stage1Receipt.CaseRow("long-case", "LONG_EXAM", "hash",
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ZERO, BigDecimal.ZERO,
                batches, List.of("same-body"), "hash"))).isFalse();
        List<Stage1Receipt.Signal> retryOnly = List.of(
                new Stage1Receipt.Signal(PARSE_FAILURE, 1, 1, 200, "long-case-batch-1"),
                new Stage1Receipt.Signal("SUCCESS", 2, 1, 200, "long-case-batch-1"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "long-case-batch-2"));
        assertThat(ResultsPipeline.recoveredFallback(new Stage1Receipt.CaseRow("long-case", "LONG_EXAM", "hash",
                CaseArmOutcome.State.COMPLETE, false, true, null, BigDecimal.ZERO, BigDecimal.ZERO,
                retryOnly, List.of("same-body"), "hash"))).isFalse();
        List<Stage1Receipt.Signal> otherWorkflowGroups = List.of(
                new Stage1Receipt.Signal(PARSE_FAILURE, 1, 1, 200, "other-batch-1"),
                new Stage1Receipt.Signal("SUCCESS", 1, 1, 200, "other-seq-1"));
        assertThat(ResultsPipeline.recoveredFallback(new Stage1Receipt.CaseRow("other", NOTE, "hash",
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ZERO, BigDecimal.ZERO,
                otherWorkflowGroups, List.of("same-body"), "hash"))).isFalse();
    }
}
