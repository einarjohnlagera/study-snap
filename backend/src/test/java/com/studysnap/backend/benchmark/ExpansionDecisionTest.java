package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic freeze, packet, finding, seal and lock helpers copied from ClassAEvidenceAssemblerTest;
 * the freeze/manifest seed is copied from FrbPipelineTest.Fixture. */
class ExpansionDecisionTest {
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
    private static final String FREEZE_HASH = "freezeSha256";
    private static final String PARAMETERS_HASH = "parametersSha256";
    private static final String SEAL_HASH = "sealSha256";
    private static final String DRAW_HASH = "drawLogSha256";
    private static final String APPROVED_FIELD = "approvedLargerCap";
    private static final String DECLINED_FIELD = "declinedOrExhausted";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String MANIFESTS_FIELD = "manifests";
    private static final String FIRST_CASE_PREFIX = "a-one\u0000";
    private static final String TIP = "STUDY_TIP";
    private static final String LEDGER_GENESIS = "2026-11-16T00:05:00Z";
    private static final String LEDGER_CREATED = "2026-11-16T00:07:00Z";
    private static final String LEDGER_EVIDENCE = "a".repeat(64);
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
    private static final String PENDING_CAUSE = "PENDING_REVIEW_ITEMS";
    private static final String MISSING_CAUSE = "MISSING_RUN_DATA";
    private static final String SAFETY_HEAD = "safety";
    private static final String QUOTE = "\"";
    private static final String ARRAY_FIELD_SUFFIX = "\":[";
    private static final String FOREIGN_ITEM = "foreign";
    private static final String STATUS_MISMATCH = "Caller statuses differ";
    private static final String TRIGGER_FIELD = "trigger";
    private static final String SINGLE_SPACE = " ";
    @TempDir Path temp;

    private record Fixture(ClassASources sources, PairMap pairs, List<String> drawn, String trigger,
            Path findingsDir, List<Path> findings, Path lock, Path seal, Path root, String stem) {
        Path decisionFile() { return findingsDir.resolve(ExpansionDecision.FILE_NAME); }
    }

    private Fixture fixture(int cases) throws Exception { return fixture(cases, false); }

    private Fixture fixture(int cases, boolean mandatory) throws Exception {
        return fixture(cases, mandatory, STEM, ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
    }

    private Fixture fixture(int cases, boolean mandatory, String stem,
            ClassACaseManifest.Workflow workflow) throws Exception {
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
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet(QUESTION_FORMAT).question().deepCopy());
        for (int i = 0; i < cases; i++) {
            String id = DECISION_PREFIX + i;
            ObjectNode row = template.deepCopy();
            row.put("caseId", id);
            row.put(WORKFLOW_FIELD, workflow.name());
            manifestRows.append(row).append('\n');
            caseRows.add(new ClassACaseManifest.Case(id, workflow,
                    SYNTHETIC, LearnerLevel.COLLEGE, null, BIOLOGY, false,
                    List.of(source), 2, null, false, null));
            outputs.add(new ClassABlindPacketExport.Output(id, baseline, questions));
            outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
        }
        Files.writeString(manifest, manifestRows.toString());
        Path plan = base.resolve("spot-plan.json");
        Files.writeString(plan, "{\"stems\":[{\"stem\":\"" + stem + "\",\"count\":1}],"
                + "\"expansion\":[{\"workflow\":\"" + stem + "\",\"stemPriority\":[\"" + stem
                + "\"],\"initialAllocation\":{\"" + stem + "\":3}}]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(), FreezeRecord.sha256(manifest));
        if (workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT) {
            ((ArrayNode) freeze.path(ARMS_FIELD).path(PAID).path(WORKFLOWS_FIELD)).add(stem);
            ((ArrayNode) freeze.path(ARMS_FIELD).path(LUNA).path(WORKFLOWS_FIELD)).add(stem);
            ((ObjectNode) freeze.path("costForecastUsdPerDeliveredCase")).put(stem, 1);
            ((ObjectNode) freeze.path("compatibilitySuites")).put("SyntheticSuite0", stem);
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
        Path findingsDir = root.resolve("findings");
        Files.createDirectories(findingsDir);
        Path registry = findingsDir.resolve("seeds.json");
        Files.writeString(registry, REGISTRY_START
                + REGISTRY_WORKFLOW_START + stem + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"
                + REGISTRY_WORKFLOW_START + stem + REGISTRY_EXPANSION_END);
        detached(registry);
        Path log = findingsDir.resolve("draws.jsonl");
        var seeds = SeedStreamRegistry.load(registry);
        List<String> frame = pairs.pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(mandatoryPair)).toList();
        String trigger = PairSampler.draw(seeds, new DrawLog(log), pairs, stem,
                SeedStreamRegistry.SPOT_CHECK, 1, frame, 1, CLOCK).selectedPairIds().getFirst();
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", CLEAN,
                    mandatoryPair != null && mandatoryPair.equals(pairs.pairOf(id))).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", CLEAN, false).toString());
            if (trigger.equals(pairs.pairOf(id)) || mandatoryPair != null
                    && mandatoryPair.equals(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, CRITICAL, false).toString());
        }
        Path p = findingsDir.resolve("p.jsonl"), s = findingsDir.resolve("s.jsonl");
        Path h = findingsDir.resolve("human.jsonl");
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        List<String> drawn = PairSampler.draw(seeds, new DrawLog(log), pairs, stem,
                SeedStreamRegistry.EXPANSION, 1,
                frame.stream().filter(id -> !id.equals(trigger)).toList(), Math.min(3, cases - 1), CLOCK)
                .selectedPairIds();
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve("lock.json");
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
            String stopReason, List<String> declaredPairs) throws Exception {
        ClassASources sources = fixture.sources();
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put(WORKFLOW_FIELD, fixture.stem());
        row.put(FREEZE_HASH, FreezeRecord.sha256(sources.freezeFile()));
        row.put(PARAMETERS_HASH, FreezeRecord.sha256(sources.parametersFile()));
        row.put(SEAL_HASH, FreezeRecord.sha256(fixture.seal()));
        row.put(DRAW_HASH, FreezeRecord.sha256(sources.drawLogFile()));
        row.put(CAP_FIELD, approved ? 121 : 120);
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
    private static ExpansionDecision.Declared verify(Fixture fixture) throws Exception {
        return verify(fixture, Set.copyOf(fixture.findings()));
    }

    private static ExpansionDecision.Declared verify(Fixture fixture, Set<Path> findings) throws Exception {
        ClassAExport export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        var entries = new DrawLog(fixture.sources().drawLogFile()).entries();
        Set<String> selected = new LinkedHashSet<>();
        entries.forEach(entry -> selected.addAll(entry.selectedPairIds()));
        Set<String> required = ReviewSets.reviewRequiredPairs(sets, selected);
        Map<String, QuestionStatus> statuses = new LinkedHashMap<>();
        for (var item : scores.values()) statuses.put(item.id(), FinalStatus.of(item,
                ClassAAdjudicationFixture.MAPPING, required.contains(fixture.pairs().pairOf(item.id()))));
        return ExpansionDecision.verify(context(fixture), statuses, fixture.pairs(), sets, entries,
                Map.of(fixture.stem(), Set.of(fixture.stem())), findings);
    }
    private static ClassAAssembly assemble(Fixture fixture) {
        return ClassAEvidenceAssembler.assemble(fixture.sources());
    }

    private static void checkStrict(Fixture fixture) throws Exception {
        LockReadiness.check(derivedStatuses(fixture), Set.of());
    }

    private static void checkDeclared(Map<String, QuestionStatus> statuses,
            ExpansionDecision.Declared declared) {
        LockReadiness.check(statuses, Set.of(), declared);
    }

    private static AdjudicatedLoaded convertStrict(Fixture fixture) throws Exception {
        ClassAExport export = fixture.sources().export();
        return AdjudicatedConverter.classA(export.key(), derivedStatuses(fixture), fixture.pairs(),
                Set.of(STEM), caseIds(fixture), export.exclusionsFileOrNull(),
                export.expectedExclusionsSha256OrNull(), widths(fixture));
    }

    private static AdjudicatedLoaded convertDeclared(Fixture fixture,
            Map<String, QuestionStatus> statuses, AdjudicatedConverter.Exclusions exclusions,
            ExpansionDecision.Declared declared) {
        return AdjudicatedConverter.classAWithDeclaredPending(fixture.sources().export().key(),
                statuses, fixture.pairs(), Set.of(STEM), caseIds(fixture), exclusions,
                widths(fixture), declared);
    }

    private static void createLock(Fixture fixture, Path path, ArmKeySeal.Seal sealed,
            LockRecord.FindingsValidator validator) throws Exception {
        LockRecord.create(path, LockRecord.Kind.PRIMARY, Set.of(STEM), fixture.findings(),
                sealed, fixture.seal(), validator, ArmKeyGateFixtures.LOCKED);
    }

    private static void validate(LockRecord.FindingsValidator validator, Fixture fixture) {
        validator.validate(fixture.findings());
    }

    private static ExpansionDecision.Declared verifyUnlisted(Fixture fixture) throws Exception {
        return verify(fixture, Set.of());
    }

    private static Map<String, ExpansionObligation.Disclosure> verifyZeroAllocation(
            ClassAEvidenceAssembler.SpotPlan plan, TwoStem evidence, SeedStreamRegistry registry) {
        return ExpansionObligation.verify(plan, Map.of(LONG_WORKFLOW, Set.of(LONG_A, LONG_B)),
                evidence.pairs(), evidence.sets(), evidence.statuses(), evidence.entries(), registry);
    }

    private static ExpansionDecision.Context context(Fixture fixture) {
        ClassASources sources = fixture.sources();
        return new ExpansionDecision.Context(fixture.decisionFile(), sources.freezeFile(),
                sources.parametersFile(), fixture.seal(), sources.drawLogFile(), sources.seedRegistryFile());
    }

    @Test void onlyVerifierCanConstructDeclaration() {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        String source = "package com.studysnap.backend.benchmark;"
                + "class ConstructorProbe { Object value = new ExpansionDecision.Declared("
                + "java.util.Map.of(), java.util.Set.of(), null); }";
        JavaFileObject probe = new SimpleJavaFileObject(URI.create("string:///ConstructorProbe.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        String classes = ExpansionDecision.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String classpath = classes + System.getProperty("path.separator") + System.getProperty("java.class.path");
        boolean compiled = compiler.getTask(null, null, diagnostics,
                List.of("-classpath", classpath, "-d", temp.toString()), null, List.of(probe)).call();
        assertThat(compiled).isFalse();
        assertThat(diagnostics.getDiagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.getCode()).contains("report.access"));
        assertThat(ExpansionDecision.Declared.NONE.itemIds()).isEmpty();
    }

    @Test void missingDecisionKeepsStrictPendingRuleAndCompleteNoFileStillWorks() throws Exception {
        Fixture fixture = fixture(5);
        assertThatThrownBy(() -> assemble(fixture))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("PENDING_REVIEW");
        for (String pair : fixture.drawn()) review(fixture, pair, 4, CLEAN);
        ClassAAssembly completed = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(completed.declared()).isSameAs(ExpansionDecision.Declared.NONE);
        assertThat(completed.expansionObligations().get(STEM).drawnPairs())
                .containsExactlyElementsOf(fixture.drawn());
    }

    @Test void declinedAndHalfReviewedSuffixAssembleWithPendingAlsoUnresolved() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CLEAN);
        review(fixture, fixture.drawn().get(1), 1, CLEAN);
        writeDecision(fixture, decision(fixture, true, false, null,
                fixture.drawn().subList(1, 3)), true);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.declared().itemIds()).hasSize(7);
        assertThat(assembly.declared().byWorkflow().get(STEM).declinedOrExhausted()).isTrue();
        assertThat(assembly.facts().byCaseArm().values()).anySatisfy(fact -> {
            assertThat(fact.pendingQuestions()).isPositive();
            assertThat(fact.unresolvedQuestions()).isEqualTo(fact.pendingQuestions());
            assertThat(fact.criticalQuestions()).isZero();
            assertThat(fact.qualityFailQuestions()).isZero();
            assertThat(fact.humanCleared()).isZero();
        });
        assertThat(assembly.digests().values()).containsKey(DECISION_DIGEST_ROLE);
        assertThat(assembly.expansionObligations().get(STEM).drawnPairs())
                .containsExactlyElementsOf(fixture.drawn());
        Map<String, QuestionStatus> finalStatuses = new LinkedHashMap<>(derivedStatuses(fixture));
        ClassAExport export = fixture.sources().export();
        var scored = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        for (String id : assembly.declared().itemIds())
            finalStatuses.put(id, FinalStatus.of(scored.get(id), ClassAAdjudicationFixture.MAPPING, false));
        AdjudicatedLoaded direct = AdjudicatedConverter.classA(export.key(), finalStatuses, fixture.pairs(),
                Set.of(STEM), caseIds(fixture), export.exclusionsFileOrNull(),
                export.expectedExclusionsSha256OrNull(), widths(fixture));
        for (String id : caseIds(fixture)) if (assembly.facts().get(id, MINI).pendingQuestions() == 0
                && assembly.facts().get(id, LUNA).pendingQuestions() == 0)
            for (String arm : List.of(MINI, LUNA))
                assertThat(assembly.facts().get(id, arm)).isEqualTo(direct.get(id, arm));
        String halfReviewed = fixture.pairs().itemsOf(fixture.drawn().get(1), SIDE_A).getFirst();
        assertThat(assembly.statuses().get(halfReviewed).state()).isEqualTo(QuestionStatus.State.FINAL);
        assertThat(assembly.statuses().get(halfReviewed).f()).isEqualTo(ClassAScoreSchemaV2.Verdict.CLEAN);
        PairEvidence unfinished = PairEvidence.build(assembly.pairs(), assembly.statuses())
                .get(fixture.drawn().get(1));
        assertThat(unfinished.complete()).isFalse();
        assertThat(Expansion.PairReview.from(unfinished).complete()).isFalse();
    }

    @Test void declaredPairsMustBeExactlyPendingSuffixAndOnlyExpansion() throws Exception {
        Fixture fixture = fixture(5);
        ObjectNode base = decision(fixture, true, false, null, fixture.drawn());
        writeDecision(fixture, base, true);
        assertThat(verify(fixture).itemIds()).hasSize(12);
        ObjectNode omitted = base.deepCopy();
        ((ArrayNode) omitted.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM)).remove(2);
        Files.writeString(fixture.decisionFile(), omitted.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        ObjectNode underDeclaredSuffix = decision(fixture, true, false, null,
                fixture.drawn().subList(1, fixture.drawn().size()));
        Files.writeString(fixture.decisionFile(), underDeclaredSuffix.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("pending expansion pairs");
        ObjectNode spot = base.deepCopy();
        ((ArrayNode) spot.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM))
                .removeAll().add(fixture.trigger());
        Files.writeString(fixture.decisionFile(), spot.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        review(fixture, fixture.drawn().getFirst(), 4, CLEAN);
        Files.writeString(fixture.decisionFile(), base.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        assertThatThrownBy(() -> checkStrict(fixture))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> convertStrict(fixture))
                .isInstanceOf(InvalidEvidence.class);
    }

    private static Map<String, QuestionStatus> derivedStatuses(Fixture fixture) throws Exception {
        ClassAExport export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        Set<String> selected = new LinkedHashSet<>();
        new DrawLog(fixture.sources().drawLogFile()).entries().forEach(entry -> selected.addAll(entry.selectedPairIds()));
        Set<String> required = ReviewSets.reviewRequiredPairs(sets, selected);
        Map<String, QuestionStatus> result = new LinkedHashMap<>();
        for (var item : scores.values()) result.put(item.id(), FinalStatus.of(item,
                ClassAAdjudicationFixture.MAPPING, required.contains(fixture.pairs().pairOf(item.id()))));
        return result;
    }
    private static Set<String> caseIds(Fixture fixture) {
        Set<String> ids = new LinkedHashSet<>();
        fixture.sources().export().key().rows().forEach(row -> ids.add(row.caseId()));
        return ids;
    }
    private static Map<String, Integer> widths(Fixture fixture) {
        Map<String, Integer> widths = new LinkedHashMap<>();
        caseIds(fixture).forEach(id -> widths.put(id, 2));
        return widths;
    }

    @Test void stopNeedsDistinctCompletedSecondMissAndNoLaterReview() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CRITICAL);
        writeDecision(fixture, decision(fixture, false, false, STOP_VALUE,
                fixture.drawn().subList(1, 3)), true);
        ClassAAssembly stopped = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(stopped.declared().itemIds()).hasSize(8);
        assertThat(stopped.declared().byWorkflow().get(STEM).stopReason()).isEqualTo(STOP_VALUE);
        String reviewedItem = fixture.pairs().itemsOf(fixture.drawn().getFirst(), SIDE_A).getFirst();
        assertThat(stopped.statuses().get(reviewedItem).state()).isEqualTo(QuestionStatus.State.FINAL);
        assertThat(stopped.statuses().get(reviewedItem).f()).isEqualTo(ClassAScoreSchemaV2.Verdict.CRITICAL);
        writeDecision(fixture, decision(fixture, true, false, null,
                fixture.drawn().subList(1, 3)), true);
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        writeDecision(fixture, decision(fixture, false, false, STOP_VALUE,
                fixture.drawn().subList(1, 3)), true);
        review(fixture, fixture.drawn().get(1), 1, CLEAN);
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void completionWithEitherCapDecisionAndInvalidCombinations() throws Exception {
        Fixture fixture = fixture(5);
        for (String pair : fixture.drawn()) review(fixture, pair, 4, CLEAN);
        writeDecision(fixture, decision(fixture, false, true, null, List.of()), true);
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).declared().byWorkflow().get(STEM)
                .approvedLargerCap()).isTrue();
        writeDecision(fixture, decision(fixture, false, false, null, List.of()), true);
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).declared().byWorkflow().get(STEM)
                .approvedLargerCap()).isFalse();
        ObjectNode invalid = decision(fixture, false, true, null, List.of());
        ((ObjectNode) invalid.path(DECISIONS_FIELD).get(0)).put(CAP_FIELD, 120);
        Files.writeString(fixture.decisionFile(), invalid.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        ((ObjectNode) invalid.path(DECISIONS_FIELD).get(0)).put(CAP_FIELD, 121)
                .put(APPROVED_FIELD, false);
        Files.writeString(fixture.decisionFile(), invalid.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void hashesSchemaListingAndLockHashFailClosed() throws Exception {
        Fixture fixture = fixture(5);
        ObjectNode valid = decision(fixture, true, false, null, fixture.drawn());
        writeDecision(fixture, valid, false);
        assertThatThrownBy(() -> assemble(fixture))
                .isInstanceOf(InvalidExpansionDecision.class);
        fixture.findings().add(fixture.decisionFile());
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThat(verify(fixture).itemIds()).hasSize(12);
        for (String field : List.of(FREEZE_HASH, PARAMETERS_HASH, SEAL_HASH, DRAW_HASH)) {
            ObjectNode mutated = valid.deepCopy();
            ((ObjectNode) mutated.path(DECISIONS_FIELD).get(0)).put(field, "0".repeat(64));
            Files.writeString(fixture.decisionFile(), mutated.toString());
            assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        }
        ObjectNode unknown = valid.deepCopy();
        ((ObjectNode) unknown.path(DECISIONS_FIELD).get(0)).put("signer", "forged");
        Files.writeString(fixture.decisionFile(), unknown.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
        assertThatThrownBy(() -> assemble(fixture))
                .isInstanceOf(LockRecord.LockBroken.class);
    }

    private static LockReadiness.ExpansionObligationContext obligationContext(Fixture fixture) {
        ClassASources sources = fixture.sources();
        ClassAExport export = sources.export();
        return new LockReadiness.ExpansionObligationContext(export.packetDirectory(), sources.freezeFile(),
                sources.manifests().get(PRIMARY), sources.manifests().get(PLAN), sources.seedRegistryFile(),
                sources.drawLogFile(), export.pScores(), export.sScores(), export.hScores(),
                ClassAAdjudicationFixture.MAPPING, Set.of(fixture.stem()), sources.parametersFile(), export.sealFile());
    }

    @Test void lockValidatorsUseFilesAndOnlyComposedValidatorAcceptsVerifiedPending() throws Exception {
        Fixture fixture = fixture(5);
        var obligation = obligationContext(fixture);
        var decisionContext = context(fixture);
        var sealed = ArmKeySeal.load(fixture.seal());
        Path first = fixture.root().resolve("strict-lock.json");
        var strict = LockReadiness.asValidator(Map::of, Set::of, obligation);
        assertThatThrownBy(() -> createLock(fixture, first, sealed, strict))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidEvidence.class);
        Path second = fixture.root().resolve("composed-lock.json");
        var composed = LockReadiness.asValidator(Map::of, Set::of, obligation, decisionContext);
        assertThatThrownBy(() -> createLock(fixture, second, sealed, composed))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidEvidence.class);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        assertThatThrownBy(() -> createLock(fixture, first, sealed, strict))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidEvidence.class);
        LockRecord.create(second, LockRecord.Kind.PRIMARY, Set.of(STEM), fixture.findings(), sealed,
                fixture.seal(), composed, ArmKeyGateFixtures.LOCKED);
        assertThat(Files.exists(second)).isTrue();
        var mismatched = new ExpansionDecision.Context(fixture.decisionFile(), fixture.sources().freezeFile(),
                fixture.sources().parametersFile(), fixture.seal(), fixture.root().resolve("other-draws.jsonl"),
                fixture.sources().seedRegistryFile());
        assertThatThrownBy(() -> LockReadiness.asValidator(Map::of, Set::of, obligation, mismatched))
                .isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void twoPreExpansionDoubleMissesCanStopWithAllDrawnPairsPending() throws Exception {
        Fixture fixture = fixture(6, true);
        writeDecision(fixture, decision(fixture, false, false, STOP_VALUE, fixture.drawn()), true);
        ClassAAssembly assembled = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembled.expansionObligations().get(STEM).triggerPairs()).hasSize(2);
        assertThat(assembled.declared().itemIds()).hasSize(12);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("declined expansion basis");
        writeDecision(fixture, decision(fixture, false, false, STOP_VALUE, fixture.drawn()), true);
        review(fixture, fixture.drawn().getFirst(), 1, CLEAN);
        assertThatThrownBy(() -> assemble(fixture))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("second double miss");
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void declaredFactsReturnTheRealFrbPipelineToOwnerAndDoNotSpreadToOtherCase() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        String pendingCase = assembly.facts().requestedQuestionsByCaseId().keySet().stream()
                .filter(id -> assembly.facts().get(id, MINI) != null
                        && assembly.facts().get(id, MINI).pendingQuestions() > 0)
                .findFirst().orElseThrow();
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "downstream-");
        FrbPipelineTest.Fixture pipeline = builder.fixture(false, 0, 2, false);
        FrbEvidenceBundle original = pipeline.frb().bundle();
        Map<String, AdjudicatedFacts> facts = new HashMap<>(original.evidence().byCaseArm());
        facts.put(FIRST_CASE_PREFIX + MINI, assembly.facts().get(pendingCase, MINI));
        facts.put(FIRST_CASE_PREFIX + LUNA, assembly.facts().get(pendingCase, LUNA));
        FrbEvidenceBundle changedBundle = new FrbEvidenceBundle(new AdjudicatedLoaded(facts,
                original.evidence().excludedCaseIds(), original.evidence().requestedQuestionsByCaseId()),
                original.requestedQuestionsByCaseId(), original.reserveDrawnByWorkflow(),
                original.workflowsAwaitingReserve());
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(changedBundle,
                pipeline.frb().reserveRecordFile(), pipeline.frb().verification(), pipeline.frb().provenance(),
                pipeline.frb().routeClassifications(), pipeline.frb().operationalCostReview());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(pipeline.run(), changed);
        var route = result.routes().get(ROUTE);
        assertThat(route.verdict()).isNotEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(route.workflows().get(STEM).unresolvedCaseIdsByCause()).containsKey("UNSCORABLE");
        var first = result.caseRecords().get(ROUTE).stream()
                .filter(row -> row.id().equals("a-one")).findFirst().orElseThrow();
        var second = result.caseRecords().get(ROUTE).stream()
                .filter(row -> row.id().equals("a-two")).findFirst().orElseThrow();
        assertThat(first.baseline().unresolvedQuestions()).isPositive();
        assertThat(second.baseline().unresolvedQuestions()).isZero();
        var classified = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                pipeline.frb().routeClassifications(), Set.of(ROUTE)).get(ROUTE);
        assertThat(classified.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(classified.causes()).contains(PENDING_CAUSE);
        assertThat(classified.classification()).isNotEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
        var t3 = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), route.d(),
                route.isolatedCriticalIncident(), route.parameterSha256(), route.workflows(), route.frb());
        var t3Classified = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, t3),
                pipeline.frb().routeClassifications(), Set.of(ROUTE)).get(ROUTE);
        assertThat(t3Classified.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(t3Classified.causes()).contains(PENDING_CAUSE);
        FrbClassificationInput conditional = new FrbClassificationInput(Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), false, false, Set.of(),
                Map.of(STEM, MISSING_CAUSE), Map.of(SAFETY_HEAD, "0:head"));
        var conditionalClassified = ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, route),
                Map.of(ROUTE, conditional), Set.of(ROUTE)).get(ROUTE);
        assertThat(conditionalClassified.classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(conditionalClassified.causes()).contains(PENDING_CAUSE, MISSING_CAUSE);
    }

    private static void invalid(Fixture fixture, ObjectNode body) throws Exception {
        Files.writeString(fixture.decisionFile(), body.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void malformedDecisionsAndForeignPairsFailClosed() throws Exception {
        Fixture fixture = fixture(6);
        ObjectNode valid = decision(fixture, true, false, null, fixture.drawn());
        writeDecision(fixture, valid, true);
        assertThat(verify(fixture).itemIds()).hasSize(12);
        ObjectNode foreignStem = valid.deepCopy();
        ObjectNode map = (ObjectNode) foreignStem.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD);
        ArrayNode moved = (ArrayNode) map.remove(STEM);
        map.set(PLAN_STEM, moved);
        invalid(fixture, foreignStem);
        ObjectNode foreignWorkflow = valid.deepCopy();
        ((ObjectNode) foreignWorkflow.path(DECISIONS_FIELD).get(0)).put(WORKFLOW_FIELD, PLAN_STEM);
        invalid(fixture, foreignWorkflow);
        ObjectNode unknownPair = valid.deepCopy();
        ((ArrayNode) unknownPair.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM))
                .set(0, IntegritySupport.JSON.getNodeFactory().textNode("NO_SUCH_PAIR"));
        invalid(fixture, unknownPair);
        String unselected = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.trigger()) && !fixture.drawn().contains(id))
                .findFirst().orElseThrow();
        ObjectNode frame = valid.deepCopy();
        ((ArrayNode) frame.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM))
                .set(0, IntegritySupport.JSON.getNodeFactory().textNode(unselected));
        invalid(fixture, frame);
        ObjectNode empty = decision(fixture, true, false, null, List.of());
        invalid(fixture, empty);
        ObjectNode falsePending = decision(fixture, false, false, null, List.of());
        invalid(fixture, falsePending);
        ObjectNode falseListed = decision(fixture, false, false, null, fixture.drawn());
        invalid(fixture, falseListed);
        ObjectNode badFlag = valid.deepCopy();
        ((ObjectNode) badFlag.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, "true");
        invalid(fixture, badFlag);
        ObjectNode badApproval = valid.deepCopy();
        ((ObjectNode) badApproval.path(DECISIONS_FIELD).get(0)).put(APPROVED_FIELD, "false");
        invalid(fixture, badApproval);
        ObjectNode badStop = valid.deepCopy();
        ((ObjectNode) badStop.path(DECISIONS_FIELD).get(0)).put(STOP_FIELD, "OTHER_STOP");
        invalid(fixture, badStop);
        ObjectNode conflicting = valid.deepCopy();
        ((ObjectNode) conflicting.path(DECISIONS_FIELD).get(0)).put(STOP_FIELD, STOP_VALUE);
        invalid(fixture, conflicting);
        ObjectNode extra = valid.deepCopy();
        ((ArrayNode) extra.path(DECISIONS_FIELD)).add(extra.path(DECISIONS_FIELD).get(0).deepCopy());
        invalid(fixture, extra);
        Files.writeString(fixture.decisionFile(), valid.toString().replace(QUOTE + STEM + ARRAY_FIELD_SUFFIX,
                QUOTE + STEM + "\":[],\"" + STEM + ARRAY_FIELD_SUFFIX));
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void suffixAllFinalAndEvidenceDerivedStopBoundariesFailClosed() throws Exception {
        Fixture suffix = fixture(5);
        review(suffix, suffix.drawn().get(1), 4, CLEAN);
        writeDecision(suffix, decision(suffix, true, false, null,
                List.of(suffix.drawn().getFirst(), suffix.drawn().getLast())), true);
        assertThatThrownBy(() -> verify(suffix)).isInstanceOf(InvalidExpansionDecision.class);
        Fixture finalPair = fixture(5);
        review(finalPair, finalPair.drawn().getFirst(), 4, CLEAN);
        writeDecision(finalPair, decision(finalPair, true, false, null, finalPair.drawn()), true);
        assertThatThrownBy(() -> verify(finalPair)).isInstanceOf(InvalidExpansionDecision.class);
        Fixture oneMiss = fixture(5);
        writeDecision(oneMiss, decision(oneMiss, false, false, STOP_VALUE, oneMiss.drawn()), true);
        assertThatThrownBy(() -> verify(oneMiss)).isInstanceOf(InvalidExpansionDecision.class);
        Fixture half = fixture(5);
        review(half, half.drawn().getFirst(), 1, CRITICAL);
        writeDecision(half, decision(half, false, false, STOP_VALUE, half.drawn()), true);
        assertThatThrownBy(() -> verify(half)).isInstanceOf(InvalidExpansionDecision.class);
        Fixture before = fixture(5);
        review(before, before.drawn().get(1), 4, CRITICAL);
        writeDecision(before, decision(before, false, false, STOP_VALUE,
                List.of(before.drawn().getFirst(), before.drawn().getLast())), true);
        assertThatThrownBy(() -> verify(before)).isInstanceOf(InvalidExpansionDecision.class);
        Fixture completed = fixture(5);
        for (String pair : completed.drawn()) review(completed, pair, 4, CLEAN);
        writeDecision(completed, decision(completed, false, false, STOP_VALUE, List.of()), true);
        assertThatThrownBy(() -> verify(completed)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void composedValidatorRejectsEveryContextFileMismatchAndSupplierDisagreement() throws Exception {
        Fixture fixture = fixture(5);
        var obligation = obligationContext(fixture);
        var decision = context(fixture);
        Path other = fixture.root().resolve("other-file.json");
        List<ExpansionDecision.Context> mismatches = List.of(
                new ExpansionDecision.Context(decision.decisionFile(), other, decision.parametersFile(),
                        decision.sealFile(), decision.drawLogFile(), decision.registryFile()),
                new ExpansionDecision.Context(decision.decisionFile(), decision.freezeFile(), other,
                        decision.sealFile(), decision.drawLogFile(), decision.registryFile()),
                new ExpansionDecision.Context(decision.decisionFile(), decision.freezeFile(),
                        decision.parametersFile(), other, decision.drawLogFile(), decision.registryFile()),
                new ExpansionDecision.Context(decision.decisionFile(), decision.freezeFile(),
                        decision.parametersFile(), decision.sealFile(), other, decision.registryFile()),
                new ExpansionDecision.Context(decision.decisionFile(), decision.freezeFile(),
                        decision.parametersFile(), decision.sealFile(), decision.drawLogFile(), other));
        for (var mismatch : mismatches)
            assertThatThrownBy(() -> LockReadiness.asValidator(Map::of, Set::of, obligation, mismatch))
                    .isInstanceOf(InvalidExpansionDecision.class);
        var actual = derivedStatuses(fixture);
        var strict = LockReadiness.asValidator(() -> Map.of(FOREIGN_ITEM, actual.values().iterator().next()),
                Set::of, obligation);
        assertThatThrownBy(() -> validate(strict, fixture))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining(STATUS_MISMATCH);
        var composed = LockReadiness.asValidator(() -> Map.of(FOREIGN_ITEM, actual.values().iterator().next()),
                Set::of, obligation, decision);
        assertThatThrownBy(() -> validate(composed, fixture))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining(STATUS_MISMATCH);
    }

    @Test void decisionDigestIsContentFreeAndAssemblyPreservesEveryFindingByte() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        byte[] draw = Files.readAllBytes(fixture.sources().drawLogFile());
        byte[] registry = Files.readAllBytes(fixture.sources().seedRegistryFile());
        byte[] decision = Files.readAllBytes(fixture.decisionFile());
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.digests().values().get(DECISION_DIGEST_ROLE))
                .isEqualTo(FreezeRecord.sha256(fixture.decisionFile()));
        assertThat(assembly.digests().values().toString()).doesNotContain(STOP_VALUE, fixture.drawn().getFirst());
        assertThat(Files.readAllBytes(fixture.sources().drawLogFile())).isEqualTo(draw);
        assertThat(Files.readAllBytes(fixture.sources().seedRegistryFile())).isEqualTo(registry);
        assertThat(Files.readAllBytes(fixture.decisionFile())).isEqualTo(decision);
    }

    @Test void composedValidatorKeepsTheObligationAndFindingRequirements() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        var validator = LockReadiness.asValidator(Map::of, Set::of,
                obligationContext(fixture), context(fixture));
        validator.validate(fixture.findings());
        List<Path> withoutDraw = new ArrayList<>(fixture.findings());
        withoutDraw.remove(fixture.sources().drawLogFile());
        assertThatThrownBy(() -> validator.validate(withoutDraw))
                .isInstanceOf(InvalidClassAAssembly.class);
        List<String> draws = Files.readAllLines(fixture.sources().drawLogFile());
        Files.write(fixture.sources().drawLogFile(), draws.subList(0, draws.size() - 1));
        assertThatThrownBy(() -> validate(validator, fixture))
                .isInstanceOfAny(InvalidClassAAssembly.class, InvalidExpansionDecision.class);
    }

    @Test void composedValidatorRejectsValidButUnderallocatedRoundOneDraw() throws Exception {
        Fixture fixture = fixture(5);
        Path drawFile = fixture.sources().drawLogFile();
        Files.delete(drawFile);
        SeedStreamRegistry registry = SeedStreamRegistry.load(fixture.sources().seedRegistryFile());
        List<String> frame = fixture.pairs().pairs().stream().map(PairMap.Pair::id).toList();
        String trigger = PairSampler.draw(registry, new DrawLog(drawFile), fixture.pairs(), STEM,
                SeedStreamRegistry.SPOT_CHECK, 1, frame, 1, CLOCK).selectedPairIds().getFirst();
        assertThat(trigger).isEqualTo(fixture.trigger());
        List<String> fewer = PairSampler.draw(registry, new DrawLog(drawFile), fixture.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 1, frame.stream().filter(id -> !id.equals(trigger)).toList(),
                2, CLOCK).selectedPairIds();
        Fixture altered = new Fixture(fixture.sources(), fixture.pairs(), fewer, trigger,
                fixture.findingsDir(), fixture.findings(), fixture.lock(), fixture.seal(), fixture.root(), STEM);
        writeDecision(altered, decision(altered, true, false, null, fewer), true);
        var validator = LockReadiness.asValidator(Map::of, Set::of,
                obligationContext(altered), context(altered));
        assertThatThrownBy(() -> validate(validator, altered))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void decisionMustBeListedAndOldTwoArgumentValidatorRemainsStrict() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        var old = LockReadiness.asValidator(() -> derivedStatusesUnchecked(fixture), Set::of);
        assertThatThrownBy(() -> validate(old, fixture)).isInstanceOf(InvalidEvidence.class);
        List<Path> unlisted = new ArrayList<>(fixture.findings());
        unlisted.remove(fixture.decisionFile());
        var composed = LockReadiness.asValidator(Map::of, Set::of,
                obligationContext(fixture), context(fixture));
        assertThatThrownBy(() -> composed.validate(unlisted)).isInstanceOf(InvalidExpansionDecision.class);
        assertThatThrownBy(() -> verifyUnlisted(fixture)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("lock finding");
    }

    private static Map<String, QuestionStatus> derivedStatusesUnchecked(Fixture fixture) {
        try { return derivedStatuses(fixture); }
        catch (Exception exception) { throw new InvalidExpansionDecision("Cannot derive test statuses", exception); }
    }

    @Test void mandatoryPairAndMissingPreExpansionTriggerCannotBeDeclared() throws Exception {
        Fixture mandatory = fixture(6, true);
        writeDecision(mandatory, decision(mandatory, true, false, null, mandatory.drawn()), true);
        String mandatoryPair = mandatory.pairs().pairs().getLast().id();
        ObjectNode extra = decision(mandatory, true, false, null, mandatory.drawn());
        ((ArrayNode) extra.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM)).add(mandatoryPair);
        invalid(mandatory, extra);

        Fixture noTrigger = fixture(5);
        List<String> cleanHuman = new ArrayList<>();
        for (String side : List.of(SIDE_A, SIDE_B))
            for (String item : noTrigger.pairs().itemsOf(noTrigger.trigger(), side))
                cleanHuman.add(ClassAAdjudicationFixture.human(item, CLEAN, false).toString());
        Files.write(noTrigger.sources().export().hScores(), cleanHuman);
        writeDecision(noTrigger, decision(noTrigger, true, false, null, noTrigger.drawn()), true);
        assertThatThrownBy(() -> verify(noTrigger)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining(TRIGGER_FIELD);
        review(noTrigger, noTrigger.drawn().getFirst(), 4, CRITICAL);
        writeDecision(noTrigger, decision(noTrigger, true, false, null,
                noTrigger.drawn().subList(1, noTrigger.drawn().size())), true);
        assertThatThrownBy(() -> verify(noTrigger)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining(TRIGGER_FIELD);
    }

    @Test void eachBindingRejectsAnotherRunsRealHashAndChangedDecisionBreaksLock() throws Exception {
        Fixture first = fixture(5);
        Fixture other = fixture(5);
        for (Path path : List.of(other.sources().freezeFile(), other.sources().parametersFile(),
                other.seal(), other.sources().drawLogFile()))
            Files.writeString(path, Files.readString(path) + SINGLE_SPACE);
        ObjectNode valid = decision(first, true, false, null, first.drawn());
        writeDecision(first, valid, true);
        Map<String, Path> foreign = Map.of(FREEZE_HASH, other.sources().freezeFile(),
                PARAMETERS_HASH, other.sources().parametersFile(), SEAL_HASH, other.seal(),
                DRAW_HASH, other.sources().drawLogFile());
        for (var binding : foreign.entrySet()) {
            ObjectNode changed = valid.deepCopy();
            ((ObjectNode) changed.path(DECISIONS_FIELD).get(0))
                    .put(binding.getKey(), FreezeRecord.sha256(binding.getValue()));
            invalid(first, changed);
        }
        Files.writeString(first.decisionFile(), valid.toString());
        relock(first.lock(), first.seal(), first.findings());
        Files.writeString(first.decisionFile(), valid.toString() + SINGLE_SPACE);
        assertThatThrownBy(() -> assemble(first))
                .isInstanceOf(LockRecord.LockBroken.class);
    }

    @Test void declaredItemMustStillBePendingWithNullFinalVerdict() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        ExpansionDecision.Declared declared = verify(fixture);
        Map<String, QuestionStatus> statuses = new LinkedHashMap<>(derivedStatuses(fixture));
        String item = declared.itemIds().iterator().next();
        QuestionStatus old = statuses.get(item);
        statuses.put(item, new QuestionStatus(old.itemId(), old.state(), ClassAScoreSchemaV2.Verdict.CLEAN,
                old.reviewReasons(), old.doubleMiss(), old.scorerFailureCritical(), old.humanCleared(),
                old.humanDowngraded(), old.overturnedAgreedQualityFail(), old.t2Event(), old.t3Event(),
                old.pairMissing(), old.unreviewedAgreedQualityFail(), old.scorerMissNoncritical(),
                old.agreedQualityReviewed()));
        assertThatThrownBy(() -> checkDeclared(statuses, declared))
                .isInstanceOf(InvalidExpansionDecision.class);
        AdjudicatedConverter.Exclusions exclusions = new AdjudicatedConverter.Exclusions(
                fixture.sources().export().exclusionsFileOrNull(),
                fixture.sources().export().expectedExclusionsSha256OrNull());
        assertThatThrownBy(() -> convertDeclared(fixture, statuses, exclusions, declared))
                .isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void extraPendingItemOutsideVerifiedDeclarationIsRefusedByBothGates() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        ExpansionDecision.Declared declared = verify(fixture);
        Map<String, QuestionStatus> statuses = new LinkedHashMap<>(derivedStatuses(fixture));
        String outside = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !fixture.drawn().contains(id) && !id.equals(fixture.trigger()))
                .flatMap(id -> fixture.pairs().itemsOf(id, SIDE_A).stream()).findFirst().orElseThrow();
        statuses.put(outside, syntheticStatus(outside, true, false));
        assertThatThrownBy(() -> checkDeclared(statuses, declared))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(outside);
        AdjudicatedConverter.Exclusions exclusions = new AdjudicatedConverter.Exclusions(
                fixture.sources().export().exclusionsFileOrNull(),
                fixture.sources().export().expectedExclusionsSha256OrNull());
        assertThatThrownBy(() -> convertDeclared(fixture, statuses, exclusions, declared))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(outside);
    }

    @Test void legacyClassifierIsBlockedBeforeItCanReadDeclaredFacts() throws Exception {
        Fixture fixture = fixture(5);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        ClassAAssembly assembled = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembled.declared().itemIds()).isNotEmpty();
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "legacy-");
        ResultsPipeline.FrbRunInput frb = builder.fixture(false, 0, 2, false).run();
        Path absent = temp.resolve("absent-key.jsonl");
        ResultsPipeline.Input legacy = new ResultsPipeline.Input(frb.repoRoot(), frb.freeze(),
                frb.parameters(), frb.mapping(), frb.manifests(), frb.baselineMini(),
                frb.baseline41(), frb.candidate(),
                new ScoreEvidence.FilesA(absent, absent, absent, absent, absent),
                new ScoreEvidence.FilesB(absent, absent, Map.of()), Map.of(), frb.compatibilityReports(),
                frb.headCommitTime(), frb.ownerDispositions(), temp.resolve("legacy-report.json"),
                temp.resolve("legacy-trigger.json"), true);
        assertThatThrownBy(() -> ResultsPipeline.run(legacy))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("FRB parameters require runFrb");
        Path nonFrb = Files.createTempFile(temp, "non-frb", ".json");
        ObjectNode withoutFrb = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readString(fixture.sources().parametersFile()));
        withoutFrb.remove("frb");
        Files.writeString(nonFrb, withoutFrb.toString());
        detached(nonFrb);
        assertThat(FrozenGateParameters.load(nonFrb).frbEnabled).isFalse();
        Path adjustedFreeze = temp.resolve("non-frb-freeze.json");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readString(fixture.sources().freezeFile()));
        freeze.put(PARAMETERS_HASH, FreezeRecord.sha256(nonFrb));
        Files.writeString(adjustedFreeze, freeze.toString());
        detached(adjustedFreeze);
        assertThat(FrozenGateParameters.load(nonFrb).sha256)
                .isEqualTo(FreezeRecord.load(adjustedFreeze).parametersSha256());
        ClassASources sources = fixture.sources();
        ClassASources altered = new ClassASources(adjustedFreeze, nonFrb, sources.mappingFile(),
                sources.manifests(), sources.export(), sources.drawLogFile(), sources.seedRegistryFile());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(altered))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    private static final String LONG_A = "LONG_EXAM_DIRECT";
    private static final String LONG_B = "LONG_EXAM_LONG_EXAM_POOL";
    private static final String LONG_WORKFLOW = "LONG_EXAM";

    private record TwoStem(PairMap pairs, Map<String, QuestionStatus> statuses, ReviewSets sets,
            List<DrawLog.Entry> entries, ExpansionDecision.Context context,
            List<String> aPairs, List<String> bPairs, ArmKeyAccess.OpenedKey key) {}

    private TwoStem twoStem() throws Exception { return twoStem(3, 3); }

    private TwoStem twoStem(int firstStemCases, int secondStemCases) throws Exception {
        Path root = Files.createTempDirectory(temp, "two-stem-");
        var source = new ClassACaseManifest.Source(SOURCE_TITLE, ClassAScoreSchemaV2Test.SOURCE, SOURCE_BOUNDARY,
                List.of(SOURCE_MEMBRANE), List.of(SOURCE_CELLS), BIOLOGY, BIOLOGY, null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet(QUESTION_FORMAT).question().deepCopy());
        for (int i = 0; i < firstStemCases + secondStemCases; i++) {
            String id = "long-" + i;
            cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                    SYNTHETIC, LearnerLevel.COLLEGE, null, BIOLOGY, false, List.of(source), 2,
                    null, false, i < firstStemCases ? ClassACaseManifest.Subgroup.DIRECT
                            : ClassACaseManifest.Subgroup.LONG_EXAM_POOL));
            outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
            outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
        }
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve(PACKETS), root.resolve(KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS));
        List<String> a = pairs.pairs().stream().filter(pair -> pair.workflow().equals(LONG_A))
                .map(PairMap.Pair::id).toList();
        List<String> b = pairs.pairs().stream().filter(pair -> pair.workflow().equals(LONG_B))
                .map(PairMap.Pair::id).toList();
        assertThat(a).hasSize(firstStemCases);
        assertThat(b).hasSize(secondStemCases);
        Map<String, QuestionStatus> statuses = new LinkedHashMap<>();
        for (String id : pairs.blindItemIds()) statuses.put(id, syntheticStatus(id, false, false));
        for (String side : List.of(SIDE_A, SIDE_B)) for (String id : pairs.itemsOf(a.getFirst(), side))
            statuses.put(id, syntheticStatus(id, false, true));
        for (String pair : b.subList(0, 2)) for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : pairs.itemsOf(pair, side))
                statuses.put(id, syntheticStatus(id, true, false));
        ReviewSets sets = new ReviewSets(Set.of(), Set.copyOf(pairs.pairs().stream()
                .map(PairMap.Pair::id).toList()),
                Map.of(LONG_A, Set.copyOf(a), LONG_B, Set.copyOf(b)),
                Map.of(LONG_A, Set.of(), LONG_B, Set.of()));
        List<DrawLog.Entry> entries = List.of(
                syntheticDraw(LONG_A, SeedStreamRegistry.SPOT_CHECK, List.of(a.getFirst()), 0),
                syntheticDraw(LONG_A, SeedStreamRegistry.EXPANSION, a.subList(1, 3), 1),
                syntheticDraw(LONG_B, SeedStreamRegistry.EXPANSION, b.subList(0, 2), 2));
        Path freeze = root.resolve("freeze.json"), parameters = root.resolve("parameters.json");
        Path draw = root.resolve("draw.jsonl");
        Path registry = root.resolve("registry.json"), decision = root.resolve(ExpansionDecision.FILE_NAME);
        for (Path file : List.of(freeze, parameters, draw, registry))
            Files.writeString(file, file.getFileName().toString());
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path human = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, human, LockRecord.Kind.PRIMARY);
        ArmKeyAccess.OpenedKey key = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, human)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve(ACCESS_LOG_FILE), ArmKeyGateFixtures.OPENED, "two-stem decision fixture");
        ExpansionDecision.Context context = new ExpansionDecision.Context(decision, freeze,
                parameters, seal, draw, registry);
        return new TwoStem(pairs, statuses, sets, entries, context, a, b, key);
    }

    private static QuestionStatus syntheticStatus(String id, boolean pending, boolean doubleMiss) {
        return new QuestionStatus(id, pending ? QuestionStatus.State.PENDING_REVIEW
                : QuestionStatus.State.FINAL, pending ? null : ClassAScoreSchemaV2.Verdict.CLEAN,
                Set.of(), doubleMiss, false, false, false, false, false, false, false,
                false, false, false);
    }

    private static DrawLog.Entry syntheticDraw(String stem, String purpose, List<String> pairs, int seq) {
        return new DrawLog.Entry(stem, purpose, 1, "frame", pairs.size(), pairs.size(), pairs,
                false, "registry", CLOCK, seq, null);
    }

    private static ObjectNode longDecision(TwoStem evidence, List<String> declaredA,
            List<String> declaredB) throws Exception {
        var context = evidence.context();
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put(WORKFLOW_FIELD, LONG_WORKFLOW);
        row.put(FREEZE_HASH, FreezeRecord.sha256(context.freezeFile()));
        row.put(PARAMETERS_HASH, FreezeRecord.sha256(context.parametersFile()));
        row.put(SEAL_HASH, FreezeRecord.sha256(context.sealFile()));
        row.put(DRAW_HASH, FreezeRecord.sha256(context.drawLogFile()));
        row.put(CAP_FIELD, 120);
        row.put(APPROVED_FIELD, false);
        row.put(DECLINED_FIELD, true);
        row.putNull(STOP_FIELD);
        ObjectNode lists = row.putObject(UNREVIEWED_FIELD);
        if (!declaredA.isEmpty()) {
            ArrayNode ids = lists.putArray(LONG_A);
            declaredA.forEach(ids::add);
        }
        if (!declaredB.isEmpty()) {
            ArrayNode ids = lists.putArray(LONG_B);
            declaredB.forEach(ids::add);
        }
        ObjectNode root = IntegritySupport.JSON.createObjectNode();
        root.put(SCHEMA_FIELD, 1);
        root.putArray(DECISIONS_FIELD).add(row);
        return root;
    }

    private static ExpansionDecision.Declared verifyLong(TwoStem evidence) {
        return ExpansionDecision.verify(evidence.context(), evidence.statuses(), evidence.pairs(),
                evidence.sets(), evidence.entries(), Map.of(LONG_WORKFLOW, Set.of(LONG_A, LONG_B)),
                Set.of(evidence.context().decisionFile()));
    }

    @Test void fullyReviewedSecondMissWithUnresolvedItemCanStopBeforePendingSuffix() throws Exception {
        TwoStem evidence = twoStem();
        String second = evidence.aPairs().get(1);
        String unresolved = evidence.pairs().itemsOf(second, SIDE_A).getFirst();
        evidence.statuses().put(unresolved, new QuestionStatus(unresolved, QuestionStatus.State.FINAL,
                ClassAScoreSchemaV2.Verdict.UNRESOLVED, Set.of(), true, false, false, false,
                false, false, false, false, false, false, false));
        for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : evidence.pairs().itemsOf(evidence.aPairs().getLast(), side))
                evidence.statuses().put(id, syntheticStatus(id, true, false));
        ObjectNode body = longDecision(evidence, List.of(evidence.aPairs().getLast()),
                evidence.bPairs().subList(0, 2));
        ((ObjectNode) body.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, false)
                .put(STOP_FIELD, STOP_VALUE);
        Files.writeString(evidence.context().decisionFile(), body.toString());
        PairEvidence pair = PairEvidence.build(evidence.pairs(), evidence.statuses()).get(second);
        assertThat(pair.complete()).isFalse();
        assertThat(Expansion.PairReview.from(pair).complete()).isTrue();
        assertThat(verifyLong(evidence).itemIds()).hasSize(12);
    }

    @Test void longExamTriggerInOneStemCanDeclareOnlyOtherStem() throws Exception {
        TwoStem evidence = twoStem();
        Files.writeString(evidence.context().decisionFile(),
                longDecision(evidence, List.of(), evidence.bPairs().subList(0, 2)).toString());
        ExpansionDecision.Declared declared = verifyLong(evidence);
        assertThat(declared.byWorkflow().get(LONG_WORKFLOW).unreviewedPairsByStem())
                .containsOnlyKeys(LONG_B);
        assertThat(declared.itemIds()).hasSize(8);
        Set<String> allCases = new LinkedHashSet<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        evidence.key().rows().forEach(row -> {
            allCases.add(row.caseId());
            widths.put(row.caseId(), 2);
        });
        AdjudicatedLoaded facts = AdjudicatedConverter.classAWithDeclaredPending(evidence.key(),
                evidence.statuses(), evidence.pairs(), Set.of(LONG_A, LONG_B), allCases,
                new AdjudicatedConverter.Exclusions(null, null), widths, declared);
        Set<String> completedCases = new LinkedHashSet<>();
        evidence.key().rows().stream().filter(row -> row.workflow().equals(LONG_A))
                .forEach(row -> completedCases.add(row.caseId()));
        for (String id : completedCases) for (String arm : List.of(MINI, LUNA)) {
            assertThat(facts.get(id, arm).pendingQuestions()).isZero();
            assertThat(facts.get(id, arm).unresolvedQuestions()).isZero();
        }
        assertThat(facts.byCaseArm().values()).anySatisfy(fact -> {
            assertThat(fact.pendingQuestions()).isPositive();
            assertThat(fact.unresolvedQuestions()).isEqualTo(fact.pendingQuestions());
        });
    }

    @Test void workflowWideSuffixRejectsEarlierStemDeclarationWithLaterReview() throws Exception {
        TwoStem evidence = twoStem();
        Map<String, QuestionStatus> statuses = evidence.statuses();
        for (String pair : evidence.bPairs().subList(0, 2)) for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : evidence.pairs().itemsOf(pair, side))
                statuses.put(id, syntheticStatus(id, false, false));
        for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : evidence.pairs().itemsOf(evidence.aPairs().getLast(), side))
                statuses.put(id, syntheticStatus(id, true, false));
        Files.writeString(evidence.context().decisionFile(),
                longDecision(evidence, List.of(evidence.aPairs().getLast()), List.of()).toString());
        assertThatThrownBy(() -> verifyLong(evidence)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("suffix");
    }

    @Test void singleDrawnPairCanBeDeclaredAndCompletionWithoutTriggerIsRefused() throws Exception {
        Fixture single = fixture(2);
        assertThat(single.drawn()).hasSize(1);
        writeDecision(single, decision(single, true, false, null, single.drawn()), true);
        assertThat(ClassAEvidenceAssembler.assemble(single.sources()).declared().itemIds()).hasSize(4);
        Fixture completedWithoutTrigger = fixture(5);
        List<String> cleanHuman = new ArrayList<>();
        for (String side : List.of(SIDE_A, SIDE_B))
            for (String item : completedWithoutTrigger.pairs().itemsOf(completedWithoutTrigger.trigger(), side))
                cleanHuman.add(ClassAAdjudicationFixture.human(item, CLEAN, false).toString());
        Files.write(completedWithoutTrigger.sources().export().hScores(), cleanHuman);
        for (String pair : completedWithoutTrigger.drawn()) review(completedWithoutTrigger, pair, 4, CLEAN);
        writeDecision(completedWithoutTrigger,
                decision(completedWithoutTrigger, false, false, null, List.of()), true);
        assertThatThrownBy(() -> verify(completedWithoutTrigger))
                .isInstanceOf(InvalidExpansionDecision.class).hasMessageContaining(TRIGGER_FIELD);
    }

    @Test void decisionOnlyValidatorVerifiesItsFindingsAndRejectsSpotDeclaration() throws Exception {
        Fixture fixture = fixture(5);
        ClassAExport export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        var entries = new DrawLog(fixture.sources().drawLogFile()).entries();
        var base = context(fixture);
        var full = new ExpansionDecision.Context(base.decisionFile(), base.freezeFile(),
                base.parametersFile(), base.sealFile(), base.drawLogFile(), base.registryFile(),
                fixture.pairs(), sets, entries, Map.of(STEM, Set.of(STEM)));
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        var validator = LockReadiness.asValidator(() -> derivedStatusesUnchecked(fixture), Set::of, full);
        validator.validate(fixture.findings());
        ObjectNode spot = decision(fixture, true, false, null, fixture.drawn());
        ((ArrayNode) spot.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD).path(STEM))
                .set(0, IntegritySupport.JSON.getNodeFactory().textNode(fixture.trigger()));
        Files.writeString(fixture.decisionFile(), spot.toString());
        assertThatThrownBy(() -> validate(validator, fixture))
                .isInstanceOf(InvalidExpansionDecision.class);
        List<Path> unlisted = new ArrayList<>(fixture.findings());
        unlisted.remove(fixture.decisionFile());
        assertThatThrownBy(() -> validator.validate(unlisted)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void unknownStopReasonIsRefusedEvenWhenEveryStopEvidenceRuleHolds() throws Exception {
        Fixture fixture = fixture(5);
        review(fixture, fixture.drawn().getFirst(), 4, CRITICAL);
        ObjectNode invalid = decision(fixture, false, false, STOP_VALUE,
                fixture.drawn().subList(1, fixture.drawn().size()));
        writeDecision(fixture, invalid, true);
        assertThat(verify(fixture).byWorkflow().get(STEM).stopReason()).isEqualTo(STOP_VALUE);
        ((ObjectNode) invalid.path(DECISIONS_FIELD).get(0)).put(STOP_FIELD, "UNKNOWN_STOP");
        Files.writeString(fixture.decisionFile(), invalid.toString());
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("stop reason");
    }

    @Test void completionRecordCannotCarryAnEmptyListForForeignStem() throws Exception {
        Fixture fixture = fixture(5);
        for (String pair : fixture.drawn()) review(fixture, pair, 4, CLEAN);
        ObjectNode invalid = decision(fixture, false, false, null, List.of());
        ((ObjectNode) invalid.path(DECISIONS_FIELD).get(0).path(UNREVIEWED_FIELD))
                .putArray(PLAN_STEM);
        writeDecision(fixture, invalid, true);
        assertThatThrownBy(() -> verify(fixture)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("Foreign decision stem");
    }

    @Test void declarationForStemWithoutDrawIsRefused() throws Exception {
        TwoStem evidence = twoStem();
        List<DrawLog.Entry> withoutSecondStem = evidence.entries().subList(0, 2);
        TwoStem altered = new TwoStem(evidence.pairs(), evidence.statuses(), evidence.sets(),
                withoutSecondStem, evidence.context(), evidence.aPairs(), evidence.bPairs(), evidence.key());
        Files.writeString(evidence.context().decisionFile(),
                longDecision(evidence, List.of(), evidence.bPairs().subList(0, 2)).toString());
        assertThatThrownBy(() -> verifyLong(altered)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("no expansion draw");
    }

    @Test void zeroAllocatedStemIsRejectedByKeyFreeObligationBeforeDecision() throws Exception {
        TwoStem evidence = twoStem(5, 3);
        Files.writeString(evidence.context().decisionFile(),
                longDecision(evidence, List.of(), evidence.bPairs().subList(0, 2)).toString());
        assertThat(verifyLong(evidence).itemIds()).hasSize(8);
        var plan = new ClassAEvidenceAssembler.SpotPlan(Map.of(LONG_A, 1, LONG_B, 1),
                Map.of(LONG_WORKFLOW, new ClassAEvidenceAssembler.ExpansionPlan(
                        List.of(LONG_A, LONG_B), Map.of(LONG_A, 3, LONG_B, 0))));
        Files.writeString(evidence.context().registryFile(),
                REGISTRY_START
                + REGISTRY_WORKFLOW_START + LONG_A + "\",\"purpose\":\"EXPANSION\",\"rounds\":1},"
                + REGISTRY_WORKFLOW_START + LONG_B + REGISTRY_EXPANSION_END);
        detached(evidence.context().registryFile());
        var registry = SeedStreamRegistry.load(evidence.context().registryFile());
        assertThatThrownBy(() -> verifyZeroAllocation(plan, evidence, registry))
                .isInstanceOf(InvalidClassAAssembly.class)
                .hasMessageContaining("priority allocation");
    }

    @Test void composedValidatorRejectsDecisionForZeroAllocatedStem() throws Exception {
        Fixture fixture = fixture(5);
        Path planFile = fixture.sources().manifests().get(PLAN);
        ObjectNode plan = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(planFile));
        ((ObjectNode) plan.path("expansion").get(0).path("initialAllocation")).put(STEM, 0);
        Files.writeString(planFile, plan.toString());
        Path freezeFile = fixture.sources().freezeFile();
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(freezeFile));
        ((ObjectNode) freeze.path(MANIFESTS_FIELD)).put(planFile.toAbsolutePath().toString(),
                FreezeRecord.sha256(planFile));
        Files.writeString(freezeFile, freeze.toString());
        detached(freezeFile);
        writeDecision(fixture, decision(fixture, true, false, null, fixture.drawn()), true);
        var composed = LockReadiness.asValidator(Map::of, Set::of,
                obligationContext(fixture), context(fixture));
        assertThatThrownBy(() -> validate(composed, fixture))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void boardExamOverCapCompletionSurfacesBothOwnerApprovalChoices() throws Exception {
        String board = "BOARD_EXAM";
        Fixture fixture = fixture(5, false, board, ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT);
        Expansion.ExpansionPlan plan = Expansion.plan(fixture.pairs(), fixture.drawn(), 3,
                ExpansionDecision.PREREGISTERED_EXPANSION_CAP_MINUTES, 15.0, null);
        assertThat(plan.fitsCap()).isFalse();
        for (String pair : fixture.drawn()) review(fixture, pair, 4, CLEAN);
        ObjectNode approved = decision(fixture, false, true, null, List.of());
        ((ObjectNode) approved.path(DECISIONS_FIELD).get(0)).put(CAP_FIELD, 225);
        writeDecision(fixture, approved, true);
        ClassAAssembly accepted = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(accepted.declared().byWorkflow().get(board).approvedLargerCap()).isTrue();
        assertThat(accepted.declared().byWorkflow().get(board).capMinutes()).isEqualTo(225);
        writeDecision(fixture, decision(fixture, false, false, null, List.of()), true);
        ClassAAssembly unapproved = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(unapproved.declared().byWorkflow().get(board).approvedLargerCap()).isFalse();
        assertThat(unapproved.declared().byWorkflow().get(board).capMinutes()).isEqualTo(120);
    }

    /** Switch ledger and review-token setup copied from ProductionSwitchEligibilityTest's private harness. */
    private record SwitchLedger(PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        FindingsLedger.ReviewCheck token(Set<FindingsLedger.ReviewSubject> extras) {
            return ledger.checkLedger(ROUTE, extras, heads());
        }
        void review(String id, FindingsLedger.ReviewSubject subject) {
            reviews.create(new ReviewRecord(id, LEDGER_CREATED, hash(), subject.kind(),
                    subject.workflow(), subject.subjectId(), ReviewRecord.Outcome.COMPLETE,
                    "owner", ReviewRecord.SignerRole.OWNER, LEDGER_EVIDENCE));
        }
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private SwitchLedger switchLedger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve(PACKETS), seal, fixture.run().parameters());
        Path root = Files.createDirectories(temp.resolve("switch-ledger-" + index));
        Path safetyPath = root.resolve(SAFETY_HEAD), disputePath = root.resolve("disputes");
        Path reviewPath = root.resolve("reviews");
        SafetyFindingStore safety = SafetyFindingStore.init(safetyPath, context, LEDGER_GENESIS);
        EvidenceDisputeStore disputes = EvidenceDisputeStore.init(disputePath, context, LEDGER_GENESIS);
        ReviewRecordStore reviews = ReviewRecordStore.init(reviewPath, context, LEDGER_GENESIS);
        return new SwitchLedger(context, safety, disputes, reviews,
                new FindingsLedger(safetyPath, disputePath, reviewPath, context));
    }

    @Test void productionSwitchDerivesPendingFromDeclaredFactsWithoutCallerHint() throws Exception {
        Fixture declaration = fixture(5);
        writeDecision(declaration, decision(declaration, true, false, null, declaration.drawn()), true);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(declaration.sources());
        String pendingCase = assembly.facts().requestedQuestionsByCaseId().keySet().stream()
                .filter(id -> assembly.facts().get(id, MINI) != null
                        && assembly.facts().get(id, MINI).pendingQuestions() > 0)
                .findFirst().orElseThrow();
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "switch-");
        FrbPipelineTest.Fixture pipeline = builder.fixture(true, 0, 1, true);
        ResultsPipeline.FrbInput original = pipeline.frb();
        var route = ResultsPipeline.runFrb(pipeline.run(), original).routes().get(ROUTE);
        Map<String, AdjudicatedFacts> facts = new HashMap<>(original.bundle().evidence().byCaseArm());
        facts.put(FIRST_CASE_PREFIX + MINI, assembly.facts().get(pendingCase, MINI));
        facts.put(FIRST_CASE_PREFIX + LUNA, assembly.facts().get(pendingCase, LUNA));
        AdjudicatedLoaded evidence = original.bundle().evidence();
        var changedEvidence = new AdjudicatedLoaded(facts, evidence.excludedCaseIds(),
                evidence.requestedQuestionsByCaseId(), evidence.excludedReasons());
        FrbEvidenceBundle source = original.bundle();
        var changedBundle = new FrbEvidenceBundle(changedEvidence,
                source.requestedQuestionsByCaseId(), source.reserveDrawnByWorkflow(),
                source.workflowsAwaitingReserve());
        var changed = new ResultsPipeline.FrbInput(changedBundle, original.reserveRecordFile(),
                original.verification(), original.provenance(), original.routeClassifications());
        FrozenGateParameters parameters = FrozenGateParameters.load(pipeline.run().parameters());
        Set<String> classA = new LinkedHashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> subjects = KeyDefectSubjects.derive(source,
                ResultsPipeline.workflowByCaseId(pipeline.run()), classA, parameters.frbClassBWorkflows);
        List<SwitchLedger> ledgers = new ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : original.provenance().primaries())
            ledgers.add(switchLedger(export.lockFile(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), pipeline));
        for (ResultsPipeline.ReserveExport export : original.provenance().reserves().values()) {
            ledgers.add(switchLedger(export.lockForPrimaryOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), pipeline));
            ledgers.add(switchLedger(export.lockForReserveOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), pipeline));
        }
        ledgers.get(2).review("class_b_complete", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP));
        int count = 0;
        for (FindingsLedger.ReviewSubject subject : subjects)
            ledgers.getFirst().review("defect_complete_" + ++count, subject);
        List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
        Map<String, FindingsLedger> byHash = new HashMap<>();
        Map<String, FindingsLedger.ExternalHeads> heads = new HashMap<>();
        for (SwitchLedger ledger : ledgers) {
            tokens.add(ledger.token(subjects));
            byHash.put(ledger.hash(), ledger.ledger());
            heads.put(ledger.hash(), ledger.heads());
        }
        String candidate = Stage1Receipt.read(pipeline.run().candidate().receipt()).arm();
        var workload = new KeyDefectWorkload.Parameters(BigDecimal.valueOf(30), BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.valueOf(4));
        var composed = ReviewComposition.compose(ROUTE, pipeline.run(), original, route, tokens,
                workload, Set.of(candidate), Set.of(MINI, PAID));
        var input = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(STEM, "NOT_TRIGGERED"), composed.heads(), composed);
        var decision = ProductionSwitchEligibility.check(ROUTE, route, input, pipeline.run(), changed,
                byHash, heads, null);
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.PENDING_REVIEW_ITEMS);
    }

    // ---- Audit additions (release session): each pins a guard that a mutant removed without any test failing ----

    private static void clean(TwoStem evidence, List<String> pairIds, boolean doubleMiss) {
        for (String pair : pairIds) for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : evidence.pairs().itemsOf(pair, side))
                evidence.statuses().put(id, syntheticStatus(id, false, doubleMiss));
    }

    private static ObjectNode stopDecision(TwoStem evidence, List<String> declaredA, List<String> declaredB)
            throws Exception {
        ObjectNode root = longDecision(evidence, declaredA, declaredB);
        ((ObjectNode) root.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, false).put(STOP_FIELD, STOP_VALUE);
        return root;
    }

    private static void write(TwoStem evidence, ObjectNode root) throws Exception {
        Files.writeString(evidence.context().decisionFile(), root.toString());
    }

    @Test void completionRecordCannotNameAnAlreadyReviewedPair() throws Exception {
        TwoStem evidence = twoStem();
        clean(evidence, evidence.bPairs().subList(0, 2), false);
        ObjectNode completion = longDecision(evidence, List.of(), List.of());
        ((ObjectNode) completion.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, false);
        write(evidence, completion);
        assertThat(verifyLong(evidence).itemIds()).isEmpty();
        ObjectNode naming = longDecision(evidence, List.of(), evidence.bPairs().subList(0, 2));
        ((ObjectNode) naming.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, false);
        write(evidence, naming);
        assertThatThrownBy(() -> verifyLong(evidence)).isInstanceOf(InvalidExpansionDecision.class);
    }

    @Test void secondExpansionDoubleMissCannotHideBehindALaterPair() throws Exception {
        TwoStem evidence = twoStem();
        clean(evidence, List.of(evidence.aPairs().get(1)), false);
        clean(evidence, List.of(evidence.aPairs().get(2)), true);
        write(evidence, stopDecision(evidence, List.of(), evidence.bPairs().subList(0, 2)));
        assertThat(verifyLong(evidence).itemIds()).hasSize(8);
        clean(evidence, List.of(evidence.aPairs().get(1)), true);
        assertThatThrownBy(() -> verifyLong(evidence)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("Invalid second double-miss pair");
    }

    @Test void completionNeedsAnActualExpansionDrawAndPairsStayUnderTheirOwnStem() throws Exception {
        TwoStem evidence = twoStem();
        clean(evidence, evidence.bPairs().subList(0, 2), false);
        ObjectNode completion = longDecision(evidence, List.of(), List.of());
        ((ObjectNode) completion.path(DECISIONS_FIELD).get(0)).put(DECLINED_FIELD, false);
        write(evidence, completion);
        List<DrawLog.Entry> spotOnly = evidence.entries().stream()
                .filter(entry -> entry.purpose().equals(SeedStreamRegistry.SPOT_CHECK)).toList();
        assertThatThrownBy(() -> ExpansionDecision.verify(evidence.context(), evidence.statuses(),
                evidence.pairs(), evidence.sets(), spotOnly, Map.of(LONG_WORKFLOW, Set.of(LONG_A, LONG_B)),
                Set.of(evidence.context().decisionFile()))).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("drawn expansion");
        TwoStem second = twoStem();
        write(second, longDecision(second, second.bPairs().subList(0, 2), List.of()));
        assertThatThrownBy(() -> verifyLong(second)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("not a drawn expansion pair");
    }

    @Test void unknownRootFieldIsRefusedAndTwoPlusOnePreExpansionMissesCannotStop() throws Exception {
        TwoStem evidence = twoStem();
        ObjectNode valid = longDecision(evidence, List.of(), evidence.bPairs().subList(0, 2));
        write(evidence, valid);
        assertThat(verifyLong(evidence).itemIds()).hasSize(8);
        valid.put("signer", "forged");
        write(evidence, valid);
        assertThatThrownBy(() -> verifyLong(evidence)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("Invalid decision fields");
        TwoStem stop = twoStem(4, 3);
        clean(stop, List.of(stop.aPairs().get(1), stop.aPairs().get(2)), false);
        for (String pair : List.of(stop.aPairs().get(1), stop.aPairs().get(2))) for (String side : List.of(SIDE_A, SIDE_B))
            for (String id : stop.pairs().itemsOf(pair, side)) stop.statuses().put(id, syntheticStatus(id, true, false));
        clean(stop, List.of(stop.bPairs().get(2)), true);
        ReviewSets twoMisses = stop.sets();
        List<DrawLog.Entry> entries = new ArrayList<>(stop.entries());
        entries.add(syntheticDraw(LONG_B, SeedStreamRegistry.SPOT_CHECK, List.of(stop.bPairs().get(2)), 3));
        TwoStem withTwo = new TwoStem(stop.pairs(), stop.statuses(), twoMisses, entries, stop.context(),
                stop.aPairs(), stop.bPairs(), stop.key());
        write(withTwo, stopDecision(withTwo, stop.aPairs().subList(1, 3), stop.bPairs().subList(0, 2)));
        assertThat(verifyLong(withTwo).itemIds()).hasSize(16);
        String third = stop.aPairs().get(3);
        clean(stop, List.of(third), true);
        ReviewSets withThird = new ReviewSets(Set.of(third), stop.sets().criticalCleanFrame(),
                stop.sets().frameByStem(), stop.sets().mandatoryByStem());
        TwoStem withThree = new TwoStem(stop.pairs(), stop.statuses(), withThird, entries, stop.context(),
                stop.aPairs(), stop.bPairs(), stop.key());
        assertThatThrownBy(() -> verifyLong(withThree)).isInstanceOf(InvalidExpansionDecision.class)
                .hasMessageContaining("Invalid pre-expansion double-miss count");
    }

    @Test void aDecisionChangedAfterTheLockIsRefusedEvenWhenTheNewContentIsValid() throws Exception {
        Fixture fixture = fixture(5);
        for (String pair : fixture.drawn()) review(fixture, pair, 4, CLEAN);
        writeDecision(fixture, decision(fixture, false, false, null, List.of()), true);
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).declared().byWorkflow().get(STEM)
                .approvedLargerCap()).isFalse();
        Files.writeString(fixture.decisionFile(), decision(fixture, false, true, null, List.of()).toString());
        assertThat(verify(fixture).byWorkflow().get(STEM).approvedLargerCap()).isTrue();
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class).hasMessageContaining("Findings changed");
    }
}
