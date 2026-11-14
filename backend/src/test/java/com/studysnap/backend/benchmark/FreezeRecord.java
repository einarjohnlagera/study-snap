package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Section 10 detached, exact-byte freeze declaration for the section 5 arm table. */
record FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                    Map<String, String> manifests, Map<String, String> prompts,
                    Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                    LocalDate ownerApprovalDate, Set<String> recheckEligibleFailureClasses) {
    record Arm(String model, List<String> workflows) {}
    FreezeRecord(String gitCommit, String preregistrationSha256, String parametersSha256,
                 Map<String, String> manifests, Map<String, String> prompts,
                 Map<String, Arm> arms, Map<String, String> baselineModelsByRoute,
                 LocalDate ownerApprovalDate) {
        this(gitCommit, preregistrationSha256, parametersSha256, manifests, prompts, arms,
                baselineModelsByRoute, ownerApprovalDate, Set.of());
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
            FreezeRecord record = JSON.readValue(bytes, FreezeRecord.class);
            if (record.gitCommit() == null || !record.gitCommit().matches("[0-9a-f]{40}")
                    || !digest(record.preregistrationSha256()) || !digest(record.parametersSha256())
                    || record.manifests() == null || record.manifests().isEmpty()
                    || record.prompts() == null || record.prompts().isEmpty()
                    || record.arms() == null || record.arms().isEmpty()
                    || record.baselineModelsByRoute() == null || record.baselineModelsByRoute().isEmpty()
                    || record.ownerApprovalDate() == null || record.recheckEligibleFailureClasses() == null
                    || record.recheckEligibleFailureClasses().stream().anyMatch(value -> value == null || value.isBlank()))
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
            return record;
        } catch (IOException exception) {
            throw new InvalidFreeze("Missing or malformed freeze record: " + file, exception);
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
