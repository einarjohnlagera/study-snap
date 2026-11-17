package com.studysnap.backend.benchmark;

import java.util.Map;
import java.util.Set;
import java.util.List;

/** Section 9 paired Sol-versus-Luna escalation rule. */
final class SolRule {
    private SolRule() {}

    record Comparison(RouteVerdictCalculator.RouteReport luna,
                      RouteVerdictCalculator.RouteReport sol,
                      int solVersusLunaD,
                      Map<String, Map<String, Boolean>> lunaReserveCases,
                      Map<String, Map<String, Boolean>> solReserveCases,
                      Set<String> lunaT1Failures,
                      Set<String> solT1Failures,
                      boolean economicsAndOtherGatesPass,
                      boolean investigationComplete,
                      boolean samePrimaryInputs,
                      Map<String, Integer> lunaCriticalLowByCaseId,
                      Map<String, Integer> solCriticalHighByCaseId) {
        Comparison(RouteVerdictCalculator.RouteReport luna, RouteVerdictCalculator.RouteReport sol,
                   int solVersusLunaD, Map<String, Map<String, Boolean>> lunaReserveCases,
                   Map<String, Map<String, Boolean>> solReserveCases, Set<String> lunaT1Failures,
                   Set<String> solT1Failures, boolean economicsAndOtherGatesPass,
                   boolean investigationComplete, boolean samePrimaryInputs) {
            this(luna, sol, solVersusLunaD, lunaReserveCases, solReserveCases, lunaT1Failures,
                    solT1Failures, economicsAndOtherGatesPass, investigationComplete, samePrimaryInputs,
                    null, null);
        }
    }

    static boolean justified(FrozenGateParameters parameters, Comparison comparison) {
        RouteVerdictCalculator.RouteReport luna = comparison.luna();
        RouteVerdictCalculator.RouteReport sol = comparison.sol();
        if (!luna.undisposedCriticalCaseIds().isEmpty()
                || !sol.undisposedCriticalCaseIds().isEmpty()
                || luna.verdict() != RouteVerdictCalculator.Verdict.FAIL
                || sol.verdict() != RouteVerdictCalculator.Verdict.PASS
                || !luna.workflows().keySet().equals(sol.workflows().keySet())
                || !parameters.sha256.equals(luna.parameterSha256())
                || !parameters.sha256.equals(sol.parameterSha256())
                || !comparison.economicsAndOtherGatesPass()
                || !comparison.investigationComplete()
                || !comparison.samePrimaryInputs()
                || sol.failedTiers().contains(RouteVerdictCalculator.Tier.T1)
                || sol.failedTiers().contains(RouteVerdictCalculator.Tier.T2)
                || !comparison.solT1Failures().isEmpty()) return false;
        if (luna.failedTiers().contains(RouteVerdictCalculator.Tier.T3)
                && comparison.solVersusLunaD() > -parameters.solT3Improvement) return false;
        if (luna.failedTiers().contains(RouteVerdictCalculator.Tier.T2)) {
            if (parameters.frbEnabled) {
                Map<String, Integer> lunaCounts = comparison.lunaCriticalLowByCaseId();
                Map<String, Integer> solCounts = comparison.solCriticalHighByCaseId();
                if (lunaCounts == null || solCounts == null || lunaCounts.isEmpty() || solCounts.isEmpty()
                        || !lunaCounts.keySet().equals(solCounts.keySet())) return false;
                try {
                    List<String> ids = lunaCounts.keySet().stream().sorted().toList();
                    if (!SolT2Improvement.evaluate(ids.stream().map(solCounts::get).toList(),
                            ids.stream().map(lunaCounts::get).toList()).improves()) return false;
                } catch (InvalidInput invalid) {
                    return false;
                }
            } else {
                for (Map.Entry<String, RouteVerdictCalculator.WorkflowReport> entry : luna.workflows().entrySet()) {
                    if (!entry.getValue().failedTiers().contains(RouteVerdictCalculator.Tier.T2)) continue;
                    Map<String, Boolean> lunaCases = comparison.lunaReserveCases().get(entry.getKey());
                    Map<String, Boolean> solCases = comparison.solReserveCases().get(entry.getKey());
                    if (lunaCases == null || solCases == null
                            || lunaCases.size() != parameters.reserveSize
                            || !lunaCases.keySet().equals(solCases.keySet())) return false;
                    long lunaCount = lunaCases.values().stream().filter(Boolean.TRUE::equals).count();
                    long solCount = solCases.values().stream().filter(Boolean.TRUE::equals).count();
                    if (lunaCount - solCount < parameters.solT2Improvement) return false;
                }
            }
        }
        if (luna.failedTiers().contains(RouteVerdictCalculator.Tier.T1)
                && (!comparison.solT1Failures().isEmpty()
                || comparison.lunaT1Failures().isEmpty())) return false;
        return true;
    }
}
