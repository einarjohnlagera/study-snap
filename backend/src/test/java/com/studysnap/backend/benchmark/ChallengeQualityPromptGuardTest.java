package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChallengeQualityPromptGuardTest {
    private static final Path PRODUCTION = Path.of("src/main/resources/prompts/study-pack-v1/challenge-quiz-developer.txt");
    private static final Path PROPOSED = Path.of("src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt");

    @Test void exactlyThreeApprovedEditsAndProductionDriftFails() throws Exception {
        String production = Files.readString(PRODUCTION, StandardCharsets.UTF_8);
        assertThat(LlmModelBenchmark.sha256(production)).isEqualTo("5bf4a8e9c93aa3644811c96a302d5c078d88f72e2b442656c2627991539a3fe7");
        String cognitive = "- Cognitive demand: write questions that test meaningful understanding and application. Prefer questions that ask the learner to apply a concept to a short situation, interpret a case, compare related ideas, or reason to a conclusion. Use recall-only questions sparingly, for terms or facts a learner must know exactly. Never create challenge through obscure trivia, trick wording, excessive complexity, or details the notes do not support. If the notes cannot support a deeper question on a concept, write a clear, fair question instead of inventing complexity.";
        List<String> inserted = List.of(
                "- Write every incorrect choice as a plausible answer for a learner with partial understanding: base it on a realistic misconception, an incomplete idea, a common reasoning error, or a closely related concept from the same category as the correct answer.",
                "- Each incorrect choice must be clearly wrong to a learner who understands the material and defensibly incorrect according to the notes. Never make a choice misleading, ambiguous, or partly correct just to raise difficulty.",
                "- Do not use absurd, unrelated, joke, or obviously false choices. Do not use \"all of the above\" or \"none of the above\".",
                "- For standard MCQ and MULTI_SELECT questions, keep the choices parallel in grammar, specificity and format, so that no choice stands out by length, detail or qualification, whether correct or incorrect. Avoid absolute words such as \"always\" or \"never\" in incorrect choices unless the concept genuinely requires them. This does not apply to assertion-style items, which use the four fixed statement choices, or to MATCHING blocks, which share one option set.",
                "- No two choices may mean the same thing, and no two questions may test the same fact from different angles.");
        String explanation = "- Explanation is required for every question and must be accurate and grounded in the notes.";
        String expected = production
                .replace("- Difficulty target: {DIFFICULTY}.", cognitive)
                .replace("- Exactly 1 choice must be correct.\n", "- Exactly 1 choice must be correct.\n" + String.join("\n", inserted) + "\n")
                .replace("- Explanation is required for every question.", explanation);
        String proposed = Files.readString(PROPOSED, StandardCharsets.UTF_8);
        assertThat(proposed.getBytes(StandardCharsets.UTF_8)).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
        List<String> before = production.lines().toList(), after = proposed.lines().toList();
        assertThat(after.size() - before.size()).isEqualTo(5);
        assertThat(before.get(5)).isEqualTo("- Difficulty target: {DIFFICULTY}.");
        assertThat(after.get(5)).isEqualTo(cognitive);
        assertThat(before.get(20)).isEqualTo("- Exactly 1 choice must be correct.");
        assertThat(after.subList(21, 26)).isEqualTo(inserted);
        assertThat(before.get(46)).isEqualTo("- Explanation is required for every question.");
        assertThat(after.get(51)).isEqualTo(explanation);
        assertThat(production.endsWith("\n")).isEqualTo(proposed.endsWith("\n"));
    }
}
