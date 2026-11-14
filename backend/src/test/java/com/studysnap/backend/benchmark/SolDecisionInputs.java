package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;

/** Preregistration section 9: required owner-authored Sol economics, investigation and pairing decisions. */
record SolDecisionInputs(String route, String lunaReportSha256, String solReportSha256,
                         Boolean economicsAndOtherGatesPass, Boolean investigationComplete,
                         Boolean samePrimaryInputs) {
    static final class InvalidDecision extends RuntimeException {
        InvalidDecision(String message) { super(message); }
        InvalidDecision(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    static SolDecisionInputs load(Path path) {
        try {
            DeliveredOutputs.guard(path);
            SolDecisionInputs input = JSON.readValue(path.toFile(), SolDecisionInputs.class);
            if (input.route() == null || input.route().isBlank()
                    || !digest(input.lunaReportSha256()) || !digest(input.solReportSha256())
                    || input.economicsAndOtherGatesPass() == null
                    || input.investigationComplete() == null || input.samePrimaryInputs() == null)
                throw new InvalidDecision("Incomplete owner Sol decision input: " + path);
            return input;
        } catch (IOException exception) {
            throw new InvalidDecision("Missing or malformed owner Sol decision input: " + path, exception);
        }
    }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
