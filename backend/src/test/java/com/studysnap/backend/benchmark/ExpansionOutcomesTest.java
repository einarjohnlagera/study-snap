package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Synthetic freeze, packet, finding, seal and lock helpers copied from ClassAEvidenceAssemblerTest;
 * the freeze/manifest seed is copied from FrbPipelineTest.Fixture. */
class ExpansionOutcomesTest {
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String CLOCK = "2026-11-16T00:00:30Z";
    private static final String PRIMARY = "classA.primary";
    private static final String PLAN = "classA.spotCheckPlan";
    private static final String BIOLOGY = "Biology";
    private static final String PACKETS = "packets";
    private static final String CLEAN = "C";
    private static final String CRITICAL = "K";
    private static final String CAP_FIELD = "capMinutes";
    private static final String STOP_FIELD = "stopReason";
    private static final String UNREVIEWED_FIELD = "unreviewedPairsByStem";
    private static final String DECISIONS_FIELD = "decisions";
    private static final String STOP_VALUE = "SECOND_DOUBLE_MISS";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String PLAN_STEM = "PLAN_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String FREEZE_HASH = "freezeSha256";
    private static final String PARAMETERS_HASH = "parametersSha256";
    private static final String SEAL_HASH = "sealSha256";
    private static final String DRAW_HASH = "drawLogSha256";
    private static final String APPROVED_FIELD = "approvedLargerCap";
    private static final String DECLINED_FIELD = "declinedOrExhausted";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String MANIFESTS_FIELD = "manifests";
    private static final String DECISION_PREFIX = "decision-";
    private static final String SOURCE_TITLE = "Cells";
    private static final String SOURCE_BOUNDARY = "Boundary";
    private static final String SOURCE_MEMBRANE = "membrane";
    private static final String SOURCE_CELLS = "cells";
    private static final String QUESTION_FORMAT = "MCQ";
    private static final String SYNTHETIC = "synthetic";
    private static final String ARMS_FIELD = "arms";
    private static final String WORKFLOWS_FIELD = "workflows";
    private static final String KEY_FILE = "key.jsonl";
    private static final String REGISTRY_START = "{\"seed\":\"synthetic-seed-123456789\",\"streams\":[";
    private static final String REGISTRY_WORKFLOW_START = "{\"workflow\":\"";
    private static final String REGISTRY_EXPANSION_END = "\",\"purpose\":\"EXPANSION\",\"rounds\":1}]}";
    private static final String ACCESS_LOG_FILE = "access.jsonl";
    private static final String HASH_SUFFIX = ".sha256";
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";
    private static final String SCHEMA_FIELD = "schema";
    private static final String DECISION_DIGEST_ROLE = "classA.expansionDecision";
    private static final String SAFETY_HEAD = "safety";
    private static final String QUOTE = "\"";
    private static final String ARRAY_FIELD_SUFFIX = "\":[";
    private static final String FOREIGN_ITEM = "foreign";
    private static final String STATUS_MISMATCH = "Caller statuses differ";
    private static final String TRIGGER_FIELD = "trigger";
    private static final String SINGLE_SPACE = " ";
    private static final String COMPLETE = "COMPLETE_NO_SECOND_DOUBLE_MISS";
    private static final String EXHAUSTED = "INCONCLUSIVE_WORKLOAD_EXHAUSTED";
    private static final String UNRELIABLE = "INCONCLUSIVE_SCORER_UNRELIABLE";
    private static final String BOARD_WORKFLOW = "BOARD_EXAM";
    private static final String NOT_TRIGGERED = "NOT_TRIGGERED";
    private static final String EXCLUDED_CASE = "excluded-frozen-case";
    private static final String MISSING_EXPANSION_CAUSE = "EXPANSION_OUTCOME_NOT_SUPPLIED";
    private static final String CENSUS_COVERAGE_ERROR = "Census does not cover";
    private static final String PLANNING_NOTE_TEXT = "planning figure used; measured median not bound";
    private static final String PLAN_CASE = "decision-plan";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String QUESTION_COUNT_FIELD = "questionCount";
    private static final String COST_FORECAST_FIELD = "costForecastUsdPerDeliveredCase";
    private static final String COMPATIBILITY_SUITES_FIELD = "compatibilitySuites";
    private static final String SPOT_PLAN_FILE = "spot-plan.json";
    private static final String SUITE_ONE = "SyntheticSuite1";
    private static final String FINDINGS_DIR = "findings";
    private static final String REGISTRY_FILE = "seeds.json";
    private static final String DRAW_LOG_FILE = "draws.jsonl";
    private static final String P_FILE = "p.jsonl";
    private static final String S_FILE = "s.jsonl";
    private static final String H_FILE = "human.jsonl";
    private static final String LOCK_FILE = "lock.json";
    private static final String SECOND_MISS_REASON = "continued past a second double miss";
    private static final String JSON_PRIORITY_START = "\",\"stemPriority\":[\"";
    private static final String JSON_INITIAL_ALLOCATION = "\"],\"initialAllocation\":{\"";
    private static final String JSON_SPOT_ROUNDS = "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},";
    private static final String JSON_STEMS_START = "{\"stems\":[{\"stem\":\"";
    private static final String JSON_COUNT = "\",\"count\":";
    private static final String JSON_EXPANSION_START = "\"expansion\":[{\"workflow\":\"";
    private static final String JSON_THREE_ALLOCATION_END = "\":3}}";
    private static final String JSON_SPOT_ONE_END = "\",\"count\":1},";
    private static final String JSON_STEM_START = "{\"stem\":\"";
    private static final String JSON_ONE_ALLOCATION = "\":1,\"";
    private static final String DIRECT_GROUP = "DIRECT";
    private static final String CALCULATED_ROUTE_PREFIX = "calculated-route-";
    private static final String PRIMARY_SIZE_FIELD = "primarySize";
    @TempDir Path temp;

    private record Fixture(ClassASources sources, PairMap pairs, List<String> drawn, String trigger,
            Path findingsDir, List<Path> findings, Path lock, Path seal, Path root, String stem) {
        Path decisionFile() { return findingsDir.resolve(ExpansionDecision.FILE_NAME); }
    }

    private Fixture fixture(int cases) throws Exception { return fixture(cases, false); }

    private Fixture fixture(int cases, boolean mandatory) throws Exception {
        return fixture(cases, mandatory, STEM, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, 2, 1);
    }

    private Fixture fixture(int cases, boolean mandatory, String stem,
            ClassACaseManifest.Workflow workflow, int questionCount) throws Exception {
        return fixture(cases, mandatory, stem, workflow, questionCount, 1);
    }

    private Fixture fixture(int cases, boolean mandatory, String stem,
            ClassACaseManifest.Workflow workflow, int questionCount, int spotCount) throws Exception {
        return fixture(cases, mandatory, stem, workflow, questionCount, spotCount, false);
    }

    private Fixture fixture(int cases, boolean mandatory, String stem,
            ClassACaseManifest.Workflow workflow, int questionCount, int spotCount,
            boolean includePlanWorkflow) throws Exception {
        Path base = Files.createTempDirectory(temp, DECISION_PREFIX);
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        ResultsPipeline.FrbRunInput run = pipeline.fixture(false, 0, 2, false).run();
        Path manifest = run.manifests().get(PRIMARY);
        ObjectNode template = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(manifest).getFirst());
        StringBuilder manifestRows = new StringBuilder();
        var source = new ClassACaseManifest.Source(SOURCE_TITLE, ClassAScoreSchemaV2Test.SOURCE, SOURCE_BOUNDARY,
                List.of(SOURCE_MEMBRANE), List.of(SOURCE_CELLS), BIOLOGY, BIOLOGY, null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> caseRows = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        String baseline = workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT ? PAID : MINI;
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < questionCount; i++) questions.add(ClassAScoreSchemaV2Test.packet(QUESTION_FORMAT).question().deepCopy());
        for (int i = 0; i < cases; i++) {
            String id = DECISION_PREFIX + i;
            ObjectNode row = template.deepCopy();
            row.put(CASE_ID_FIELD, id);
            row.put(WORKFLOW_FIELD, workflow.name());
            row.put(QUESTION_COUNT_FIELD, questionCount);
            manifestRows.append(row).append('\n');
            caseRows.add(new ClassACaseManifest.Case(id, workflow,
                    SYNTHETIC, LearnerLevel.COLLEGE, null, BIOLOGY, false,
                    List.of(source), questionCount, null, false, null));
            outputs.add(new ClassABlindPacketExport.Output(id, baseline, questions));
            outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
        }
        if (includePlanWorkflow) {
            ObjectNode row = template.deepCopy();
            row.put(CASE_ID_FIELD, PLAN_CASE);
            row.put(WORKFLOW_FIELD, ClassACaseManifest.Workflow.PLAN_ADAPTIVE.name());
            row.put(QUESTION_COUNT_FIELD, questionCount);
            manifestRows.append(row).append('\n');
            caseRows.add(new ClassACaseManifest.Case(PLAN_CASE,
                    ClassACaseManifest.Workflow.PLAN_ADAPTIVE, SYNTHETIC, LearnerLevel.COLLEGE,
                    null, BIOLOGY, false, List.of(source), questionCount, null, false, null));
            outputs.add(new ClassABlindPacketExport.Output(PLAN_CASE, MINI, questions));
            outputs.add(new ClassABlindPacketExport.Output(PLAN_CASE, LUNA, questions));
        }
        Files.writeString(manifest, manifestRows.toString());
        Path plan = base.resolve(SPOT_PLAN_FILE);
        Files.writeString(plan, JSON_STEMS_START + stem + JSON_COUNT + spotCount + "}"
                + (includePlanWorkflow ? "," + JSON_STEM_START + PLAN_STEM + JSON_COUNT + "1}" : "") + "],"
                + JSON_EXPANSION_START + stem + JSON_PRIORITY_START + stem
                + JSON_INITIAL_ALLOCATION + stem + JSON_THREE_ALLOCATION_END
                + (includePlanWorkflow ? ", " + REGISTRY_WORKFLOW_START + PLAN_STEM
                        + JSON_PRIORITY_START + PLAN_STEM
                        + JSON_INITIAL_ALLOCATION + PLAN_STEM + JSON_THREE_ALLOCATION_END : "") + "]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(), FreezeRecord.sha256(manifest));
        if (workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT) {
            ((ArrayNode) freeze.path(ARMS_FIELD).path(PAID).path(WORKFLOWS_FIELD)).add(stem);
            ((ArrayNode) freeze.path(ARMS_FIELD).path(LUNA).path(WORKFLOWS_FIELD)).add(stem);
            ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(stem, 1);
            ((ObjectNode) freeze.path(COMPATIBILITY_SUITES_FIELD)).put("SyntheticSuite0", stem);
        }
        if (includePlanWorkflow) {
            ((ArrayNode) freeze.path(ARMS_FIELD).path(MINI).path(WORKFLOWS_FIELD)).add(PLAN_STEM);
            ((ArrayNode) freeze.path(ARMS_FIELD).path(LUNA).path(WORKFLOWS_FIELD)).add(PLAN_STEM);
            ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(PLAN_STEM, 1);
            ((ObjectNode) freeze.path(COMPATIBILITY_SUITES_FIELD)).put(SUITE_ONE, PLAN_STEM);
        }
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(PLAN, plan);
        Path root = base.resolve("assembled");
        Files.createDirectories(root);
        var exported = ClassABlindPacketExport.export(caseRows, outputs, baseline, LUNA,
                root.resolve(PACKETS), root.resolve(KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS));
        String mandatoryPair = mandatory ? pairs.pairs().getLast().id() : null;
        Path findingsDir = root.resolve(FINDINGS_DIR);
        Files.createDirectories(findingsDir);
        Path registry = findingsDir.resolve(REGISTRY_FILE);
        Files.writeString(registry, REGISTRY_START
                + REGISTRY_WORKFLOW_START + stem + JSON_SPOT_ROUNDS
                + REGISTRY_WORKFLOW_START + stem + "\",\"purpose\":\"EXPANSION\",\"rounds\":1}"
                + (includePlanWorkflow ? "," + REGISTRY_WORKFLOW_START + PLAN_STEM
                        + JSON_SPOT_ROUNDS
                        + REGISTRY_WORKFLOW_START + PLAN_STEM + REGISTRY_EXPANSION_END : "]}"));
        detached(registry);
        Path log = findingsDir.resolve(DRAW_LOG_FILE);
        var seeds = SeedStreamRegistry.load(registry);
        List<String> frame = pairs.pairs().stream().map(PairMap.Pair::id)
                .filter(id -> stem.equals(pairs.workflowOf(id)) && !id.equals(mandatoryPair)).toList();
        List<String> spots = PairSampler.draw(seeds, new DrawLog(log), pairs, stem,
                SeedStreamRegistry.SPOT_CHECK, 1, frame, spotCount, CLOCK).selectedPairIds();
        List<String> planSpots = includePlanWorkflow ? PairSampler.draw(seeds, new DrawLog(log), pairs,
                PLAN_STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                pairs.pairs().stream().map(PairMap.Pair::id)
                        .filter(id -> PLAN_STEM.equals(pairs.workflowOf(id))).toList(), 1, CLOCK)
                .selectedPairIds() : List.of();
        String trigger = spots.getFirst();
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", CLEAN,
                    mandatoryPair != null && mandatoryPair.equals(pairs.pairOf(id))).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", CLEAN, false).toString());
            if (spots.contains(pairs.pairOf(id)) || planSpots.contains(pairs.pairOf(id)) || mandatoryPair != null
                    && mandatoryPair.equals(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id,
                        planSpots.contains(pairs.pairOf(id)) ? CLEAN : CRITICAL, false).toString());
        }
        Path p = findingsDir.resolve(P_FILE), s = findingsDir.resolve(S_FILE);
        Path h = findingsDir.resolve(H_FILE);
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        List<String> drawn = cases - spotCount - (mandatory ? 1 : 0) == 0 ? List.of()
                : PairSampler.draw(seeds, new DrawLog(log), pairs, stem,
                    SeedStreamRegistry.EXPANSION, 1,
                    frame.stream().filter(id -> !spots.contains(id)).toList(),
                    Math.min(3, cases - spotCount - (mandatory ? 1 : 0)), CLOCK).selectedPairIds();
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve(LOCK_FILE);
        List<Path> findings = new ArrayList<>(List.of(p, s, h, log, registry));
        relock(lock, seal, findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, h)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve(ACCESS_LOG_FILE), ArmKeyGateFixtures.OPENED, "synthetic decision fixture");
        ClassAExport export = new ClassAExport(root.resolve(PACKETS), p, s, h, opened, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources sources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), export, log, registry);
        return new Fixture(sources, pairs, drawn, trigger, findingsDir, findings, lock, seal, root, stem);
    }

    private static void detached(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + HASH_SUFFIX), FreezeRecord.sha256(file) + "\n");
    }
    private static void relock(Path lock, Path seal, List<Path> findings) throws Exception {
        Files.deleteIfExists(lock);
        Files.deleteIfExists(lock.resolveSibling(lock.getFileName() + HASH_SUFFIX));
        ArmKeySeal.Seal sealed = ArmKeySeal.load(seal);
        LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(), findings,
                sealed, seal, files -> {}, ArmKeyGateFixtures.LOCKED);
    }
    private static void review(Fixture fixture, String pair, int count, String verdict) throws Exception {
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        List<String> ids = new ArrayList<>();
        ids.addAll(fixture.pairs().itemsOf(pair, SIDE_A));
        ids.addAll(fixture.pairs().itemsOf(pair, SIDE_B));
        for (String id : ids.subList(0, count))
            rows.add(ClassAAdjudicationFixture.human(id, verdict, false).toString());
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }
    private static ObjectNode decision(Fixture fixture, boolean declined, boolean approved,
            int capMinutes, String stopReason, List<String> declaredPairs) throws Exception {
        ClassASources sources = fixture.sources();
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put(WORKFLOW_FIELD, fixture.stem());
        row.put(FREEZE_HASH, FreezeRecord.sha256(sources.freezeFile()));
        row.put(PARAMETERS_HASH, FreezeRecord.sha256(sources.parametersFile()));
        row.put(SEAL_HASH, FreezeRecord.sha256(fixture.seal()));
        row.put(DRAW_HASH, FreezeRecord.sha256(sources.drawLogFile()));
        row.put(CAP_FIELD, capMinutes);
        row.put(APPROVED_FIELD, approved);
        row.put(DECLINED_FIELD, declined);
        if (stopReason == null) row.putNull(STOP_FIELD); else row.put(STOP_FIELD, stopReason);
        ObjectNode byStem = row.putObject(UNREVIEWED_FIELD);
        if (!declaredPairs.isEmpty()) {
            ArrayNode ids = byStem.putArray(fixture.stem());
            declaredPairs.forEach(ids::add);
        }
        ObjectNode root = IntegritySupport.JSON.createObjectNode();
        root.put(SCHEMA_FIELD, 1);
        root.putArray(DECISIONS_FIELD).add(row);
        return root;
    }
    private static void writeDecision(Fixture fixture, ObjectNode root, boolean listed) throws Exception {
        Files.writeString(fixture.decisionFile(), root.toString());
        if (listed && !fixture.findings().contains(fixture.decisionFile()))
            fixture.findings().add(fixture.decisionFile());
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    private static void reviewAll(Fixture fixture, String verdict, int questionsPerOutput) throws Exception {
        for (String pair : fixture.drawn()) review(fixture, pair, 2 * questionsPerOutput, verdict);
    }

    private static void replaceHumanVerdict(Fixture fixture, String pair, String verdict) throws Exception {
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            rows.add(pair.equals(fixture.pairs().pairOf(id))
                    ? ClassAAdjudicationFixture.human(id, verdict, false).toString() : line);
        }
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    @Test void noTriggerIsProducedByExpansionAndResultIsImmutable() throws Exception {
        Fixture fixture = fixture(5);
        replaceHumanVerdict(fixture, fixture.trigger(), CLEAN);
        Path log = fixture.sources().drawLogFile();
        Files.write(log, Files.readAllLines(log).subList(0, 1));
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        assertThat(result.outcomeByWorkflow()).containsEntry(STEM, NOT_TRIGGERED);
        assertThat(result.findings().get(STEM).reviewedPairs()).isEmpty();
        assertThat(result.outcomeByWorkflow().values()).doesNotContainNull().doesNotContain("");
        Map<String, String> outcomeMap = result.outcomeByWorkflow();
        Map<String, ExpansionOutcomes.Finding> findingMap = result.findings();
        assertThatThrownBy(() -> outcomeMap.put(FOREIGN_ITEM, COMPLETE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> findingMap.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void missingDecisionIsUnsuppliedAndCompletionRecordSuppliesIt() throws Exception {
        Fixture fixture = fixture(5);
        reviewAll(fixture, CLEAN, 2);
        ExpansionOutcomes.Result missing = ExpansionOutcomes.derive(fixture.sources());
        assertThat(missing.outcomeByWorkflow()).doesNotContainKey(STEM);
        assertThat(missing.findings().get(STEM).supplied()).isFalse();
        assertThat(missing.findings().get(STEM).outcome()).isNull();
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        ExpansionOutcomes.Result completed = ExpansionOutcomes.derive(fixture.sources());
        assertThat(completed.outcomeByWorkflow()).containsEntry(STEM, COMPLETE);
        assertThat(completed.findings().get(STEM).reviewedPairs()).containsExactlyElementsOf(fixture.drawn());
        assertThat(completed.findings().get(STEM).decisionSha256OrNull()).isNotBlank();
        assertThat(ExpansionOutcomes.derive(ClassAEvidenceAssembler.assemble(fixture.sources())))
                .isEqualTo(completed);
    }

    @Test void capIsMeasuredAgainstDecisionCapAndNeverBypassed() throws Exception {
        Fixture fixture = fixture(5, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 30);
        reviewAll(fixture, CLEAN, 30);
        Expansion.ExpansionPlan expected = Expansion.plan(fixture.pairs(), fixture.drawn(), 3,
                120, ExpansionOutcomes.PLANNING_MINUTES_PER_QUESTION, null);
        assertThat(expected.totalMinutes()).isEqualTo(225.0).isGreaterThan(120);
        for (int cap : List.of(120, 150, 224, 225, 250)) {
            boolean approved = cap != 120;
            writeDecision(fixture, decision(fixture, false, approved, cap, null, List.of()), true);
            ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources())
                    .findings().get(BOARD_WORKFLOW);
            assertThat(finding.plannedMinutes()).isEqualTo(225.0).isGreaterThan(120);
            assertThat(finding.capMinutes()).isEqualTo(cap);
            assertThat(finding.approvedLargerCapAsserted()).isEqualTo(approved);
            assertThat(finding.outcome()).isEqualTo(cap >= 225 ? COMPLETE : EXHAUSTED);
        }
    }

    @Test void capEqualToPlannedAndOneBelowHaveDifferentOutcomes() throws Exception {
        Fixture fixture = fixture(5, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 20);
        reviewAll(fixture, CLEAN, 20);
        Expansion.ExpansionPlan expected = Expansion.plan(fixture.pairs(), fixture.drawn(), 3,
                120, ExpansionOutcomes.PLANNING_MINUTES_PER_QUESTION, null);
        assertThat(expected.totalMinutes()).isEqualTo(150.0).isGreaterThan(120);
        for (int cap : List.of(149, 150)) {
            writeDecision(fixture, decision(fixture, false, true, cap, null, List.of()), true);
            ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources())
                    .findings().get(BOARD_WORKFLOW);
            assertThat(finding.plannedMinutes()).isEqualTo(150.0).isGreaterThan(120);
            assertThat(finding.outcome()).isEqualTo(cap == 150 ? COMPLETE : EXHAUSTED);
        }
    }

    @Test void declinedIncludesHalfReviewedPairAndDeclaredSuffix() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CLEAN);
        review(fixture, fixture.drawn().get(1), 1, "U");
        writeDecision(fixture, decision(fixture, true, false, 120, null,
                fixture.drawn().subList(1, 3)), true);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.outcome()).isEqualTo(EXHAUSTED);
        assertThat(finding.reviewedPairs()).containsExactlyElementsOf(fixture.drawn().subList(0, 2));
        assertThat(finding.declaredPairs()).containsExactlyElementsOf(fixture.drawn().subList(1, 3));
        assertThat(finding.unresolvedFindingPresent()).isTrue();
        List<String> reviewed = finding.reviewedPairs();
        assertThatThrownBy(() -> reviewed.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void declinedWithNoReviewedPairsIsStillExhausted() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, 120, null, fixture.drawn()), true);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.outcome()).isEqualTo(EXHAUSTED);
        assertThat(finding.reviewedPairs()).isEmpty();
        assertThat(finding.declaredPairs()).containsExactlyElementsOf(fixture.drawn());
    }

    @Test void confirmedSecondMissWinsEvenWhenPlanExceedsCap() throws Exception {
        Fixture fixture = fixture(5, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 30);
        review(fixture, fixture.drawn().getFirst(), 60, CRITICAL);
        writeDecision(fixture, decision(fixture, false, false, 120, STOP_VALUE,
                fixture.drawn().subList(1, 3)), true);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources())
                .findings().get(BOARD_WORKFLOW);
        assertThat(finding.plannedMinutes()).isEqualTo(225.0).isGreaterThan(120);
        assertThat(finding.outcome()).isEqualTo(UNRELIABLE);
        assertThat(finding.reviewedPairs()).containsExactly(fixture.drawn().getFirst());
    }

    @Test void lastDrawnSecondMissNeedsNoDecision() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CLEAN);
        review(fixture, fixture.drawn().get(1), 4, CLEAN);
        review(fixture, fixture.drawn().getLast(), 4, CRITICAL);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.outcome()).isEqualTo(UNRELIABLE);
        assertThat(finding.reviewedPairs()).containsExactlyElementsOf(fixture.drawn());
        assertThat(finding.decisionSha256OrNull()).isNull();
    }

    @Test void reviewPastSecondMissIsUnsuppliedWithoutAbortingResult() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CRITICAL);
        review(fixture, fixture.drawn().get(1), 4, CLEAN);
        review(fixture, fixture.drawn().getLast(), 4, CLEAN);
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        assertThat(result.outcomeByWorkflow()).doesNotContainKey(STEM);
        assertThat(result.findings().get(STEM).reason()).contains(SECOND_MISS_REASON);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        assertThat(ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM).supplied()).isFalse();
    }

    @Test void censusRequiresEveryWorkflowPairAndDisclosesItsLimit() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(assembly.expansionObligations().get(STEM).requiredPairs()).isZero();
        assertThat(finding.outcome()).isEqualTo(COMPLETE);
        assertThat(finding.censusClaimed()).isTrue();
        assertThat(finding.frameExhausted()).isTrue();
        assertThat(finding.reason()).isEqualTo("frame exhausted; every workflow pair human-reviewed as a census; no additional expansion sampling was possible; not proof of scorer reliability");
        assertThat(finding.reviewedPairs()).isEmpty();
        Expansion.ExpansionPlan empty = Expansion.plan(fixture.pairs(), List.of(), 3,
                120, ExpansionOutcomes.PLANNING_MINUTES_PER_QUESTION, null);
        assertThat(Expansion.outcome(empty, Set.of(fixture.trigger()), List.of(), true, true).outcome())
                .isEqualTo(Expansion.outcome(empty, Set.of(fixture.trigger()), List.of(), false, false).outcome());
    }

    @Test void twoInitialTriggersWithEmptyFrameAreUnreliableNotCensus() throws Exception {
        Fixture fixture = fixture(2, false, STEM, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, 2, 2);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.triggerPairs()).hasSize(2);
        assertThat(finding.outcome()).isEqualTo(UNRELIABLE);
        assertThat(finding.reviewedPairs()).isEmpty();
        assertThat(finding.censusClaimed()).isFalse();
    }

    @Test void twoInitialTriggersWithStopDecisionPassEmptyReviewList() throws Exception {
        Fixture fixture = fixture(5, false, STEM, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, 2, 2);
        writeDecision(fixture, decision(fixture, false, false, 120, STOP_VALUE,
                fixture.drawn()), true);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.outcome()).isEqualTo(UNRELIABLE);
        assertThat(finding.reviewedPairs()).isEmpty();
        assertThat(finding.declaredPairs()).containsExactlyElementsOf(fixture.drawn());
        assertThat(finding.decisionSha256OrNull()).isNotBlank();
    }

    @Test void insufficientFrameWithOneDrawNeedsCompletionRecord() throws Exception {
        Fixture fixture = fixture(2);
        reviewAll(fixture, CLEAN, 2);
        assertThat(ExpansionOutcomes.derive(fixture.sources()).outcomeByWorkflow()).doesNotContainKey(STEM);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(finding.outcome()).isEqualTo(COMPLETE);
        assertThat(finding.requiredPairs()).isEqualTo(1);
        assertThat(finding.frameExhausted()).isTrue();
        assertThat(finding.censusClaimed()).isFalse();
        assertThat(finding.reviewedPairs()).containsExactly(fixture.drawn().getFirst());
    }

    @Test void forgedCensusFlagsCannotOverrideNonFinalStatus() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        String item = fixture.pairs().itemsOf(fixture.trigger(), SIDE_A).getFirst();
        QuestionStatus original = verified.statuses().get(item);
        Map<String, QuestionStatus> statuses = new HashMap<>(verified.statuses());
        statuses.put(item, new QuestionStatus(item, QuestionStatus.State.PENDING_REVIEW,
                original.f(), original.reviewReasons(), original.doubleMiss(),
                original.scorerFailureCritical(), original.humanCleared(), original.humanDowngraded(),
                original.overturnedAgreedQualityFail(), original.t2Event(), original.t3Event(),
                original.pairMissing(), original.unreviewedAgreedQualityFail(),
                original.scorerMissNoncritical(), original.agreedQualityReviewed()));
        ClassAAssembly forged = new ClassAAssembly(verified.facts(), statuses, verified.pairs(),
                verified.sets(), verified.reviewRequiredPairs(), verified.drawEntries(), verified.digests(),
                verified.expansionObligations(), verified.declared());
        assertThat(forged.expansionObligations().get(STEM).frameExhausted()).isTrue();
        assertThatThrownBy(() -> ExpansionOutcomes.derive(forged))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining("pending");
    }

    @Test void excludedFrozenCaseMakesCensusUnsupplied() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        AdjudicatedLoaded excluded = new AdjudicatedLoaded(verified.facts().byCaseArm(),
                Set.of(EXCLUDED_CASE), verified.facts().requestedQuestionsByCaseId());
        ClassAAssembly seam = new ClassAAssembly(excluded, verified.statuses(), verified.pairs(),
                verified.sets(), verified.reviewRequiredPairs(), verified.drawEntries(), verified.digests(),
                verified.expansionObligations(), verified.declared());
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(seam);
        assertThat(result.outcomeByWorkflow()).doesNotContainKey(STEM);
        assertThat(result.findings().get(STEM).reason()).contains(EXCLUDED_CASE);
        assertThat(result.findings().get(STEM).censusClaimed()).isFalse();
    }

    @Test void censusRechecksRequiredAndSpotCoverageInTheSeam() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        ClassAAssembly missingRequired = new ClassAAssembly(verified.facts(), verified.statuses(),
                verified.pairs(), verified.sets(), Set.of(), verified.drawEntries(), verified.digests(),
                verified.expansionObligations(), verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(missingRequired))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining(CENSUS_COVERAGE_ERROR);
        ClassAAssembly missingSpot = new ClassAAssembly(verified.facts(), verified.statuses(),
                verified.pairs(), verified.sets(), verified.reviewRequiredPairs(), List.of(),
                verified.digests(), verified.expansionObligations(), verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(missingSpot))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining(CENSUS_COVERAGE_ERROR);
    }

    @Test void censusIsScopedToItsWorkflowWhenAnotherHasADeclaration() throws Exception {
        Fixture declaredFixture = fixture(5);
        writeDecision(declaredFixture, decision(declaredFixture, true, false, 120, null,
                declaredFixture.drawn()), true);
        ExpansionDecision.Declared anotherDeclaration = ClassAEvidenceAssembler
                .assemble(declaredFixture.sources()).declared();
        Fixture censusFixture = fixture(1, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 2);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(censusFixture.sources());
        Map<String, ExpansionObligation.Disclosure> obligations = new HashMap<>(verified.expansionObligations());
        obligations.put(STEM, new ExpansionObligation.Disclosure(STEM, Set.of("first", "second"),
                Map.of(STEM, 0), List.of(), true, 0));
        ClassAAssembly seam = new ClassAAssembly(verified.facts(), verified.statuses(),
                verified.pairs(), verified.sets(), verified.reviewRequiredPairs(),
                verified.drawEntries(), verified.digests(), obligations, anotherDeclaration);
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(seam);
        assertThat(result.outcomeByWorkflow()).containsEntry(BOARD_WORKFLOW, COMPLETE);
        assertThat(result.findings().get(BOARD_WORKFLOW).censusClaimed()).isTrue();
        assertThat(result.findings().get(STEM).declaredPairs()).isEmpty();
    }

    @Test void derivationDoesNotChangeLockedEvidenceFiles() throws Exception {
        Fixture fixture = fixture(5);
        reviewAll(fixture, CLEAN, 2);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        byte[] drawBefore = Files.readAllBytes(fixture.sources().drawLogFile());
        byte[] registryBefore = Files.readAllBytes(fixture.sources().seedRegistryFile());
        byte[] decisionBefore = Files.readAllBytes(fixture.decisionFile());
        ExpansionOutcomes.derive(fixture.sources());
        assertThat(Files.readAllBytes(fixture.sources().drawLogFile())).isEqualTo(drawBefore);
        assertThat(Files.readAllBytes(fixture.sources().seedRegistryFile())).isEqualTo(registryBefore);
        assertThat(Files.readAllBytes(fixture.decisionFile())).isEqualTo(decisionBefore);
    }
    // Copied from ClassAEvidenceAssemblerTest.longFixture, using FrbPipelineTest.Fixture's synthetic freeze.
    private record LongFixture(ClassASources sources, PairMap pairs, String selected,
            Set<String> selections, Path log, Path registry, Path lock, Path seal,
            Path root, Path[] findings) {}
    private static final String LONG_WORKFLOW = "LONG_EXAM";
    private static final String LONG_DIRECT = "LONG_EXAM_DIRECT";
    private static final String LONG_POOL = "LONG_EXAM_LONG_EXAM_POOL";
    private static final String BOARD_POOL = "LONG_EXAM_BOARD_EXAM_POOL";

    private LongFixture longFixture() throws Exception { return longFixture(3); }

    private LongFixture longFixture(int pairsPerStem) throws Exception {
        return longFixture(pairsPerStem, pairsPerStem, pairsPerStem);
    }

    private LongFixture longFixture(int directPairs, int poolPairs, int boardPairs) throws Exception {
        Path base = Files.createTempDirectory(temp, "long-assembly-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        ResultsPipeline.FrbRunInput run = pipeline.fixture(false, 0, 2, false).run();
        List<String> priority = List.of(LONG_DIRECT, LONG_POOL, BOARD_POOL);
        Path manifest = run.manifests().get(PRIMARY);
        ObjectNode template = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(manifest).getFirst());
        StringBuilder manifestRows = new StringBuilder();
        var source = new ClassACaseManifest.Source(SOURCE_TITLE, ClassAScoreSchemaV2Test.SOURCE, SOURCE_BOUNDARY,
                List.of(SOURCE_MEMBRANE), List.of(SOURCE_CELLS), BIOLOGY, BIOLOGY, null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet(QUESTION_FORMAT).question().deepCopy());
        for (ClassACaseManifest.Subgroup subgroup : ClassACaseManifest.Subgroup.values())
            for (int i = 0; i < (subgroup.name().equals(DIRECT_GROUP) ? directPairs
                    : subgroup.name().equals("LONG_EXAM_POOL") ? poolPairs : boardPairs); i++) {
                String id = "long-" + subgroup + "-" + i;
                ObjectNode row = template.deepCopy();
                row.put(CASE_ID_FIELD, id).put(WORKFLOW_FIELD, LONG_WORKFLOW).put("subgroup", subgroup.name());
                manifestRows.append(row).append('\n');
                cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                        SYNTHETIC, LearnerLevel.COLLEGE, null, BIOLOGY, false,
                        List.of(source), 2, null, false, subgroup));
                outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
                outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
            }
        Files.writeString(manifest, manifestRows.toString());
        Path plan = base.resolve(SPOT_PLAN_FILE);
        Files.writeString(plan, JSON_STEMS_START + LONG_DIRECT + JSON_SPOT_ONE_END
                + JSON_STEM_START + LONG_POOL + JSON_SPOT_ONE_END
                + JSON_STEM_START + BOARD_POOL + JSON_COUNT + Math.min(3, boardPairs) + "}],"
                + JSON_EXPANSION_START + LONG_WORKFLOW + JSON_PRIORITY_START
                + String.join("\",\"", priority) + JSON_INITIAL_ALLOCATION + LONG_DIRECT
                + JSON_ONE_ALLOCATION + LONG_POOL + JSON_ONE_ALLOCATION + BOARD_POOL + "\":1}}]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(), FreezeRecord.sha256(manifest));
        ((ArrayNode) freeze.path(ARMS_FIELD).path(MINI).path(WORKFLOWS_FIELD)).add(LONG_WORKFLOW);
        ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(LONG_WORKFLOW, 1);
        ((ObjectNode) freeze.path(COMPATIBILITY_SUITES_FIELD)).put(SUITE_ONE, LONG_WORKFLOW);
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(PLAN, plan);
        Path root = base.resolve("assembled-a");
        Files.createDirectories(root);
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve(PACKETS), root.resolve(KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS));
        Path directory = root.resolve(FINDINGS_DIR);
        Files.createDirectories(directory);
        Path registry = directory.resolve(REGISTRY_FILE);
        StringBuilder streams = new StringBuilder(REGISTRY_START);
        for (String stem : priority) streams.append(REGISTRY_WORKFLOW_START).append(stem)
                .append(JSON_SPOT_ROUNDS)
                .append(REGISTRY_WORKFLOW_START).append(stem)
                .append("\",\"purpose\":\"EXPANSION\",\"rounds\":1},");
        streams.setLength(streams.length() - 1);
        Files.writeString(registry, streams.append("]}").toString());
        detached(registry);
        Path log = directory.resolve(DRAW_LOG_FILE);
        Set<String> selections = new java.util.LinkedHashSet<>();
        String selected = null;
        for (String stem : priority) {
            DrawLog.Entry receipt = PairSampler.draw(SeedStreamRegistry.load(registry), new DrawLog(log), pairs,
                    stem, SeedStreamRegistry.SPOT_CHECK, 1,
                    pairs.pairs().stream().map(PairMap.Pair::id)
                            .filter(id -> stem.equals(pairs.workflowOf(id))).toList(),
                    stem.equals(BOARD_POOL) ? Math.min(3, boardPairs) : 1, CLOCK);
            if (stem.equals(LONG_DIRECT)) selected = receipt.selectedPairIds().getFirst();
            selections.addAll(receipt.selectedPairIds());
        }
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", "C", false).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", "C", false).toString());
            if (selections.contains(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        }
        Path p = directory.resolve(P_FILE), s = directory.resolve(S_FILE), h = directory.resolve(H_FILE);
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve(LOCK_FILE);
        Path[] findings = {p, s, h, log, registry};
        relock(lock, seal, findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, h)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve(ACCESS_LOG_FILE), ArmKeyGateFixtures.OPENED, "long assembler test");
        ClassAExport export = new ClassAExport(root.resolve(PACKETS), p, s, h, opened, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources sources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), export, log, registry);
        return new LongFixture(sources, pairs, selected, Set.copyOf(selections),
                log, registry, lock, seal, root, findings);
    }


    private static void relock(Path lock, Path seal, Path[] findings) throws Exception {
        relock(lock, seal, new ArrayList<>(List.of(findings)));
    }

    @Test void longExamThreeStemsProduceOneMappedWorkflowOutcome() throws Exception {
        LongFixture fixture = longFixture();
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        assertThat(assembly.expansionObligations()).containsOnlyKeys(LONG_WORKFLOW);
        assertThat(assembly.expansionObligations().get(LONG_WORKFLOW).allocationByStem())
                .containsKeys(LONG_DIRECT, LONG_POOL, BOARD_POOL);
        assertThat(result.outcomeByWorkflow()).containsOnlyKeys(LONG_WORKFLOW);
        assertThat(result.outcomeByWorkflow()).containsEntry(LONG_WORKFLOW, NOT_TRIGGERED);
    }

    private static void triggerLong(LongFixture fixture) throws Exception {
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            rows.add(fixture.selected().equals(fixture.pairs().pairOf(id))
                    ? ClassAAdjudicationFixture.human(id, CRITICAL, false).toString() : line);
        }
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    @Test void longExamAllThreeEmptyStemsAreOneCensus() throws Exception {
        LongFixture fixture = longFixture(1);
        triggerLong(fixture);
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        assertThat(result.outcomeByWorkflow()).containsOnlyKeys(LONG_WORKFLOW);
        assertThat(result.outcomeByWorkflow()).containsEntry(LONG_WORKFLOW, COMPLETE);
        assertThat(result.findings().get(LONG_WORKFLOW).censusClaimed()).isTrue();
        assertThat(result.findings().get(LONG_WORKFLOW).drawnPairs()).isEmpty();
    }

    @Test void longExamOneNonemptyStemDrawsAndIsNotCensus() throws Exception {
        LongFixture fixture = longFixture(2, 1, 1);
        triggerLong(fixture);
        List<String> remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> LONG_DIRECT.equals(fixture.pairs().workflowOf(id)))
                .filter(id -> !fixture.selections().contains(id)).toList();
        List<String> drawn = PairSampler.draw(SeedStreamRegistry.load(fixture.registry()),
                new DrawLog(fixture.log()), fixture.pairs(), LONG_DIRECT,
                SeedStreamRegistry.EXPANSION, 1, remaining, 1, CLOCK).selectedPairIds();
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (drawn.contains(fixture.pairs().pairOf(id)))
                rows.add(ClassAAdjudicationFixture.human(id, CLEAN, false).toString());
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources())
                .findings().get(LONG_WORKFLOW);
        assertThat(finding.drawnPairs()).containsExactlyElementsOf(drawn);
        assertThat(finding.requiredPairs()).isEqualTo(1);
        assertThat(finding.frameExhausted()).isTrue();
        assertThat(finding.censusClaimed()).isFalse();
        assertThat(finding.supplied()).isFalse();
    }

    @Test void longExamTriggerInOneStemAndDrawInAnotherIsOneWorkflow() throws Exception {
        LongFixture fixture = longFixture();
        triggerLong(fixture);
        SeedStreamRegistry seeds = SeedStreamRegistry.load(fixture.registry());
        List<String> drawn = new ArrayList<>();
        for (String stem : List.of(LONG_DIRECT, LONG_POOL)) {
            List<String> remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                    .filter(id -> stem.equals(fixture.pairs().workflowOf(id)))
                    .filter(id -> !fixture.selections().contains(id)).toList();
            drawn.addAll(PairSampler.draw(seeds, new DrawLog(fixture.log()), fixture.pairs(), stem,
                    SeedStreamRegistry.EXPANSION, 1, remaining,
                    stem.equals(LONG_DIRECT) ? 2 : 1, CLOCK).selectedPairIds());
        }
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (drawn.contains(fixture.pairs().pairOf(id)))
                rows.add(ClassAAdjudicationFixture.human(id, CLEAN, false).toString());
        Files.write(human, rows);
        Path decisionFile = human.getParent().resolve(ExpansionDecision.FILE_NAME);
        ObjectNode choice = IntegritySupport.JSON.createObjectNode();
        choice.put(WORKFLOW_FIELD, LONG_WORKFLOW);
        choice.put(FREEZE_HASH, FreezeRecord.sha256(fixture.sources().freezeFile()));
        choice.put(PARAMETERS_HASH, FreezeRecord.sha256(fixture.sources().parametersFile()));
        choice.put(SEAL_HASH, FreezeRecord.sha256(fixture.seal()));
        choice.put(DRAW_HASH, FreezeRecord.sha256(fixture.log()));
        choice.put(CAP_FIELD, 120).put(APPROVED_FIELD, false).put(DECLINED_FIELD, false);
        choice.putNull(STOP_FIELD);
        choice.putObject(UNREVIEWED_FIELD);
        ObjectNode root = IntegritySupport.JSON.createObjectNode();
        root.put(SCHEMA_FIELD, 1);
        root.putArray(DECISIONS_FIELD).add(choice);
        Files.writeString(decisionFile, root.toString());
        List<Path> findings = new ArrayList<>(List.of(fixture.findings()));
        findings.add(decisionFile);
        relock(fixture.lock(), fixture.seal(), findings);
        ExpansionOutcomes.Finding finding = ExpansionOutcomes.derive(fixture.sources())
                .findings().get(LONG_WORKFLOW);
        assertThat(finding.outcome()).isEqualTo(COMPLETE);
        assertThat(finding.triggerPairs()).containsExactly(fixture.selected());
        assertThat(finding.drawnPairs()).containsExactlyElementsOf(drawn);
        assertThat(finding.drawnPairs()).anyMatch(id -> id.startsWith(LONG_POOL));
        assertThat(finding.reviewedPairs()).containsExactlyElementsOf(drawn);
        assertThat(finding.censusClaimed()).isFalse();
    }

    @Test void mappedBoardAndLongKeysReachRealFrbClassificationLookup() throws Exception {
        Fixture board = fixture(5, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 2);
        replaceHumanVerdict(board, board.trigger(), CLEAN);
        Files.write(board.sources().drawLogFile(),
                Files.readAllLines(board.sources().drawLogFile()).subList(0, 1));
        relock(board.lock(), board.seal(), board.findings());
        ExpansionOutcomes.Result boardResult = ExpansionOutcomes.derive(board.sources());
        ExpansionOutcomes.Result longResult = ExpansionOutcomes.derive(longFixture().sources());
        assertThat(boardResult.outcomeByWorkflow()).containsOnlyKeys(BOARD_WORKFLOW);
        assertThat(longResult.outcomeByWorkflow()).containsOnlyKeys(LONG_WORKFLOW);

        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "route-");
        FrbPipelineTest.Fixture pipeline = builder.fixture(false, 0, 2, false);
        var actual = ResultsPipeline.runFrb(pipeline.run(), pipeline.frb()).routes().get(ROUTE);
        var workflows = new LinkedHashMap<>(actual.workflows());
        var classARow = workflows.get(STEM);
        workflows.put(BOARD_WORKFLOW, classARow);
        workflows.put(LONG_WORKFLOW, classARow);
        var route = new RouteVerdictCalculator.RouteReport(actual.verdict(), actual.failedTiers(),
                actual.inconclusiveTiers(), actual.d(), actual.isolatedCriticalIncident(),
                actual.parameterSha256(), workflows, actual.frb());
        assertThat(boardResult.outcomeByWorkflow().keySet()).isSubsetOf(route.workflows().keySet());
        assertThat(longResult.outcomeByWorkflow().keySet()).isSubsetOf(route.workflows().keySet());
        // The literal is for the fixture's unrelated class-A workflow only; it cannot hide a producer key.
        Map<String, String> outcomes = new HashMap<>(Map.of(STEM, NOT_TRIGGERED));
        outcomes.putAll(boardResult.outcomeByWorkflow());
        outcomes.putAll(longResult.outcomeByWorkflow());
        var supplied = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(supplied.causes()).doesNotContain(MISSING_EXPANSION_CAUSE);
        outcomes.remove(BOARD_WORKFLOW);
        var missing = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(missing.causes()).contains(MISSING_EXPANSION_CAUSE);
        outcomes.put(BOARD_WORKFLOW, EXHAUSTED);
        var exhausted = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(exhausted.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        outcomes.put(BOARD_WORKFLOW, UNRELIABLE);
        var unreliable = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(unreliable.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        outcomes.put(BOARD_WORKFLOW, COMPLETE);
        var failed = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                actual.failedTiers(), actual.inconclusiveTiers(), actual.d(),
                actual.isolatedCriticalIncident(), actual.parameterSha256(), workflows, actual.frb());
        var completeOnFailedRoute = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, failed),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(completeOnFailedRoute.classification())
                .isNotEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
    }

    private static FrbClassificationInput routeInput(Map<String, String> outcomes) {
        return new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.copyOf(outcomes), Map.of(SAFETY_HEAD, "0:head"));
    }

    @Test void findingKeepsExpansionReasonAndPlanningDisclosureWhenDecisionIsMissing() throws Exception {
        Fixture fixture = fixture(5);
        reviewAll(fixture, CLEAN, 2);
        ExpansionOutcomes.Finding missing = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(missing.supplied()).isFalse();
        assertThat(missing.outcome()).isNull();
        assertThat(missing.expansionReason()).contains("no second double-miss pair");
        assertThat(missing.planningNote()).isEqualTo(PLANNING_NOTE_TEXT);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        ExpansionOutcomes.Finding supplied = ExpansionOutcomes.derive(fixture.sources()).findings().get(STEM);
        assertThat(supplied.expansionReason()).isEqualTo(missing.expansionReason());
        assertThat(supplied.planningNote()).isEqualTo(PLANNING_NOTE_TEXT);
    }

    @Test void exactlyRequiredPairsAreReviewedAndReturnedCollectionsAreImmutable() throws Exception {
        Fixture fixture = fixture(5);
        reviewAll(fixture, CLEAN, 2);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        ExpansionOutcomes.Finding finding = result.findings().get(STEM);
        assertThat(fixture.drawn()).hasSize(ExpansionObligation.K_REQ);
        assertThat(finding.requiredPairs()).isEqualTo(ExpansionObligation.K_REQ);
        assertThat(finding.reviewedPairs()).containsExactlyElementsOf(fixture.drawn());
        Set<String> triggers = finding.triggerPairs();
        List<String> drawn = finding.drawnPairs();
        List<String> declared = finding.declaredPairs();
        assertThatThrownBy(() -> triggers.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> drawn.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> declared.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.outcomeByWorkflow().values()).allSatisfy(value -> assertThat(value).isNotBlank());
    }

    @Test void forgedZeroRequirementCannotClaimCensusForAnUndrawnFrame() throws Exception {
        Fixture fixture = fixture(2);
        reviewAll(fixture, CLEAN, 2);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        ExpansionObligation.Disclosure original = verified.expansionObligations().get(STEM);
        Map<String, ExpansionObligation.Disclosure> obligations = Map.of(STEM,
                new ExpansionObligation.Disclosure(STEM, original.triggerPairs(),
                        original.allocationByStem(), List.of(), true, 0));
        List<DrawLog.Entry> spotsOnly = verified.drawEntries().stream()
                .filter(entry -> SeedStreamRegistry.SPOT_CHECK.equals(entry.purpose())).toList();
        ClassAAssembly forged = new ClassAAssembly(verified.facts(), verified.statuses(), verified.pairs(),
                verified.sets(), verified.reviewRequiredPairs(), spotsOnly, verified.digests(),
                obligations, verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(forged))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining(CENSUS_COVERAGE_ERROR);
    }

    @Test void realFrbRouteClassifiesProducerOutcomesWithoutAHandWrittenOutcome() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "real-route-");
        FrbPipelineTest.Fixture pipeline = builder.fixture(false, 0, 2, false);
        var route = ResultsPipeline.runFrb(pipeline.run(), pipeline.frb()).routes().get(ROUTE);
        assertThat(route.workflows()).containsKey(STEM);

        Fixture exhaustedFixture = fixture(5);
        writeDecision(exhaustedFixture, decision(exhaustedFixture, true, false, 120, null,
                exhaustedFixture.drawn()), true);
        ExpansionOutcomes.Result exhausted = ExpansionOutcomes.derive(exhaustedFixture.sources());
        assertThat(exhausted.outcomeByWorkflow()).containsEntry(STEM, EXHAUSTED);
        var exhaustedClassification = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(exhausted.outcomeByWorkflow())), Set.of()).get(ROUTE);
        assertThat(exhaustedClassification.causes()).doesNotContain(MISSING_EXPANSION_CAUSE);
        assertThat(exhaustedClassification.classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(exhaustedClassification.classification())
                .isNotIn(DeadlineRule.Classification.BENCHMARK_PASS,
                        DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);

        Fixture unreliableFixture = fixture(5);
        review(unreliableFixture, unreliableFixture.drawn().getFirst(), 4, CRITICAL);
        writeDecision(unreliableFixture, decision(unreliableFixture, false, false, 120,
                STOP_VALUE, unreliableFixture.drawn().subList(1, 3)), true);
        ExpansionOutcomes.Result unreliable = ExpansionOutcomes.derive(unreliableFixture.sources());
        assertThat(unreliable.outcomeByWorkflow()).containsEntry(STEM, UNRELIABLE);
        var unreliableClassification = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, routeInput(unreliable.outcomeByWorkflow())), Set.of()).get(ROUTE);
        assertThat(unreliableClassification.causes()).doesNotContain(MISSING_EXPANSION_CAUSE);
        assertThat(unreliableClassification.classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(unreliableClassification.classification())
                .isNotIn(DeadlineRule.Classification.BENCHMARK_PASS,
                        DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);

        Fixture completeFixture = fixture(5);
        reviewAll(completeFixture, CLEAN, 2);
        writeDecision(completeFixture, decision(completeFixture, false, false, 120,
                null, List.of()), true);
        ExpansionOutcomes.Result complete = ExpansionOutcomes.derive(completeFixture.sources());
        assertThat(complete.outcomeByWorkflow()).containsEntry(STEM, COMPLETE);
        var failedRoute = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                route.failedTiers(), route.inconclusiveTiers(), route.d(),
                route.isolatedCriticalIncident(), route.parameterSha256(), route.workflows(), route.frb());
        var completeClassification = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, failedRoute),
                Map.of(ROUTE, routeInput(complete.outcomeByWorkflow())), Set.of()).get(ROUTE);
        assertThat(completeClassification.causes()).doesNotContain(MISSING_EXPANSION_CAUSE);
        assertThat(completeClassification.classification())
                .isNotIn(DeadlineRule.Classification.BENCHMARK_PASS,
                        DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
    }

    @Test void reviewPastSecondMissLeavesAnotherVerifiedWorkflowInTheResult() throws Exception {
        Fixture fixture = fixture(5, false, STEM, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                2, 1, true);
        review(fixture, fixture.drawn().getFirst(), 4, CRITICAL);
        review(fixture, fixture.drawn().get(1), 4, CLEAN);
        review(fixture, fixture.drawn().getLast(), 4, CLEAN);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.expansionObligations()).containsKeys(STEM, PLAN_STEM);
        ExpansionOutcomes.Result result = ExpansionOutcomes.derive(fixture.sources());
        assertThat(result.findings()).containsKeys(STEM, PLAN_STEM);
        assertThat(result.findings().get(STEM).supplied()).isFalse();
        assertThat(result.findings().get(STEM).reason()).contains(SECOND_MISS_REASON);
        assertThat(result.outcomeByWorkflow()).containsEntry(PLAN_STEM, NOT_TRIGGERED);
        writeDecision(fixture, decision(fixture, false, false, 120, null, List.of()), true);
        ExpansionOutcomes.Result withCompletion = ExpansionOutcomes.derive(fixture.sources());
        assertThat(withCompletion.findings().get(STEM).supplied()).isFalse();
        assertThat(withCompletion.outcomeByWorkflow()).containsEntry(PLAN_STEM, NOT_TRIGGERED);
    }

    @Test void assemblerRefusesFrozenManifestCaseMissingFromPacketExport() throws Exception {
        Fixture fixture = fixture(1);
        Path manifest = fixture.sources().manifests().get(PRIMARY);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(manifest).getFirst());
        row.put(CASE_ID_FIELD, "missing-packet-case");
        Files.writeString(manifest, row + "\n");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllBytes(fixture.sources().freezeFile()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(),
                FreezeRecord.sha256(manifest));
        Files.writeString(fixture.sources().freezeFile(), freeze.toString());
        detached(fixture.sources().freezeFile());
        ClassASources sources = fixture.sources();
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(sources))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("frozen class-A scope");
    }

    @Test void mappedProducerKeysReachCalculatorBuiltFrbRouteAndLookup() throws Exception {
        Fixture board = fixture(5, false, BOARD_WORKFLOW,
                ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT, 2);
        replaceHumanVerdict(board, board.trigger(), CLEAN);
        Files.write(board.sources().drawLogFile(),
                Files.readAllLines(board.sources().drawLogFile()).subList(0, 1));
        relock(board.lock(), board.seal(), board.findings());
        ExpansionOutcomes.Result boardResult = ExpansionOutcomes.derive(board.sources());
        ExpansionOutcomes.Result longResult = ExpansionOutcomes.derive(longFixture().sources());

        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, CALCULATED_ROUTE_PREFIX);
        FrbPipelineTest.Fixture pipeline = builder.fixture(false, 0, 2, false);
        ResultsPipeline.Result evaluated = ResultsPipeline.runFrb(pipeline.run(), pipeline.frb());
        List<RouteVerdictCalculator.CaseRecord> records = new ArrayList<>(evaluated.caseRecords().get(ROUTE));
        RouteVerdictCalculator.CaseRecord source = records.stream()
                .filter(record -> STEM.equals(record.workflow())).findFirst().orElseThrow();
        String boardId = "calculated-board";
        String longId = "calculated-long";
        records.add(new RouteVerdictCalculator.CaseRecord(boardId, BOARD_WORKFLOW, ROUTE, null,
                boardId, boardId, source.baseline(), source.candidate(), source.requestedQuestions()));
        records.add(new RouteVerdictCalculator.CaseRecord(longId, LONG_WORKFLOW, ROUTE, DIRECT_GROUP,
                longId, longId, source.baseline(), source.candidate(), source.requestedQuestions()));
        ObjectNode parametersJson = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllBytes(pipeline.run().parameters()));
        ObjectNode workflowsJson = (ObjectNode) parametersJson.path(WORKFLOWS_FIELD);
        ObjectNode note = (ObjectNode) workflowsJson.path(STEM);
        ObjectNode boardWorkflow = note.deepCopy();
        boardWorkflow.put(PRIMARY_SIZE_FIELD, 1);
        workflowsJson.set(BOARD_WORKFLOW, boardWorkflow);
        ObjectNode longWorkflow = note.deepCopy();
        longWorkflow.put(PRIMARY_SIZE_FIELD, 1);
        longWorkflow.putObject("subgroups").put(DIRECT_GROUP, 1);
        workflowsJson.set(LONG_WORKFLOW, longWorkflow);
        ArrayNode routeWorkflows = (ArrayNode) parametersJson.path("routes").path(ROUTE).path(WORKFLOWS_FIELD);
        routeWorkflows.add(BOARD_WORKFLOW).add(LONG_WORKFLOW);
        Path calculatedParameters = Files.createTempFile(temp, CALCULATED_ROUTE_PREFIX, ".json");
        Files.writeString(calculatedParameters, parametersJson.toString());
        detached(calculatedParameters);
        FrozenGateParameters parameters = FrozenGateParameters.load(calculatedParameters);
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        Map<String, Integer> widths = new HashMap<>();
        for (RouteVerdictCalculator.CaseRecord record : records) {
            frozen.computeIfAbsent(record.workflow(), ignored -> new LinkedHashSet<>()).add(record.id());
            widths.put(record.id(), record.requestedQuestions());
        }
        var calculated = RouteVerdictCalculator.calculate(parameters, ROUTE, frozen, records,
                Map.of(), Map.of(STEM, true, TIP, true, BOARD_WORKFLOW, true, LONG_WORKFLOW, true),
                List.of(), Map.of(TIP, false), widths);
        assertThat(calculated.workflows()).containsKeys(BOARD_WORKFLOW, LONG_WORKFLOW);
        assertThat(boardResult.outcomeByWorkflow().keySet()).isSubsetOf(calculated.workflows().keySet());
        assertThat(longResult.outcomeByWorkflow().keySet()).isSubsetOf(calculated.workflows().keySet());
        Map<String, String> outcomes = new HashMap<>(Map.of(STEM, NOT_TRIGGERED));
        assertThat(outcomes.keySet()).doesNotContainAnyElementsOf(boardResult.outcomeByWorkflow().keySet());
        assertThat(outcomes.keySet()).doesNotContainAnyElementsOf(longResult.outcomeByWorkflow().keySet());
        outcomes.putAll(boardResult.outcomeByWorkflow());
        outcomes.putAll(longResult.outcomeByWorkflow());
        var supplied = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, calculated),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(supplied.causes()).doesNotContain(MISSING_EXPANSION_CAUSE);
        outcomes.remove(BOARD_WORKFLOW);
        var missing = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, calculated),
                Map.of(ROUTE, routeInput(outcomes)), Set.of()).get(ROUTE);
        assertThat(missing.causes()).contains(MISSING_EXPANSION_CAUSE);
    }

    @Test void seamCensusChecksEveryFramePairEvenIfThePairMapOmitsIt() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        ReviewSets original = verified.sets();
        ReviewSets forgedSets = new ReviewSets(original.mandatory(), original.criticalCleanFrame(),
                Map.of(STEM, Set.of(fixture.trigger(), "ghost-pair")), original.mandatoryByStem());
        ClassAAssembly forged = new ClassAAssembly(verified.facts(), verified.statuses(), verified.pairs(),
                forgedSets, verified.reviewRequiredPairs(), verified.drawEntries(), verified.digests(),
                verified.expansionObligations(), verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(forged))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining("workflow frame");
    }


    // ---- Audit additions (release session): seam-only guards a mutant removed without any test failing ----

    @Test void aCensusWithADrawnPairInItsObligationIsRefusedInTheSeam() throws Exception {
        Fixture fixture = fixture(1);
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        ExpansionObligation.Disclosure real = verified.expansionObligations().get(STEM);
        ExpansionObligation.Disclosure forgedDisclosure = new ExpansionObligation.Disclosure(STEM,
                real.triggerPairs(), real.allocationByStem(), List.of(fixture.trigger()),
                real.frameExhausted(), 0);
        ClassAAssembly forged = new ClassAAssembly(verified.facts(), verified.statuses(), verified.pairs(),
                verified.sets(), verified.reviewRequiredPairs(), verified.drawEntries(), verified.digests(),
                Map.of(STEM, forgedDisclosure), verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(forged))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining("draw or decision");
    }

    @Test void aNoTriggerWorkflowWithADrawnPairInItsObligationIsRefusedInTheSeam() throws Exception {
        Fixture fixture = fixture(5);
        replaceHumanVerdict(fixture, fixture.trigger(), CLEAN);
        Path log = fixture.sources().drawLogFile();
        Files.write(log, Files.readAllLines(log).subList(0, 1));
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        ClassAAssembly verified = ClassAEvidenceAssembler.assemble(fixture.sources());
        ExpansionObligation.Disclosure real = verified.expansionObligations().get(STEM);
        ExpansionObligation.Disclosure forgedDisclosure = new ExpansionObligation.Disclosure(STEM,
                real.triggerPairs(), real.allocationByStem(), List.of(fixture.trigger()),
                real.frameExhausted(), real.requiredPairs());
        ClassAAssembly forged = new ClassAAssembly(verified.facts(), verified.statuses(), verified.pairs(),
                verified.sets(), verified.reviewRequiredPairs(), verified.drawEntries(), verified.digests(),
                Map.of(STEM, forgedDisclosure), verified.declared());
        assertThatThrownBy(() -> ExpansionOutcomes.derive(forged))
                .isInstanceOf(InvalidExpansionOutcome.class).hasMessageContaining("expansion or decision");
    }

    @Test void aFindingCannotClaimASuppliedOutcomeWithoutOneOrAnOutcomeWithoutBeingSupplied() {
        assertThatThrownBy(() -> finding(true, null)).isInstanceOf(InvalidExpansionOutcome.class);
        assertThatThrownBy(() -> finding(false, NOT_TRIGGERED)).isInstanceOf(InvalidExpansionOutcome.class);
        assertThatThrownBy(() -> finding(true, " ")).isInstanceOf(InvalidExpansionOutcome.class);
        assertThat(finding(true, NOT_TRIGGERED).supplied()).isTrue();
        assertThat(finding(false, null).supplied()).isFalse();
    }

    private static ExpansionOutcomes.Finding finding(boolean supplied, String outcome) {
        return new ExpansionOutcomes.Finding(STEM, outcome, supplied, "reason", null, false, Set.of(),
                List.of(), List.of(), List.of(), 0, 0.0, 120, false, false, false, null, "note");
    }
}
