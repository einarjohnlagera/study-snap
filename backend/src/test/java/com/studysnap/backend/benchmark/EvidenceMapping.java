package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Preregistration sections 4, 7 and 8: owner-frozen score, structure and cost interpretation. */
final class EvidenceMapping {
    static final class InvalidMapping extends RuntimeException {
        InvalidMapping(String message) { super(message); }
        InvalidMapping(String message, Throwable cause) { super(message, cause); }
    }
    private static final String CLASS_A_CRITICAL_TYPES = "classACriticalTypes";
    private static final String CLASS_A_FACTUAL_PASS = "classAFactualPass";
    private static final String CLASS_A_GROUNDING_PASS = "classAGroundingPass";
    private static final String CLASS_A_CLARITY_MINIMUM = "classAClarityMinimum";
    private static final String CLASS_A_FAITHFULNESS_MINIMUM = "classAFaithfulnessMinimum";
    private static final String CLASS_B_QUALITY_DIMENSIONS = "classBQualityDimensions";
    private static final String CLASS_B_CRITICAL_DIMENSION = "classBCriticalDimension";
    private static final String COST_TOLERANCE = "costTolerance";
    private static final String COST_FIGURE = "costFigure";
    private static final String STRUCTURAL_COMPONENTS = "structuralComponents";
    private static final String BILLED_USD = "billedUsd";
    private static final String TASK_FULFILLED = "taskFulfilled";
    private static final String GROUNDED = "grounded";
    private static final String ACTIONABLE = "actionable";
    private static final String NO_CRITICAL_MISLEADING = "noCriticalMisleading";
    private static final String CRITICAL = "critical";
    private static final String INVALID_STRUCTURAL_COMPONENTS = "Invalid structuralComponents";
    private static final String INVALID_PREFIX = "Invalid ";
    private static final Set<String> FIELDS = Set.of(CLASS_A_CRITICAL_TYPES, CLASS_A_FACTUAL_PASS,
            CLASS_A_GROUNDING_PASS, CLASS_A_CLARITY_MINIMUM, CLASS_A_FAITHFULNESS_MINIMUM,
            CLASS_B_QUALITY_DIMENSIONS, CLASS_B_CRITICAL_DIMENSION, COST_TOLERANCE, COST_FIGURE,
            STRUCTURAL_COMPONENTS);
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    final Set<String> classACriticalTypes;
    final String classAFactualPass;
    final String classAGroundingPass;
    final int classAClarityMinimum;
    final int classAFaithfulnessMinimum;
    final Set<String> classBQualityDimensions;
    final String classBCriticalDimension;
    final BigDecimal costTolerance;
    final String costFigure;
    final Map<String, String> structuralComponents;
    final String sha256;

    private EvidenceMapping(JsonNode root, String hash) {
        Set<String> names = new HashSet<>();
        root.fieldNames().forEachRemaining(names::add);
        if (!names.equals(FIELDS)) throw new InvalidMapping("Missing or unknown mapping fields");
        classACriticalTypes = strings(root, CLASS_A_CRITICAL_TYPES);
        classAFactualPass = string(root, CLASS_A_FACTUAL_PASS);
        classAGroundingPass = string(root, CLASS_A_GROUNDING_PASS);
        classAClarityMinimum = integer(root, CLASS_A_CLARITY_MINIMUM);
        classAFaithfulnessMinimum = integer(root, CLASS_A_FAITHFULNESS_MINIMUM);
        classBQualityDimensions = strings(root, CLASS_B_QUALITY_DIMENSIONS);
        classBCriticalDimension = string(root, CLASS_B_CRITICAL_DIMENSION);
        structuralComponents = components(root.get(STRUCTURAL_COMPONENTS));
        costFigure = string(root, COST_FIGURE);
        JsonNode tolerance = root.get(COST_TOLERANCE);
        if (tolerance == null || !tolerance.isNumber() || tolerance.decimalValue().compareTo(BigDecimal.ONE) < 0)
            throw new InvalidMapping("Invalid costTolerance");
        costTolerance = tolerance.decimalValue();
        if (!Set.of("conservativeUsd", BILLED_USD).contains(costFigure)
                || !QualityScoreFiles.TYPES.containsAll(classACriticalTypes)
                || classAClarityMinimum > 5 || classAFaithfulnessMinimum > 5
                || !Set.of(TASK_FULFILLED, GROUNDED, ACTIONABLE, NO_CRITICAL_MISLEADING)
                    .containsAll(classBQualityDimensions)
                || !Set.of(TASK_FULFILLED, GROUNDED, ACTIONABLE, NO_CRITICAL_MISLEADING)
                    .contains(classBCriticalDimension))
            throw new InvalidMapping("Invalid score or cost interpretation");
        sha256 = hash;
    }

    static EvidenceMapping load(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String hash = FreezeRecord.sha256(bytes);
            String declared = Files.readString(file.resolveSibling(file.getFileName() + ".sha256")).trim();
            if (!hash.equals(declared)) throw new InvalidMapping("Mapping SHA-256 differs: " + file);
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject()) throw new InvalidMapping("Mapping root must be an object: " + file);
            return new EvidenceMapping(root, hash);
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof InvalidMapping invalid) throw invalid;
            throw new InvalidMapping("Cannot load evidence mapping: " + file, exception);
        }
    }

    boolean criticalA(JsonNode row) {
        return row.path(CRITICAL).asBoolean() && classACriticalTypes.contains(row.path("criticalType").asText());
    }
    boolean qualityA(JsonNode row) {
        return !criticalA(row) && (row.path(CRITICAL).asBoolean()
                || !classAFactualPass.equals(row.path("factuallyCorrect").asText())
                || !classAGroundingPass.equals(row.path("grounding").asText())
                || row.path("clarity").asInt() < classAClarityMinimum
                || row.path("faithfulness").asInt() < classAFaithfulnessMinimum
                || row.path("seriousGiveaway").asBoolean());
    }
    BigDecimal cost(Stage1Receipt.CaseRow row) {
        return BILLED_USD.equals(costFigure) ? row.billedUsd() : row.conservativeUsd();
    }
    boolean criticalB(ClassBScoreFiles.Score score) { return !dimensionB(score, classBCriticalDimension); }
    boolean qualityB(ClassBScoreFiles.Score score) {
        return classBQualityDimensions.stream().anyMatch(name -> !dimensionB(score, name));
    }
    String component(String kind) {
        String value = structuralComponents.get(kind);
        if (value == null) throw new InvalidMapping("Missing structural component: " + kind);
        return value;
    }
    private static Map<String, String> components(JsonNode node) {
        Set<String> required = Set.of("recoveredParse", "recoveredFallback", "timeout",
                "isolatedFinalFailure", "cost", "quality");
        if (node == null || !node.isObject()) throw new InvalidMapping(INVALID_STRUCTURAL_COMPONENTS);
        Map<String, String> result = new LinkedHashMap<>();
        node.properties().forEach(item -> {
            if (!item.getValue().isTextual() || item.getValue().asText().isBlank())
                throw new InvalidMapping("Invalid structural component: " + item.getKey());
            result.put(item.getKey(), item.getValue().asText());
        });
        if (!result.keySet().equals(required) || Set.copyOf(result.values()).size() != result.size())
            throw new InvalidMapping(INVALID_STRUCTURAL_COMPONENTS);
        return Map.copyOf(result);
    }
    private static boolean dimensionB(ClassBScoreFiles.Score score, String name) {
        return switch (name) {
            case TASK_FULFILLED -> score.taskFulfilled();
            case GROUNDED -> score.grounded();
            case ACTIONABLE -> score.actionable();
            case NO_CRITICAL_MISLEADING -> score.noCriticalMisleading();
            default -> throw new InvalidMapping("Unknown class-B dimension: " + name);
        };
    }
    private static String string(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) throw new InvalidMapping(INVALID_PREFIX + field);
        return node.asText();
    }
    private static int integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isInt() || node.asInt() < 1) throw new InvalidMapping(INVALID_PREFIX + field);
        return node.asInt();
    }
    private static Set<String> strings(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) throw new InvalidMapping(INVALID_PREFIX + field);
        Set<String> values = new HashSet<>();
        node.forEach(item -> { if (!item.isTextual() || item.asText().isBlank() || !values.add(item.asText()))
            throw new InvalidMapping(INVALID_PREFIX + field); });
        return Set.copyOf(values);
    }
}
