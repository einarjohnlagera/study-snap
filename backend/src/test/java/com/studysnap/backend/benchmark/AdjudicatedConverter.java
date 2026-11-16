package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.benchmark.ClassAScoreSchemaV2.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Joins final blind findings to arms only after ArmKeyAccess has opened a locked key. */
final class AdjudicatedConverter {
    private static final String SEPARATOR = "\u0000";
    private static final String LONG_EXAM_PREFIX = "LONG_EXAM_";
    private static final String CASE_ID = "caseId";
    private static final String REASON = "reason";
    private static final Set<String> EXCLUSION_FIELDS = Set.of(CASE_ID, REASON);
    private static final Set<String> EXCLUSION_REASONS = Set.of("MISSING_ARM_OUTPUT", "SHORT_DELIVERY");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private AdjudicatedConverter() {}

    static AdjudicatedLoaded classA(ArmKeyAccess.OpenedKey key, Map<String, QuestionStatus> statuses,
            PairMap pairs, Set<String> classAWorkflowStems, Set<String> frozenClassACaseIds,
            Path exclusionsFileOrNull, String expectedExclusionsSha256OrNull,
            Map<String, Integer> requestedQuestionsByCaseId) {
        if (key == null || statuses == null || pairs == null || classAWorkflowStems == null
                || frozenClassACaseIds == null)
            throw new InvalidEvidence("Opened key, blind evidence and frozen scope required");
        Map<String, Integer> widths = requestedWidths(requestedQuestionsByCaseId, frozenClassACaseIds);
        Set<String> excluded = exclusions(exclusionsFileOrNull, expectedExclusionsSha256OrNull);
        if (statuses.values().stream().anyMatch(java.util.Objects::isNull))
            throw new InvalidEvidence("Null final question status");
        LockReadiness.check(statuses, Set.of());

        Map<String, ArmKeyAccess.KeyRow> selected = new LinkedHashMap<>();
        Set<String> keyedCases = new LinkedHashSet<>();
        for (ArmKeyAccess.KeyRow row : key.rows()) if (classAWorkflowStems.contains(row.workflow())) {
            selected.put(row.itemId(), row);
            keyedCases.add(row.caseId());
            QuestionStatus status = statuses.get(row.itemId());
            if (status == null || !row.itemId().equals(status.itemId()) || status.f() == null)
                throw new InvalidEvidence("Key item lacks final status: " + row.itemId());
            if (excluded.contains(row.caseId()))
                throw new InvalidEvidence("Excluded case is keyed: " + row.caseId());
        }
        for (var entry : statuses.entrySet()) if (!selected.containsKey(entry.getKey()))
            throw new InvalidEvidence("Status item absent from selected key: " + entry.getKey());
        Set<String> covered = new HashSet<>(keyedCases);
        covered.addAll(excluded);
        if (!covered.equals(frozenClassACaseIds))
            throw new InvalidEvidence("Keyed and excluded cases differ from frozen class-A scope");

        Set<String> pairedIds = new HashSet<>();
        for (PairMap.Pair pair : pairs.pairs()) if (classAWorkflowStems.contains(pair.workflow())) {
            Placement a = placement(pair, "A", key, selected.keySet(), pairedIds);
            Placement b = placement(pair, "B", key, selected.keySet(), pairedIds);
            if (!a.caseId().equals(b.caseId()) || a.arm().equals(b.arm()))
                throw new InvalidEvidence("Pair sides must be opposite arms of one case: " + pair.id());
            if (excluded.contains(a.caseId()))
                throw new InvalidEvidence("Excluded case has a pair: " + a.caseId());
        }
        if (!pairedIds.equals(selected.keySet()))
            throw new InvalidEvidence("Selected key items differ from blind pair items");

        Map<String, MutableFacts> collected = new LinkedHashMap<>();
        for (ArmKeyAccess.KeyRow row : selected.values()) {
            String identity = row.caseId() + SEPARATOR + row.arm();
            String subgroup = row.workflow().startsWith(LONG_EXAM_PREFIX)
                    ? row.workflow().substring(LONG_EXAM_PREFIX.length()) : null;
            MutableFacts facts = collected.computeIfAbsent(identity, ignored -> new MutableFacts(subgroup));
            if (!java.util.Objects.equals(facts.subgroup, subgroup))
                throw new InvalidEvidence("Case arm crosses subgroups: " + identity);
            facts.add(statuses.get(row.itemId()));
        }
        Map<String, AdjudicatedFacts> result = new LinkedHashMap<>();
        collected.forEach((identity, facts) -> {
            String caseId = identity.substring(0, identity.indexOf(SEPARATOR));
            if (facts.scoredQuestions < widths.get(caseId))
                throw new InvalidEvidence("Arm is shorter than requested: " + identity);
            result.put(identity, facts.freeze());
        });
        return new AdjudicatedLoaded(result, excluded, widths);
    }

    static Map<String, Integer> requestedWidths(Map<String, Integer> byCaseId,
                                                 java.util.Collection<String> caseIds) {
        if (byCaseId == null || caseIds == null) throw new InvalidEvidence("Requested widths required");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String caseId : caseIds) {
            if (caseId == null || caseId.isBlank() || result.containsKey(caseId))
                throw new InvalidEvidence("Invalid frozen case id: " + caseId);
            Integer width = byCaseId.get(caseId);
            if (width == null || width <= 0)
                throw new InvalidEvidence("Missing or nonpositive requested width: " + caseId);
            result.put(caseId, width);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Placement placement(PairMap.Pair pair, String side,
            ArmKeyAccess.OpenedKey key, Set<String> selectedIds, Set<String> pairedIds) {
        List<String> ids = pair.items().get(side);
        if (ids == null || ids.isEmpty()) throw new InvalidEvidence("Empty pair placement: " + pair.id());
        Placement placement = null;
        for (String id : ids) {
            ArmKeyAccess.KeyRow row = selectedIds.contains(id) ? key.rows().stream()
                    .filter(candidate -> id.equals(candidate.itemId())).findFirst().orElse(null) : null;
            if (row == null || !pair.workflow().equals(row.workflow()) || !pairedIds.add(id))
                throw new InvalidEvidence("Pair item missing or inconsistent in key: " + id);
            Placement current = new Placement(row.caseId(), row.arm());
            if (placement != null && !placement.equals(current))
                throw new InvalidEvidence("Placement spans cases or arms: " + pair.id());
            placement = current;
        }
        return placement;
    }

    private static Set<String> exclusions(Path exclusionsFileOrNull, String expectedHash) {
        if ((exclusionsFileOrNull == null) != (expectedHash == null))
            throw new InvalidEvidence("Exclusions file and expected SHA-256 must be supplied together");
        if (exclusionsFileOrNull == null) return Set.of();
        try {
            if (!expectedHash.equals(FreezeRecord.sha256(exclusionsFileOrNull)))
                throw new InvalidEvidence("Exclusions SHA-256 differs from expected hash");
            Set<String> result = new LinkedHashSet<>();
            for (String line : Files.readAllLines(exclusionsFileOrNull)) {
                if (line.isBlank()) throw new InvalidEvidence("Blank exclusion row");
                JsonNode row = JSON.readTree(line);
                if (row == null || !row.isObject()) throw new InvalidEvidence("Invalid exclusion row");
                Set<String> fields = new HashSet<>();
                row.fieldNames().forEachRemaining(fields::add);
                if (!fields.equals(EXCLUSION_FIELDS)) throw new InvalidEvidence("Invalid exclusion fields");
                String caseId = requiredText(row, CASE_ID);
                String reason = requiredText(row, REASON);
                if (!EXCLUSION_REASONS.contains(reason) || !result.add(caseId))
                    throw new InvalidEvidence("Unknown reason or duplicate excluded case: " + caseId);
            }
            return Set.copyOf(result);
        } catch (IOException exception) {
            throw new InvalidEvidence("Cannot read exclusions: " + exception.getMessage());
        }
    }

    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new InvalidEvidence("Blank exclusion field: " + field);
        return value.asText();
    }

    private record Placement(String caseId, String arm) {}

    private static final class MutableFacts {
        private final String subgroup;
        private int scoredQuestions;
        private int criticalQuestions;
        private int qualityFailQuestions;
        private int unresolvedQuestions;
        private int pendingQuestions;
        private int doubleMisses;
        private int scorerFailureCriticals;
        private int humanCleared;

        private MutableFacts(String subgroup) { this.subgroup = subgroup; }

        private void add(QuestionStatus status) {
            scoredQuestions++;
            criticalQuestions += status.f() == Verdict.CRITICAL ? 1 : 0;
            qualityFailQuestions += status.f() == Verdict.QUALITY_FAIL ? 1 : 0;
            unresolvedQuestions += status.f() == Verdict.UNRESOLVED ? 1 : 0;
            pendingQuestions += status.state() == QuestionStatus.State.PENDING_REVIEW ? 1 : 0;
            doubleMisses += status.doubleMiss() ? 1 : 0;
            scorerFailureCriticals += status.scorerFailureCritical() ? 1 : 0;
            humanCleared += status.humanCleared() ? 1 : 0;
        }

        private AdjudicatedFacts freeze() {
            return new AdjudicatedFacts(scoredQuestions, criticalQuestions, qualityFailQuestions,
                    unresolvedQuestions, pendingQuestions, doubleMisses, scorerFailureCriticals,
                    humanCleared, subgroup);
        }
    }
}
