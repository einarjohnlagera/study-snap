package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PilotBarsEvaluator {
    private static final String L_P = "P";
    private static final String L_S = "S";
    private static final String L_BORDERLINE = "borderline";
    private static final String L_RATIONALE = "rationale";
    private static final String L_AMBIGUITYCLASS = "ambiguityClass";
    private static final String L_DEFENSIBLEANSWERCOUNT = "defensibleAnswerCount";
    private static final String L_KEYDEFENSIBLE = "keyDefensible";
    private static final String L_SLASH = "/";
    record Bar(boolean passed, String observed, String required) {}
    record ScorerReport(Map<Integer, Bar> bars, double ownFamilyCriticalRecall, double borderlineRate,
                        double criticalFlagRate, Map<PlantedType, Double> recallByType,
                        int cleanQualityFails, int cleanQualityFailReference,
                        double cleanControlDisagreementLowerBound, double cleanControlBorderlineLowerBound) {}
    record PilotBarReport(Map<String, ScorerReport> scorers, boolean pilotPassed, double psVerdictAgreementRate) {}
    private PilotBarsEvaluator() {}

    static PilotBarReport evaluate(PilotKey key, Map<String, ClassAScoreFilesV2.ItemScores> scores,
                                   EvidenceMapping mapping, PilotBars bars, List<PilotLeakCheck.Hit> leakHits,
                                   PilotOwnerRatings ratings) {
        if (key == null || scores == null || mapping == null || bars == null || leakHits == null || ratings == null
                || ratings.ratings() == null) throw new PilotBars.InvalidPilotBars("Evaluator inputs required");
        composition(key, bars);
        Set<String> keyIds = new HashSet<>();
        key.rows().forEach(row -> keyIds.add(row.id()));
        if (!scores.keySet().equals(keyIds)) throw new PilotBars.InvalidPilotBars("Score ids differ from key");
        Map<String, ScorerReport> reports = new LinkedHashMap<>();
        List<RationaleSample.NonCleanRow> candidates = new ArrayList<>();
        for (String role : List.of(L_P, L_S)) {
            int valid = 0, criticalRecall = 0, material = 0, zeroCorrect = 0;
            int crossCount = 0, crossRecall = 0, ownCount = 0, ownRecall = 0;
            int minor = 0, noncritical = 0, cleanCritical = 0, cleanQuality = 0;
            int borderline = 0, criticalFlags = 0, nonClean = 0, nonCleanWithRationale = 0;
            int cleanDisagreement = 0, cleanBorderline = 0;
            Map<PlantedType, Integer> typeCounts = new EnumMap<>(PlantedType.class);
            Map<PlantedType, Integer> typeHits = new EnumMap<>(PlantedType.class);
            for (PilotKey.Row keyRow : key.rows()) {
                ClassAScoreFilesV2.ItemScores item = scores.get(keyRow.id());
                ClassAScoreFilesV2.Slot slot = slot(item, role);
                JsonNode row = slot.status() == ClassAScoreFilesV2.Status.OK ? slot.row() : null;
                ClassAScoreSchemaV2.Verdict verdict = row == null ? null : ClassAScoreSchemaV2.verdict(row, mapping);
                if (row != null) {
                    valid++;
                    if (row.path(L_BORDERLINE).asBoolean()) borderline++;
                    if (verdict == ClassAScoreSchemaV2.Verdict.CRITICAL) criticalFlags++;
                    if (verdict != ClassAScoreSchemaV2.Verdict.CLEAN) {
                        nonClean++;
                        if (row.path(L_RATIONALE).isTextual() && !row.path(L_RATIONALE).asText().isBlank())
                            nonCleanWithRationale++;
                        candidates.add(new RationaleSample.NonCleanRow(keyRow.id(), role));
                    }
                }
                boolean markedCritical = verdict == ClassAScoreSchemaV2.Verdict.CRITICAL;
                typeCounts.merge(keyRow.plantedType(), 1, Integer::sum);
                if (keyRow.severity() == PlantedType.Severity.CRITICAL && markedCritical
                        || keyRow.severity() == PlantedType.Severity.NONCRITICAL && verdict != null
                            && verdict != ClassAScoreSchemaV2.Verdict.CLEAN
                        || keyRow.severity() == PlantedType.Severity.CLEAN
                            && verdict == ClassAScoreSchemaV2.Verdict.CLEAN)
                    typeHits.merge(keyRow.plantedType(), 1, Integer::sum);
                if (keyRow.severity() == PlantedType.Severity.CRITICAL) {
                    if (markedCritical) criticalRecall++;
                    boolean cross = L_P.equals(role)
                            ? keyRow.authoringSource() == PilotFixtureSource.AuthoringSource.CLAUDE_OR_OWNER
                            : keyRow.authoringSource() == PilotFixtureSource.AuthoringSource.CODEX;
                    if (cross) { crossCount++; if (markedCritical) crossRecall++; }
                    else { ownCount++; if (markedCritical) ownRecall++; }
                }
                if (materialType(keyRow.plantedType()) && row != null
                        && "MATERIAL".equals(row.path(L_AMBIGUITYCLASS).asText())) material++;
                if (keyRow.plantedType() == PlantedType.MATERIAL_AMBIGUITY_ZERO && row != null
                        && row.path(L_DEFENSIBLEANSWERCOUNT).isInt()
                        && row.path(L_DEFENSIBLEANSWERCOUNT).asInt() == 0
                        && row.path(L_KEYDEFENSIBLE).isBoolean() && !row.path(L_KEYDEFENSIBLE).asBoolean()) zeroCorrect++;
                if (keyRow.plantedType() == PlantedType.MINOR_WORDING && row != null
                        && "MINOR_WORDING".equals(row.path(L_AMBIGUITYCLASS).asText()) && !markedCritical) minor++;
                if (keyRow.severity() == PlantedType.Severity.NONCRITICAL && verdict != null
                        && verdict != ClassAScoreSchemaV2.Verdict.CLEAN) noncritical++;
                if (keyRow.severity() == PlantedType.Severity.CLEAN) {
                    if (markedCritical) cleanCritical++;
                    if (verdict == ClassAScoreSchemaV2.Verdict.QUALITY_FAIL) cleanQuality++;
                    if (row != null && row.path(L_BORDERLINE).asBoolean()) cleanBorderline++;
                    ClassAScoreFilesV2.Slot other = slot(item, L_P.equals(role) ? L_S : L_P);
                    if (row != null && other.status() == ClassAScoreFilesV2.Status.OK
                            && verdict != ClassAScoreSchemaV2.verdict(other.row(), mapping)) cleanDisagreement++;
                }
            }
            Map<Integer, Bar> result = new LinkedHashMap<>();
            result.put(1, bar(valid == key.rows().size(), valid, key.rows().size()));
            boolean criticalPass = criticalRecall >= bars.minCriticalRecallCount
                    && material >= bars.minMaterialAmbiguityMaterialCount
                    && zeroCorrect == key.countsByType().get(PlantedType.MATERIAL_AMBIGUITY_ZERO)
                    && crossCount > 0 && (double) crossRecall / crossCount >= bars.minCrossFamilyRecallFraction;
            result.put(2, new Bar(criticalPass,
                    "critical=" + criticalRecall + ", material=" + material + ", zero=" + zeroCorrect
                            + ", cross=" + crossRecall + L_SLASH + crossCount,
                    "critical>=" + bars.minCriticalRecallCount + ", material>=" + bars.minMaterialAmbiguityMaterialCount
                            + ", zero=all, cross>=" + bars.minCrossFamilyRecallFraction));
            result.put(3, bar(minor >= bars.minMinorWordingCount, minor, bars.minMinorWordingCount));
            result.put(4, bar(noncritical >= bars.minNoncriticalFlaggedCount, noncritical, bars.minNoncriticalFlaggedCount));
            result.put(5, bar(cleanCritical <= bars.maxCleanControlCriticalCount, cleanCritical,
                    bars.maxCleanControlCriticalCount));
            result.put(6, bar(leakHits.isEmpty(), leakHits.size(), 0));
            Map<PlantedType, Double> typeRecall = new EnumMap<>(PlantedType.class);
            for (PlantedType type : PlantedType.values())
                typeRecall.put(type, (double) typeHits.getOrDefault(type, 0) / typeCounts.get(type));
            reports.put(role, new ScorerReport(result, ownCount == 0 ? Double.NaN : (double) ownRecall / ownCount,
                    (double) borderline / key.rows().size(), (double) criticalFlags / key.rows().size(),
                    Map.copyOf(typeRecall), cleanQuality, bars.cleanQualityFailReference,
                    (double) cleanDisagreement / bars.cleanTotal, (double) cleanBorderline / bars.cleanTotal));
            // Bar 7 is finalized after the per-role seeded sample is selected.
            result.put(7, bar(nonClean == nonCleanWithRationale, nonCleanWithRationale, nonClean));
        }
        List<RationaleSample.NonCleanRow> sample = RationaleSample.select(candidates, bars.rationaleSampleSeed,
                bars.ratedRationales);
        Set<RationaleSample.NonCleanRow> expected = Set.copyOf(sample);
        Set<RationaleSample.NonCleanRow> actual = new HashSet<>();
        Map<String, Integer> adequate = new HashMap<>(Map.of(L_P, 0, L_S, 0));
        for (OwnerTimingSheet.RationaleRating rating : ratings.ratings()) {
            RationaleSample.NonCleanRow selected = new RationaleSample.NonCleanRow(rating.itemId(), rating.scorerRole());
            if (!actual.add(selected)) throw new PilotBars.InvalidPilotBars("Duplicate rationale rating");
            if (rating.adequate()) adequate.merge(rating.scorerRole(), 1, Integer::sum);
        }
        if (!actual.equals(expected)) throw new PilotBars.InvalidPilotBars("Ratings differ from seeded sample");
        for (String role : List.of(L_P, L_S)) {
            ScorerReport prior = reports.get(role);
            Map<Integer, Bar> updated = new LinkedHashMap<>(prior.bars());
            updated.put(7, new Bar(prior.bars().get(7).passed()
                    && adequate.get(role) >= bars.minAdequateRationales,
                    "rationales=" + prior.bars().get(7).observed() + ", adequate=" + adequate.get(role),
                    "all non-clean; adequate>=" + bars.minAdequateRationales + L_SLASH + bars.ratedRationales));
            reports.put(role, new ScorerReport(Map.copyOf(updated), prior.ownFamilyCriticalRecall(), prior.borderlineRate(),
                    prior.criticalFlagRate(), prior.recallByType(), prior.cleanQualityFails(),
                    prior.cleanQualityFailReference(), prior.cleanControlDisagreementLowerBound(),
                    prior.cleanControlBorderlineLowerBound()));
        }
        int comparable = 0, agreements = 0;
        for (ClassAScoreFilesV2.ItemScores item : scores.values()) {
            if (item.p().status() == ClassAScoreFilesV2.Status.OK && item.s().status() == ClassAScoreFilesV2.Status.OK) {
                comparable++;
                if (ClassAScoreSchemaV2.verdict(item.p().row(), mapping)
                        == ClassAScoreSchemaV2.verdict(item.s().row(), mapping)) agreements++;
            }
        }
        double agreementRate = comparable == 0 ? Double.NaN : (double) agreements / comparable;
        for (String role : List.of(L_P, L_S)) {
            ScorerReport prior = reports.get(role);
            Map<Integer, Bar> updated = new LinkedHashMap<>(prior.bars());
            updated.put(8, new Bar(true,
                    "P-S agreement=" + agreementRate + ", borderline=" + prior.borderlineRate()
                            + ", critical flags=" + prior.criticalFlagRate()
                            + ", per-type recall=" + prior.recallByType()
                            + ", clean disagreement (lower bound only)="
                            + prior.cleanControlDisagreementLowerBound()
                            + ", clean borderline (lower bound only)="
                            + prior.cleanControlBorderlineLowerBound(), "reported only"));
            reports.put(role, new ScorerReport(Map.copyOf(updated), prior.ownFamilyCriticalRecall(),
                    prior.borderlineRate(), prior.criticalFlagRate(), prior.recallByType(),
                    prior.cleanQualityFails(), prior.cleanQualityFailReference(),
                    prior.cleanControlDisagreementLowerBound(), prior.cleanControlBorderlineLowerBound()));
        }
        boolean passed = reports.values().stream().allMatch(report -> report.bars().entrySet().stream()
                .filter(entry -> entry.getKey() <= 7).allMatch(entry -> entry.getValue().passed()));
        return new PilotBarReport(Map.copyOf(reports), passed, agreementRate);
    }
    private static void composition(PilotKey key, PilotBars bars) {
        Map<PlantedType.Severity, Long> severity = key.countsBySeverity();
        Map<PlantedType, Long> types = key.countsByType();
        if (severity.get(PlantedType.Severity.CRITICAL) != bars.criticalTotal
                || severity.get(PlantedType.Severity.NONCRITICAL) != bars.noncriticalTotal
                || severity.get(PlantedType.Severity.CLEAN) != bars.cleanTotal
                || types.get(PlantedType.MATERIAL_AMBIGUITY_MULTI) + types.get(PlantedType.MATERIAL_AMBIGUITY_ZERO)
                    != bars.materialAmbiguityTotal
                || types.get(PlantedType.MINOR_WORDING) != bars.minorWordingTotal)
            throw new PilotBars.InvalidPilotBars("Pilot key composition differs from frozen bars");
    }
    private static boolean materialType(PlantedType type) {
        return type == PlantedType.MATERIAL_AMBIGUITY_MULTI || type == PlantedType.MATERIAL_AMBIGUITY_ZERO;
    }
    private static ClassAScoreFilesV2.Slot slot(ClassAScoreFilesV2.ItemScores item, String role) {
        if (item == null) throw new PilotBars.InvalidPilotBars("Missing ItemScores");
        return L_P.equals(role) ? item.p() : item.s();
    }
    private static Bar bar(boolean passed, int observed, int required) {
        return new Bar(passed, Integer.toString(observed), Integer.toString(required));
    }
}
