package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline, file-backed FRB integration. No scorer, model, network or private benchmark data. */
class FrbPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String HEAD = "a".repeat(40);
    private static final String PREREG = "docs/claude-plans/2026-11-12-v0182-llm-migration-preregistration.md";
    private static final String PROMPT = "backend/src/main/resources/prompts/study-pack-v1/system.txt";
    private static final String PARAMS = "backend/src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json";
    private static final String MAPPING = "backend/src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json";
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    private static final String SEALED = "2026-11-16T00:00:00Z";
    private static final String LOCKED = "2026-11-16T00:01:00Z";
    private static final String DECIDED = "2026-11-16T00:02:00Z";
    private static final String RESERVE_SEALED = "2026-11-16T00:03:00Z";
    private static final String RESERVE_LOCKED = "2026-11-16T00:04:00Z";
    private static final String OPENED = "2026-11-16T00:05:00Z";
    private static final String RESERVE_OPENED = "2026-11-16T00:06:00Z";
    private static final String[] A_IDS = {"a-one", "a-two"};
    private static final String EXCLUDED_A = "a-three";
    @TempDir Path temp;

    private record Export(AdjudicatedLoaded facts, ResultsPipeline.PrimaryExport provenance) {}
    private record BExport(AdjudicatedLoaded facts, ResultsPipeline.PrimaryExport primary,
                           ResultsPipeline.ReserveExport reserve, Path scores, Path lock, Path seal,
                           Path packets, ReserveDecisionRecord decision) {}
    record Fixture(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
                           Path primaryScores, Path primaryFindings) {}

    Fixture fixture(boolean drawn, int reserveCritical) throws Exception {
        return fixture(drawn, reserveCritical, 1, false, false);
    }

    Fixture fixture(boolean drawn, int reserveCritical, int classAWidth, boolean criticalA) throws Exception {
        return fixture(drawn, reserveCritical, classAWidth, criticalA, false);
    }

    Fixture fixture(boolean drawn, int reserveCritical, int classAWidth, boolean criticalA,
                    boolean unresolvedReserve) throws Exception {
        return fixture(drawn, reserveCritical, classAWidth, criticalA, unresolvedReserve, false);
    }

    Fixture fixture(boolean drawn, int reserveCritical, int classAWidth, boolean criticalA,
                    boolean unresolvedReserve, boolean excludeA) throws Exception {
        return fixture(drawn, reserveCritical, classAWidth, criticalA, unresolvedReserve, excludeA, 1);
    }

    Fixture fixture(boolean drawn, int reserveCritical, int classAWidth, boolean criticalA,
                    boolean unresolvedReserve, boolean excludeA, int classBPrimaryCount) throws Exception {
        List<String> primaryBIds = new ArrayList<>();
        primaryBIds.add("tip");
        for (int index = 1; index < classBPrimaryCount; index++) primaryBIds.add("tip-" + index);
        Path root = temp.resolve("synthetic-repo");
        ObjectNode gates = (ObjectNode) JSON.readTree(Files.readAllBytes(Path.of(
                "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json")));
        ObjectNode workflows = (ObjectNode) gates.get("workflows");
        List<String> all = new ArrayList<>();
        workflows.fieldNames().forEachRemaining(all::add);
        all.stream().filter(name -> !Set.of(NOTE, TIP).contains(name)).forEach(workflows::remove);
        ((ObjectNode) workflows.get(NOTE)).put("primarySize", excludeA ? 3 : 2);
        ((ObjectNode) workflows.get(TIP)).put("primarySize", classBPrimaryCount);
        ObjectNode routes = (ObjectNode) gates.get("routes");
        routes.removeAll();
        // This derived fixture has three primary cases, so its own consistent margin is zero.
        routes.set(ROUTE, JSON.valueToTree(Map.of("margin", 0, "workflows", List.of(NOTE, TIP))));
        ((ObjectNode) gates.get("frb")).set("classBWorkflows", JSON.valueToTree(List.of(TIP)));
        Path parameters = write(temp.resolve("parameters.json"), JSON.writeValueAsBytes(gates));
        detached(parameters);
        write(root.resolve(PARAMS), Files.readAllBytes(parameters));
        Path mapping = write(temp.resolve("mapping.json"), Files.readAllBytes(Path.of(
                "src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json")));
        detached(mapping);
        write(root.resolve(MAPPING), Files.readAllBytes(mapping));
        Path prereg = write(root.resolve(PREREG), "draft");
        Path prompt = write(root.resolve(PROMPT), "prompt");

        List<String> aRows = new ArrayList<>();
        for (String id : A_IDS) aRows.add(JSON.writeValueAsString(aCase(id, classAWidth)));
        if (excludeA) aRows.add(JSON.writeValueAsString(aCase(EXCLUDED_A, classAWidth)));
        Path aPrimary = write(temp.resolve("a-primary.jsonl"), String.join("\n", aRows) + "\n");
        Path aReserve = write(temp.resolve("a-reserve.jsonl"), "");
        List<String> bRows = new ArrayList<>();
        for (String id : primaryBIds)
            bRows.add(JSON.writeValueAsString(Map.of("fixtureId", id, "workflow", TIP)));
        Path bPrimary = write(temp.resolve("b-primary.jsonl"), String.join("\n", bRows) + "\n");
        List<String> reserveRows = new ArrayList<>();
        for (int i = 0; i < 30; i++) reserveRows.add(JSON.writeValueAsString(
                Map.of("fixtureId", "reserve-" + i, "workflow", TIP)));
        Path bReserve = write(temp.resolve("b-reserve.jsonl"), String.join("\n", reserveRows) + "\n");
        Map<String, Path> manifests = Map.of("classA.primary", aPrimary, "classA.reserve", aReserve,
                "classB.primary", bPrimary, "classB.reserve", bReserve);
        Map<String, String> pinned = new HashMap<>();
        for (Path file : manifests.values()) pinned.put(file.toAbsolutePath().toString(), FreezeRecord.sha256(file));
        Map<String, String> suites = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) suites.put("SyntheticSuite" + i, i % 2 == 0 ? NOTE : TIP);
        FreezeRecord freeze = new FreezeRecord(HEAD, FreezeRecord.sha256(prereg), FreezeRecord.sha256(parameters),
                pinned, Map.of(PROMPT, FreezeRecord.sha256(prompt)), Map.of(
                    MINI, new FreezeRecord.Arm("gpt-4.1-mini", List.of(NOTE)),
                    PAID, new FreezeRecord.Arm("gpt-4.1", List.of(TIP)),
                    LUNA, new FreezeRecord.Arm("gpt-6-luna", List.of(NOTE, TIP))),
                Map.of(ROUTE, "gpt-4.1-mini"), LocalDate.of(2026, 11, 17), Set.of("SCHEMA_OR_PARSE"),
                FreezeRecord.sha256(mapping), suites, Map.of(NOTE, BigDecimal.ONE, TIP, BigDecimal.ONE));
        Path freezeFile = write(temp.resolve("freeze.json"), JSON.writeValueAsBytes(freeze));
        detached(freezeFile);
        String freezeHash = FreezeRecord.sha256(freezeFile);
        Map<String, String> hashes = new HashMap<>();
        for (String line : aRows) hashes.put(JSON.readTree(line).path("caseId").asText(),
                FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        for (String line : bRows) hashes.put(JSON.readTree(line).path("fixtureId").asText(),
                FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        String caseSetHash = caseSetHash(aRows, bRows);
        List<String> aReceiptIds = new ArrayList<>(List.of(A_IDS));
        if (excludeA) aReceiptIds.add(EXCLUDED_A);
        ResultsPipeline.ArmFiles mini = receipt(MINI, "gpt-4.1-mini", aReceiptIds,
                hashes, freezeHash, caseSetHash, StageCapLedger.Stage.STAGE_1, "mini", classAWidth);
        ResultsPipeline.ArmFiles paid = receipt(PAID, "gpt-4.1", primaryBIds,
                hashes, freezeHash, caseSetHash, StageCapLedger.Stage.STAGE_1, "paid", classAWidth);
        List<String> candidateIds = new ArrayList<>(aReceiptIds);
        candidateIds.addAll(primaryBIds);
        ResultsPipeline.ArmFiles candidate = receipt(LUNA, "gpt-6-luna", candidateIds,
                hashes, freezeHash, caseSetHash, StageCapLedger.Stage.STAGE_1, "candidate", classAWidth);

        Export first = classA(temp.resolve("class-a-one"), A_IDS[0], classAWidth,
                excludeA ? EXCLUDED_A : null);
        Export second = classA(temp.resolve("class-a-two"), A_IDS[1], classAWidth, null);
        BExport primaryB = classBPrimary(temp.resolve("class-b-primary"), primaryBIds, drawn);
        Map<String, ResultsPipeline.ReserveArms> reserveArms = new HashMap<>();
        Map<String, ResultsPipeline.ReserveExport> reserveProvenance = new HashMap<>();
        List<ArmKeyAccess.LockBinding> reserveBindings = new ArrayList<>();
        AdjudicatedLoaded reserveFacts = null;
        if (drawn) {
            BExport reserveB = classBReserve(temp.resolve("class-b-reserve"), reserveCritical, unresolvedReserve);
            reserveProvenance.put(TIP, reserveB.reserve());
            reserveFacts = reserveB.facts();
            reserveBindings.add(new ArmKeyAccess.LockBinding(reserveB.reserve().lockForPrimaryOpening(),
                    reserveB.reserve().sealFile(), reserveB.reserve().findingsDirectory()));
            Map<String, String> reserveHashes = new HashMap<>();
            for (String line : reserveRows) reserveHashes.put(JSON.readTree(line).path("fixtureId").asText(),
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
            reserveArms.put(TIP, new ResultsPipeline.ReserveArms(
                    receipt(PAID, "gpt-4.1", new ArrayList<>(reserveHashes.keySet()),
                            reserveHashes, freezeHash, caseSetHash, StageCapLedger.Stage.STAGE_2, "reserve-paid", classAWidth),
                    receipt(LUNA, "gpt-6-luna", new ArrayList<>(reserveHashes.keySet()),
                            reserveHashes, freezeHash, caseSetHash, StageCapLedger.Stage.STAGE_2, "reserve-luna", classAWidth)));
        }
        // The decision and both reserve locks exist before any primary key is opened.
        List<ResultsPipeline.PrimaryExport> primaries = List.of(first.provenance(), second.provenance(), primaryB.primary());
        Map<Path, ArmKeyAccess.OpenedKey> opened = new HashMap<>();
        for (ResultsPipeline.PrimaryExport export : primaries) {
            List<ArmKeyAccess.LockBinding> bindings = new ArrayList<>();
            bindings.add(new ArmKeyAccess.LockBinding(export.lockFile(), export.sealFile(),
                    export.findingsDirectory()));
            bindings.addAll(reserveBindings);
            Path key = export.sealFile().getParent().resolve(export == primaryB.primary()
                    ? "arm-key.jsonl" : "key.jsonl");
            ArmKeyAccess.OpenedKey keyRows = ArmKeyAccess.open(key, export.sealFile(), bindings,
                    drawn ? Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE)
                            : Set.of(LockRecord.Kind.PRIMARY), export.accessLog(), OPENED, "FRB integration");
            opened.put(export.sealFile().getParent(), keyRows);
        }
        // Convert using the opened keys; this fixture does not re-open them.
        Path a1Root = first.provenance().sealFile().getParent();
        Path a2Root = second.provenance().sealFile().getParent();
        Path bRoot = primaryB.primary().sealFile().getParent();
        AdjudicatedLoaded a1 = convertA(a1Root, A_IDS[0], opened.get(a1Root), classAWidth, criticalA,
                excludeA ? EXCLUDED_A : null);
        AdjudicatedLoaded a2 = convertA(a2Root, A_IDS[1], opened.get(a2Root), classAWidth, criticalA, null);
        AdjudicatedLoaded bFacts = convertB(bRoot, false, Set.copyOf(primaryBIds), opened.get(bRoot));
        List<FrbEvidenceBundle.ReservePart> parts = new ArrayList<>();
        if (drawn) parts.add(new FrbEvidenceBundle.ReservePart(TIP, reserveFacts, reserveIds()));
        Set<String> frozenA = new LinkedHashSet<>(List.of(A_IDS));
        if (excludeA) frozenA.add(EXCLUDED_A);
        FrbEvidenceBundle bundle = FrbEvidenceBundle.assemble(List.of(a1, a2), bFacts, parts,
                frozenA, Set.copyOf(primaryBIds), primaryB.decision(), Set.of(TIP));
        Path suitesDir = temp.resolve("suites");
        Files.createDirectories(suitesDir);
        for (String name : suites.keySet()) write(suitesDir.resolve("TEST-com.studysnap.backend.service." + name + ".xml"),
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        ResultsPipeline.FrbRunInput run = new ResultsPipeline.FrbRunInput(root, freezeFile, parameters, mapping,
                manifests, mini, paid, candidate, Map.copyOf(reserveArms), suitesDir, Instant.EPOCH,
                dispositions(drawn, reserveCritical, primaryBIds), temp.resolve("frb-report.json"), true);
        ResultsPipeline.ReserveVerification check = new ResultsPipeline.ReserveVerification(primaryB.packets(),
                primaryB.seal(), primaryB.scores(), SCORERS, Set.of(TIP), primaryB.lock());
        ResultsPipeline.FrbInput frb = new ResultsPipeline.FrbInput(bundle,
                temp.resolve("decision.json"), check,
                new ResultsPipeline.ProvenanceLogs(primaries, Map.copyOf(reserveProvenance)));
        return new Fixture(run, frb, primaryB.scores(), primaryB.scores().getParent());
    }

    private Export classA(Path root, String id, int width, String excluded) throws Exception {
        Files.createDirectories(root);
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var item = new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                List.of(source), width, null, false, null);
        ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int index = 0; index < width; index++) questions.add(question.deepCopy());
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        cases.add(item);
        if (excluded != null) cases.add(aCase(excluded, width));
        var exported = ClassABlindPacketExport.export(cases, List.of(
                new ClassABlindPacketExport.Output(id, MINI, questions),
                new ClassABlindPacketExport.Output(id, LUNA, questions)), MINI, LUNA,
                root.resolve("packets"), root.resolve("key.jsonl"), 17L);
        Path seal = root.resolve("seal.json");
        ArmKeySeal.Seal sealed = ArmKeySeal.seal(root.resolve("key.jsonl"), root.resolve("packets"), seal, SEALED);
        Path finding = write(root.resolve("findings/human.jsonl"), "");
        Path lock = root.resolve("primary-lock.json");
        LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(), List.of(finding),
                sealed, seal, files -> {}, LOCKED);
        Path exclusions = exported.exclusions();
        return new Export(null, new ResultsPipeline.PrimaryExport(root.resolve("access.jsonl"), seal, lock,
                finding.getParent(), exclusions, FreezeRecord.sha256(exclusions)));
    }

    private AdjudicatedLoaded convertA(Path root, String id, ArmKeyAccess.OpenedKey opened,
                                       int width, boolean criticalA, String excluded) throws Exception {
        PairMap pairs = PairMap.fromPackets(root.resolve("packets"));
        Map<String, QuestionStatus> statuses = new HashMap<>();
        Map<String, Integer> indices = new HashMap<>();
        for (String blind : pairs.blindItemIds()) {
            String arm = opened.armOf(blind);
            int index = indices.merge(arm, 1, Integer::sum) - 1;
            boolean critical = criticalA && index < (arm.equals(LUNA) ? 2 : 1);
            String verdict = critical ? "K" : "C";
            statuses.put(blind, FinalStatus.of(ClassAAdjudicationFixture.direct(blind,
                    verdict, verdict, 0, critical ? "K" : null), ClassAScoreSchemaV2Test.MAPPING, false));
        }
        Path exclusions = root.resolve("key.jsonl.exclusions.jsonl");
        Set<String> frozen = excluded == null ? Set.of(id) : Set.of(id, excluded);
        Map<String, Integer> widths = new HashMap<>();
        for (String caseId : frozen) widths.put(caseId, width);
        return AdjudicatedConverter.classA(opened, statuses, pairs, Set.of(NOTE), frozen,
                exclusions, FreezeRecord.sha256(exclusions), widths);
    }

    private BExport classBPrimary(Path root, List<String> ids, boolean drawn) throws Exception {
        Files.createDirectories(root);
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (String id : ids) {
            cases.add(bCase(id, false));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        var export = ClassBBlindPacketExport.export(cases, outputs,
                root.resolve("packets"), root.resolve("arm-key.jsonl"), 17L);
        Path scores = writeScores(root.resolve("findings/scores.jsonl"), export,
                drawn ? Set.copyOf(ids) : Set.of(), 0);
        Path seal = root.resolve("seal.json");
        ArmKeySeal.Seal sealed = ArmKeySeal.seal(root.resolve("arm-key.jsonl"), root.resolve("packets"), seal, SEALED);
        Path lock = root.resolve("primary-lock.json");
        LockRecord primary = LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(),
                List.of(scores), sealed, seal, files -> {}, LOCKED);
        ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(scores, export.manifest(), SCORERS);
        List<ReserveDecisionRecord.WorkflowDecision> decisions = ReserveDecisionRecord.compute(
                root.resolve("packets"), validated, ClassAScoreSchemaV2Test.MAPPING, Set.of(TIP));
        ReserveDecisionRecord record = ReserveDecisionRecord.create(temp.resolve("decision.json"),
                decisions, primary, lock, scores, root.resolve("packets"), seal, DECIDED);
        return new BExport(null, new ResultsPipeline.PrimaryExport(root.resolve("access.jsonl"), seal,
                lock, scores.getParent(), null, null), null, scores, lock, seal, root.resolve("packets"), record);
    }

    private BExport classBReserve(Path root, int criticalCount, boolean unresolved) throws Exception {
        Files.createDirectories(root);
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "reserve-" + i;
            cases.add(bCase(id, true));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        var export = ClassBBlindPacketExport.export(cases, outputs, root.resolve("packets"),
                root.resolve("arm-key.jsonl"), 19L);
        Path scores = writeScores(root.resolve("findings/scores.jsonl"), export, Set.of(), criticalCount,
                unresolved);
        Path seal = root.resolve("seal.json");
        ArmKeySeal.Seal sealed = ArmKeySeal.seal(root.resolve("arm-key.jsonl"), root.resolve("packets"),
                seal, RESERVE_SEALED);
        Path forPrimary = root.resolve("reserve-lock.json");
        Path forReserve = root.resolve("primary-lock.json");
        LockRecord.create(forPrimary, LockRecord.Kind.RESERVE, sealed.workflowStems(), List.of(scores),
                sealed, seal, files -> {}, RESERVE_LOCKED);
        LockRecord.create(forReserve, LockRecord.Kind.PRIMARY, sealed.workflowStems(), List.of(scores),
                sealed, seal, files -> {}, RESERVE_LOCKED);
        Path access = root.resolve("access.jsonl");
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(root.resolve("arm-key.jsonl"), seal,
                List.of(new ArmKeyAccess.LockBinding(forReserve, seal, scores.getParent())),
                Set.of(LockRecord.Kind.PRIMARY), access, RESERVE_OPENED, "FRB reserve integration");
        AdjudicatedLoaded facts = convertB(root, true, reserveIds(), opened);
        return new BExport(facts, null, new ResultsPipeline.ReserveExport(TIP, access, seal,
                forPrimary, forReserve, scores.getParent()), scores, forReserve, seal,
                root.resolve("packets"), null);
    }

    private AdjudicatedLoaded convertB(Path root, boolean reserve, Set<String> frozen,
                                        ArmKeyAccess.OpenedKey opened) throws Exception {
        ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(root.resolve("findings/scores.jsonl"),
                ReserveDecisionRecord.manifestFromPackets(root.resolve("packets")), SCORERS);
        return ClassBAdjudicatedConverter.classB(opened,
                reserve ? ClassBAdjudicatedConverter.ExportKind.RESERVE
                        : ClassBAdjudicatedConverter.ExportKind.PRIMARY,
                validated, ClassAScoreSchemaV2Test.MAPPING, PairMap.fromPackets(root.resolve("packets")),
                Set.of(TIP), frozen, new ClassBAdjudicatedConverter.ArmLabels(LUNA, ignored -> PAID));
    }

    private static ClassBCaseManifest.Case bCase(String id, boolean reserve) {
        return new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", reserve, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
    }

    private static Path writeScores(Path file, ClassBBlindPacketExport.Export export,
                                    Set<String> criticalCases, int reserveCritical) throws Exception {
        return writeScores(file, export, criticalCases, reserveCritical, false);
    }

    private static Path writeScores(Path file, ClassBBlindPacketExport.Export export,
                                    Set<String> criticalCases, int reserveCritical,
                                    boolean unresolvedReserve) throws Exception {
        List<String> rows = new ArrayList<>();
        for (ClassBScoreAggregator.Key key : export.armKey()) {
            if (unresolvedReserve && key.caseId().equals("reserve-0")
                    && key.arm() == ClassBScoreAggregator.Arm.CANDIDATE) continue;
            boolean critical = key.arm() == ClassBScoreAggregator.Arm.CANDIDATE
                    && (criticalCases.contains(key.caseId()) || key.caseId().startsWith("reserve-")
                    && Integer.parseInt(key.caseId().substring(8)) < reserveCritical);
            ObjectNode row = JSON.createObjectNode();
            row.put("itemId", key.itemId()).put("workflow", TIP).put("round", 1)
                    .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", !critical).put("critical", critical)
                    .put("borderline", false).put("adjudication", false);
            if (critical) row.put("reasonCode", "CRITICAL_MISLEADING");
            else row.putNull("reasonCode");
            rows.add(row.toString());
        }
        return write(file, String.join("\n", rows) + "\n");
    }

    private ResultsPipeline.ArmFiles receipt(String arm, String model, List<String> ids,
            Map<String, String> rowHashes, String freezeHash, String caseSetHash,
            StageCapLedger.Stage stage, String suffix, int classAWidth) throws Exception {
        List<String> outputLines = new ArrayList<>();
        List<Stage1Receipt.CaseRow> rows = new ArrayList<>();
        for (String id : ids) {
            String workflow = id.startsWith("a-") ? NOTE : TIP;
            JsonNode content;
            if (id.startsWith("a-")) {
                ArrayNode questions = JSON.createArrayNode();
                for (int index = 0; index < classAWidth; index++)
                    questions.addObject().put("question", "Question").put("answer", "Answer");
                content = questions;
            } else content = JSON.valueToTree("Useful study advice.");
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(id, workflow, arm,
                    rowHashes.get(id), 0, CaseArmOutcome.State.COMPLETE, null, content));
            outputLines.add(line);
            rows.add(new Stage1Receipt.CaseRow(id, workflow, rowHashes.get(id),
                    CaseArmOutcome.State.COMPLETE, true, true, null,
                    new BigDecimal("0.01"), new BigDecimal("0.01"),
                    List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, 200)), List.of("body"),
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))));
        }
        Path output = write(temp.resolve(suffix + "-delivered.jsonl"), String.join("\n", outputLines) + "\n");
        Stage1Receipt receipt = new Stage1Receipt(HEAD, freezeHash, arm, model, stage,
                Instant.now(), Instant.now(), 1, false, caseSetHash, rows, Map.of(), FreezeRecord.sha256(output));
        Path file = temp.resolve(suffix + "-receipt.json");
        receipt.write(file);
        return new ResultsPipeline.ArmFiles(file, output, null, null);
    }

    private static ClassACaseManifest.Case aCase(String id, int width) {
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE,
                "Boundary", List.of("membrane"), List.of("cells"), "Biology", "Biology", null,
                LearnerLevel.COLLEGE);
        return new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                List.of(source), width, null, false, null);
    }

    private static String caseSetHash(List<String> a, List<String> b) throws Exception {
        StringBuilder signature = new StringBuilder();
        for (String line : a) signature.append(JSON.readTree(line).path("caseId").asText()).append(':')
                .append(FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))).append('\n');
        for (String line : b) signature.append(JSON.readTree(line).path("fixtureId").asText()).append(':')
                .append(FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8))).append('\n');
        return FreezeRecord.sha256(signature.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> reserveIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < 30; i++) ids.add("reserve-" + i);
        return ids;
    }

    private Path dispositions(boolean drawn, int reserveCritical, List<String> primaryBIds) throws Exception {
        if (!drawn) return null;
        List<RouteVerdictCalculator.OwnerDisposition> rows = new ArrayList<>();
        for (String id : primaryBIds) rows.add(new RouteVerdictCalculator.OwnerDisposition(TIP, id,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION, "Accepted mitigation"));
        for (int i = 0; i < reserveCritical; i++) rows.add(new RouteVerdictCalculator.OwnerDisposition(TIP,
                "reserve-" + i, RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION, "Accepted mitigation"));
        return write(temp.resolve("dispositions.json"), JSON.writeValueAsBytes(rows));
    }

    private static Path write(Path file, String value) throws Exception {
        return write(file, value.getBytes(StandardCharsets.UTF_8));
    }
    private static Path write(Path file, byte[] value) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, value);
        return file;
    }
    private static void detached(Path file) throws Exception {
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file));
    }

    @Test void allPassAndTwoClassAExportsReachFrbCalculator() throws Exception {
        Fixture fixture = fixture(false, 0);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(result.caseRecords().get(ROUTE)).hasSize(3);
        assertThat(result.triggerRecord()).isNull();
        assertThat(result.triggers()).isEmpty();
        assertThat(JSON.readTree(result.report().toFile()).path("frb").isObject()).isTrue();
    }

    @Test void drawnReservePassesAndRecordsItsDecision() throws Exception {
        Fixture fixture = fixture(true, 0);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(JSON.readTree(result.report().toFile()).path("frb")
                .path("reserveDrawnByWorkflow").path(TIP).asBoolean()).isTrue();
    }

    @Test void drawnReserveCanRejectThroughPrimaryPlusReserveGate() throws Exception {
        Fixture fixture = fixture(true, 8);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
    }

    @Test void reserveReceiptArmsMustEqualMappedBaselineAndCandidate() throws Exception {
        Fixture fixture = fixture(true, 0);
        ResultsPipeline.FrbRunInput old = fixture.run();
        ResultsPipeline.ReserveArms pair = old.reserves().get(TIP);
        ResultsPipeline.FrbRunInput swapped = new ResultsPipeline.FrbRunInput(old.repoRoot(), old.freeze(),
                old.parameters(), old.mapping(), old.manifests(), old.baselineMini(), old.baseline41(),
                old.candidate(), Map.of(TIP, new ResultsPipeline.ReserveArms(pair.candidate(), pair.baseline())),
                old.compatibilityReports(), old.headCommitTime(), old.ownerDispositions(), old.report(),
                old.synthetic());
        assertThatThrownBy(() -> ResultsPipeline.runFrb(swapped, fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("mapped arms");
    }

    @Test void classACriticalQuestionCountsAndWidthsReachCaseRecords() throws Exception {
        Fixture fixture = fixture(false, 0, 3, true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        RouteVerdictCalculator.CaseRecord first = result.caseRecords().get(ROUTE).stream()
                .filter(row -> row.id().equals(A_IDS[0])).findFirst().orElseThrow();
        assertThat(first.requestedQuestions()).isEqualTo(3);
        assertThat(first.baseline().criticalQuestions()).isEqualTo(1);
        assertThat(first.candidate().criticalQuestions()).isEqualTo(2);
        assertThat(fixture.frb().bundle().requestedQuestionsByCaseId()).containsEntry(A_IDS[0], 3);
    }

    @Test void eachSuccessfulRecheckCarriesTheSameThreeQuestionCaseFact() throws Exception {
        Fixture fixture = fixture(false, 0, 3, true);
        ResultsPipeline.FrbRunInput original = fixture.run();
        ObjectNode receipt = (ObjectNode) JSON.readTree(original.candidate().receipt().toFile());
        ObjectNode row = (ObjectNode) receipt.path("cases").get(0);
        String rowHash = row.path("manifestRowSha256").asText();
        row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                .put("failureClass", "SCHEMA_OR_PARSE");
        ArrayNode calls = (ArrayNode) row.path("llmCalls");
        calls.removeAll();
        calls.addObject().put("outcome", "SCHEMA_OR_PARSE").put("attempt", 1)
                .put("latencyMs", 1).put("httpStatus", 200);
        List<String> outputs = Files.readAllLines(original.candidate().delivered());
        JsonNode successfulContent = JSON.readTree(outputs.getFirst()).path("content");
        String failed = JSON.writeValueAsString(new DeliveredOutputs.Line(A_IDS[0], NOTE, LUNA,
                rowHash, 0, CaseArmOutcome.State.FAILED, "SCHEMA_OR_PARSE", null));
        row.put("deliveredOutputSha256", FreezeRecord.sha256(failed.getBytes(StandardCharsets.UTF_8)));
        outputs.set(0, failed);
        Files.write(original.candidate().delivered(), outputs);
        receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(original.candidate().delivered()));
        JSON.writeValue(original.candidate().receipt().toFile(), receipt);
        Path directory = temp.resolve("rechecks");
        List<RecheckReceipt.Repeat> repeats = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            String line = JSON.writeValueAsString(new DeliveredOutputs.Line(A_IDS[0], NOTE, LUNA,
                    rowHash, index, CaseArmOutcome.State.COMPLETE, null, successfulContent));
            Path file = write(directory.resolve(A_IDS[0]).resolve("recheck-" + index)
                    .resolve("delivered-outputs.jsonl"), line + "\n");
            repeats.add(new RecheckReceipt.Repeat(index, null, RouteVerdictCalculator.Evidence.SCORED,
                    null, FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)), FreezeRecord.sha256(file)));
        }
        Path recheckReceipt = directory.resolve("receipt.json");
        new RecheckReceipt(HEAD, FreezeRecord.sha256(original.freeze()), LUNA, "gpt-6-luna",
                Set.of("SCHEMA_OR_PARSE"), List.of(new RecheckReceipt.Case(A_IDS[0], "SCHEMA_OR_PARSE", repeats)),
                receipt.path("caseSetSha256").asText()).write(recheckReceipt);
        ResultsPipeline.ArmFiles candidate = new ResultsPipeline.ArmFiles(original.candidate().receipt(),
                original.candidate().delivered(), recheckReceipt, directory);
        ResultsPipeline.FrbRunInput changed = new ResultsPipeline.FrbRunInput(original.repoRoot(), original.freeze(),
                original.parameters(), original.mapping(), original.manifests(), original.baselineMini(),
                original.baseline41(), candidate, original.reserves(), original.compatibilityReports(),
                original.headCommitTime(), original.ownerDispositions(), original.report(), original.synthetic());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(changed, fixture.frb());
        RouteVerdictCalculator.CaseRecord first = result.caseRecords().get(ROUTE).stream()
                .filter(item -> item.id().equals(A_IDS[0])).findFirst().orElseThrow();
        assertThat(first.candidate().rechecks()).hasSize(2);
        assertThat(first.candidate().rechecks()).allSatisfy(check -> {
            assertThat(check.criticalQuestions()).isEqualTo(2);
            assertThat(check.unresolvedQuestions()).isZero();
        });
    }

    @Test void overDeliveredQuestionCountsRefuseWithCaseAndArm() throws Exception {
        Fixture fixture = fixture(false, 0);
        FrbEvidenceBundle original = fixture.frb().bundle();
        Map<String, AdjudicatedFacts> facts = new HashMap<>(original.evidence().byCaseArm());
        facts.put(A_IDS[0] + "\u0000" + LUNA,
                new AdjudicatedFacts(2, 0, 0, 0, 0, 0, 0, 0, null));
        AdjudicatedLoaded changedFacts = new AdjudicatedLoaded(facts, original.evidence().excludedCaseIds(),
                original.evidence().requestedQuestionsByCaseId());
        FrbEvidenceBundle changedBundle = new FrbEvidenceBundle(changedFacts,
                original.requestedQuestionsByCaseId(), original.reserveDrawnByWorkflow(), Set.of());
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(changedBundle,
                fixture.frb().reserveRecordFile(), fixture.frb().verification(), fixture.frb().provenance());
        assertThatThrownBy(() -> ResultsPipeline.runFrb(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining(A_IDS[0] + "/" + LUNA);
    }

    @Test void excludedClassACaseStaysUnresolvedAtItsFullWidth() throws Exception {
        Fixture fixture = fixture(false, 0, 1, false, false, true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        RouteVerdictCalculator.CaseRecord excluded = result.caseRecords().get(ROUTE).stream()
                .filter(row -> row.id().equals(EXCLUDED_A)).findFirst().orElseThrow();
        assertThat(excluded.baseline().evidence()).isEqualTo(RouteVerdictCalculator.Evidence.MISSING);
        assertThat(excluded.baseline().unresolvedQuestions()).isEqualTo(1);
        assertThat(excluded.candidate().unresolvedQuestions()).isEqualTo(1);
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void unresolvedReserveFindingNeverBecomesResolvedPass() throws Exception {
        Fixture fixture = fixture(true, 0, 1, false, true);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        JsonNode reserve = JSON.readTree(result.report().toFile()).path("reserveResults")
                .path(ROUTE).path(TIP);
        JsonNode unresolved = null;
        for (JsonNode row : reserve) if (row.path("id").asText().equals("reserve-0")) unresolved = row;
        assertThat(unresolved).isNotNull();
        assertThat(unresolved.path("candidateEvidence").asText()).isEqualTo("UNSCORABLE");
        assertThat(JSON.readTree(result.report().toFile()).path("deadlineClassification")
                .path(ROUTE).asText()).isEqualTo("RETURN_TO_OWNER");
    }

    @Test void drawnReserveWithPrimaryReceiptGapReportsCauseAndR7Fallback() throws Exception {
        Fixture fixture = fixture(true, 0);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.run().candidate().receipt().toFile());
        for (JsonNode row : receipt.path("cases"))
            if (row.path("id").asText().equals("tip"))
                ((ObjectNode) row.path("llmCalls").get(0)).put("outcome", "UNMETERED");
        JSON.writeValue(fixture.run().candidate().receipt().toFile(), receipt);
        Files.delete(fixture.run().ownerDispositions());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        JsonNode report = JSON.readTree(result.report().toFile());
        assertThat(report.path("routes").path(ROUTE).path("workflows").path(TIP).path("frb")
                .path("unresolvedReasonsByCaseId").path("tip").asText()).contains("UNMETERED");
        assertThat(report.path("deadlineClassification").path(ROUTE).asText())
                .isEqualTo("RETURN_TO_OWNER");
    }

    @Test void independentlyRejectingPrimaryGateSurvivesReceiptGapWithDrawnReserve() throws Exception {
        Fixture fixture = fixture(true, 0, 1, false, false, false, 9);
        ObjectNode receipt = (ObjectNode) JSON.readTree(fixture.run().candidate().receipt().toFile());
        for (JsonNode row : receipt.path("cases"))
            if (row.path("id").asText().equals("tip"))
                ((ObjectNode) row.path("llmCalls").get(0)).put("outcome", "UNMETERED");
        JSON.writeValue(fixture.run().candidate().receipt().toFile(), receipt);
        ArrayNode dispositions = (ArrayNode) JSON.readTree(fixture.run().ownerDispositions().toFile());
        for (int index = 0; index < dispositions.size(); index++)
            if (dispositions.get(index).path("caseId").asText().equals("tip")) {
                dispositions.remove(index);
                break;
            }
        JSON.writeValue(fixture.run().ownerDispositions().toFile(), dispositions);
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        assertThat(result.routes().get(ROUTE).failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
    }

    @Test void undisposedCandidateOnlyCriticalKeepsR7ReturnToOwnerToday() throws Exception {
        Fixture fixture = fixture(true, 0);
        Files.delete(fixture.run().ownerDispositions());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        assertThat(result.routes().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(JSON.readTree(result.report().toFile()).path("deadlineClassification")
                .path(ROUTE).asText()).isEqualTo("RETURN_TO_OWNER");
    }

    @Test void classBUsablePairRequiresBothMappedFacts() throws Exception {
        Fixture fixture = fixture(false, 0);
        FrbEvidenceBundle original = fixture.frb().bundle();
        Map<String, AdjudicatedFacts> facts = new HashMap<>(original.evidence().byCaseArm());
        facts.remove("tip\u0000" + LUNA);
        AdjudicatedLoaded changedFacts = new AdjudicatedLoaded(facts, original.evidence().excludedCaseIds(),
                original.evidence().requestedQuestionsByCaseId());
        FrbEvidenceBundle changedBundle = new FrbEvidenceBundle(changedFacts,
                original.requestedQuestionsByCaseId(), original.reserveDrawnByWorkflow(), Set.of());
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(changedBundle,
                fixture.frb().reserveRecordFile(), fixture.frb().verification(), fixture.frb().provenance());
        assertThatThrownBy(() -> ResultsPipeline.runFrb(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("Missing FRB class-B fact");
    }

    @Test void awaitingReserveRefusesAtPipelineEntryPoint() throws Exception {
        Fixture fixture = fixture(true, 0);
        FrbEvidenceBundle old = fixture.frb().bundle();
        FrbEvidenceBundle changedBundle = new FrbEvidenceBundle(old.evidence(),
                old.requestedQuestionsByCaseId(), old.reserveDrawnByWorkflow(), Set.of(TIP));
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(changedBundle,
                fixture.frb().reserveRecordFile(), fixture.frb().verification(), fixture.frb().provenance());
        assertThatThrownBy(() -> ResultsPipeline.runFrb(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining(TIP);
    }

    @Test void alteredPrimaryScoresCannotBeUsedAsReserveScores() throws Exception {
        Fixture fixture = fixture(true, 0);
        Files.writeString(fixture.primaryScores(), "{}\n", java.nio.file.StandardOpenOption.APPEND);
        assertThatThrownBy(() -> ResultsPipeline.runFrb(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void noKeyBearingRunFrbInputOrParameter() throws Exception {
        for (Class<?> type : List.of(ResultsPipeline.FrbRunInput.class, ResultsPipeline.FrbInput.class))
            for (RecordComponent component : type.getRecordComponents()) {
                assertThat(component.getName().toLowerCase()).doesNotContain("key");
                assertThat(component.getType()).isNotEqualTo(ScoreEvidence.FilesA.class)
                        .isNotEqualTo(ScoreEvidence.FilesB.class);
            }
        for (var method : ResultsPipeline.class.getDeclaredMethods()) {
            if (!method.getName().equals("runFrb")) continue;
            for (var parameter : method.getParameters()) {
                assertThat(parameter.getName().toLowerCase()).doesNotContain("key");
                assertThat(parameter.getType()).isNotEqualTo(ScoreEvidence.FilesA.class)
                        .isNotEqualTo(ScoreEvidence.FilesB.class);
            }
            assertThat(method.getParameterTypes()).containsExactly(ResultsPipeline.FrbRunInput.class,
                    ResultsPipeline.FrbInput.class);
        }
    }

    @Test void legacyReportJsonHasNoFrbKey() throws Exception {
        Fixture fixture = fixture(false, 0);
        ResultsPipeline.FrbRunInput frb = fixture.run();
        ObjectNode parameters = (ObjectNode) JSON.readTree(frb.parameters().toFile());
        parameters.remove("frb");
        Path legacyParameters = write(temp.resolve("legacy-parameters.json"), JSON.writeValueAsBytes(parameters));
        detached(legacyParameters);
        write(frb.repoRoot().resolve(PARAMS), Files.readAllBytes(legacyParameters));
        FreezeRecord old = FreezeRecord.load(frb.freeze());
        FreezeRecord changed = new FreezeRecord(old.gitCommit(), old.preregistrationSha256(),
                FreezeRecord.sha256(legacyParameters), old.manifests(), old.prompts(), old.arms(),
                old.baselineModelsByRoute(), old.ownerApprovalDate(), old.recheckEligibleFailureClasses(),
                old.evidenceMappingSha256(), old.compatibilitySuites(), old.costForecastUsdPerDeliveredCase());
        write(frb.freeze(), JSON.writeValueAsBytes(changed));
        detached(frb.freeze());
        String newFreezeHash = FreezeRecord.sha256(frb.freeze());
        for (ResultsPipeline.ArmFiles arm : List.of(frb.baselineMini(), frb.baseline41(), frb.candidate())) {
            ObjectNode receipt = (ObjectNode) JSON.readTree(arm.receipt().toFile());
            receipt.put("freezeSha256", newFreezeHash);
            JSON.writeValue(arm.receipt().toFile(), receipt);
        }
        Path aKey = write(temp.resolve("legacy-a-key.jsonl"), "");
        Path aScores = write(temp.resolve("legacy-a-scores.jsonl"), "");
        Path aReviews = write(temp.resolve("legacy-a-reviews.jsonl"), "");
        Path aAdjudications = write(temp.resolve("legacy-a-adjudications.jsonl"), "");
        Path aExclusions = write(temp.resolve("legacy-a-exclusions.jsonl"), "");
        Path bRoot = temp.resolve("class-b-primary");
        ResultsPipeline.Input legacy = new ResultsPipeline.Input(frb.repoRoot(), frb.freeze(), legacyParameters,
                frb.mapping(), frb.manifests(), frb.baselineMini(), frb.baseline41(), frb.candidate(),
                new ScoreEvidence.FilesA(aKey, aScores, aReviews, aAdjudications, aExclusions),
                new ScoreEvidence.FilesB(bRoot.resolve("arm-key.jsonl"), fixture.primaryScores(), SCORERS),
                Map.of(), frb.compatibilityReports(), frb.headCommitTime(), null,
                temp.resolve("legacy-report.json"), temp.resolve("legacy-trigger.json"), true);
        ResultsPipeline.Result result = ResultsPipeline.run(legacy);
        assertThat(JSON.readTree(result.report().toFile()).has("frb")).isFalse();
    }
}
