package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Post-unlock placement sanity check, not a randomization proof or verdict. No seed is sealed:
 * export-time layout cannot be reproduced. Integrity failure throws InvalidAudit and means BLOCKED;
 * a returned flag is only a statistic and cannot silently clear an open concern.
 */
final class PlacementAudit {
    static final class InvalidAudit extends RuntimeException {
        InvalidAudit(String message, Throwable cause) { super(message, cause); }
    }
    static final String SEED_CHECK = "UNVERIFIABLE_NO_SEALED_SEED";
    static final String SEED_REASON = "Export placement uses an unsealed seed at export time; neither seal nor lock records it";
    record Row(int pairs, int candidateInA, Rational twoSidedP, boolean undefined, boolean flagged) {}
    record Report(Map<String, Row> workflows, Row overall, Map<String, Row> reserveWorkflows,
                  Row reserveOverall, String seedCheck, String seedReason, String limitation,
                  String commitmentAnchor) {
        Report(Map<String, Row> workflows, Row overall, Map<String, Row> reserveWorkflows,
               Row reserveOverall, String seedCheck, String seedReason, String limitation) {
            this(workflows, overall, reserveWorkflows, reserveOverall, seedCheck, seedReason,
                    limitation, "CALLER_SUPPLIED");
        }
    }
    static Report run(Path packets, Path seal, String candidateArm,
                      ArmKeyAccess.OpenedKey opened, Rational alpha) {
        try {
            List<BlindAuditSupport.Pair> pairs = BlindAuditSupport.pairs(packets, seal, candidateArm, opened, alpha);
            Map<String, List<BlindAuditSupport.Pair>> primary = new TreeMap<>(), reserve = new TreeMap<>();
            for (BlindAuditSupport.Pair pair : pairs)
                (pair.reserve() ? reserve : primary).computeIfAbsent(pair.workflow(), unused -> new ArrayList<>()).add(pair);
            Map<String, Row> primaryRows = rows(primary, alpha), reserveRows = rows(reserve, alpha);
            return new Report(primaryRows, row(primary.values().stream().flatMap(List::stream).toList(), alpha),
                    reserveRows, row(reserve.values().stream().flatMap(List::stream).toList(), alpha),
                    SEED_CHECK, SEED_REASON,
                    "Placement balance is only a sanity check; an alternating layout can pass it");
        } catch (Exception exception) { throw new InvalidAudit("Cannot run placement audit", exception); }
    }
    static Report run(Path packets, Path seal, String candidateArm,
                      ArmKeyAccess.OpenedKey opened, Rational alpha,
                      String expectedCommitment, String exportId, Map<String, Set<String>> frozen) {
        Report base = run(packets, seal, candidateArm, opened, alpha);
        PlacementSidecar.Reveal reveal = opened.placement();
        if (reveal == null) return new Report(base.workflows(), base.overall(), base.reserveWorkflows(),
                base.reserveOverall(), SEED_CHECK, SEED_REASON, base.limitation(), "CALLER_SUPPLIED");
        if (expectedCommitment == null) return new Report(base.workflows(), base.overall(), base.reserveWorkflows(),
                base.reserveOverall(), "COMMITMENT_NOT_PROVIDED", "Caller supplied no commitment",
                base.limitation(), "CALLER_SUPPLIED");
        int mismatches = 0;
        try {
            ArmKeySeal.Seal declaration = ArmKeySeal.load(seal);
            if (!IntegritySupport.digest(expectedCommitment)
                    || !expectedCommitment.equals(reveal.commitment())
                    || exportId == null || !exportId.equals(reveal.exportId())
                    || !exportId.equals(declaration.placementExportId())) mismatches++;
            if (frozen == null) mismatches++;
            else {
                Map<String, Set<String>> sealed = new HashMap<>();
                for (PlacementSidecar.Group group : reveal.groups())
                    sealed.put(group.groupKey(), Set.copyOf(group.caseIds()));
                Set<String> excluded = new HashSet<>();
                reveal.excluded().forEach(row -> excluded.add(row.caseId()));
                Set<String> allFrozen = new HashSet<>();
                for (var entry : frozen.entrySet()) {
                    Set<String> declared = sealed.getOrDefault(entry.getKey(), Set.of());
                    Set<String> expected = entry.getValue();
                    if (expected == null || !allFrozen.addAll(expected)) { mismatches++; continue; }
                    Set<String> actual = new HashSet<>(declared);
                    for (String id : excluded) if (expected.contains(id)) {
                        if (!actual.add(id)) mismatches++;
                    }
                    if (!actual.equals(expected)) mismatches++;
                }
                if (!allFrozen.containsAll(excluded) || !frozen.keySet().containsAll(sealed.keySet())) mismatches++;
            }
            PairMap map = PairMap.fromPackets(packets);
            Map<String, ArmKeyAccess.KeyRow> byId = new HashMap<>();
            opened.rows().forEach(row -> byId.put(row.itemId(), row));
            Set<String> expectedPairs = new HashSet<>();
            for (PlacementSidecar.Group group : reveal.groups()) {
                List<PlacementPlan.Placed> planned = PlacementPlan.layout(group.caseIds(),
                        reveal.secret(), reveal.exportId(), group.groupKey());
                List<PairMap.Pair> actual = map.pairs().stream()
                        .filter(pair -> pair.workflow().equals(group.groupKey())).toList();
                if (actual.size() != planned.size()) mismatches++;
                for (int i = 0; i < Math.min(actual.size(), planned.size()); i++) {
                    PairMap.Pair pair = actual.get(i);
                    expectedPairs.add(pair.id());
                    ArmKeyAccess.KeyRow a = byId.get(pair.items().get("A").getFirst());
                    ArmKeyAccess.KeyRow b = byId.get(pair.items().get("B").getFirst());
                    PlacementPlan.Placed target = planned.get(i);
                    if (a == null || b == null || !target.caseId().equals(a.caseId())
                            || !target.caseId().equals(b.caseId())
                            || !a.arm().equals(target.firstArmInA() ? reveal.firstArm() : reveal.secondArm()))
                        mismatches++;
                }
            }
            if (expectedPairs.size() != map.pairs().size()) mismatches++;
        } catch (Exception exception) { mismatches++; }
        return new Report(base.workflows(), base.overall(), base.reserveWorkflows(), base.reserveOverall(),
                mismatches == 0 ? "VERIFIED" : "MISMATCH",
                mismatches == 0 ? "Sealed placement reproduced" : "Placement mismatch count=" + mismatches,
                base.limitation(), "CALLER_SUPPLIED");
    }
    private static Map<String, Row> rows(Map<String, List<BlindAuditSupport.Pair>> groups, Rational alpha) {
        Map<String, Row> result = new TreeMap<>();
        groups.forEach((name, pairs) -> result.put(name, row(pairs, alpha)));
        return java.util.Collections.unmodifiableMap(result);
    }
    private static Row row(List<BlindAuditSupport.Pair> pairs, Rational alpha) {
        int candidateInA = (int) pairs.stream().filter(pair -> "A".equals(pair.candidatePlacement())).count();
        BlindAuditSupport.Binomial result = BlindAuditSupport.binomial(candidateInA, pairs.size());
        return new Row(pairs.size(), candidateInA, result.p(), result.undefined(),
                !result.undefined() && BlindAuditSupport.atMost(result.p(), alpha));
    }
}
