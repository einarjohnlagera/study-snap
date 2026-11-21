package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Owner-approved stage ceilings, pinned to the exact external file bytes. */
record ApprovedCaps(Map<StageCapLedger.Stage, BigDecimal> caps, String sourceSha256) {
    private static final String SCHEMA = "schema";
    private static final String CAPS = "caps";
    private static final int VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    ApprovedCaps {
        if (caps == null || caps.isEmpty() || sourceSha256 == null
                || !sourceSha256.matches("[0-9a-f]{64}"))
            throw new InvalidCostEvidence("Invalid approved caps");
        EnumMap<StageCapLedger.Stage, BigDecimal> checked = new EnumMap<>(StageCapLedger.Stage.class);
        caps.forEach((stage, value) -> {
            if (stage == null || value == null || value.signum() <= 0)
                throw new InvalidCostEvidence("Approved stage caps must be positive");
            checked.put(stage, value);
        });
        caps = Map.copyOf(checked);
    }

    static ApprovedCaps load(Path file) {
        try {
            CostEvidenceProducer.requireExternal(file);
            byte[] bytes = Files.readAllBytes(file);
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || root.size() != 2
                    || !root.has(SCHEMA) || !root.get(SCHEMA).isIntegralNumber()
                    || root.get(SCHEMA).intValue() != VERSION || !root.has(CAPS)
                    || !root.get(CAPS).isObject() || root.get(CAPS).isEmpty())
                throw new InvalidCostEvidence("Malformed approved caps");
            Set<String> fields = new HashSet<>();
            root.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(Set.of(SCHEMA, CAPS)))
                throw new InvalidCostEvidence("Unknown approved caps field");
            EnumMap<StageCapLedger.Stage, BigDecimal> caps = new EnumMap<>(StageCapLedger.Stage.class);
            root.get(CAPS).fields().forEachRemaining(entry -> {
                StageCapLedger.Stage stage = StageCapLedger.Stage.valueOf(entry.getKey());
                if (!entry.getValue().isTextual()) throw new InvalidCostEvidence("Cap must be a decimal string");
                caps.put(stage, new BigDecimal(entry.getValue().asText()));
            });
            return new ApprovedCaps(caps, FreezeRecord.sha256(bytes));
        } catch (Exception exception) {
            if (exception instanceof InvalidCostEvidence invalid) throw invalid;
            throw new InvalidCostEvidence("Cannot load approved caps", exception);
        }
    }
}
