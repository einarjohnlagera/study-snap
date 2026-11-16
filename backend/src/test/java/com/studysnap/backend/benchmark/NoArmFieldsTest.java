package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoArmFieldsTest {
    private static final String CLEAN_LOG = """
            {"workflow":"NOTE_ADAPTIVE","purpose":"SPOT_CHECK","selectedPairIds":["NOTE_ADAPTIVE#1"]}
            """;
    @TempDir Path temp;
    @Test void everyDeniedKeyIsFoundInsideAnArray() {
        for (String key : List.of("arm", "armKey", "armLabel", "baseline", "candidate", "model",
                "modelName", "modelId", "route", "tier", "planted", "plantedType",
                "authoringSource", "canary", "severity", "inputTokenCount", "costUsd", "usage")) {
            String json = "{\"outer\":[{\"inner\":[{\"" + key + "\":\"neutral\"}]}]}";
            assertThatThrownBy(() -> NoArmFields.check(json))
                    .isInstanceOf(NoArmFields.ArmFieldDetected.class).hasMessageContaining(key);
        }
    }
    @Test void everyExporterLeakWordIsFoundThreeLevelsDeep() {
        for (String word : List.of("baseline", "candidate", "luna", "sol", "gpt-4", "codex",
                "claude", "gemini", "openai", "model", "modelName", "modelId", "token",
                "tokens", "tokenCount", "inputTokens", "outputTokens", "reasoning_tokens",
                "usage", "costUsd")) {
            String json = "{\"a\":[{\"b\":{\"c\":\"" + word + "\"}}]}";
            assertThatThrownBy(() -> NoArmFields.check(json))
                    .isInstanceOf(NoArmFields.ArmFieldDetected.class).hasMessageContaining("c");
        }
    }
    @Test void cleanLogAndFilePassButDuplicateJsonKeyFails() throws Exception {
        NoArmFields.check(CLEAN_LOG);
        Path file = temp.resolve("log.jsonl");
        Files.writeString(file, CLEAN_LOG);
        NoArmFields.check(file);
        assertThat(Files.readString(file)).contains("selectedPairIds");
        String duplicate = "{\"ok\":1,\"ok\":2}";
        assertThatThrownBy(() -> NoArmFields.check(duplicate))
                .isInstanceOf(NoArmFields.ArmFieldDetected.class);
    }
}
