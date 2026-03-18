package com.studysnap.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class LlmResponseUtils {

    public Optional<String> findOutputJson(JsonNode responseJson) {
        if (responseJson == null) {
            return Optional.empty();
        }

        JsonNode outputTextNode = responseJson.get("output_text");
        if (outputTextNode != null && outputTextNode.isTextual()) {
            return Optional.of(outputTextNode.asText());
        }

        for (JsonNode outputNode : responseJson.path("output")) {
            for (JsonNode contentNode : outputNode.path("content")) {
                if ("output_text".equals(contentNode.path("type").asText()) && contentNode.hasNonNull("text")) {
                    return Optional.of(contentNode.path("text").asText());
                }
            }
        }

        return Optional.empty();
    }

    public Integer asNullableInt(JsonNode node) {
        return node != null && node.isNumber() ? node.intValue() : null;
    }

    public String extractUpstreamErrorMessage(String responseBody, ObjectMapper objectMapper) {
        if (StringNormalizationUtils.isBlank(responseBody)) {
            return "n/a";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String message = node.path("error").path("message").asText();
            if (StringNormalizationUtils.isBlank(message)) {
                return "n/a";
            }
            return message;
        } catch (IOException ex) {
            return "unparseable_upstream_error";
        }
    }

    public String sanitizeStudyTip(String rawTip, int maxWords) {
        if (rawTip == null) {
            return null;
        }

        String normalized = rawTip.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return null;
        }

        String[] sentences = normalized.split("(?<=[.!?])\\s+");
        if (sentences.length > 0) {
            normalized = sentences[0].trim();
        }

        String[] words = normalized.split("\\s+");
        if (words.length > maxWords) {
            normalized = String.join(" ", List.of(words).subList(0, maxWords)).trim();
        }

        normalized = normalized.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
