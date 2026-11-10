package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeQualityGPlusPromptGuardTest {
    private static final Path PRODUCTION = Path.of("src/main/resources/prompts/study-pack-v1/challenge-quiz-developer.txt");
    private static final Path PROPOSED = Path.of("src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt");
    private static final Path GPLUS = Path.of("src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.gplus.txt");
    private static final String PRODUCTION_SHA256 = "5bf4a8e9c93aa3644811c96a302d5c078d88f72e2b442656c2627991539a3fe7";
    private static final String GPLUS_SHA256 = "019c5bdf9f868c0aa206490a345b91020e588c1afe993f8930ecd1eed1029f16";
    private static final String PROPOSED_BLOB = "352bc91b1fb3fa2e90420f72f9963041e042a3df";
    private static final String ANCHOR = "- Exactly 1 choice must be correct.";
    private static final String DIFFICULTY = "- Difficulty target: {DIFFICULTY}.";
    private static final String EXPLANATION = "- Explanation is required for every question.";
    private static final String PLACEHOLDER = "{DIFFICULTY}";

    @Test void productionPromptHashIsPinned() throws Exception {
        assertThat(LlmModelBenchmark.sha256(Files.readString(PRODUCTION, StandardCharsets.UTF_8)))
                .isEqualTo(PRODUCTION_SHA256);
    }

    @Test void gplusBytesEqualProductionWithApprovedBulletsAtTheAnchor() throws Exception {
        String production = Files.readString(PRODUCTION, StandardCharsets.UTF_8);
        String anchorWithNewline = ANCHOR + "\n";
        assertThat(Collections.frequency(production.lines().toList(), ANCHOR)).isEqualTo(1);
        assertThat(Files.readAllBytes(GPLUS)).isEqualTo(production.replace(anchorWithNewline,
                anchorWithNewline + String.join("\n", approvedBullets()) + "\n").getBytes(StandardCharsets.UTF_8));
        assertThat(LlmModelBenchmark.sha256(Files.readString(GPLUS, StandardCharsets.UTF_8)))
                .isEqualTo(GPLUS_SHA256);
    }

    @Test void gplusLineDiffAddsOnlyFiveApprovedLines() throws Exception {
        List<String> before = Files.readAllLines(PRODUCTION, StandardCharsets.UTF_8);
        List<String> after = Files.readAllLines(GPLUS, StandardCharsets.UTF_8);
        List<String> added = new ArrayList<>();
        int beforeIndex = 0;
        for (String line : after) {
            if (beforeIndex < before.size() && line.equals(before.get(beforeIndex))) {
                beforeIndex++;
            } else {
                added.add(line);
            }
        }
        assertThat(beforeIndex).isEqualTo(before.size());
        assertThat(added).containsExactlyElementsOf(approvedBullets());
        assertThat(after.size() - before.size()).isEqualTo(5);
    }

    @Test void gplusKeepsProductionDifficultyAndExplanationWithoutChangesAOrC() throws Exception {
        List<String> lines = Files.readAllLines(GPLUS, StandardCharsets.UTF_8);
        assertThat(Collections.frequency(lines, DIFFICULTY)).isEqualTo(1);
        assertThat(lines).noneMatch(line -> line.startsWith("- Cognitive demand:"));
        assertThat(Collections.frequency(lines, EXPLANATION)).isEqualTo(1);
        assertThat(lines).noneMatch(line -> line.contains("must be accurate and grounded in the notes"));
    }

    @Test void gplusPreservesAllProductionFormatInstructionsInOrder() throws Exception {
        List<String> production = Files.readAllLines(PRODUCTION, StandardCharsets.UTF_8);
        List<String> gplus = Files.readAllLines(GPLUS, StandardCharsets.UTF_8);
        List<String> formatLines = production.stream().filter(line -> line.contains("correctIndices")
                || line.contains("assertion") || line.contains("MATCHING") || line.contains("MULTI_SELECT")).toList();
        int lastIndex = -1;
        for (String line : formatLines) {
            assertThat(Collections.frequency(gplus, line)).as("format line: %s", line).isEqualTo(1);
            int index = gplus.indexOf(line);
            assertThat(index).as("format order: %s", line).isGreaterThan(lastIndex);
            lastIndex = index;
        }
        List<String> coreLines = List.of(
                "- Standard MCQ questions must have 4 choices.",
                ANCHOR,
                "- For MULTI_SELECT questions, set \"questionFormat\" to \"MULTI_SELECT\"",
                "- Do not use MULTI_SELECT when all 4 choices are correct.",
                "- You may include 1 MATCHING block",
                "- A MATCHING block must be 2 to 4 consecutive questions");
        lastIndex = -1;
        for (String prefix : coreLines) {
            List<String> matches = production.stream().filter(line -> line.startsWith(prefix)).toList();
            assertThat(matches).as("production line: %s", prefix).hasSize(1);
            String line = matches.get(0);
            assertThat(Collections.frequency(gplus, line)).as("G+ line: %s", prefix).isEqualTo(1);
            int index = gplus.indexOf(line);
            assertThat(index).as("core format order: %s", prefix).isGreaterThan(lastIndex);
            lastIndex = index;
        }
        List<String> insertedFormatLines = approvedBullets().stream().filter(line -> line.contains("correctIndices")
                || line.contains("assertion") || line.contains("MATCHING") || line.contains("MULTI_SELECT")).toList();
        assertThat(insertedFormatLines).containsExactly(approvedBullets().get(3));
        assertThat(insertedFormatLines.get(0)).doesNotContain("correctIndices");
    }

    @Test void pinnedProposedPromptGitBlobIsUnchanged() throws Exception {
        byte[] bytes = Files.readAllBytes(PROPOSED);
        byte[] prefix = ("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] blob = new byte[prefix.length + bytes.length];
        System.arraycopy(prefix, 0, blob, 0, prefix.length);
        System.arraycopy(bytes, 0, blob, prefix.length, bytes.length);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(blob)))
                .isEqualTo(PROPOSED_BLOB);
    }

    @Test void challengeHarnessLoadsGplusAndKeepsCurrentPrompt() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String gplus = LlmModelBenchmark.challengeResources(mapper, GPLUS.toString())
                .challengeQuizDeveloperPromptTemplate();
        assertThat(LlmModelBenchmark.sha256(gplus)).isEqualTo(GPLUS_SHA256);
        assertThat(gplus.getBytes(StandardCharsets.UTF_8)).isEqualTo(Files.readAllBytes(GPLUS));
        assertThat(count(gplus, PLACEHOLDER)).isEqualTo(1);
        String current = LlmModelBenchmark.challengeResources(mapper, null)
                .challengeQuizDeveloperPromptTemplate();
        assertThat(LlmModelBenchmark.sha256(current)).isEqualTo(PRODUCTION_SHA256);
    }

    private static List<String> approvedBullets() throws Exception {
        List<String> proposed = Files.readAllLines(PROPOSED, StandardCharsets.UTF_8);
        int anchorIndex = proposed.indexOf(ANCHOR);
        assertThat(anchorIndex).isGreaterThanOrEqualTo(0);
        int nextFormatIndex = anchorIndex + 1;
        while (nextFormatIndex < proposed.size() && !proposed.get(nextFormatIndex).startsWith("- You may include 1 to 2 MULTI_SELECT questions")) {
            nextFormatIndex++;
        }
        assertThat(nextFormatIndex).isLessThan(proposed.size());
        List<String> bullets = proposed.subList(anchorIndex + 1, nextFormatIndex);
        assertThat(bullets).hasSize(5);
        return bullets;
    }

    private static int count(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }
}
