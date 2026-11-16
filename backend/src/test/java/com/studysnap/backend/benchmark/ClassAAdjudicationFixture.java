package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Synthetic valid rows, always loaded against real exported blind packets. */
final class ClassAAdjudicationFixture {
    static final EvidenceMapping MAPPING = ClassAScoreSchemaV2Test.MAPPING;
    private ClassAAdjudicationFixture() {}

    static PairMap export(Path root, int count, ClassACaseManifest.Workflow workflow) {
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "case-" + i;
            var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                    List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
            cases.add(new ClassACaseManifest.Case(id, workflow, "synthetic", LearnerLevel.COLLEGE,
                    null, "Biology", false, List.of(source), 1, null, false, null));
            var questions = ClassAScoreSchemaV2Test.JSON.createArrayNode()
                    .add(ClassAScoreSchemaV2Test.packet("MCQ").question());
            outputs.add(new ClassABlindPacketExport.Output(id, "BASELINE", questions));
            outputs.add(new ClassABlindPacketExport.Output(id, "CANDIDATE", questions));
        }
        ClassABlindPacketExport.export(cases, outputs, "BASELINE", "CANDIDATE",
                root.resolve("packets"), root.resolve("source-key.jsonl"), 17L);
        return PairMap.fromPackets(root.resolve("packets"));
    }

    static PairMap exportLong(Path root, int pairsPerStem) {
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassACaseManifest.Subgroup subgroup : ClassACaseManifest.Subgroup.values())
            for (int i = 0; i < pairsPerStem; i++) {
                String id = subgroup.name() + "-" + i;
                var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE,
                        "Boundary", List.of("membrane"), List.of("cells"), "Biology", "Biology",
                        null, LearnerLevel.COLLEGE);
                cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                        "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                        List.of(source), 1, null, false, subgroup));
                var questions = ClassAScoreSchemaV2Test.JSON.createArrayNode()
                        .add(ClassAScoreSchemaV2Test.packet("MCQ").question());
                outputs.add(new ClassABlindPacketExport.Output(id, "BASELINE", questions));
                outputs.add(new ClassABlindPacketExport.Output(id, "CANDIDATE", questions));
            }
        ClassABlindPacketExport.export(cases, outputs, "BASELINE", "CANDIDATE",
                root.resolve("packets"), root.resolve("source-key.jsonl"), 17L);
        return PairMap.fromPackets(root.resolve("packets"));
    }

    static ObjectNode scorer(String id, String role, String verdict, boolean borderline) {
        ObjectNode row = ClassAScoreSchemaV2Test.scorer(id, role);
        switch (verdict) {
            case "Q" -> row.put("factuallyCorrect", "NO");
            case "K" -> row.put("critical", true).put("criticalType", "EXPLANATION_CONTRADICTS_KEY");
            case "C", "INVALID" -> { }
            default -> throw new InvalidEvidence("Unknown fixture scorer verdict: " + verdict);
        }
        row.put("borderline", borderline);
        if (borderline || verdict.equals("Q") || verdict.equals("K")) ClassAScoreSchemaV2Test.evidence(row);
        if (verdict.equals("INVALID")) row.put("keyDefensible", false);
        return row;
    }

    static ObjectNode human(String id, String verdict, boolean criticalScorer) {
        ObjectNode row = ClassAScoreSchemaV2Test.human(id);
        switch (verdict) {
            case "C" -> row.put("humanVerdict", "CLEAN");
            case "Q" -> row.put("humanVerdict", "QUALITY_FAIL").put("ambiguityClass", "MINOR_WORDING");
            case "K" -> row.put("humanVerdict", "CRITICAL").put("criticalType", "EXPLANATION_CONTRADICTS_KEY");
            case "U" -> row.put("humanVerdict", "UNRESOLVED");
            default -> throw new InvalidEvidence("Unknown fixture human verdict: " + verdict);
        }
        if (criticalScorer && verdict.equals("C")) row.put("humanClearReason", "REVIEWED_NOT_DEFECT");
        if (criticalScorer && verdict.equals("Q")) row.put("humanClearReason", "DOWNGRADED_NONCRITICAL");
        if (!verdict.equals("C") || criticalScorer) ClassAScoreSchemaV2Test.evidence(row);
        return row;
    }

    static Map<String, ClassAScoreFilesV2.ItemScores> load(Path root, PairMap pairs,
            Map<String, String> pVerdicts, Map<String, String> sVerdicts, Map<String, String> hVerdicts,
            Map<String, Integer> borderlineBits) throws Exception {
        Path key = root.resolve("id-only.jsonl");
        if (!Files.exists(key)) pairs.idOnlyKey(key);
        List<String> p = new ArrayList<>(), s = new ArrayList<>(), h = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            String pv = pVerdicts.get(id), sv = sVerdicts.get(id), hv = hVerdicts.get(id);
            int bits = borderlineBits.getOrDefault(id, 0);
            if (pv != null && !pv.equals("MISSING")) p.add(scorer(id, "P", pv, (bits & 1) != 0).toString());
            if (sv != null && !sv.equals("MISSING")) s.add(scorer(id, "S", sv, (bits & 2) != 0).toString());
            if (hv != null) h.add(human(id, hv, "K".equals(pv) || "K".equals(sv)).toString());
        }
        Path pFile = root.resolve("p.jsonl"), sFile = root.resolve("s.jsonl"), hFile = root.resolve("h.jsonl");
        Files.write(pFile, p); Files.write(sFile, s); Files.write(hFile, h);
        return ClassAScoreFilesV2.load(key, ClassAPacketIndex.load(root.resolve("packets")),
                pFile, sFile, hFile, MAPPING);
    }

    static ClassAScoreFilesV2.ItemScores direct(String id, String p, String s, int borderlineBits, String h) {
        return new ClassAScoreFilesV2.ItemScores(id, slot(id, "P", p, (borderlineBits & 1) != 0),
                slot(id, "S", s, (borderlineBits & 2) != 0),
                h == null ? null : new ClassAScoreFilesV2.HumanRow(human(id, h, p.equals("K") || s.equals("K"))));
    }

    private static ClassAScoreFilesV2.Slot slot(String id, String role, String verdict, boolean borderline) {
        if (verdict.equals("MISSING")) return ClassAScoreFilesV2.Slot.missing();
        if (verdict.equals("INVALID")) return ClassAScoreFilesV2.Slot.invalid(
                new ClassAScoreSchemaV2.InvalidRow(ClassAScoreSchemaV2.Code.STRUCTURE, "Invalid synthetic row"));
        return ClassAScoreFilesV2.Slot.ok(scorer(id, role, verdict, borderline));
    }
}
