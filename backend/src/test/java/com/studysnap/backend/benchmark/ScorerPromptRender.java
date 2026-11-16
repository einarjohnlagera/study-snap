package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders one complete independent scorer prompt and records its file-byte hash. */
final class ScorerPromptRender {
    static final class InvalidPrompt extends RuntimeException {
        InvalidPrompt(String message) { super(message); }
        InvalidPrompt(String message, Throwable cause) { super(message, cause); }
    }
    record PromptRecord(String role, String templateSha256, String renderedSha256,
                        Path renderedPath, long renderedBytes) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLACEHOLDER = "{{PACKET_JSONL}}";
    private static final Pattern PLACEHOLDERS = Pattern.compile("\\{\\{.*?}}", Pattern.DOTALL);
    private ScorerPromptRender() {}

    static PromptRecord render(Path template, String role, Path packetFile, Path outputFile) {
        try {
            if (!Set.of("P", "S").contains(role)) throw new InvalidPrompt("Role must be P or S");
            byte[] templateBytes = Files.readAllBytes(template);
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(templateBytes)).toString();
            Matcher matcher = PLACEHOLDERS.matcher(source);
            int count = 0;
            while (matcher.find()) {
                if (!PLACEHOLDER.equals(matcher.group())) throw new InvalidPrompt("Unknown prompt placeholder");
                count++;
            }
            if (count != 1) throw new InvalidPrompt("Exactly one packet placeholder required");
            byte[] packetBytes = Files.readAllBytes(packetFile);
            int position = source.indexOf(PLACEHOLDER);
            ByteArrayOutputStream rendered = new ByteArrayOutputStream();
            rendered.write(source.substring(0, position).getBytes(StandardCharsets.UTF_8));
            rendered.write(packetBytes);
            rendered.write(source.substring(position + PLACEHOLDER.length()).getBytes(StandardCharsets.UTF_8));
            byte[] bytes = rendered.toByteArray();
            Files.write(outputFile, bytes);
            PromptRecord record = new PromptRecord(role, FreezeRecord.sha256(templateBytes),
                    FreezeRecord.sha256(Files.readAllBytes(outputFile)), outputFile.toAbsolutePath().normalize(),
                    Files.size(outputFile));
            Files.writeString(outputFile.resolveSibling(outputFile.getFileName() + ".record.json"),
                    JSON.writeValueAsString(record) + "\n");
            return record;
        } catch (InvalidPrompt exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPrompt("Cannot render scorer prompt", exception);
        }
    }
    static void verify(PromptRecord record) {
        try {
            byte[] bytes = Files.readAllBytes(record.renderedPath());
            if (!FreezeRecord.sha256(bytes).equals(record.renderedSha256()) || bytes.length != record.renderedBytes())
                throw new InvalidPrompt("Rendered prompt hash or length differs");
        } catch (InvalidPrompt exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPrompt("Cannot verify rendered prompt", exception);
        }
    }
}
