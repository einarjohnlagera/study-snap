package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCompatibilityTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;

    @Test void oldNonRejectionReceiptBytesStayPinned() throws Exception {
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow("case", "workflow", "hash",
                CaseArmOutcome.State.COMPLETE, true, true, null, BigDecimal.ONE, BigDecimal.ONE,
                List.of(new Stage1Receipt.Signal("SUCCESS", 1, 2, 200, "group")), List.of("digest"), null);
        Stage1Receipt stage = new Stage1Receipt("head", "freeze", "LUNA", "model",
                StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 1, false, "set",
                List.of(row), Map.of(), null);
        RecheckReceipt.Repeat first = new RecheckReceipt.Repeat(1, "PARSE", RouteVerdictCalculator.Evidence.SCORED,
                null, null, "file");
        RecheckReceipt.Repeat second = new RecheckReceipt.Repeat(2, "PARSE", RouteVerdictCalculator.Evidence.SCORED,
                null, null, "file", false);
        RecheckReceipt recheck = new RecheckReceipt("head", "freeze", "LUNA", "model", Set.of("PARSE"),
                List.of(new RecheckReceipt.Case("case", "PARSE", List.of(first, second))), "set");
        // Both literals were printed by the pre-edit serializer run on 2026-11-18.
        String stageBytes = """
                {"head":"head","freezeSha256":"freeze","arm":"LUNA","model":"model","stage":"STAGE_1","startedAt":0.0,"endedAt":0.0,"ledgerRecordCount":1,"unreconciled":false,"caseSetSha256":"set","cases":[{"id":"case","workflow":"workflow","manifestRowSha256":"hash","state":"COMPLETE","firstAttemptClean":true,"finalSuccess":true,"failureClass":null,"conservativeUsd":1,"billedUsd":1,"llmCalls":[{"outcome":"SUCCESS","attempt":1,"latencyMs":2,"httpStatus":200,"groupId":"group"}],"attempt1BodyHashes":["digest"],"deliveredOutputSha256":null}],"triggers":{},"deliveredOutputsFileSha256":null}
                """.strip();
        String recheckBytes = """
                {"head":"head","freezeSha256":"freeze","arm":"LUNA","model":"model","recheckEligibleFailureClasses":["PARSE"],"cases":[{"caseId":"case","originalFailureClass":"PARSE","repeats":[{"recheckIndex":1,"finalFailureClass":"PARSE","evidence":"SCORED","protocolDeviation":null,"deliveredOutputSha256":null,"deliveredOutputsFileSha256":"file","providerRejected":false},{"recheckIndex":2,"finalFailureClass":"PARSE","evidence":"SCORED","protocolDeviation":null,"deliveredOutputSha256":null,"deliveredOutputsFileSha256":"file","providerRejected":false}]}],"caseSetSha256":"set"}
                """.strip();
        assertThat(JSON.writeValueAsString(stage)).isEqualTo(stageBytes);
        assertThat(JSON.writeValueAsString(recheck)).isEqualTo(recheckBytes);
        Path oldStage = temp.resolve("old-stage.json");
        Path oldRecheck = temp.resolve("old-recheck.json");
        Files.writeString(oldStage, stageBytes);
        Files.writeString(oldRecheck, recheckBytes);
        assertThat(Stage1Receipt.read(oldStage).cases().getFirst().llmCalls().getFirst().errorCode()).isNull();
        assertThat(RecheckReceipt.read(oldRecheck).cases().getFirst().repeats().getFirst().rejectedCalls())
                .isNull();
    }
}
