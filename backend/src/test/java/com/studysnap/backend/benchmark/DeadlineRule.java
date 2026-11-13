package com.studysnap.backend.benchmark;

/** Section 9 deadline fallback classification, distinct from benchmark PASS. */
final class DeadlineRule {
    private DeadlineRule() {}
    enum Classification { LUNA_FALLBACK_ELIGIBLE, RETURN_TO_OWNER, BENCHMARK_PASS }

    static Classification classify(RouteVerdictCalculator.RouteReport report) {
        if (report.failedTiers().contains(RouteVerdictCalculator.Tier.T1)
                || report.failedTiers().contains(RouteVerdictCalculator.Tier.T2)
                || report.isolatedCriticalIncident()) return Classification.RETURN_TO_OWNER;
        if (report.verdict() == RouteVerdictCalculator.Verdict.INCONCLUSIVE
                || report.failedTiers().equals(java.util.Set.of(RouteVerdictCalculator.Tier.T3)))
            return Classification.LUNA_FALLBACK_ELIGIBLE;
        return Classification.BENCHMARK_PASS;
    }
}
