package com.studysnap.backend.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class H2JsonFunctions {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private H2JsonFunctions() {
    }

    public static String jsonbExtractPathText(String json, String key) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root.isTextual()) {
                root = OBJECT_MAPPER.readTree(root.asText());
            }
            JsonNode value = root.get(key);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception ex) {
            return null;
        }
    }
}
