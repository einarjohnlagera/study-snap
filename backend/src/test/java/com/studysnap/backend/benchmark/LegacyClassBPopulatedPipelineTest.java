package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Populated human scores exercise the legacy receipt-arm lookup and its blind-key boundary. */
class LegacyClassBPopulatedPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String BASELINE = "BASELINE";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String TIP = "STUDY_TIP";
    private static final String COLLECTION = "COLLECTION_COMPANION";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String HUMAN = "HUMAN_A";
    private static final String HEAD = "a".repeat(40);
    private static final String PROMPT = "backend/src/main/resources/prompts/study-pack-v1/system.txt";
    private static final String PREREG = "docs/claude-plans/2026-11-12-v0182-llm-migration-preregistration.md";
    private static final String PARAMS = "backend/src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json";
    private static final String MAPPING = "backend/src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json";
    private static final List<String> IDS = List.of("tip", "collection", "ask", "interview");
    private static final List<String> WORKFLOWS = List.of(TIP, COLLECTION, ASK, INTERVIEW);
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    @TempDir Path temp;

    private record Fixture(ResultsPipeline.Input input, ClassBBlindPacketExport.Export export) {}

    @Test void allFourWorkflowsAreScoredAndTriggerRoundTrips() throws Exception {
        Fixture fixture = fixture(false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        assertThat(result.triggers()).containsEntry(TIP, Set.of("tip"));
        T2TriggerRecord verified = T2TriggerRecord.readAndVerify(result.triggerRecord(), TIP);
        assertThat(verified.candidateOnlyCritical()).containsEntry(TIP, Set.of("tip"));
        Map<String, RouteVerdictCalculator.CaseRecord> records = records(result);
        assertThat(records).hasSize(4);
        for (String id : IDS) {
            RouteVerdictCalculator.CaseRecord row = records.get(id);
            assertThat(row.baseline().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.SCORED);
            assertThat(row.candidate().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.SCORED);
        }
        assertThat(records.get("tip").candidate().critical()).isTrue();
        assertThat(records.get("tip").baseline().critical()).isFalse();
        assertThat(records.get("ask").candidate().t3Components()).contains("QUALITY");
        assertThat(records.get("collection").baseline().t3Components()).contains("QUALITY");
        assertThat(records.get("collection").candidate().critical()).isFalse();
    }

    @Test void legacyFactsEqualOpenedFrbConverterFactsForResolvedItems() throws Exception {
        Fixture fixture = fixture(false);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        Path exportRoot = fixture.input().classB().key().getParent();
        Path seal = ArmKeyGateFixtures.seal(exportRoot, fixture.input().classB().key());
        Path findings = ArmKeyGateFixtures.findings(exportRoot);
        Path lock = ArmKeyGateFixtures.lock(exportRoot, seal, findings, LockRecord.Kind.PRIMARY);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(fixture.input().classB().key(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, findings)), Set.of(LockRecord.Kind.PRIMARY),
                exportRoot.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "test resolved attribution");
        ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(fixture.input().classB().scores(),
                fixture.export().manifest(), SCORERS);
        EvidenceMapping mapping = EvidenceMapping.load(fixture.input().mapping());
        AdjudicatedLoaded converted = ClassBAdjudicatedConverter.classB(opened,
                ClassBAdjudicatedConverter.ExportKind.PRIMARY, validated, mapping,
                PairMap.fromPackets(exportRoot.resolve("packets")), Set.copyOf(WORKFLOWS), Set.copyOf(IDS),
                new ClassBAdjudicatedConverter.ArmLabels(LUNA, LegacyClassBPopulatedPipelineTest::baseline));
        Map<String, RouteVerdictCalculator.CaseRecord> records = records(result);
        for (String id : IDS) {
            String workflow = WORKFLOWS.get(IDS.indexOf(id));
            for (String arm : List.of(baseline(workflow), LUNA)) {
                RouteVerdictCalculator.ArmResult actual = LUNA.equals(arm)
                        ? records.get(id).candidate() : records.get(id).baseline();
                AdjudicatedFacts expected = converted.get(id, arm);
                assertThat(actual.critical()).isEqualTo(expected.criticalQuestions() > 0);
                assertThat(actual.t3Components().contains("QUALITY")).isEqualTo(expected.qualityFailQuestions() > 0);
            }
        }
    }

    @Test void populatedScoresCannotSilentlyLoseDeliveredPair() throws Exception {
        Fixture fixture = fixture(false);
        List<String> keyRows = Files.readAllLines(fixture.input().classB().key());
        Set<String> removedItems = fixture.export().armKey().stream().filter(key -> key.caseId().equals("ask"))
                .map(ClassBScoreAggregator.Key::itemId).collect(java.util.stream.Collectors.toSet());
        Files.write(fixture.input().classB().key(), keyRows.stream()
                .filter(line -> removedItems.stream().noneMatch(id -> line.contains("\"itemId\":\"" + id + "\""))).toList());
        Files.write(fixture.input().classB().scores(), Files.readAllLines(fixture.input().classB().scores()).stream()
                .filter(line -> removedItems.stream().noneMatch(id -> line.contains("\"itemId\":\"" + id + "\""))).toList());
        assertThatThrownBy(() -> ResultsPipeline.run(fixture.input()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("ask");
    }

    @Test void emptyClassBFilesKeepUnscorableOutcome() throws Exception {
        Fixture fixture = fixture(false);
        Files.writeString(fixture.input().classB().key(), "");
        Files.writeString(fixture.input().classB().scores(), "");
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        for (RouteVerdictCalculator.CaseRecord row : records(result).values()) {
            assertThat(row.baseline().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.UNSCORABLE);
            assertThat(row.candidate().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.UNSCORABLE);
        }
    }

    @Test void oneDeliveredArmWithoutKeyRowsRemainsUnscorable() throws Exception {
        Fixture fixture = fixture(true);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        RouteVerdictCalculator.CaseRecord row = records(result).get("ask");
        assertThat(row.baseline().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.UNSCORABLE);
        assertThat(row.candidate().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
    }

    @Test void unresolvedHumanDisagreementStillAborts() throws Exception {
        Fixture fixture = fixture(false);
        ClassBScoreAggregator.Key key = fixture.export().armKey().getFirst();
        ObjectNode conflicting = score(key, !isCritical(key), !isQuality(key));
        conflicting.put("scorerId", "HUMAN_B");
        Files.writeString(fixture.input().classB().scores(), conflicting + "\n", java.nio.file.StandardOpenOption.APPEND);
        ScoreEvidence.FilesB changed = new ScoreEvidence.FilesB(fixture.input().classB().key(),
                fixture.input().classB().scores(), Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN,
                        "HUMAN_B", ClassBScoreFiles.ScorerKind.HUMAN));
        ResultsPipeline.Input input = withClassB(fixture.input(), changed);
        assertThatThrownBy(() -> ResultsPipeline.run(input)).isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Class-B human score disagreement remains unresolved");
    }

    @Test void populatedReserveUsesTheSameReceiptArmMapping() throws Exception {
        Fixture fixture = fixture(false, true);
        ResultsPipeline.Result result = ResultsPipeline.run(fixture.input());
        JsonNode reserve = JSON.readTree(result.report().toFile()).path("reserveResults")
                .path("INTERACTIVE_QUIZ").path(TIP).get(0);
        assertThat(reserve.path("id").asText()).isEqualTo("reserve-tip");
        assertThat(reserve.path("baselineEvidence").asText()).isEqualTo("SCORED");
        assertThat(reserve.path("candidateEvidence").asText()).isEqualTo("SCORED");
        assertThat(reserve.path("candidateCritical").asBoolean()).isTrue();
    }

    @Test void reserveReceiptArmMustMatchMappedCandidateArm() throws Exception {
        Fixture fixture = fixture(false, true);
        ResultsPipeline.Input original = fixture.input();
        ResultsPipeline.ReserveInput reserve = original.reserves().get(TIP);
        ResultsPipeline.ReserveInput wrong = new ResultsPipeline.ReserveInput(reserve.baseline(),
                reserve.baseline(), reserve.classA(), reserve.classB());
        ResultsPipeline.Input input = new ResultsPipeline.Input(original.repoRoot(), original.freeze(),
                original.parameters(), original.mapping(), original.manifests(), original.baselineMini(),
                original.baseline41(), original.candidate(), original.classA(), original.classB(),
                Map.of(TIP, wrong), original.compatibilityReports(), original.headCommitTime(),
                original.ownerDispositions(), original.report(), original.triggerRecord(), original.synthetic());
        assertThatThrownBy(() -> ResultsPipeline.run(input)).isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Reserve receipt arm differs from mapped arms");
    }

    private Fixture fixture(boolean unpaired) throws Exception {
        return fixture(unpaired, false);
    }

    private Fixture fixture(boolean unpaired, boolean reserve) throws Exception {
        Path root = temp.resolve("repo");
        ObjectNode gates = (ObjectNode) JSON.readTree(Files.readAllBytes(GateTestFixture.SOURCE));
        ObjectNode workflows = (ObjectNode) gates.get("workflows");
        List<String> original = new ArrayList<>();
        workflows.fieldNames().forEachRemaining(original::add);
        original.stream().filter(name -> !WORKFLOWS.contains(name)).forEach(workflows::remove);
        WORKFLOWS.forEach(name -> ((ObjectNode) workflows.get(name)).put("primarySize", 1));
        ObjectNode routes = (ObjectNode) gates.get("routes");
        routes.set("INTERACTIVE_QUIZ", JSON.valueToTree(Map.of("margin", 0, "workflows", List.of(TIP))));
        routes.set("PAID_INTERACTIVE", JSON.valueToTree(Map.of("margin", 0, "workflows", List.of(COLLECTION))));
        routes.set("CRITIQUE", JSON.valueToTree(Map.of("margin", 0, "workflows", List.of(ASK, INTERVIEW))));
        gates.put("reserveSize", 1).put("t2FailCount", 1);
        byte[] parameterBytes = JSON.writeValueAsBytes(gates);
        Path parameter = write(temp.resolve("parameters.json"), parameterBytes);
        Files.writeString(temp.resolve("parameters.json.sha256"), FreezeRecord.sha256(parameterBytes));
        write(root.resolve(PARAMS), parameterBytes);
        byte[] mappingBytes = Files.readAllBytes(Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json"));
        Path mapping = write(temp.resolve("mapping.json"), mappingBytes);
        Files.writeString(temp.resolve("mapping.json.sha256"), FreezeRecord.sha256(mappingBytes));
        write(root.resolve(MAPPING), mappingBytes);
        Path prereg = write(root.resolve(PREREG), "draft");
        Path prompt = write(root.resolve(PROMPT), "prompt");
        Path aPrimary = write(temp.resolve("a-primary.jsonl"), "");
        Path aReserve = write(temp.resolve("a-reserve.jsonl"), "");
        List<String> manifestLines = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++)
            manifestLines.add(JSON.writeValueAsString(Map.of("fixtureId", IDS.get(i), "workflow", WORKFLOWS.get(i))));
        Path bPrimary = write(temp.resolve("b-primary.jsonl"), String.join("\n", manifestLines) + "\n");
        String reserveManifest = JSON.writeValueAsString(Map.of("fixtureId", "reserve-tip", "workflow", TIP));
        Path bReserve = write(temp.resolve("b-reserve.jsonl"), reserve ? reserveManifest + "\n" : "");
        Map<String, Path> manifests = Map.of("classA.primary", aPrimary, "classA.reserve", aReserve,
                "classB.primary", bPrimary, "classB.reserve", bReserve);
        Map<String, String> manifestHashes = new HashMap<>();
        for (Path path : manifests.values()) manifestHashes.put(path.toAbsolutePath().toString(), FreezeRecord.sha256(path));
        Map<String, String> suites = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) suites.put("SyntheticSuite" + i, WORKFLOWS.get(i % WORKFLOWS.size()));
        Map<String, BigDecimal> forecasts = new HashMap<>();
        WORKFLOWS.forEach(name -> forecasts.put(name, BigDecimal.ONE));
        FreezeRecord freeze = new FreezeRecord(HEAD, FreezeRecord.sha256(prereg), FreezeRecord.sha256(parameter),
                manifestHashes, Map.of(PROMPT, FreezeRecord.sha256(prompt)), Map.of(
                    MINI, new FreezeRecord.Arm("gpt-4.1-mini", List.of(TIP, ASK, INTERVIEW)),
                    PAID, new FreezeRecord.Arm("gpt-4.1", List.of(COLLECTION)),
                    LUNA, new FreezeRecord.Arm("gpt-6-luna", WORKFLOWS)),
                Map.of("INTERACTIVE_QUIZ", "gpt-4.1-mini", "PAID_INTERACTIVE", "gpt-4.1",
                        "CRITIQUE", "gpt-4.1-mini"), LocalDate.of(2026, 11, 17), Set.of("SCHEMA_OR_PARSE"),
                FreezeRecord.sha256(mapping), suites, forecasts);
        byte[] freezeBytes = JSON.writeValueAsBytes(freeze);
        Path freezeFile = write(temp.resolve("freeze.json"), freezeBytes);
        Files.writeString(temp.resolve("freeze.json.sha256"), FreezeRecord.sha256(freezeBytes));
        String freezeHash = FreezeRecord.sha256(freezeBytes);
        String caseSetHash = caseSetHash(manifestLines);
        Map<String, String> hashes = new HashMap<>();
        for (int i = 0; i < IDS.size(); i++) hashes.put(IDS.get(i), FreezeRecord.sha256(manifestLines.get(i).getBytes(StandardCharsets.UTF_8)));
        ResultsPipeline.ArmFiles mini = arm(MINI, "gpt-4.1-mini", List.of("tip", "ask", "interview"),
                hashes, freezeHash, caseSetHash);
        ResultsPipeline.ArmFiles paid = arm(PAID, "gpt-4.1", List.of("collection"), hashes, freezeHash, caseSetHash);
        ResultsPipeline.ArmFiles candidate = arm(LUNA, "gpt-6-luna", unpaired
                ? List.of("tip", "collection", "interview") : IDS, hashes, freezeHash, caseSetHash);
        Path exportRoot = temp.resolve("blind");
        Files.createDirectories(exportRoot);
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classBAll(exportRoot, false);
        if (unpaired) {
            Files.write(exportRoot.resolve("arm-key.jsonl"), Files.readAllLines(exportRoot.resolve("arm-key.jsonl"))
                    .stream().filter(line -> !line.contains("\"caseId\":\"ask\"")).toList());
        }
        List<String> scoreRows = new ArrayList<>();
        for (ClassBScoreAggregator.Key key : export.armKey())
            if (!unpaired || !key.caseId().equals("ask")) scoreRows.add(score(key, isCritical(key), isQuality(key)).toString());
        Path bScores = write(exportRoot.resolve("scores.jsonl"), String.join("\n", scoreRows) + "\n");
        ScoreEvidence.FilesA classA = new ScoreEvidence.FilesA(write(temp.resolve("a-key.jsonl"), ""),
                write(temp.resolve("a-scores.jsonl"), ""), write(temp.resolve("a-reviews.jsonl"), ""),
                write(temp.resolve("a-adjudications.jsonl"), ""), write(temp.resolve("a-exclusions.jsonl"), ""));
        ScoreEvidence.FilesB classB = new ScoreEvidence.FilesB(exportRoot.resolve("arm-key.jsonl"), bScores, SCORERS);
        Path suiteDir = temp.resolve("suites");
        Files.createDirectories(suiteDir);
        for (String name : suites.keySet()) write(suiteDir.resolve("TEST-com.studysnap.backend.service." + name + ".xml"),
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        Map<String, ResultsPipeline.ReserveInput> reserves = reserve
                ? Map.of(TIP, reserveInput(freezeHash, caseSetHash, reserveManifest, classA)) : Map.of();
        ResultsPipeline.Input input = new ResultsPipeline.Input(root, freezeFile, parameter, mapping, manifests,
                mini, paid, candidate, classA, classB, reserves, suiteDir, Instant.EPOCH, null,
                temp.resolve("report.json"), temp.resolve("trigger.json"), true);
        return new Fixture(input, export);
    }

    private ResultsPipeline.ReserveInput reserveInput(String freezeHash, String caseSetHash,
            String manifest, ScoreEvidence.FilesA classA) throws Exception {
        Path root = temp.resolve("reserve-blind");
        Files.createDirectories(root);
        var fixture = new ClassBCaseManifest.Case("reserve-tip", ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", true, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        ClassBBlindPacketExport.Export export = ClassBBlindPacketExport.export(List.of(fixture), List.of(
                new ClassBBlindPacketExport.Output("reserve-tip", ClassBScoreAggregator.Arm.BASELINE, "Advice one."),
                new ClassBBlindPacketExport.Output("reserve-tip", ClassBScoreAggregator.Arm.CANDIDATE, "Advice two.")),
                root.resolve("packets"), root.resolve("arm-key.jsonl"), 19L);
        List<String> scores = new ArrayList<>();
        for (ClassBScoreAggregator.Key key : export.armKey()) scores.add(score(key,
                key.arm() == ClassBScoreAggregator.Arm.CANDIDATE, false).toString());
        ScoreEvidence.FilesB classB = new ScoreEvidence.FilesB(root.resolve("arm-key.jsonl"),
                write(root.resolve("scores.jsonl"), String.join("\n", scores) + "\n"), SCORERS);
        String hash = FreezeRecord.sha256(manifest.getBytes(StandardCharsets.UTF_8));
        ResultsPipeline.ArmFiles b = reserveArm(MINI, "gpt-4.1-mini", hash, freezeHash, caseSetHash);
        ResultsPipeline.ArmFiles c = reserveArm(LUNA, "gpt-6-luna", hash, freezeHash, caseSetHash);
        return new ResultsPipeline.ReserveInput(b, c, classA, classB);
    }

    private ResultsPipeline.ArmFiles reserveArm(String arm, String model, String manifestHash,
            String freezeHash, String caseSetHash) throws Exception {
        String id = "reserve-tip";
        String line = JSON.writeValueAsString(new DeliveredOutputs.Line(id, TIP, arm, manifestHash, 0,
                CaseArmOutcome.State.COMPLETE, null, JSON.valueToTree("Useful study advice.")));
        Path output = write(temp.resolve(arm + "-reserve-delivered.jsonl"), line + "\n");
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(id, TIP, manifestHash,
                CaseArmOutcome.State.COMPLETE, true, true, null, new BigDecimal("0.01"), new BigDecimal("0.01"),
                List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, 200)), List.of("body"),
                FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        Stage1Receipt receipt = new Stage1Receipt(HEAD, freezeHash, arm, model, StageCapLedger.Stage.STAGE_2,
                Instant.now(), Instant.now(), 1, false, caseSetHash, List.of(row), Map.of(), FreezeRecord.sha256(output));
        Path path = temp.resolve(arm + "-reserve-receipt.json");
        receipt.write(path);
        return new ResultsPipeline.ArmFiles(path, output, null, null);
    }

    private ResultsPipeline.ArmFiles arm(String arm, String model, List<String> ids, Map<String, String> hashes,
            String freezeHash, String caseSetHash) throws Exception {
        List<String> outputLines = new ArrayList<>();
        List<Stage1Receipt.CaseRow> rows = new ArrayList<>();
        for (String id : ids) {
            String workflow = WORKFLOWS.get(IDS.indexOf(id));
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(id, workflow, arm, hashes.get(id), 0,
                    CaseArmOutcome.State.COMPLETE, null, JSON.valueToTree("Useful study advice.")));
            outputLines.add(line);
            rows.add(new Stage1Receipt.CaseRow(id, workflow, hashes.get(id), CaseArmOutcome.State.COMPLETE,
                    true, true, null, new BigDecimal("0.01"), new BigDecimal("0.01"),
                    List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, 200)), List.of("body"),
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))));
        }
        Path output = write(temp.resolve(arm + "-delivered.jsonl"), String.join("\n", outputLines) + "\n");
        Stage1Receipt receipt = new Stage1Receipt(HEAD, freezeHash, arm, model, StageCapLedger.Stage.STAGE_1,
                Instant.now(), Instant.now(), 1, false, caseSetHash, rows, Map.of(), FreezeRecord.sha256(output));
        Path path = temp.resolve(arm + "-receipt.json");
        receipt.write(path);
        return new ResultsPipeline.ArmFiles(path, output, null, null);
    }

    private static ObjectNode score(ClassBScoreAggregator.Key key, boolean critical, boolean quality) {
        ObjectNode row = JSON.createObjectNode();
        row.put("itemId", key.itemId()).put("workflow", key.workflow().name()).put("round", 1)
                .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                .put("taskFulfilled", !quality).put("grounded", true).put("actionable", true)
                .put("noCriticalMisleading", !critical).put("critical", critical)
                .put("borderline", false).put("adjudication", false);
        if (critical) row.put("reasonCode", "CRITICAL_MISLEADING");
        else if (quality) row.put("reasonCode", "TASK_NOT_FULFILLED");
        else row.putNull("reasonCode");
        return row;
    }

    private static boolean isCritical(ClassBScoreAggregator.Key key) {
        return key.caseId().equals("tip") && key.arm() == ClassBScoreAggregator.Arm.CANDIDATE;
    }
    private static boolean isQuality(ClassBScoreAggregator.Key key) {
        return key.caseId().equals("ask") && key.arm() == ClassBScoreAggregator.Arm.CANDIDATE
                || key.caseId().equals("collection") && key.arm() == ClassBScoreAggregator.Arm.BASELINE;
    }
    private static String baseline(String workflow) { return COLLECTION.equals(workflow) ? PAID : MINI; }

    private static String caseSetHash(List<String> lines) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            JsonNode row = JSON.readTree(line);
            text.append(row.path("fixtureId").asText()).append(':')
                    .append(FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))).append('\n');
        }
        return FreezeRecord.sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, RouteVerdictCalculator.CaseRecord> records(ResultsPipeline.Result result) {
        Map<String, RouteVerdictCalculator.CaseRecord> rows = new HashMap<>();
        result.caseRecords().values().forEach(cases -> cases.forEach(row -> rows.put(row.id(), row)));
        return rows;
    }

    private static ResultsPipeline.Input withClassB(ResultsPipeline.Input input, ScoreEvidence.FilesB classB) {
        return new ResultsPipeline.Input(input.repoRoot(), input.freeze(), input.parameters(), input.mapping(),
                input.manifests(), input.baselineMini(), input.baseline41(), input.candidate(), input.classA(),
                classB, input.reserves(), input.compatibilityReports(), input.headCommitTime(),
                input.ownerDispositions(), input.report(), input.triggerRecord(), input.synthetic());
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
}
