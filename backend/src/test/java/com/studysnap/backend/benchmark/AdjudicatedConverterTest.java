package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdjudicatedConverterTest {
    private static final String LUNA = "LUNA";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String CASE_0 = "case-0";
    private static final String CASE_1 = "case-1";
    private static final String CASE_2 = "case-2";
    private static final String A = "A";
    private static final String B = "B";
    private static final String CLEAN = "C";
    private static final String CRITICAL = "K";
    private static final String QUALITY_FAIL = "Q";
    private static final String CLASS_A_METHOD = "classA";
    private static final String BIOLOGY = "Biology";
    private static final String PACKETS = "packets";
    private static final String DIRECT = "DIRECT";
    private static final String DIGEST = "digest";
    private static final String VALUE = "value";
    private static final String ARM_FIELD = "arm";
    private static final String OUTSIDE_PAIR = "outside-pair";
    private static final String KEY_FILE = "key.jsonl";
    private static final String MINI_DIR = "mini";
    private static final String PAID_DIR = "paid";
    private static final String OUTSIDE_KEY = "outside-key";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String ID_FIELD = "id";
    private static final String EXCLUSIONS_PARAMETER = "exclusionsFileOrNull";
    private static final String EXCLUSIONS_METHOD = "exclusions";
    private static final String EXCLUDED_SHORT_ROW = "{\"caseId\":\"x\",\"reason\":\"SHORT_DELIVERY\"}\n";
    @TempDir Path temp;

    private record Fixture(Path root, ClassABlindPacketExport.Export export, PairMap pairs,
                           ArmKeyAccess.OpenedKey key, Map<String, QuestionStatus> statuses,
                           Set<String> frozen, Map<String, Integer> widths, Set<String> stems) {
        AdjudicatedLoaded convert() {
            return convert(statuses, pairs, key, frozen, widths, export.exclusions(),
                    hash(export.exclusions()));
        }
        AdjudicatedLoaded convert(Map<String, QuestionStatus> status, PairMap pairMap,
                ArmKeyAccess.OpenedKey opened, Set<String> cases, Map<String, Integer> requested,
                Path exclusions, String expectedHash) {
            return AdjudicatedConverter.classA(opened, status, pairMap, stems, cases,
                    exclusions, expectedHash, requested);
        }
    }

    private static String hash(Path file) {
        try { return FreezeRecord.sha256(file); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private Fixture fixture(Path root, ClassACaseManifest.Workflow workflow, String baseline,
                            int count, int questions, Set<String> omitted, Set<String> shortCases) throws Exception {
        return fixture(root, workflow, baseline, 0, count, questions, omitted, shortCases);
    }

    private Fixture fixture(Path root, ClassACaseManifest.Workflow workflow, String baseline,
                            int firstIndex, int count, int questions, Set<String> omitted,
                            Set<String> shortCases) throws Exception {
        return fixture(root, workflow, baseline, firstIndex, count, questions, questions, omitted, shortCases);
    }

    private Fixture fixture(Path root, ClassACaseManifest.Workflow workflow, String baseline,
                            int firstIndex, int count, int requested, int deliveredQuestions,
                            Set<String> omitted, Set<String> shortCases) throws Exception {
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        Map<String, Integer> widths = new HashMap<>();
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), BIOLOGY, BIOLOGY, null, LearnerLevel.COLLEGE);
        for (int index = 0; index < count; index++) {
            String caseId = "case-" + (firstIndex + index);
            widths.put(caseId, requested);
            var subgroup = workflow == ClassACaseManifest.Workflow.LONG_EXAM
                    ? ClassACaseManifest.Subgroup.DIRECT : null;
            cases.add(new ClassACaseManifest.Case(caseId, workflow, "synthetic", LearnerLevel.COLLEGE,
                    null, BIOLOGY, false, List.of(source), requested, null, false, subgroup));
            if (omitted.contains(caseId)) continue;
            for (String arm : List.of(baseline, LUNA)) {
                var array = ClassAScoreSchemaV2Test.JSON.createArrayNode();
                int delivered = shortCases.contains(caseId) && arm.equals(baseline)
                        ? requested - 1 : deliveredQuestions;
                for (int j = 0; j < delivered; j++) array.add(ClassAScoreSchemaV2Test.packet("MCQ").question());
                outputs.add(new ClassABlindPacketExport.Output(caseId, arm, array));
            }
        }
        var export = ClassABlindPacketExport.export(cases, outputs, baseline, LUNA,
                root.resolve(PACKETS), root.resolve(KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS));
        ArmKeyAccess.OpenedKey key = open(root, export);
        Map<String, String> clean = new HashMap<>();
        pairs.blindItemIds().forEach(id -> clean.put(id, CLEAN));
        var scores = ClassAAdjudicationFixture.load(root, pairs, clean, clean, Map.of(), Map.of());
        Map<String, QuestionStatus> statuses = new HashMap<>();
        scores.forEach((id, item) -> statuses.put(id, FinalStatus.of(item, ClassAAdjudicationFixture.MAPPING, false)));
        return new Fixture(root, export, pairs, key, statuses, Set.copyOf(widths.keySet()), widths,
                Set.copyOf(export.packets().stream().map(path -> path.getFileName().toString()
                        .replace(".jsonl", "")).toList()));
    }

    private Fixture fixture(Path root, int count, int questions) throws Exception {
        return fixture(root, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, MINI, count, questions,
                Set.of(), Set.of());
    }

    private static ArmKeyAccess.OpenedKey open(Path root, ClassABlindPacketExport.Export export) throws Exception {
        Path seal = ArmKeyGateFixtures.seal(root, export.armKey());
        Path findings = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, findings, LockRecord.Kind.PRIMARY);
        return ArmKeyAccess.open(export.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, findings)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "test attribution");
    }

    private static QuestionStatus status(String id, String p, String s, String h) {
        return FinalStatus.of(ClassAAdjudicationFixture.direct(id, p, s, 0, h),
                ClassAAdjudicationFixture.MAPPING, false);
    }

    private static String item(Fixture fixture, String caseId, String arm) {
        return fixture.key().rows().stream().filter(row -> row.caseId().equals(caseId) && row.arm().equals(arm))
                .findFirst().orElseThrow().itemId();
    }

    private static Map<String, QuestionStatus> replacing(Fixture fixture, String id, QuestionStatus status) {
        Map<String, QuestionStatus> copy = new HashMap<>(fixture.statuses());
        copy.put(id, status);
        return copy;
    }

    private static AdjudicatedLoaded convert(Fixture fixture, Map<String, QuestionStatus> statuses) {
        return fixture.convert(statuses, fixture.pairs(), fixture.key(), fixture.frozen(), fixture.widths(),
                fixture.export().exclusions(), hash(fixture.export().exclusions()));
    }

    private static AdjudicatedLoaded convertWithKey(Fixture fixture, ArmKeyAccess.OpenedKey key) {
        return convertWithStatusAndKey(fixture, fixture.statuses(), key);
    }

    private static AdjudicatedLoaded convertWithStatusAndKey(Fixture fixture,
            Map<String, QuestionStatus> statuses, ArmKeyAccess.OpenedKey key) {
        return fixture.convert(statuses, fixture.pairs(), key, fixture.frozen(), fixture.widths(),
                fixture.export().exclusions(), hash(fixture.export().exclusions()));
    }

    private static AdjudicatedLoaded convertWithWidths(Fixture fixture, Map<String, Integer> widths) {
        return fixture.convert(fixture.statuses(), fixture.pairs(), fixture.key(), fixture.frozen(), widths,
                fixture.export().exclusions(), hash(fixture.export().exclusions()));
    }

    private static AdjudicatedLoaded convertWithScope(Fixture fixture, Set<String> frozen,
            Map<String, Integer> widths) {
        return fixture.convert(fixture.statuses(), fixture.pairs(), fixture.key(), frozen, widths,
                fixture.export().exclusions(), hash(fixture.export().exclusions()));
    }

    private static AdjudicatedLoaded convertWithExclusions(Fixture fixture, Path file, String digest) {
        return fixture.convert(fixture.statuses(), fixture.pairs(), fixture.key(), fixture.frozen(),
                fixture.widths(), file, digest);
    }

    @Test void countsFinalStatusesPerRealOpenedArmAndPreservesLongExamSubgroup() throws Exception {
        Fixture fixture = fixture(temp, ClassACaseManifest.Workflow.LONG_EXAM, MINI, 1, 4, Set.of(), Set.of());
        List<String> luna = fixture.key().rows().stream().filter(row -> row.arm().equals(LUNA))
                .map(ArmKeyAccess.KeyRow::itemId).toList();
        Map<String, QuestionStatus> statuses = new HashMap<>(fixture.statuses());
        statuses.put(luna.get(0), status(luna.get(0), CLEAN, CLEAN, CRITICAL));
        statuses.put(luna.get(1), status(luna.get(1), QUALITY_FAIL, QUALITY_FAIL, null));
        statuses.put(luna.get(2), status(luna.get(2), CLEAN, CLEAN, "U"));
        statuses.put(luna.get(3), status(luna.get(3), CRITICAL, CLEAN, CLEAN));
        AdjudicatedLoaded loaded = convert(fixture, statuses);
        AdjudicatedFacts facts = loaded.get(CASE_0, LUNA);
        assertThat(facts).isEqualTo(new AdjudicatedFacts(4, 1, 1, 1, 0, 1, 0, 1, DIRECT));
        assertThat(loaded.get(CASE_0, MINI)).isEqualTo(new AdjudicatedFacts(4, 0, 0, 0, 0, 0, 0, 0, DIRECT));
        assertThat(facts.toFacts()).isEqualTo(new ScoreEvidence.Facts(true, true, 4, DIRECT));
        assertThat(loaded.toLoaded(Map.of(DIGEST, VALUE)).get(CASE_0, LUNA)).isEqualTo(facts.toFacts());
        assertThat(loaded.toLoaded(Map.of(DIGEST, VALUE)).hashes()).containsEntry(DIGEST, VALUE);
        assertThat(loaded.toLoaded(Map.of()).excluded()).isEmpty();
    }

    @Test void qualityAndCriticalEventsAreIndependentAndArmLabelsPassThrough() throws Exception {
        Fixture mini = fixture(temp.resolve(MINI_DIR), 1, 1);
        String miniId = item(mini, CASE_0, MINI);
        String lunaId = item(mini, CASE_0, LUNA);
        Map<String, QuestionStatus> statuses = replacing(mini, miniId, status(miniId, QUALITY_FAIL, QUALITY_FAIL, null));
        statuses.put(lunaId, status(lunaId, CLEAN, CLEAN, CRITICAL));
        AdjudicatedLoaded result = convert(mini, statuses);
        assertThat(result.get(CASE_0, MINI).toFacts()).isEqualTo(new ScoreEvidence.Facts(false, true, 1, null));
        assertThat(result.get(CASE_0, LUNA).toFacts()).isEqualTo(new ScoreEvidence.Facts(true, false, 1, null));
        Fixture paid = fixture(temp.resolve(PAID_DIR), ClassACaseManifest.Workflow.INTERVIEW_PRACTICE,
                PAID, 1, 1, Set.of(), Set.of());
        assertThat(paid.convert().get(CASE_0, PAID)).isNotNull();
        assertThat(paid.convert().get(CASE_0, LUNA)).isNotNull();
        assertThat(paid.convert().byCaseArm()).hasSize(2);
    }

    @Test void rejectsMissingStatusExtraStatusAndPendingStatus() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        String id = item(fixture, CASE_0, LUNA);
        Map<String, QuestionStatus> missing = new HashMap<>(fixture.statuses());
        missing.remove(id);
        assertThatThrownBy(() -> convert(fixture, missing)).isInstanceOf(InvalidEvidence.class);
        Map<String, QuestionStatus> extra = new HashMap<>(fixture.statuses());
        extra.put(OUTSIDE_KEY, status(OUTSIDE_KEY, CLEAN, CLEAN, null));
        assertThatThrownBy(() -> convert(fixture, extra)).isInstanceOf(InvalidEvidence.class);
        Map<String, QuestionStatus> pending = replacing(fixture, id, status(id, CRITICAL, CLEAN, null));
        assertThatThrownBy(() -> convert(fixture, pending)).isInstanceOf(InvalidEvidence.class);
        Map<String, QuestionStatus> nullStatus = replacing(fixture, id, null);
        assertThatThrownBy(() -> convert(fixture, nullStatus)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void countsScorerFailureCriticalWithoutConfusingItWithDoubleMiss() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        String id = item(fixture, CASE_0, LUNA);
        Map<String, QuestionStatus> statuses = replacing(fixture, id, status(id, "MISSING", CLEAN, CRITICAL));
        AdjudicatedFacts facts = convert(fixture, statuses).get(CASE_0, LUNA);
        assertThat(facts.criticalQuestions()).isEqualTo(1);
        assertThat(facts.scorerFailureCriticals()).isEqualTo(1);
        assertThat(facts.doubleMisses()).isZero();
    }

    @Test void rejectsPairPlacementSplitSameArmCrossCaseAndOrphanKey() throws Exception {
        Fixture fixture = fixture(temp, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, MINI,
                0, 2, 1, 2, Set.of(), Set.of());
        String a = fixture.pairs().itemsOf(fixture.pairs().pairs().getFirst().id(), A).get(1);
        String b = fixture.pairs().itemsOf(fixture.pairs().pairs().getFirst().id(), B).getFirst();
        String other = item(fixture, CASE_1, LUNA);
        ArmKeyAccess.OpenedKey split = editedKey(fixture, a, ARM_FIELD, fixture.key().armOf(b));
        assertThatThrownBy(() -> convertWithKey(fixture, split)).isInstanceOf(InvalidEvidence.class);
        ArmKeyAccess.OpenedKey same = editedKey(fixture, b, ARM_FIELD, fixture.key().armOf(a));
        assertThatThrownBy(() -> convertWithKey(fixture, same)).isInstanceOf(InvalidEvidence.class);
        String otherCase = fixture.key().rows().stream().filter(row -> row.itemId().equals(a))
                .findFirst().orElseThrow().caseId().equals(CASE_0) ? CASE_1 : CASE_0;
        ArmKeyAccess.OpenedKey cross = editedKey(fixture, a, CASE_ID_FIELD, otherCase);
        assertThatThrownBy(() -> convertWithKey(fixture, cross)).isInstanceOf(InvalidEvidence.class);
        ArmKeyAccess.OpenedKey orphan = editedKey(fixture, other, ID_FIELD, OUTSIDE_PAIR);
        Map<String, QuestionStatus> orphanStatuses = new HashMap<>(fixture.statuses());
        orphanStatuses.remove(other);
        orphanStatuses.put(OUTSIDE_PAIR, status(OUTSIDE_PAIR, CLEAN, CLEAN, null));
        assertThatThrownBy(() -> convertWithStatusAndKey(fixture, orphanStatuses, orphan))
                .isInstanceOf(InvalidEvidence.class);
    }

    private ArmKeyAccess.OpenedKey editedKey(Fixture fixture, String itemId, String field, String value)
            throws Exception {
        Path root = Files.createTempDirectory(temp, "edited-key-");
        Path packets = root.resolve(PACKETS);
        Files.createDirectories(packets);
        for (Path packet : fixture.export().packets())
            Files.copy(packet, packets.resolve(packet.getFileName()));
        Path keyFile = root.resolve(KEY_FILE);
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(fixture.export().armKey())) {
            ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(line);
            if (itemId.equals(row.path(ID_FIELD).asText())) row.put(field, value);
            lines.add(row.toString());
        }
        Files.write(keyFile, lines);
        var exported = new ClassABlindPacketExport.Export(fixture.export().packets().stream()
                .map(packet -> packets.resolve(packet.getFileName())).toList(),
                keyFile, fixture.export().itemCount(), root.resolve("unused.jsonl"), Map.of());
        return open(root, exported);
    }

    @Test void rejectsPairWhoseTwoPlacementsUseTheSameArm() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        PairMap.Pair pair = fixture.pairs().pairs().getFirst();
        String a = pair.items().get(A).getFirst();
        String b = pair.items().get(B).getFirst();
        ArmKeyAccess.OpenedKey same = editedKey(fixture, b, ARM_FIELD, fixture.key().armOf(a));
        assertThatThrownBy(() -> convertWithKey(fixture, same)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void rejectsPairSpanningTwoCases() throws Exception {
        Fixture fixture = fixture(temp, 2, 1);
        PairMap.Pair pair = fixture.pairs().pairs().getFirst();
        String a = pair.items().get(A).getFirst();
        String caseId = fixture.key().rows().stream().filter(row -> row.itemId().equals(a))
                .findFirst().orElseThrow().caseId();
        String otherCase = caseId.equals(CASE_0) ? CASE_1 : CASE_0;
        ArmKeyAccess.OpenedKey cross = editedKey(fixture, a, CASE_ID_FIELD, otherCase);
        assertThatThrownBy(() -> convertWithKey(fixture, cross)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void exclusionsAreHashedAndCarryWidthsWithoutFacts() throws Exception {
        Fixture fixture = fixture(temp, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, MINI, 3, 2,
                Set.of(CASE_1), Set.of(CASE_2));
        AdjudicatedLoaded result = fixture.convert();
        assertThat(result.excludedCaseIds()).containsExactlyInAnyOrder(CASE_1, CASE_2);
        assertThat(result.requestedQuestionsByCaseId()).containsKeys(CASE_0, CASE_1, CASE_2);
        assertThat(result.get(CASE_1, LUNA)).isNull();
        assertThat(result.get(CASE_2, MINI)).isNull();
        assertThat(result.toLoaded(Map.of()).excluded()).containsExactlyInAnyOrder(CASE_1, CASE_2);
        assertThat(Files.readString(fixture.export().exclusions()))
                .contains("MISSING_ARM_OUTPUT", "SHORT_DELIVERY");
        Map<String, Integer> missing = Map.of(CASE_0, 2, CASE_2, 2);
        assertThatThrownBy(() -> convertWithWidths(fixture, missing)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void exclusionReasonsAreCarriedExactlyFromTheFileToTheLoadedFacts() throws Exception {
        Fixture fixture = fixture(temp, ClassACaseManifest.Workflow.NOTE_ADAPTIVE, MINI, 3, 2,
                Set.of(CASE_1), Set.of(CASE_2));
        Map<String, String> expected = new HashMap<>();
        for (String line : Files.readAllLines(fixture.export().exclusions())) {
            var row = IntegritySupport.JSON.readTree(line);
            expected.put(row.get("caseId").asText(), row.get("reason").asText());
        }
        assertThat(expected.values()).containsExactlyInAnyOrder("MISSING_ARM_OUTPUT", "SHORT_DELIVERY");
        assertThat(fixture.convert().excludedReasons()).isEqualTo(expected);
    }

    @Test void rejectsMalformedExclusionsAndKeyedExcludedCase() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        Path file = fixture.export().exclusions();
        for (String body : List.of("{\"caseId\":\"x\",\"reason\":\"UNKNOWN\"}\n",
                EXCLUDED_SHORT_ROW + EXCLUDED_SHORT_ROW,
                "{\"caseId\":\"\",\"reason\":\"SHORT_DELIVERY\"}\n",
                "{\"caseId\":\"x\",\"reason\":\"\"}\n")) {
            Files.writeString(file, body);
            String digest = hash(file);
            assertThatThrownBy(() -> convertWithExclusions(fixture, file, digest))
                    .isInstanceOf(InvalidEvidence.class);
        }
        Files.writeString(file, "{\"caseId\":\"case-0\",\"reason\":\"SHORT_DELIVERY\"}\n");
        String digest = hash(file);
        assertThatThrownBy(() -> convertWithExclusions(fixture, file, digest))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void exclusionsNeedMatchingHashAndRejectPostLockByteEdit() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        Path file = fixture.export().exclusions();
        assertThatThrownBy(() -> convertWithExclusions(fixture, file, null)).isInstanceOf(InvalidEvidence.class);
        String currentHash = hash(file);
        assertThatThrownBy(() -> convertWithExclusions(fixture, null, currentHash))
                .isInstanceOf(InvalidEvidence.class);
        String wrongHash = "0".repeat(64);
        assertThatThrownBy(() -> convertWithExclusions(fixture, file, wrongHash))
                .isInstanceOf(InvalidEvidence.class);
        String oldHash = hash(file);
        Files.writeString(file, "\n");
        assertThatThrownBy(() -> convertWithExclusions(fixture, file, oldHash))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void excludedCaseOutsideFrozenScopeIsRejected() throws Exception {
        Fixture fixture = fixture(temp, 1, 1);
        Path file = fixture.export().exclusions();
        Files.writeString(file, "{\"caseId\":\"outside\",\"reason\":\"MISSING_ARM_OUTPUT\"}\n");
        String digest = hash(file);
        assertThatThrownBy(() -> convertWithExclusions(fixture, file, digest))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void requestedWidthsAndFrozenScopeAreCompleteAndShortArmsFail() throws Exception {
        Fixture fixture = fixture(temp, 1, 2);
        Map<String, Integer> missing = Map.of();
        Map<String, Integer> zero = Map.of(CASE_0, 0);
        Set<String> frozen = fixture.frozen();
        assertThatThrownBy(() -> AdjudicatedConverter.requestedWidths(missing, frozen))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> AdjudicatedConverter.requestedWidths(zero, frozen))
                .isInstanceOf(InvalidEvidence.class);
        Set<String> expanded = Set.of(CASE_0, CASE_1);
        Map<String, Integer> expandedWidths = Map.of(CASE_0, 2, CASE_1, 2);
        assertThatThrownBy(() -> convertWithScope(fixture, expanded, expandedWidths))
                .isInstanceOf(InvalidEvidence.class);
        Set<String> reduced = Set.of(CASE_1);
        Map<String, Integer> reducedWidths = Map.of(CASE_1, 2);
        assertThatThrownBy(() -> convertWithScope(fixture, reduced, reducedWidths))
                .isInstanceOf(InvalidEvidence.class);
        Map<String, Integer> tooWide = Map.of(CASE_0, 3);
        assertThatThrownBy(() -> convertWithWidths(fixture, tooWide)).isInstanceOf(InvalidEvidence.class);
        assertThat(fixture.convert().get(CASE_0, LUNA).scoredQuestions()).isEqualTo(2);
        assertThat(fixture.convert(fixture.statuses(), fixture.pairs(), fixture.key(), fixture.frozen(),
                Map.of(CASE_0, 1), fixture.export().exclusions(), hash(fixture.export().exclusions()))
                .get(CASE_0, LUNA).scoredQuestions()).isEqualTo(2);
        assertThat(AdjudicatedConverter.requestedWidths(Map.of(CASE_0, 1), fixture.frozen()))
                .containsEntry(CASE_0, 1);
    }

    @Test void mergesTwoRealBaselineExportsAndRejectsOverlapOrIncompleteUnion() throws Exception {
        Fixture mini = fixture(temp.resolve(MINI_DIR), 1, 1);
        Fixture paid = fixture(temp.resolve(PAID_DIR), ClassACaseManifest.Workflow.INTERVIEW_PRACTICE,
                PAID, 1, 1, 1, Set.of(), Set.of());
        AdjudicatedLoaded merged = AdjudicatedLoaded.merge(List.of(mini.convert(), paid.convert()),
                Set.of(CASE_0, CASE_1));
        assertThat(merged.get(CASE_0, MINI)).isNotNull();
        assertThat(merged.get(CASE_1, PAID)).isNotNull();
        assertThat(merged.requestedQuestionsByCaseId()).containsKeys(CASE_0, CASE_1);
        List<AdjudicatedLoaded> duplicate = List.of(mini.convert(), mini.convert());
        Set<String> oneCase = Set.of(CASE_0);
        assertThatThrownBy(() -> AdjudicatedLoaded.merge(duplicate, oneCase))
                .isInstanceOf(InvalidEvidence.class);
        List<AdjudicatedLoaded> incomplete = List.of(mini.convert());
        Set<String> twoCases = Set.of(CASE_0, CASE_1);
        assertThatThrownBy(() -> AdjudicatedLoaded.merge(incomplete, twoCases))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void converterRequiresOpenedKeyAndNoRawArmKeyEntryPoint() throws Exception {
        Method method = AdjudicatedConverter.class.getDeclaredMethod(CLASS_A_METHOD, ArmKeyAccess.OpenedKey.class,
                Map.class, PairMap.class, Set.class, Set.class, Path.class, String.class, Map.class);
        assertThat(method.getParameterTypes()[0]).isEqualTo(ArmKeyAccess.OpenedKey.class);
        for (Class<?> type : List.of(AdjudicatedConverter.class, AdjudicatedLoaded.class, AdjudicatedFacts.class))
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.isSynthetic()) continue;
                if (candidate.getName().equals(CLASS_A_METHOD)) {
                    assertThat(candidate.getParameterTypes()).hasSize(8);
                    assertThat(candidate.getParameterTypes()[0]).isEqualTo(ArmKeyAccess.OpenedKey.class);
                    assertThat(candidate.getParameterTypes()[5]).isEqualTo(Path.class);
                    assertThat(candidate.getParameters()[5].getName()).isEqualTo(EXCLUSIONS_PARAMETER);
                }
                assertThat(Arrays.stream(candidate.getParameterTypes()).filter(File.class::equals)).isEmpty();
                long paths = Arrays.stream(candidate.getParameterTypes()).filter(Path.class::equals).count();
                assertThat(paths).as(candidate.getName()).isLessThanOrEqualTo(
                        Set.of(CLASS_A_METHOD, EXCLUSIONS_METHOD).contains(candidate.getName()) ? 1 : 0);
                if (paths == 1) {
                    assertThat(candidate.getName()).isIn(CLASS_A_METHOD, EXCLUSIONS_METHOD);
                    assertThat(Arrays.stream(candidate.getParameters()).filter(parameter -> parameter.getType() == Path.class))
                            .allSatisfy(parameter -> assertThat(parameter.getName()).isEqualTo(EXCLUSIONS_PARAMETER));
                }
                assertThat(Arrays.stream(candidate.getGenericParameterTypes()).map(Object::toString))
                        .noneMatch(name -> name.contains("KeyRow")
                                || candidate.getName().equals(CLASS_A_METHOD)
                                && name.contains("Map<java.lang.String, java.lang.String>"));
            }
    }
}
