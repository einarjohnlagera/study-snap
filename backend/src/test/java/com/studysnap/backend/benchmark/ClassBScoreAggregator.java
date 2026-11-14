package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Human-grounded class-B aggregation for preregistration sections 7 and 8, Revision 2.2. */
final class ClassBScoreAggregator {
    enum Arm { BASELINE, CANDIDATE }
    static final class InvalidArmKey extends RuntimeException {
        InvalidArmKey(String message) { super(message); }
    }
    record Key(String itemId, ClassBCaseManifest.Workflow workflow, String caseId, Arm arm, boolean reserve) {}
    record Agreement(int bothPass, int bothFail, int disagreement, double humanOverrideRate) {}
    record Result(List<RouteVerdictCalculator.CaseRecord> primary,
                  Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve,
                  Map<String, Agreement> agreementReport, Set<String> inconclusiveItems) {}
    private ClassBScoreAggregator() {}

    static Result aggregate(ClassBScoreFiles.Validated scores, List<Key> armKey,
                            Map<String, RouteVerdictCalculator.ArmResult> structural) {
        Map<String, ClassBScoreFiles.BlindItem> blinded = new HashMap<>();
        for (ClassBScoreFiles.BlindItem item : scores.manifest()) blinded.put(item.itemId(), item);
        Map<String, Key> byItem = new HashMap<>();
        Map<String, Map<Arm, Key>> byCase = new LinkedHashMap<>();
        for (Key key : armKey) {
            ClassBScoreFiles.BlindItem item = blinded.get(key.itemId());
            if (item == null || item.workflow() != key.workflow() || item.reserve() != key.reserve()
                    || key.caseId() == null || key.caseId().isBlank() || key.arm() == null
                    || byItem.putIfAbsent(key.itemId(), key) != null)
                throw new InvalidArmKey("Invalid or duplicate arm key");
            String identity = key.workflow() + ClassBScoreFiles.KEY_SEPARATOR + key.caseId()
                    + ClassBScoreFiles.KEY_SEPARATOR + key.reserve();
            if (byCase.computeIfAbsent(identity, unused -> new LinkedHashMap<>())
                    .putIfAbsent(key.arm(), key) != null)
                throw new InvalidArmKey("Duplicate arm for case");
        }
        if (!byItem.keySet().equals(blinded.keySet())) throw new InvalidArmKey("Arm key does not cover blinded manifest");
        Map<String, ClassBScoreFiles.Score> human = scores.finalHumanScores();
        Map<String, RouteVerdictCalculator.ArmResult> arms = new HashMap<>();
        Set<String> unresolved = new LinkedHashSet<>();
        for (Key key : armKey) {
            RouteVerdictCalculator.ArmResult event = structural.get(key.itemId());
            if (event == null) throw new InvalidArmKey("Missing deterministic structural event");
            ClassBScoreFiles.Score score = human.get(key.itemId());
            if (score == null) {
                unresolved.add(key.itemId());
                arms.put(key.itemId(), new RouteVerdictCalculator.ArmResult(
                        RouteVerdictCalculator.Evidence.UNSCORABLE, event.t3Components(), event.critical(),
                        event.finalFailureClass(), event.rechecks(), event.providerRejected(), event.unusable()));
            } else {
                Set<String> components = new HashSet<>(event.t3Components());
                if (score.qualityDeficit()) components.add("QUALITY");
                arms.put(key.itemId(), new RouteVerdictCalculator.ArmResult(event.evidence(), components,
                        event.critical() || score.critical(), event.finalFailureClass(), event.rechecks(),
                        event.providerRejected(), event.unusable()));
            }
        }
        List<RouteVerdictCalculator.CaseRecord> primary = new ArrayList<>();
        Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve = new LinkedHashMap<>();
        for (Map<Arm, Key> pair : byCase.values()) {
            if (pair.size() != Arm.values().length) throw new InvalidArmKey("Unpaired case in arm key");
            Key baseline = pair.get(Arm.BASELINE), candidate = pair.get(Arm.CANDIDATE);
            if (baseline == null || candidate == null) throw new InvalidArmKey("Missing baseline or candidate");
            RouteVerdictCalculator.ArmResult b = arms.get(baseline.itemId());
            RouteVerdictCalculator.ArmResult c = arms.get(candidate.itemId());
            String workflow = baseline.workflow().name();
            if (baseline.reserve()) {
                reserve.computeIfAbsent(workflow, unused -> new ArrayList<>())
                        .add(new RouteVerdictCalculator.ReserveCase(baseline.caseId(), b.evidence(), c.evidence(),
                                b.critical(), c.critical()));
            } else {
                primary.add(new RouteVerdictCalculator.CaseRecord(baseline.caseId(), workflow,
                        route(workflow), null, baseline.caseId(), candidate.caseId(), b, c));
            }
        }
        return new Result(List.copyOf(primary), immutableLists(reserve), agreement(scores, human), Set.copyOf(unresolved));
    }

    private static String route(String workflow) {
        return switch (workflow) {
            case "STUDY_TIP" -> "INTERACTIVE_QUIZ";
            case "COLLECTION_COMPANION" -> "PAID_INTERACTIVE";
            case "ASK_COMPANION", "INTERVIEW_CRITIQUE" -> "CRITIQUE";
            default -> throw new InvalidArmKey("Unknown class-B route");
        };
    }
    private static Map<String, List<RouteVerdictCalculator.ReserveCase>> immutableLists(
            Map<String, List<RouteVerdictCalculator.ReserveCase>> source) {
        Map<String, List<RouteVerdictCalculator.ReserveCase>> copy = new LinkedHashMap<>();
        source.forEach((name, cases) -> copy.put(name, List.copyOf(cases)));
        return Map.copyOf(copy);
    }
    private static Map<String, Agreement> agreement(ClassBScoreFiles.Validated scores,
                                                    Map<String, ClassBScoreFiles.Score> human) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values())
            counts.put(workflow.name(), new int[3]);
        for (ClassBScoreFiles.Score model : scores.scores()) {
            if (model.scorerKind() != ClassBScoreFiles.ScorerKind.MODEL) continue;
            ClassBScoreFiles.Score owner = human.get(model.itemId());
            if (owner == null) continue;
            int[] count = counts.computeIfAbsent(model.workflow().name(), unused -> new int[3]);
            boolean modelPass = model.dimensions().stream().allMatch(Boolean.TRUE::equals);
            boolean humanPass = owner.dimensions().stream().allMatch(Boolean.TRUE::equals);
            if (model.dimensions().equals(owner.dimensions()) && modelPass && humanPass) count[0]++;
            else if (model.dimensions().equals(owner.dimensions()) && !modelPass && !humanPass) count[1]++;
            else count[2]++;
        }
        Map<String, Agreement> result = new LinkedHashMap<>();
        counts.forEach((workflow, count) -> {
            int total = count[0] + count[1] + count[2];
            result.put(workflow, new Agreement(count[0], count[1], count[2],
                    total == 0 ? 0 : count[2] / (double) total));
        });
        return Map.copyOf(result);
    }
}
