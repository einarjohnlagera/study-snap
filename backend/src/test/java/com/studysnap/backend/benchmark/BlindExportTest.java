package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class BlindExportTest {
    @TempDir Path temp;
    @Test void samplesEachModelAndKeepsKeySeparate() throws Exception {
        Path a = Files.createDirectory(temp.resolve("a"));
        Path b = Files.createDirectory(temp.resolve("b"));
        for (Path dir : List.of(a,b)) {
            String model = dir.getFileName().toString();
            Files.writeString(dir.resolve("results.jsonl"),
                    "{\"success\":true,\"model\":\"" + model + "\",\"sourceNoteText\":\"source\",\"questions\":[{\"question\":\"Q?\",\"choices\":[\"yes\",\"no\"],\"correctIndex\":0,\"explanation\":\"Because.\"}]}\n");
        }
        Path out = temp.resolve("blind");
        BlindExport.export(List.of(a,b),out,1,174L);
        List<String> items = Files.readAllLines(out.resolve("blind-items.jsonl"));
        List<String> key = Files.readAllLines(out.resolve("blind-key.jsonl"));
        assertThat(items).hasSize(2).allSatisfy(line -> assertThat(line).contains("sourceNoteText").doesNotContain("model"));
        assertThat(key).hasSize(2).allSatisfy(line -> assertThat(line).contains("model").doesNotContain("sourceNoteText"));
    }
}
