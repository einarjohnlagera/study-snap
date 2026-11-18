package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

/** Content-free T1 repeat evidence for preregistration sections 1 and 8. */
record RecheckReceipt(String head, String freezeSha256, String arm, String model,
                      Set<String> recheckEligibleFailureClasses, List<Case> cases,
                      String caseSetSha256) {
    RecheckReceipt {
        if (cases == null || cases.stream().anyMatch(item -> item.repeats() == null
                || item.repeats().size() != 2 || item.repeats().get(0).recheckIndex() != 1
                || item.repeats().get(1).recheckIndex() != 2))
            throw new IllegalArgumentException("Recheck receipt requires ordered indices 1 and 2");
    }
    record Case(String caseId, String originalFailureClass, List<Repeat> repeats) {}
    record RejectedCall(int attempt, Integer status, String errorCode, String errorType,
                        String modelRemovedDigest) {
        static final Comparator<RejectedCall> ORDER = Comparator
                .comparing(RejectedCall::modelRemovedDigest, Comparator.nullsFirst(String::compareTo))
                .thenComparing(RejectedCall::status, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(RejectedCall::errorCode, Comparator.nullsFirst(String::compareTo))
                .thenComparing(RejectedCall::errorType, Comparator.nullsFirst(String::compareTo));
    }
    record Repeat(int recheckIndex, String finalFailureClass,
                  RouteVerdictCalculator.Evidence evidence, String protocolDeviation,
                  String deliveredOutputSha256, String deliveredOutputsFileSha256,
                  boolean providerRejected,
                  @JsonInclude(JsonInclude.Include.NON_NULL) List<RejectedCall> rejectedCalls) {
        Repeat(int recheckIndex, String finalFailureClass,
               RouteVerdictCalculator.Evidence evidence, String protocolDeviation,
               String deliveredOutputSha256, String deliveredOutputsFileSha256,
               boolean providerRejected) {
            this(recheckIndex, finalFailureClass, evidence, protocolDeviation,
                    deliveredOutputSha256, deliveredOutputsFileSha256, providerRejected, null);
        }
        Repeat(int recheckIndex, String finalFailureClass,
               RouteVerdictCalculator.Evidence evidence, String protocolDeviation,
               String deliveredOutputSha256, String deliveredOutputsFileSha256) {
            this(recheckIndex, finalFailureClass, evidence, protocolDeviation,
                    deliveredOutputSha256, deliveredOutputsFileSha256, false, null);
        }
    }
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    void write(Path path) throws IOException {
        DeliveredOutputs.guard(path);
        if (!Files.isDirectory(path.getParent()))
            throw new DeliveredOutputs.InvalidOutput("Recheck receipt directory missing");
        JSON.writeValue(path.toFile(), this);
    }

    static RecheckReceipt read(Path path) {
        try {
            DeliveredOutputs.guard(path);
            return JSON.readValue(path.toFile(), RecheckReceipt.class);
        } catch (IOException exception) {
            throw new DeliveredOutputs.InvalidOutput("Cannot read recheck receipt", exception);
        }
    }
}
