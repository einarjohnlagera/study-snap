package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                  Row reserveOverall, String seedCheck, String seedReason, String limitation) {}
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
