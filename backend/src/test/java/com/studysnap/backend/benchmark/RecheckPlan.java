package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frozen-input T1 eligibility for preregistration sections 1 and 8. */
final class RecheckPlan {
    record Eligible(String caseId, String workflow, String originalFailureClass,
                    String manifestRowSha256, List<String> originalAttempt1BodyHashes) {}
    record Refused(String caseId, String reason) {}
    record Decision(List<Eligible> eligible, List<Refused> refused,
                    Set<String> freezeEligibleFailureClasses) {}
    private static final Set<String> NEVER = Set.of("HTTP_ERROR", "TIMEOUT", "INPUT_DEFECT", "ARM_MISMATCH");
    private static final String ASK_UNAVAILABLE = "ASK_COMPANION_UNAVAILABLE";
    private RecheckPlan() {}

    static Decision build(Stage1Receipt receipt, Path outputsPath, FreezeRecord freeze,
                          Map<String, String> currentManifestHashes) {
        List<DeliveredOutputs.Line> outputs = DeliveredOutputs.read(outputsPath, receipt);
        List<Eligible> eligible = new ArrayList<>();
        List<Refused> refused = new ArrayList<>();
        for (int index = 0; index < receipt.cases().size(); index++) {
            Stage1Receipt.CaseRow row = receipt.cases().get(index);
            DeliveredOutputs.Line output = outputs.get(index);
            String reason = refusal(row, output, freeze.recheckEligibleFailureClasses(),
                    currentManifestHashes.get(row.id()));
            if (reason == null) eligible.add(new Eligible(row.id(), row.workflow(), row.failureClass(),
                    row.manifestRowSha256(), row.attempt1BodyHashes()));
            else refused.add(new Refused(row.id(), reason));
        }
        return new Decision(List.copyOf(eligible), List.copyOf(refused),
                Set.copyOf(freeze.recheckEligibleFailureClasses()));
    }

    private static String refusal(Stage1Receipt.CaseRow row, DeliveredOutputs.Line output,
                                  Set<String> frozen, String currentHash) {
        if (currentHash == null || !currentHash.equals(row.manifestRowSha256()))
            return "Manifest-row hash differs from frozen input";
        if (output.state() != row.state() || !java.util.Objects.equals(output.failureClass(), row.failureClass()))
            return "Delivered-output state differs from receipt";
        if (row.state() == CaseArmOutcome.State.INCONCLUSIVE) return "Original evidence is INCONCLUSIVE";
        if (row.state() != CaseArmOutcome.State.FAILED) return "Original case did not fail";
        String failure = row.failureClass();
        if (failure == null) return "Original failure class is missing";
        if (NEVER.contains(failure) || failure.startsWith("INCONCLUSIVE"))
            return "Failure class is never a T1 recheck trigger: " + failure;
        if (!frozen.contains(failure)) return "Failure class absent from freeze record: " + failure;
        if (ASK_UNAVAILABLE.equals(failure) && row.llmCalls().stream()
                .noneMatch(call -> call.httpStatus() >= 200 && call.httpStatus() < 300))
            return "Ask Companion has no metered HTTP 2xx call";
        if (row.attempt1BodyHashes().isEmpty()) return "Original attempt-1 input hashes are missing";
        return null;
    }
}
