package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Content-free T1 repeat evidence for preregistration sections 1 and 8. */
record RecheckReceipt(String head, String freezeSha256, String arm, String model,
                      Set<String> recheckEligibleFailureClasses, List<Case> cases) {
    RecheckReceipt {
        if (cases == null || cases.stream().anyMatch(item -> item.repeats() == null
                || item.repeats().size() != 2 || item.repeats().get(0).recheckIndex() != 1
                || item.repeats().get(1).recheckIndex() != 2))
            throw new IllegalArgumentException("Recheck receipt requires ordered indices 1 and 2");
    }
    record Case(String caseId, String originalFailureClass, List<Repeat> repeats) {}
    record Repeat(int recheckIndex, String finalFailureClass,
                  RouteVerdictCalculator.Evidence evidence, String protocolDeviation,
                  String deliveredOutputSha256, String deliveredOutputsFileSha256) {}
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
