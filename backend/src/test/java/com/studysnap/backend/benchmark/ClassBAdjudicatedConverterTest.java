package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassBAdjudicatedConverterTest {
    private static final String TIP = "STUDY_TIP";
    private static final String LUNA = "LUNA";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String REVIEWER = "HUMAN_REVIEWER";
    private static final String CASE = "case-0";
    private static final EvidenceMapping MAPPING = ClassAScoreSchemaV2Test.MAPPING;
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS = Map.of(
            HUMAN, ClassBScoreFiles.ScorerKind.HUMAN, REVIEWER, ClassBScoreFiles.ScorerKind.HUMAN);
    private static final ClassBAdjudicatedConverter.ArmLabels LABELS =
            new ClassBAdjudicatedConverter.ArmLabels(LUNA, workflow ->
                    TIP.equals(workflow) ? MINI : PAID);
    @TempDir Path temp;

    private record Fixture(Path root, ClassBBlindPacketExport.Export export,
            ArmKeyAccess.OpenedKey opened, PairMap pairs, ClassBScoreFiles.Validated validated) {
        AdjudicatedLoaded convert() {
            return ClassBAdjudicatedConverter.classB(opened, ClassBAdjudicatedConverter.ExportKind.PRIMARY,
                    validated, MAPPING, pairs, Set.of(TIP), Set.of(CASE), LABELS);
        }
    }

    private static ObjectNode score(String id, String workflow, String scorer, int round,
            boolean critical, boolean quality) {
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put("itemId", id).put("workflow", workflow).put("round", round)
                .put("scorerId", scorer).put("scorerKind", "HUMAN")
                .put("taskFulfilled", !quality).put("grounded", true).put("actionable", true)
                .put("noCriticalMisleading", !critical).put("critical", critical)
                .put("borderline", false).put("adjudication", false);
        if (critical) row.put("reasonCode", "CRITICAL_MISLEADING");
        else if (quality) row.put("reasonCode", "TASK_NOT_FULFILLED");
        else row.putNull("reasonCode");
        return row;
    }

    private static ClassBScoreFiles.Validated validated(Path root, ClassBBlindPacketExport.Export export,
            List<ObjectNode> rows) throws Exception {
        Path file = root.resolve("scores.jsonl");
        Files.write(file, rows.stream().map(ObjectNode::toString).toList());
        return ClassBScoreFiles.validate(file, export.manifest(), SCORERS);
    }

    private static ArmKeyAccess.OpenedKey open(Path root, Path key) throws Exception {
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path findings = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, findings, LockRecord.Kind.PRIMARY);
        return ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, findings)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"),
                ArmKeyGateFixtures.OPENED, "test class-B attribution");
    }

    private Fixture fixture(Path root, int count, Set<String> missing, Set<String> critical,
            Set<String> quality) throws Exception {
        Files.createDirectories(root);
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classB(root, count);
        ArmKeyAccess.OpenedKey opened = open(root, root.resolve("arm-key.jsonl"));
        PairMap pairs = PairMap.fromPackets(root.resolve("packets"));
        List<ObjectNode> rows = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : export.manifest())
            if (!missing.contains(item.itemId())) rows.add(score(item.itemId(), TIP, HUMAN, 1,
                    critical.contains(item.itemId()), quality.contains(item.itemId())));
        return new Fixture(root, export, opened, pairs, validated(root, export, rows));
    }

    private static String item(Fixture fixture, String caseId, String arm) {
        return fixture.opened().rows().stream().filter(row -> row.caseId().equals(caseId)
                && row.arm().equals(arm)).findFirst().orElseThrow().itemId();
    }

    private static AdjudicatedLoaded convert(Fixture fixture, ArmKeyAccess.OpenedKey key,
            ClassBScoreFiles.Validated validated, PairMap pairs, Set<String> cases,
            ClassBAdjudicatedConverter.ExportKind kind) {
        return ClassBAdjudicatedConverter.classB(key, kind, validated, MAPPING, pairs,
                Set.of(TIP), cases, LABELS);
    }

    private static ArmKeyAccess.OpenedKey altered(ArmKeyAccess.OpenedKey source,
            java.util.function.UnaryOperator<List<ArmKeyAccess.KeyRow>> change) throws Exception {
        Constructor<ArmKeyAccess.OpenedKey> constructor = ArmKeyAccess.OpenedKey.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(change.apply(new ArrayList<>(source.rows())));
    }

    @Test void resolvedCriticalCleanAndQualityAreAttributedToReceiptArmsWithWidthOne() throws Exception {
        Fixture fixture = fixture(temp, 2, Set.of(), Set.of(), Set.of());
        String critical = item(fixture, CASE, "CANDIDATE");
        String quality = item(fixture, "case-1", "BASELINE");
        List<ObjectNode> rows = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : fixture.export().manifest())
            rows.add(score(item.itemId(), TIP, HUMAN, 1, item.itemId().equals(critical),
                    item.itemId().equals(quality)));
        ClassBScoreFiles.Validated validated = validated(temp, fixture.export(), rows);
        AdjudicatedLoaded result = convert(fixture, fixture.opened(), validated, fixture.pairs(),
                Set.of(CASE, "case-1"), ClassBAdjudicatedConverter.ExportKind.PRIMARY);
        assertThat(result.get(CASE, LUNA)).isEqualTo(new AdjudicatedFacts(1, 1, 0, 0, 0, 0, 0, 0, null));
        assertThat(result.get(CASE, MINI).toFacts()).isEqualTo(new ScoreEvidence.Facts(false, false, 1, null));
        assertThat(result.get("case-1", MINI).toFacts()).isEqualTo(new ScoreEvidence.Facts(false, true, 1, null));
        assertThat(result.requestedQuestionsByCaseId()).containsEntry(CASE, 1).containsEntry("case-1", 1);
        assertThat(result.get(CASE, "CANDIDATE")).isNull();
        assertThat(result.get(CASE, "BASELINE")).isNull();
    }

    @Test void missingHumanAndUnadjudicatedDisagreementAreUnresolvedLikeReserveRecord() throws Exception {
        Fixture fixture = fixture(temp, 1, Set.of(), Set.of(), Set.of());
        String candidate = item(fixture, CASE, "CANDIDATE");
        String baseline = item(fixture, CASE, "BASELINE");
        List<ObjectNode> rows = List.of(score(candidate, TIP, HUMAN, 1, true, false));
        ClassBScoreFiles.Validated noHuman = validated(temp, fixture.export(), rows);
        AdjudicatedLoaded missing = convert(fixture, fixture.opened(), noHuman, fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY);
        assertThat(missing.get(CASE, MINI)).isEqualTo(new AdjudicatedFacts(1, 0, 0, 1, 0, 0, 0, 0, null));
        assertThat(missing.get(CASE, LUNA).criticalQuestions()).isEqualTo(1);
        assertThat(ReserveDecisionRecord.compute(temp.resolve("packets"), noHuman, MAPPING,
                Set.of(TIP)).getFirst().primaryUnresolved()).isTrue();
        List<ObjectNode> disagree = List.of(score(candidate, TIP, HUMAN, 1, true, false),
                score(candidate, TIP, REVIEWER, 2, false, false), score(baseline, TIP, HUMAN, 1, false, false));
        ClassBScoreFiles.Validated disputed = validated(temp, fixture.export(), disagree);
        AdjudicatedLoaded unresolved = convert(fixture, fixture.opened(), disputed, fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY);
        assertThat(unresolved.get(CASE, LUNA)).isEqualTo(new AdjudicatedFacts(1, 0, 0, 1, 0, 0, 0, 0, null));
        assertThat(ReserveDecisionRecord.compute(temp.resolve("packets"), disputed, MAPPING,
                Set.of(TIP)).getFirst().primaryUnresolved()).isTrue();
    }

    @Test void rejectsExportKindManifestGapsAndFrozenCaseGaps() throws Exception {
        Fixture fixture = fixture(temp, 1, Set.of(), Set.of(), Set.of());
        assertThatThrownBy(() -> convert(fixture, fixture.opened(), fixture.validated(), fixture.pairs(),
                Set.of(), ClassBAdjudicatedConverter.ExportKind.PRIMARY)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> convert(fixture, fixture.opened(), fixture.validated(), fixture.pairs(),
                Set.of(CASE, "extra"), ClassBAdjudicatedConverter.ExportKind.PRIMARY))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> convert(fixture, fixture.opened(), fixture.validated(), fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.RESERVE)).isInstanceOf(InvalidEvidence.class);
        var rows = new ArrayList<>(fixture.opened().rows());
        var first = rows.getFirst();
        rows.set(0, new ArmKeyAccess.KeyRow(first.itemId(), first.arm(), first.workflow(), first.caseId(), true));
        ArmKeyAccess.OpenedKey wrongKind = altered(fixture.opened(), ignored -> rows);
        assertThatThrownBy(() -> convert(fixture, wrongKind, fixture.validated(), fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY)).isInstanceOf(InvalidEvidence.class);
        rows.set(0, new ArmKeyAccess.KeyRow(first.itemId(), first.arm(), first.workflow(), first.caseId(), null));
        ArmKeyAccess.OpenedKey nullFlag = altered(fixture.opened(), ignored -> rows);
        assertThatThrownBy(() -> convert(fixture, nullFlag, fixture.validated(), fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY)).isInstanceOf(InvalidEvidence.class);
        var noKey = altered(fixture.opened(), list -> list.subList(1, list.size()));
        assertThatThrownBy(() -> convert(fixture, noKey, fixture.validated(), fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY)).isInstanceOf(InvalidEvidence.class);
        var noManifest = new ClassBScoreFiles.Validated(List.of(fixture.export().manifest().getFirst()),
                fixture.validated().scores());
        assertThatThrownBy(() -> convert(fixture, fixture.opened(), noManifest, fixture.pairs(),
                Set.of(CASE), ClassBAdjudicatedConverter.ExportKind.PRIMARY)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void rejectsCrossCaseSameArmAndUnknownKeyLabels() throws Exception {
        Fixture fixture = fixture(temp, 2, Set.of(), Set.of(), Set.of());
        var source = fixture.opened().rows();
        var rows = new ArrayList<>(source);
        var first = rows.getFirst();
        int matching = -1;
        for (int index = 1; index < rows.size(); index++)
            if (rows.get(index).caseId().equals("case-1") && rows.get(index).arm().equals(first.arm()))
                matching = index;
        var matchingRow = rows.get(matching);
        rows.set(0, new ArmKeyAccess.KeyRow(first.itemId(), first.arm(), first.workflow(),
                "case-1", first.reserve()));
        rows.set(matching, new ArmKeyAccess.KeyRow(matchingRow.itemId(), matchingRow.arm(),
                matchingRow.workflow(), CASE, matchingRow.reserve()));
        var crossingRows = rows;
        var crossing = altered(fixture.opened(), ignored -> crossingRows);
        assertThatThrownBy(() -> convert(fixture, crossing, fixture.validated(), fixture.pairs(),
                Set.of(CASE, "case-1"), ClassBAdjudicatedConverter.ExportKind.PRIMARY))
                .isInstanceOf(InvalidEvidence.class);
        rows = new ArrayList<>(source);
        var second = rows.get(1);
        rows.set(1, new ArmKeyAccess.KeyRow(second.itemId(), first.arm(), second.workflow(),
                second.caseId(), second.reserve()));
        var sameArmRows = rows;
        var sameArm = altered(fixture.opened(), ignored -> sameArmRows);
        assertThatThrownBy(() -> convert(fixture, sameArm, fixture.validated(), fixture.pairs(),
                Set.of(CASE, "case-1"), ClassBAdjudicatedConverter.ExportKind.PRIMARY))
                .isInstanceOf(InvalidEvidence.class);
        rows = new ArrayList<>(source);
        rows.set(0, new ArmKeyAccess.KeyRow(first.itemId(), "MYSTERY", first.workflow(),
                first.caseId(), first.reserve()));
        var unknownRows = rows;
        var unknown = altered(fixture.opened(), ignored -> unknownRows);
        assertThatThrownBy(() -> convert(fixture, unknown, fixture.validated(), fixture.pairs(),
                Set.of(CASE, "case-1"), ClassBAdjudicatedConverter.ExportKind.PRIMARY))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void mapsBothBaselineReceiptNamesAndRejectsBlankLabels() throws Exception {
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classBAll(temp, false);
        ArmKeyAccess.OpenedKey opened = open(temp, temp.resolve("arm-key.jsonl"));
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        List<ObjectNode> rows = export.manifest().stream().map(item ->
                score(item.itemId(), item.workflow().name(), HUMAN, 1, false, false)).toList();
        var validated = validated(temp, export, rows);
        Set<String> cases = Set.of("tip", "collection", "ask", "interview");
        Set<String> workflows = Set.of(TIP, "COLLECTION_COMPANION", "ASK_COMPANION", "INTERVIEW_CRITIQUE");
        AdjudicatedLoaded result = ClassBAdjudicatedConverter.classB(opened,
                ClassBAdjudicatedConverter.ExportKind.PRIMARY, validated, MAPPING, pairs,
                workflows, cases, LABELS);
        assertThat(result.get("tip", MINI)).isNotNull();
        assertThat(result.get("collection", PAID)).isNotNull();
        assertThat(result.get("ask", LUNA)).isNotNull();
        assertThatThrownBy(() -> new ClassBAdjudicatedConverter.ArmLabels(null, ignored -> MINI))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> new ClassBAdjudicatedConverter.ArmLabels(" ", ignored -> MINI))
                .isInstanceOf(InvalidEvidence.class);
        var nullBaseline = new ClassBAdjudicatedConverter.ArmLabels(LUNA, ignored -> null);
        assertThatThrownBy(() -> ClassBAdjudicatedConverter.classB(opened,
                ClassBAdjudicatedConverter.ExportKind.PRIMARY, validated, MAPPING, pairs,
                workflows, cases, nullBaseline)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void publicKeyBearingSignaturesOnlyAcceptOpenedKey() {
        for (Class<?> type : List.of(ClassBAdjudicatedConverter.class, FrbEvidenceBundle.class))
            for (Method method : type.getDeclaredMethods())
                for (Class<?> parameter : method.getParameterTypes())
                    assertThat(parameter).isNotEqualTo(Path.class).isNotEqualTo(File.class);
        for (Method method : ClassBAdjudicatedConverter.class.getDeclaredMethods())
            for (java.lang.reflect.Type parameter : method.getGenericParameterTypes()) {
                assertThat(parameter.getTypeName()).doesNotContain("ArmKeyAccess.KeyRow");
                assertThat(parameter.getTypeName()).doesNotContain("java.util.Map<java.lang.String, java.lang.String>");
            }
        assertThat(Arrays.stream(ClassBAdjudicatedConverter.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("classB")).findFirst().orElseThrow()
                .getParameterTypes()[0]).isEqualTo(ArmKeyAccess.OpenedKey.class);
    }
}
