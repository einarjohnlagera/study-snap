package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Private delivered-output evidence for preregistration sections 7 and 8, including T1 rechecks. */
final class DeliveredOutputs {
    record Line(String caseId, String workflow, String arm, String manifestRowSha256,
                int recheckIndex, CaseArmOutcome.State state, String failureClass, JsonNode content) {}
    record Written(Path path, String fileSha256, Map<String, String> lineSha256) {}
    static final class InvalidOutput extends RuntimeException {
        InvalidOutput(String message) { super(message); }
        InvalidOutput(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private DeliveredOutputs() {}

    static Written write(Path path, String arm, int recheckIndex, List<CaseArmOutcome> outcomes,
                         Map<String, String> workflows, Map<String, String> rowHashes,
                         Map<String, Object> content) throws IOException {
        guard(path);
        if (!Files.isDirectory(path.getParent())) throw new InvalidOutput("Output directory missing");
        List<String> lines = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (CaseArmOutcome outcome : outcomes) {
            String id = outcome.caseArmId();
            if (workflows.get(id) == null || rowHashes.get(id) == null || hashes.containsKey(id))
                throw new InvalidOutput("Missing or duplicate manifest row for " + id);
            JsonNode delivered = outcome.state() == CaseArmOutcome.State.COMPLETE
                    ? JSON.valueToTree(content.get(id)) : null;
            if (outcome.state() == CaseArmOutcome.State.COMPLETE && (delivered == null || delivered.isNull()))
                throw new InvalidOutput("Missing delivered content for " + id);
            String line = JSON.writeValueAsString(new Line(id, workflows.get(id), arm, rowHashes.get(id),
                    recheckIndex, outcome.state(), outcome.failureClass(), delivered));
            lines.add(line);
            hashes.put(id, FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        }
        byte[] bytes = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        return new Written(path, FreezeRecord.sha256(bytes), Map.copyOf(hashes));
    }

    static List<Line> read(Path path, Stage1Receipt receipt) {
        try {
            guard(path);
            if (!FreezeRecord.sha256(path).equals(receipt.deliveredOutputsFileSha256()))
                throw new InvalidOutput("Delivered-output file hash differs from receipt");
            List<String> raw = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (raw.size() != receipt.cases().size()) throw new InvalidOutput("Delivered-output count differs");
            List<Line> lines = new ArrayList<>();
            for (int index = 0; index < raw.size(); index++) {
                String text = raw.get(index);
                Stage1Receipt.CaseRow caseRow = receipt.cases().get(index);
                if (!FreezeRecord.sha256(text.getBytes(StandardCharsets.UTF_8))
                        .equals(caseRow.deliveredOutputSha256()))
                    throw new InvalidOutput("Delivered-output line hash differs for " + caseRow.id());
                Line line = JSON.readValue(text, Line.class);
                if (!caseRow.id().equals(line.caseId()) || !caseRow.workflow().equals(line.workflow())
                        || !caseRow.manifestRowSha256().equals(line.manifestRowSha256())
                        || !receipt.arm().equals(line.arm()) || line.recheckIndex() != 0)
                    throw new InvalidOutput("Delivered-output metadata differs for " + caseRow.id());
                lines.add(line);
            }
            return List.copyOf(lines);
        } catch (IOException exception) {
            throw new InvalidOutput("Cannot read delivered outputs", exception);
        }
    }

    static Line readRecheck(Path path, RecheckReceipt receipt, RecheckReceipt.Case caseRow,
                            RecheckReceipt.Repeat repeat) {
        try {
            guard(path);
            if (!FreezeRecord.sha256(path).equals(repeat.deliveredOutputsFileSha256()))
                throw new InvalidOutput("Recheck output file hash differs for " + caseRow.caseId());
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.size() != 1 || !FreezeRecord.sha256(lines.getFirst().getBytes(StandardCharsets.UTF_8))
                    .equals(repeat.deliveredOutputSha256()))
                throw new InvalidOutput("Recheck output line hash differs for " + caseRow.caseId());
            Line line = JSON.readValue(lines.getFirst(), Line.class);
            if (!line.caseId().equals(caseRow.caseId()) || !line.arm().equals(receipt.arm())
                    || line.recheckIndex() != repeat.recheckIndex())
                throw new InvalidOutput("Recheck output identity differs for " + caseRow.caseId());
            return line;
        } catch (IOException exception) {
            throw new InvalidOutput("Cannot read recheck output", exception);
        }
    }

    static void guard(Path path) {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        ClassBBlindPacketExport.guarded(path, repo);
    }
}
