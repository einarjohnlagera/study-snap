package com.studysnap.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseUtilsTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void findOutputJson_readsTopLevelOutputText() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("{\"output_text\":\"{\\\"tip\\\":\\\"Review ATP\\\"}\"}");

        assertThat(LlmResponseUtils.findOutputJson(node)).contains("{\"tip\":\"Review ATP\"}");
    }

    @Test
    void findOutputJson_readsNestedOutputContent() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "output": [
                    {
                      "content": [
                        {"type": "output_text", "text": "{\\"quiz\\":[]}"}
                      ]
                    }
                  ]
                }
                """);

        assertThat(LlmResponseUtils.findOutputJson(node)).contains("{\"quiz\":[]}");
    }

    @Test
    void findOutputJson_returnsEmptyWhenMissing() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("{\"output\":[]}");
        assertThat(LlmResponseUtils.findOutputJson(node)).isEmpty();
    }

    @Test
    void asNullableInt_returnsNumberOrNull() throws Exception {
        JsonNode numberNode = OBJECT_MAPPER.readTree("12");
        JsonNode textNode = OBJECT_MAPPER.readTree("\"12\"");

        assertThat(LlmResponseUtils.asNullableInt(numberNode)).isEqualTo(12);
        assertThat(LlmResponseUtils.asNullableInt(textNode)).isNull();
        assertThat(LlmResponseUtils.asNullableInt(null)).isNull();
    }

    @Test
    void extractUpstreamErrorMessage_handlesValidMissingAndInvalidJson() {
        assertThat(LlmResponseUtils.extractUpstreamErrorMessage(
                "{\"error\":{\"message\":\"Rate limit\"}}",
                OBJECT_MAPPER
        )).isEqualTo("Rate limit");

        assertThat(LlmResponseUtils.extractUpstreamErrorMessage(
                "{\"error\":{}}",
                OBJECT_MAPPER
        )).isEqualTo("n/a");

        assertThat(LlmResponseUtils.extractUpstreamErrorMessage(
                "invalid-json",
                OBJECT_MAPPER
        )).isEqualTo("unparseable_upstream_error");
    }

    @Test
    void sanitizeStudyTip_keepsSingleSentenceAndWordLimit() {
        String raw = "Review ATP synthesis and enzyme control first. Then revisit glycolysis examples.";
        assertThat(LlmResponseUtils.sanitizeStudyTip(raw, 6))
                .isEqualTo("Review ATP synthesis and enzyme control");
    }
}
