package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** Cost is a separate operational review over frozen primary cases, never a T3 component. */
final class OperationalCostReview {
    enum Status { OK, TRIGGERED, UNDETERMINED }
    record Result(BigDecimal candidateMeanUsd, BigDecimal baselineMeanUsd,
                  int candidateDelivered, int baselineDelivered, BigDecimal ratio,
                  Status status, BigDecimal triggerMultiplier) {}
    private record Totals(BigDecimal dollars, int delivered) {
        Totals add(BigDecimal value) { return new Totals(dollars.add(value), delivered + 1); }
        BigDecimal mean() { return delivered == 0 ? null : dollars.divide(BigDecimal.valueOf(delivered), MathContext.DECIMAL128); }
    }
    private static final String TRIGGERED_PREFIX = "COST_REVIEW_TRIGGERED:";
    private static final String UNDETERMINED_PREFIX = "COST_REVIEW_UNDETERMINED:";
    private static final String PAID_BASELINE = "BASELINE_41";

    private OperationalCostReview() {}

    static Map<String, Result> review(FrozenGateParameters parameters, FreezeRecord freeze,
            EvidenceMapping mapping, Map<String, String> primaryWorkflows,
            Map<String, Stage1Receipt.CaseRow> mini, Map<String, Stage1Receipt.CaseRow> paid,
            Map<String, Stage1Receipt.CaseRow> candidate) {
        if (parameters == null || freeze == null || mapping == null || primaryWorkflows == null
                || mini == null || paid == null || candidate == null || mapping.costTolerance == null
                || freeze.arms() == null || freeze.arms().get(PAID_BASELINE) == null
                || parameters.workflows == null)
            throw new InvalidCostReview("Missing operational cost review input");
        Map<String, Totals> baselineTotals = new LinkedHashMap<>();
        Map<String, Totals> candidateTotals = new LinkedHashMap<>();
        for (String workflow : parameters.workflows.keySet()) {
            baselineTotals.put(workflow, new Totals(BigDecimal.ZERO, 0));
            candidateTotals.put(workflow, new Totals(BigDecimal.ZERO, 0));
        }
        for (var primary : primaryWorkflows.entrySet()) {
            String workflow = primary.getValue();
            if (!baselineTotals.containsKey(workflow)) throw new InvalidCostReview("Unknown primary workflow: " + workflow);
            boolean usePaid = usesPaidBaseline(freeze, workflow);
            Map<String, Stage1Receipt.CaseRow> baseline = usePaid ? paid : mini;
            baselineTotals.put(workflow, add(baselineTotals.get(workflow), baseline.get(primary.getKey()),
                    primary.getKey(), workflow, mapping));
            candidateTotals.put(workflow, add(candidateTotals.get(workflow), candidate.get(primary.getKey()),
                    primary.getKey(), workflow, mapping));
        }
        Map<String, Result> results = new TreeMap<>();
        for (String workflow : parameters.workflows.keySet()) {
            Totals b = baselineTotals.get(workflow);
            Totals c = candidateTotals.get(workflow);
            BigDecimal ratio = b.delivered() == 0 || c.delivered() == 0 || b.dollars().signum() == 0
                    ? null : c.dollars().multiply(BigDecimal.valueOf(b.delivered()))
                        .divide(b.dollars().multiply(BigDecimal.valueOf(c.delivered())), MathContext.DECIMAL128);
            Status status = ratio == null ? Status.UNDETERMINED
                    : c.dollars().multiply(BigDecimal.valueOf(b.delivered())).compareTo(
                            b.dollars().multiply(BigDecimal.valueOf(c.delivered()))
                                    .multiply(mapping.costTolerance)) > 0 ? Status.TRIGGERED : Status.OK;
            results.put(workflow, new Result(c.mean(), b.mean(), c.delivered(), b.delivered(),
                    ratio, status, mapping.costTolerance));
        }
        return java.util.Collections.unmodifiableMap(results);
    }

    static boolean usesPaidBaseline(FreezeRecord freeze, String workflow) {
        return freeze.arms().get(PAID_BASELINE).workflows().contains(workflow);
    }

    private static Totals add(Totals totals, Stage1Receipt.CaseRow row, String id,
            String workflow, EvidenceMapping mapping) {
        if (row == null) return totals;
        if (row.finalSuccess() && (row.billedUsd() == null || row.conservativeUsd() == null)
                || !id.equals(row.id()) || !workflow.equals(row.workflow()))
            throw new InvalidCostReview("Malformed primary cost row: " + id);
        if (!row.finalSuccess()) return totals;
        BigDecimal cost = mapping.cost(row);
        if (cost == null || cost.signum() < 0) throw new InvalidCostReview("Invalid primary cost: " + id);
        return totals.add(cost);
    }

    static Map<String, SortedSet<String>> flagsByRoute(Map<String, Result> review,
            FrozenGateParameters parameters) {
        Map<String, SortedSet<String>> flags = new TreeMap<>();
        review.forEach((workflow, result) -> {
            String flag = switch (result.status()) {
                case OK -> null;
                case TRIGGERED -> TRIGGERED_PREFIX + workflow;
                case UNDETERMINED -> UNDETERMINED_PREFIX + workflow;
            };
            if (flag != null) flags.computeIfAbsent(parameters.workflows.get(workflow).route(),
                    ignored -> new TreeSet<>()).add(flag);
        });
        return flags;
    }
}
