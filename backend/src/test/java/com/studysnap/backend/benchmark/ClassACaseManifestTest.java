package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline schema checks for preregistration section 6. */
class ClassACaseManifestTest {
    private static final String FIRST_ID = "first";
    private static final String SECOND_ID = "second";
    private static final String FIRST = "{\"caseId\":\"first\",\"workflow\":\"TEACHER_QUIZ\",\"stratum\":\"synthetic\","
            + "\"noteLevel\":\"COLLEGE\",\"domainContext\":null,\"subject\":\"Biology\","
            + "\"quantitative\":false,\"sources\":[{\"title\":\"Cells\",\"noteText\":\"Cells have membranes.\","
            + "\"summary\":\"Cells have membranes.\",\"keyConcepts\":[\"Membrane\"],\"tags\":[],"
            + "\"courseProgram\":\"Biology\",\"subject\":\"Biology\",\"domainContext\":null,"
            + "\"noteLevel\":\"COLLEGE\"}],\"requestedQuestionCount\":5,\"difficulty\":null,\"reserve\":false}";
    @TempDir Path tempDir;

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("class-a.jsonl");
        Files.writeString(file, content);
        return file;
    }

    @Test void retainsStoredOrder() throws IOException {
        Path file = write(FIRST.replace(FIRST_ID, SECOND_ID) + "\n" + FIRST + "\n");
        assertThat(ClassACaseManifest.load(file).stream().map(ClassACaseManifest.Case::caseId))
                .containsExactly(SECOND_ID, FIRST_ID);
        assertThat(ClassAAdapters.load(file).stream().map(StageRunner.CaseCall::id))
                .containsExactly(SECOND_ID, FIRST_ID);
    }

    @Test void rejectsUnknownField() throws IOException {
        Path file = write(FIRST.replace("\"reserve\":false", "\"unexpected\":1,\"reserve\":false"));
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IOException.class);
    }

    @Test void rejectsDuplicateId() throws IOException {
        Path file = write(FIRST + "\n" + FIRST + "\n");
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsMissingRequiredCaseField() throws IOException {
        Path file = write(FIRST.replace(",\"reserve\":false", ""));
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IOException.class);
    }

    @Test void rejectsMissingCaseId() throws IOException {
        Path file = write(FIRST.replace("\"caseId\":\"first\",", ""));
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IOException.class);
    }

    @Test void rejectsWrongWorkflow() throws IOException {
        Path file = write(FIRST.replace("TEACHER_QUIZ", "LONG_EXAM_DIRECT"));
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IOException.class);
    }

    @Test void rejectsDifficultyThatProductionDoesNotTake() throws IOException {
        Path file = write(FIRST.replace("\"difficulty\":null", "\"difficulty\":\"hard\""));
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsRepoPath() {
        Path file = Path.of("src/test/java/com/studysnap/backend/benchmark/ClassACaseManifestTest.java")
                .toAbsolutePath();
        assertThatThrownBy(() -> ClassACaseManifest.load(file)).isInstanceOf(IllegalArgumentException.class);
    }
}
