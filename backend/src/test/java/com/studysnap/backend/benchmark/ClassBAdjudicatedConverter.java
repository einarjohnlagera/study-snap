package com.studysnap.backend.benchmark;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Attributes blind class-B human findings only through an opened, locked arm key. */
final class ClassBAdjudicatedConverter {
    enum ExportKind { PRIMARY, RESERVE }

    record ArmLabels(String candidateArm, Function<String, String> baselineArmByWorkflow) {
        ArmLabels {
            if (candidateArm == null || candidateArm.isBlank() || baselineArmByWorkflow == null)
                throw new InvalidEvidence("Class-B receipt arm labels required");
        }

        String receiptArm(String workflow, String keyArm) {
            if ("CANDIDATE".equals(keyArm)) return candidateArm;
            if (!"BASELINE".equals(keyArm)) throw new InvalidEvidence("Unknown class-B key arm: " + keyArm);
            String baseline = baselineArmByWorkflow.apply(workflow);
            if (baseline == null || baseline.isBlank() || baseline.equals(candidateArm))
                throw new InvalidEvidence("Invalid baseline receipt arm: " + workflow);
            return baseline;
        }
    }

    private ClassBAdjudicatedConverter() {}

    static AdjudicatedLoaded classB(ArmKeyAccess.OpenedKey key, ExportKind kind,
            ClassBScoreFiles.Validated validated, EvidenceMapping mapping, PairMap classBPairs,
            Set<String> classBWorkflowNames, Set<String> expectedCaseIds, ArmLabels labels) {
        if (key == null || kind == null || validated == null || mapping == null || classBPairs == null
                || classBWorkflowNames == null || expectedCaseIds == null || labels == null
                || classBWorkflowNames.stream().anyMatch(name -> name == null || name.isBlank())
                || expectedCaseIds.stream().anyMatch(id -> id == null || id.isBlank()))
            throw new InvalidEvidence("Complete class-B conversion inputs required");
        boolean reserve = kind == ExportKind.RESERVE;
        Map<String, ArmKeyAccess.KeyRow> rows = new LinkedHashMap<>();
        Set<String> cases = new LinkedHashSet<>();
        for (ArmKeyAccess.KeyRow row : key.rows()) {
            if (!classBWorkflowNames.contains(row.workflow())) continue;
            if (row.reserve() == null || row.reserve() != reserve)
                throw new InvalidEvidence("Class-B key row has wrong export kind: " + row.itemId());
            if (row.itemId() == null || row.itemId().isBlank() || row.caseId() == null
                    || row.caseId().isBlank() || rows.putIfAbsent(row.itemId(), row) != null)
                throw new InvalidEvidence("Invalid or duplicate class-B key row");
            labels.receiptArm(row.workflow(), row.arm());
            cases.add(row.caseId());
        }
        if (!cases.equals(expectedCaseIds))
            throw new InvalidEvidence("Class-B key cases differ from frozen export cases");

        Map<String, ClassBScoreFiles.BlindItem> manifest = new HashMap<>();
        for (ClassBScoreFiles.BlindItem item : validated.manifest()) {
            if (item == null) throw new InvalidEvidence("Null class-B manifest item");
            if (!classBWorkflowNames.contains(item.workflow().name()) || item.reserve() != reserve) continue;
            if (manifest.putIfAbsent(item.itemId(), item) != null)
                throw new InvalidEvidence("Duplicate class-B manifest item: " + item.itemId());
        }
        if (!manifest.keySet().equals(rows.keySet()))
            throw new InvalidEvidence("Class-B key and manifest item sets differ");
        for (ArmKeyAccess.KeyRow row : rows.values()) {
            ClassBScoreFiles.BlindItem item = manifest.get(row.itemId());
            if (!row.workflow().equals(item.workflow().name()) || row.reserve() != item.reserve())
                throw new InvalidEvidence("Class-B key and manifest disagree: " + row.itemId());
        }

        Set<String> paired = new HashSet<>();
        for (PairMap.Pair pair : classBPairs.pairs()) {
            if (!classBWorkflowNames.contains(pair.workflow())) continue;
            List<String> a = pair.items().get("A"), b = pair.items().get("B");
            if (a == null || b == null || a.size() != 1 || b.size() != 1)
                throw new InvalidEvidence("Class-B pair needs one item per placement: " + pair.id());
            boolean selectedA = rows.containsKey(a.getFirst()), selectedB = rows.containsKey(b.getFirst());
            if (!selectedA && !selectedB) continue;
            if (!selectedA || !selectedB || !paired.add(a.getFirst()) || !paired.add(b.getFirst()))
                throw new InvalidEvidence("Incomplete or duplicate class-B pair: " + pair.id());
            ArmKeyAccess.KeyRow left = rows.get(a.getFirst()), right = rows.get(b.getFirst());
            if (!left.caseId().equals(right.caseId()) || left.arm().equals(right.arm())
                    || !left.workflow().equals(right.workflow()) || !pair.workflow().equals(left.workflow())
                    || !left.reserve().equals(right.reserve()))
                throw new InvalidEvidence("Class-B pair crosses case, arm, workflow or export: " + pair.id());
        }
        if (!paired.equals(rows.keySet()))
            throw new InvalidEvidence("Class-B key items differ from blind pairs");

        Map<String, ClassBScoreFiles.Score> scores = validated.finalHumanScores();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        for (String id : expectedCaseIds) widths.put(id, 1);
        for (ArmKeyAccess.KeyRow row : rows.values()) {
            ClassBScoreFiles.Score score = scores.get(row.itemId());
            String arm = labels.receiptArm(row.workflow(), row.arm());
            String identity = row.caseId() + "\u0000" + arm;
            AdjudicatedFacts value = new AdjudicatedFacts(1,
                    score != null && mapping.criticalB(score) ? 1 : 0,
                    score != null && mapping.qualityB(score) ? 1 : 0,
                    score == null ? 1 : 0, 0, 0, 0, 0, null);
            if (facts.putIfAbsent(identity, value) != null)
                throw new InvalidEvidence("Duplicate class-B receipt arm: " + identity);
        }
        return new AdjudicatedLoaded(facts, Set.of(), widths);
    }
}
