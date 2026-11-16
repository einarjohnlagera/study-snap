package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Frozen subgroup manifest validation for preregistration sections 3 and 6. */
class ClassAFanOutManifestTest {
    private static final String BIOLOGY = "Biology";
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path tempDir;

    @Test void mapsEachLongExamSubgroupToItsProductionAdapter() throws IOException {
        Path file = write(rows(ClassACaseManifest.Subgroup.values()));
        assertThat(ClassAAdapters.load(file)).extracting(Object::getClass)
                .containsExactly(LongExamDirectAdapter.class, LongExamPoolAdapter.class,
                        BoardExamPoolAdapter.class);
    }

    @Test void refusesWrongSubgroup() throws IOException {
        ObjectNode row = row(ClassACaseManifest.Subgroup.DIRECT);
        row.put("workflow", "NOTE_ADAPTIVE");
        Path file = write(row.toString());
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesUnknownField() throws IOException {
        ObjectNode row = row(ClassACaseManifest.Subgroup.DIRECT);
        row.put("unexpected", true);
        Path file = write(row.toString());
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IOException.class);
    }

    @Test void refusesDuplicateCaseId() throws IOException {
        ObjectNode row = row(ClassACaseManifest.Subgroup.DIRECT);
        Path file = write(row + "\n" + row);
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesRepoPath() {
        Path file = Path.of("src/test/java/com/studysnap/backend/benchmark/ClassAFanOutManifestTest.java")
                .toAbsolutePath();
        assertThatThrownBy(() -> ClassACaseManifest.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Benchmark input/output inside repo");
    }

    private String rows(ClassACaseManifest.Subgroup[] subgroups) throws IOException {
        StringBuilder result = new StringBuilder();
        for (ClassACaseManifest.Subgroup subgroup : subgroups) {
            if (!result.isEmpty()) result.append('\n');
            result.append(row(subgroup));
        }
        return result.toString();
    }

    private ObjectNode row(ClassACaseManifest.Subgroup subgroup) throws IOException {
        var source = new ClassACaseManifest.Source("Cells", "Cells have membranes.",
                "Cells contain membranes.", List.of("Membrane"), List.of("cells"), BIOLOGY,
                BIOLOGY, null, LearnerLevel.COLLEGE);
        int count = switch (subgroup) {
            case DIRECT -> 25;
            case LONG_EXAM_POOL -> 48;
            case BOARD_EXAM_POOL -> 24;
        };
        var item = new ClassACaseManifest.Case("synthetic-" + subgroup,
                ClassACaseManifest.Workflow.LONG_EXAM, "synthetic", LearnerLevel.COLLEGE,
                null, BIOLOGY, false, List.of(source), count, null, false, subgroup);
        return (ObjectNode) json.readTree(json.writeValueAsString(item));
    }

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("class-a-fan-out.jsonl");
        Files.writeString(file, content);
        return file;
    }
}
