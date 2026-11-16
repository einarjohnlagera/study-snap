package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScorerPromptRenderTest {
    @TempDir Path temp;
    // TEST-ONLY PLACEHOLDER, NOT THE PILOT PROMPT.
    private static final String TEMPLATE = "Read this synthetic packet:\n{{PACKET_JSONL}}\nReturn rows.";
    private static final String PACKET = "{\"requestedQuestionCount\":1}\n";
    private Path template(String body) throws Exception {
        Path file = temp.resolve("test-placeholder.txt");
        Files.writeString(file, body);
        return file;
    }
    private Path packet() throws Exception {
        Path file = temp.resolve("packet.jsonl");
        Files.writeString(file, PACKET);
        return file;
    }
    @Test void renderedFileHashIsRecordedAndVerifyRereadsIt() throws Exception {
        Path source = template(TEMPLATE);
        Path packet = packet();
        Path output = temp.resolve("p-rendered.txt");
        ScorerPromptRender.PromptRecord p = ScorerPromptRender.render(source, "P", packet, output);
        ScorerPromptRender.PromptRecord s = ScorerPromptRender.render(source, "S", packet,
                temp.resolve("s-rendered.txt"));
        assertThat(Files.readString(output)).contains(PACKET);
        assertThat(Files.readString(output)).isEqualTo(Files.readString(s.renderedPath()));
        assertThat(p.templateSha256()).isEqualTo(FreezeRecord.sha256(Files.readAllBytes(source)));
        assertThat(p.renderedSha256()).isEqualTo(FreezeRecord.sha256(Files.readAllBytes(output)))
                .isNotEqualTo(p.templateSha256());
        assertThat(Files.readString(output.resolveSibling("p-rendered.txt.record.json"))).contains(p.renderedSha256());
        ScorerPromptRender.verify(p);
        Files.writeString(output, Files.readString(output) + "x");
        assertThatThrownBy(() -> ScorerPromptRender.verify(p))
                .isInstanceOf(ScorerPromptRender.InvalidPrompt.class);
    }
    @Test void exactlyOneKnownPlaceholderAndOnlyTwoRoles() throws Exception {
        Path packet = packet();
        Path output = temp.resolve("rendered.txt");
        Path source = template("no placeholder");
        assertThatThrownBy(() -> ScorerPromptRender.render(source, "P", packet, output))
                .isInstanceOf(ScorerPromptRender.InvalidPrompt.class);
        Files.writeString(source, TEMPLATE + "{{PACKET_JSONL}}");
        assertThatThrownBy(() -> ScorerPromptRender.render(source, "P", packet, output))
                .isInstanceOf(ScorerPromptRender.InvalidPrompt.class);
        Files.writeString(source, TEMPLATE + "{{OTHER_SCORER_FINDINGS}}");
        assertThatThrownBy(() -> ScorerPromptRender.render(source, "S", packet, output))
                .isInstanceOf(ScorerPromptRender.InvalidPrompt.class);
        Files.writeString(source, TEMPLATE);
        assertThatThrownBy(() -> ScorerPromptRender.render(source, "X", packet, output))
                .isInstanceOf(ScorerPromptRender.InvalidPrompt.class);
        assertThat(Arrays.stream(ScorerPromptRender.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("render"))).singleElement()
                .satisfies(method -> assertThat(method.getParameterCount()).isEqualTo(4));
    }
}
