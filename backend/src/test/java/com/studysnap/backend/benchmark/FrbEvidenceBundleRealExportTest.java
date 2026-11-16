package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrbEvidenceBundleRealExportTest {
    private static final String TIP = "STUDY_TIP";
    private static final String MINI = "BASELINE_MINI";
    private static final String LUNA = "LUNA";
    private static final EvidenceMapping MAPPING = ClassAScoreSchemaV2Test.MAPPING;
    @TempDir Path temp;

    private static ArmKeyAccess.OpenedKey open(Path root, Path key) throws Exception {
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        return ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), root.resolve("access.jsonl"),
                ArmKeyGateFixtures.OPENED, "real-export FRB bundle fixture");
    }

    private static AdjudicatedLoaded a(Path root, String included, String excluded) throws Exception {
        Files.createDirectories(root);
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        for (String id : excluded == null ? List.of(included) : List.of(included, excluded))
            cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                    List.of(source), 1, null, false, null));
        ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
        var questions = ClassAScoreSchemaV2Test.JSON.createArrayNode().add(question);
        var export = ClassABlindPacketExport.export(cases, List.of(
                new ClassABlindPacketExport.Output(included, MINI, questions),
                new ClassABlindPacketExport.Output(included, LUNA, questions)), MINI, LUNA,
                root.resolve("packets"), root.resolve("key.jsonl"), 17L);
        var opened = open(root, export.armKey());
        var pairs = PairMap.fromPackets(root.resolve("packets"));
        Map<String, QuestionStatus> statuses = new HashMap<>();
        for (String id : pairs.blindItemIds())
            statuses.put(id, FinalStatus.of(ClassAAdjudicationFixture.direct(id, "C", "C", 0, null),
                    MAPPING, false));
        Set<String> frozen = excluded == null ? Set.of(included) : Set.of(included, excluded);
        Map<String, Integer> widths = new HashMap<>();
        frozen.forEach(id -> widths.put(id, 1));
        return AdjudicatedConverter.classA(opened, statuses, pairs, Set.of("NOTE_ADAPTIVE"),
                frozen, export.exclusions(), FreezeRecord.sha256(export.exclusions()), widths);
    }

    private static AdjudicatedLoaded b(Path root, String caseId, boolean reserve) throws Exception {
        return b(root, caseId, reserve, Set.of(caseId), reserve
                ? ClassBAdjudicatedConverter.ExportKind.RESERVE
                : ClassBAdjudicatedConverter.ExportKind.PRIMARY);
    }

    private static AdjudicatedLoaded b(Path root, String caseId, boolean reserve,
            Set<String> expected, ClassBAdjudicatedConverter.ExportKind kind) throws Exception {
        Files.createDirectories(root);
        var fixture = new ClassBCaseManifest.Case(caseId, ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", reserve, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        var export = ClassBBlindPacketExport.export(List.of(fixture), List.of(
                new ClassBBlindPacketExport.Output(caseId, ClassBScoreAggregator.Arm.BASELINE, "Answer one."),
                new ClassBBlindPacketExport.Output(caseId, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two.")),
                root.resolve("packets"), root.resolve("arm-key.jsonl"), 17L);
        var opened = open(root, root.resolve("arm-key.jsonl"));
        List<String> scores = new ArrayList<>();
        for (var item : export.manifest()) scores.add("""
                {"itemId":"%s","workflow":"STUDY_TIP","round":1,"scorerId":"HUMAN_OWNER",
                "scorerKind":"HUMAN","taskFulfilled":true,"grounded":true,"actionable":true,
                "noCriticalMisleading":true,"critical":false,"reasonCode":null,
                "borderline":false,"adjudication":false}""".formatted(item.itemId()).replace("\n", ""));
        Path file = root.resolve("scores.jsonl");
        Files.write(file, scores);
        var validated = ClassBScoreFiles.validate(file, export.manifest(),
                Map.of("HUMAN_OWNER", ClassBScoreFiles.ScorerKind.HUMAN));
        return ClassBAdjudicatedConverter.classB(opened, kind,
                validated, MAPPING, PairMap.fromPackets(root.resolve("packets")), Set.of(TIP),
                expected, new ClassBAdjudicatedConverter.ArmLabels(LUNA, ignored -> MINI));
    }

    @Test void twoRealClassAExportsAndTwoSeparateClassBKeysPreserveExclusions() throws Exception {
        var first = a(temp.resolve("a1"), "a1", "excluded-a");
        var second = a(temp.resolve("a2"), "a2", null);
        var primary = b(temp.resolve("b-primary"), "b-primary", false);
        var reserve = b(temp.resolve("b-reserve"), "b-reserve", true);
        String digest = "0".repeat(64);
        var record = new ReserveDecisionRecord(1, List.of(new ReserveDecisionRecord.WorkflowDecision(
                TIP, 1, true, false, digest)), digest, digest, ArmKeyGateFixtures.OPENED);
        var bundle = FrbEvidenceBundle.assemble(List.of(first, second), primary,
                List.of(new FrbEvidenceBundle.ReservePart(TIP, reserve, Set.of("b-reserve"))),
                Set.of("a1", "a2", "excluded-a"), Set.of("b-primary"), record, Set.of(TIP));
        assertThat(bundle.toLoaded(Map.of()).excluded()).containsExactly("excluded-a");
        assertThat(bundle.toLoaded(Map.of()).get("b-primary", LUNA)).isNotNull();
        assertThat(bundle.toLoaded(Map.of()).get("b-reserve", MINI)).isNotNull();
        assertThat(bundle.requestedQuestionsByCaseId()).containsEntry("b-primary", 1)
                .containsEntry("b-reserve", 1);
    }

    @Test void reserveExportRejectsWrongKindAndMissingOrExtraFrozenCase() throws Exception {
        assertThatThrownBy(() -> b(temp.resolve("wrong-kind"), "reserve-case", true,
                Set.of("reserve-case"), ClassBAdjudicatedConverter.ExportKind.PRIMARY))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> b(temp.resolve("missing"), "reserve-case", true,
                Set.of(), ClassBAdjudicatedConverter.ExportKind.RESERVE))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> b(temp.resolve("extra"), "reserve-case", true,
                Set.of("reserve-case", "extra"), ClassBAdjudicatedConverter.ExportKind.RESERVE))
                .isInstanceOf(InvalidEvidence.class);
    }
}
