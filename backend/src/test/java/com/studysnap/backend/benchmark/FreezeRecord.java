package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Section 10 detached, exact-byte freeze declaration for the section 5 arm table. */
record FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                    Map<String, String> manifests, Map<String, String> prompts,
                    Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                    LocalDate ownerApprovalDate, Set<String> recheckEligibleFailureClasses,
                    String evidenceMappingSha256, Map<String, String> compatibilitySuites,
                    Map<String, BigDecimal> costForecastUsdPerDeliveredCase,
                    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> placementCommitments) {
    private static final Set<String> LEGACY_FIELDS = Set.of("gitCommit", "preregistrationSha256",
            "parametersSha256", "manifests", "prompts", "arms", "baselineModelsByRoute",
            "ownerApprovalDate", "recheckEligibleFailureClasses", "evidenceMappingSha256",
            "compatibilitySuites", "costForecastUsdPerDeliveredCase");
    FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                 Map<String, String> manifests, Map<String, String> prompts,
                 Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                 LocalDate ownerApprovalDate, Set<String> recheckEligibleFailureClasses,
                 String evidenceMappingSha256, Map<String, String> compatibilitySuites,
                 Map<String, BigDecimal> costForecastUsdPerDeliveredCase) {
        this(gitCommit, preregistrationSha256, parametersSha256, manifests, prompts, arms,
                baselineModelsByRoute, ownerApprovalDate, recheckEligibleFailureClasses,
                evidenceMappingSha256, compatibilitySuites, costForecastUsdPerDeliveredCase, null);
    }
    record Arm(String model, List<String> workflows) {}
    FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                 Map<String, String> manifests, Map<String, String> prompts,
                 Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                 LocalDate ownerApprovalDate, Set<String> recheckEligibleFailureClasses) {
        this(gitCommit, preregistrationSha256, parametersSha256, manifests, prompts, arms,
                baselineModelsByRoute, ownerApprovalDate, recheckEligibleFailureClasses, null, null, null);
    }
    FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                 Map<String, String> manifests, Map<String, String> prompts,
                 Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                 LocalDate ownerApprovalDate) {
        this(gitCommit, preregistrationSha256, parametersSha256, manifests, prompts, arms,
                baselineModelsByRoute, ownerApprovalDate, Set.of(), null, null, null);
    }

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    static FreezeRecord load(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String actual = sha256(bytes);
            String declared = Files.readString(file.resolveSibling(file.getFileName() + ".sha256")).trim();
            if (!actual.equals(declared)) throw new InvalidFreeze("Freeze record SHA-256 mismatch: " + declared + " != " + actual);
            FreezeRecord record = bindExact(bytes);
            if (record.gitCommit() == null || !record.gitCommit().matches("[0-9a-f]{40}")
                    || !digest(record.preregistrationSha256()) || !digest(record.parametersSha256())
                    || record.manifests() == null || record.manifests().isEmpty()
                    || record.prompts() == null || record.prompts().isEmpty()
                    || record.arms() == null || record.arms().isEmpty()
                    || record.baselineModelsByRoute() == null || record.baselineModelsByRoute().isEmpty()
                    || record.ownerApprovalDate() == null || record.recheckEligibleFailureClasses() == null
                    || record.recheckEligibleFailureClasses().stream().anyMatch(value -> value == null || value.isBlank())
                    || !digest(record.evidenceMappingSha256())
                    || record.compatibilitySuites() == null || record.compatibilitySuites().size() != 10
                    || record.compatibilitySuites().entrySet().stream().anyMatch(item -> item.getKey().isBlank()
                        || item.getValue() == null || item.getValue().isBlank())
                    || record.costForecastUsdPerDeliveredCase() == null
                    || record.costForecastUsdPerDeliveredCase().entrySet().stream().anyMatch(item ->
                        item.getKey().isBlank() || item.getValue() == null
                        || item.getValue().signum() <= 0))
                throw new InvalidFreeze("Incomplete freeze record");
            record.manifests().values().forEach(FreezeRecord::requireDigest);
            record.prompts().values().forEach(FreezeRecord::requireDigest);
            record.baselineModelsByRoute().forEach((route, model) -> {
                if (route.isBlank() || model == null || model.isBlank())
                    throw new InvalidFreeze("Invalid baseline route model " + route);
            });
            record.arms().forEach((name, arm) -> {
                if (name.isBlank() || arm == null || arm.model() == null || arm.model().isBlank()
                        || arm.workflows() == null || arm.workflows().isEmpty()
                        || arm.workflows().stream().anyMatch(String::isBlank)
                        || arm.workflows().stream().distinct().count() != arm.workflows().size())
                    throw new InvalidFreeze("Invalid arm " + name);
            });
            Set<String> frozenWorkflows = record.arms().values().stream()
                    .flatMap(arm -> arm.workflows().stream()).collect(java.util.stream.Collectors.toSet());
            if (!record.costForecastUsdPerDeliveredCase().keySet().containsAll(frozenWorkflows)
                    || !record.compatibilitySuites().values().containsAll(frozenWorkflows))
                throw new InvalidFreeze("Missing frozen cost forecast or compatibility suite");
            if (record.placementCommitments() != null) {
                Set<String> distinct = new HashSet<>();
                for (var entry : record.placementCommitments().entrySet()) {
                    String id = entry.getKey();
                    String value = entry.getValue();
                    if (id == null || !id.matches("CLASS_A:[A-Za-z0-9_]+-[A-Za-z0-9_]+|CLASS_B_PRIMARY|CLASS_B_RESERVE:[A-Z][A-Z0-9_]*")
                            || !digest(value) || !distinct.add(value))
                        throw new InvalidFreeze("Invalid placement commitment");
                }
            }
            return record;
        } catch (IOException exception) {
            throw new InvalidFreeze("Missing or malformed freeze record: " + file, exception);
        }
    }

    private static FreezeRecord bindExact(byte[] bytes) {
        try {
            JsonNode tree = JSON.readTree(bytes);
            if (!tree.isObject()) throw new InvalidFreeze("Malformed freeze record");
            Set<String> fields = new HashSet<>();
            tree.fieldNames().forEachRemaining(fields::add);
            Set<String> anchoredFields = new HashSet<>(LEGACY_FIELDS);
            anchoredFields.add("placementCommitments");
            if (!fields.equals(LEGACY_FIELDS) && !fields.equals(anchoredFields))
                throw new InvalidFreeze("Unexpected freeze record fields");
            return JSON.copy().disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .treeToValue(tree, FreezeRecord.class);
        } catch (IOException exception) {
            throw new InvalidFreeze("Malformed freeze record");
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    static String sha256(Path file) throws IOException { return sha256(Files.readAllBytes(file)); }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static void requireDigest(String value) {
        if (!digest(value)) throw new InvalidFreeze("Invalid pinned SHA-256");
    }
    static final class InvalidFreeze extends RuntimeException {
        InvalidFreeze(String message) { super(message); }
        InvalidFreeze(String message, Throwable cause) { super(message, cause); }
    }
}
