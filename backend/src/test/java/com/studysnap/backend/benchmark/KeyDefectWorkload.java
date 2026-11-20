package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure estimate of the owner review required by the fail-closed key-defect subjects. Every
 * class-A case with a human-confirmed critical in either arm creates one review, including
 * nonstructural types. An excluded short-delivery pair has no facts and is not counted; its
 * exclusion is handled by the route. The estimate is only as good as its owner-supplied
 * parameters, and approves no figure.
 * Arithmetic is cases times minutes per case divided by 60, rounded UP to four review-hour decimals;
 * total hours add other planned hours; each day count is the ceiling of hours divided by productive
 * hours per day. The limit is exceeded only when total hours are strictly greater than H_max.
 */
final class KeyDefectWorkload {
    private static final String SEPARATOR = "\u0000";
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    private static final int HOUR_SCALE = 4;

    private KeyDefectWorkload() {}

    record Parameters(BigDecimal minutesPerCaseReview, BigDecimal otherPlannedHours,
                      BigDecimal hMaxHours, BigDecimal productiveHoursPerDay) {
        Parameters {
            if (minutesPerCaseReview == null || otherPlannedHours == null || hMaxHours == null
                    || productiveHoursPerDay == null || minutesPerCaseReview.signum() <= 0
                    || otherPlannedHours.signum() < 0 || hMaxHours.signum() <= 0
                    || productiveHoursPerDay.signum() <= 0)
                throw new InvalidEvidence("Invalid owner-supplied key-defect workload parameters");
        }
    }

    enum ArmPattern { CANDIDATE_ONLY, BASELINE_ONLY, SHARED }

    record Estimate(int cases, Map<ArmPattern, Integer> casesByPattern, BigDecimal reviewHours,
                    BigDecimal totalHours, int reviewDays, int totalDays, boolean exceedsLimit,
                    List<String> report) {
        Estimate {
            casesByPattern = Map.copyOf(casesByPattern);
            report = List.copyOf(report);
        }
    }

    static Estimate estimate(FrbEvidenceBundle bundle, Map<String, String> workflowByCaseId,
            Set<String> classAWorkflows, Set<String> classBWorkflows, Set<String> candidateArms,
            Set<String> baselineArms, Parameters parameters) {
        if (bundle == null || workflowByCaseId == null || classAWorkflows == null
                || classBWorkflows == null || candidateArms == null || baselineArms == null
                || parameters == null)
            throw new InvalidEvidence("Complete key-defect workload inputs required");
        if (candidateArms.stream().anyMatch(java.util.Objects::isNull)
                || baselineArms.stream().anyMatch(java.util.Objects::isNull)
                || candidateArms.stream().anyMatch(baselineArms::contains))
            throw new InvalidEvidence("Candidate and baseline arms must be disjoint and non-null");

        Set<FindingsLedger.ReviewSubject> subjects = KeyDefectSubjects.derive(bundle,
                workflowByCaseId, classAWorkflows, classBWorkflows);
        Set<String> subjectIds = new HashSet<>();
        subjects.forEach(subject -> subjectIds.add(subject.subjectId()));
        Map<String, Integer> criticalSides = new HashMap<>();
        for (var entry : bundle.evidence().byCaseArm().entrySet()) {
            String identity = entry.getKey();
            int separator = identity.indexOf(SEPARATOR);
            String caseId = identity.substring(0, separator);
            if (!subjectIds.contains(caseId)) continue;
            String arm = identity.substring(separator + 1);
            boolean candidate = candidateArms.contains(arm);
            boolean baseline = baselineArms.contains(arm);
            if (!candidate && !baseline)
                throw new InvalidEvidence("Unknown arm for key-defect subject: " + identity);
            if (entry.getValue().criticalQuestions() > 0)
                criticalSides.merge(caseId, candidate ? 1 : 2, (left, right) -> left | right);
        }

        EnumMap<ArmPattern, Integer> counts = new EnumMap<>(ArmPattern.class);
        for (ArmPattern pattern : ArmPattern.values()) counts.put(pattern, 0);
        for (String caseId : subjectIds) {
            int sides = criticalSides.getOrDefault(caseId, 0);
            ArmPattern pattern = switch (sides) {
                case 1 -> ArmPattern.CANDIDATE_ONLY;
                case 2 -> ArmPattern.BASELINE_ONLY;
                case 3 -> ArmPattern.SHARED;
                default -> throw new InvalidEvidence("Subject has no classified critical arm: " + caseId);
            };
            counts.merge(pattern, 1, Integer::sum);
        }

        BigDecimal reviewHours = parameters.minutesPerCaseReview()
                .multiply(BigDecimal.valueOf(subjects.size()))
                .divide(MINUTES_PER_HOUR, HOUR_SCALE, RoundingMode.UP);
        BigDecimal totalHours = parameters.otherPlannedHours().add(reviewHours);
        int reviewDays = days(reviewHours, parameters.productiveHoursPerDay());
        int totalDays = days(totalHours, parameters.productiveHoursPerDay());
        boolean exceedsLimit = totalHours.compareTo(parameters.hMaxHours()) > 0;
        List<String> report = new ArrayList<>(List.of(
                "key-defect review cases: " + subjects.size() + " (candidate-only "
                        + counts.get(ArmPattern.CANDIDATE_ONLY) + ", baseline-only "
                        + counts.get(ArmPattern.BASELINE_ONLY) + ", shared "
                        + counts.get(ArmPattern.SHARED) + ")",
                "review hours " + reviewHours + " (" + parameters.minutesPerCaseReview()
                        + " minutes per case), other planned hours " + parameters.otherPlannedHours()
                        + ", total " + totalHours + " of limit " + parameters.hMaxHours(),
                "days at " + parameters.productiveHoursPerDay() + " productive hours per day: review "
                        + reviewDays + ", total " + totalDays,
                exceedsLimit ? "WORKLOAD EXCEEDS LIMIT: return to owner" : "workload within limit",
                "input-caused findings are not broken out because root cause is not carried in the verified facts; "
                        + "the owner's per-case review sees it in the human scoring rows",
                "owner-supplied parameters determine this estimate; no figure is approved here",
                "excluded short-delivery pairs have no facts and are not counted; their exclusion returns the route to the owner"));
        report.sort(String::compareTo);
        return new Estimate(subjects.size(), counts, reviewHours, totalHours, reviewDays,
                totalDays, exceedsLimit, report);
    }

    private static int days(BigDecimal hours, BigDecimal productiveHoursPerDay) {
        try {
            return hours.divide(productiveHoursPerDay, 0, RoundingMode.UP).intValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidEvidence("Key-defect review day count exceeds supported range");
        }
    }
}
