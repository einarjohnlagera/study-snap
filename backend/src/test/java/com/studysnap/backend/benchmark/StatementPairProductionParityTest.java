package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.studysnap.backend.service.impl.StatementPairVerification;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.StudySnapProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementPairProductionParityTest {
    private static final Path PRODUCTION = Path.of("src/main/resources/prompts/study-pack-v1");
    private static final Path HARNESS = Path.of("src/test/resources/benchmark/verifier");
    private static final Path SIGNED = Path.of("../docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void productionPromptsEqualSignedHarnessAndDocumentBytes() throws Exception {
        String[] production = {"statement-pair-verifier-system.txt", "statement-pair-verifier-developer.txt",
                "statement-pair-verifier-user.txt", "statement-pair-verifier-schema.json"};
        for (int i = 0; i < production.length; i++) {
            assertThat(stripTrailingNewline(Files.readString(PRODUCTION.resolve(production[i]))))
                    .isEqualTo(stripTrailingNewline(Files.readString(HARNESS.resolve(VerifierRequestBuilder.NAMES[i]))));
        }
        String signed = Files.readString(SIGNED, StandardCharsets.UTF_8);
        String[] headings = {"### 3.1 System message", "### 3.2 Developer message",
                "### 3.3 User message template", "### 3.4 Response schema"};
        for (int i = 0; i < production.length; i++) {
            assertThat(stripTrailingNewline(Files.readString(PRODUCTION.resolve(production[i]))))
                    .isEqualTo(sectionBlock(signed, headings[i], i == 3));
        }
        assertThat(stripTrailingNewline(Files.readString(PRODUCTION.resolve("challenge-more-no-statement-pair-developer.txt"))))
                .isEqualTo(block(signed, "## Part 1. The +5 rule", 0));
        assertThat(stripTrailingNewline(Files.readString(PRODUCTION.resolve("statement-pair-replacement-developer.txt"))))
                .isEqualTo(block(signed, "## Part 2. The replacement message", 0));
        assertThat(stripTrailingNewline(Files.readString(PRODUCTION.resolve("statement-pair-replacement-user.txt"))))
                .isEqualTo(block(signed, "## Part 2. The replacement message", 1));
    }

    private static String sectionBlock(String document, String heading, boolean json) {
        String section = document.split(Pattern.quote(heading), 2)[1].split("\n### ", 2)[0];
        Matcher matcher = Pattern.compile(json ? "```json\\n(.*?)\\n```" : "```\\n(.*?)\\n```",
                Pattern.DOTALL).matcher(section);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String stripTrailingNewline(String value) {
        return value.replaceFirst("\\R$", "");
    }

    private static String block(String document, String heading, int index) {
        String section = document.split(Pattern.quote(heading), 2)[1].split("\\n## ", 2)[0];
        Matcher matcher = Pattern.compile("```\\n(.*?)\\n```", Pattern.DOTALL).matcher(section);
        for (int i = 0; i <= index; i++) assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void productionBlindRequestMatchesTheHarnessBuilder() throws Exception {
        var note = mapper.createObjectNode().put("domainContext", "Nursing")
                .put("subject", "Anatomy").put("learnerLevel", "College")
                .put("noteText", "A pump moves water.").put("summary", "A short summary.");
        var pair = new StatementPairExtractor.Pair("A pump moves water", "Water is a metal");
        var keyed = mapper.createArrayNode().add(true).add(false);
        var harnessItem = new StatementPairVerifierBenchmark.Item("synthetic", "N", "batch", "note",
                "synthetic", keyed, mapper.nullNode(), pair);
        JsonNode harnessRequest = new VerifierRequestBuilder(mapper, HARNESS)
                .build("gpt-6-luna", note, List.of(harnessItem));
        var prompts = new OpenAiLlmConfig().openAiPromptResources(mapper, new StudySnapProperties());
        JsonNode productionRequest = StatementPairVerification.request(mapper, prompts, "gpt-6-luna",
                "Nursing", "Anatomy", "College", "A pump moves water.", "A short summary.",
                List.of(new StatementPairVerification.Item("1",
                        new StatementPairVerification.Pair(pair.statement1(), pair.statement2()), true, false)));
        assertThat(productionRequest).isEqualTo(harnessRequest);
    }

    @Test
    void productionAndHarnessKeepLiteralPlaceholdersAndMetacharacters() throws Exception {
        String noteText = "A pump moves water. {ITEMS_JSON} {STUDY_PACK_SUMMARY} $5 C:\\notes";
        String summary = "Summary {ITEMS_JSON} $7 C:\\summary";
        var note = mapper.createObjectNode().put("domainContext", "Nursing")
                .put("subject", "Anatomy").put("learnerLevel", "College")
                .put("noteText", noteText).put("summary", summary);
        var pair = new StatementPairExtractor.Pair("A pump moves water", "Water is a metal");
        var keyed = mapper.createArrayNode().add(true).add(false);
        var harnessItem = new StatementPairVerifierBenchmark.Item("synthetic", "N", "batch", "note",
                "synthetic", keyed, mapper.nullNode(), pair);
        JsonNode harnessRequest = new VerifierRequestBuilder(mapper, HARNESS)
                .build("gpt-6-luna", note, List.of(harnessItem));
        var prompts = new OpenAiLlmConfig().openAiPromptResources(mapper, new StudySnapProperties());
        JsonNode productionRequest = StatementPairVerification.request(mapper, prompts, "gpt-6-luna",
                "Nursing", "Anatomy", "College", noteText, summary,
                List.of(new StatementPairVerification.Item("1",
                        new StatementPairVerification.Pair(pair.statement1(), pair.statement2()), true, false)));
        assertThat(productionRequest).isEqualTo(harnessRequest);
        String user = productionRequest.path("input").get(2).path("content").get(0).path("text").asText();
        assertThat(user).contains(noteText, summary);
    }

    @Test
    void extractorAndDecisionMatchHarnessOnSharedFixtures() throws Exception {
        String note = "The machine uses a pump and a valve. A valve controls flow; the pump raises pressure.";
        List<String> stems = List.of(
                "Statement 1: The machine uses a pump. Statement 2: A valve controls flow. Which of the statements is/are correct?",
                "Statement 1: The pump raises pressure.\\nStatement 2: The valve controls flow. Which is correct?");
        for (String stem : stems) {
            StatementPairExtractor.Pair harness = StatementPairExtractor.extract(stem);
            StatementPairVerification.Pair production = StatementPairVerification.extract(stem);
            assertThat(production).isEqualTo(new StatementPairVerification.Pair(harness.statement1(), harness.statement2()));
        }
        JsonNode good = judgment("SUPPORTED", "\"The machine uses a pump\" ; A valve controls flow", "TRUE");
        JsonNode paraphrase = judgment("SUPPORTED", "The machinery includes a pump", "TRUE");
        JsonNode contradictory = judgment("SUPPORTED", "A valve controls flow", "FALSE");
        JsonNode uncertain = judgment("SILENT", null, "UNCERTAIN");
        for (JsonNode judgment : List.of(good, paraphrase, contradictory, uncertain)) {
            VerifierDecision.Normalized harness = VerifierDecision.normalize(judgment, note);
            StatementPairVerification.Normalized production = StatementPairVerification.normalize(judgment, note);
            assertThat(production.verdict()).isEqualTo(harness.verdict());
            assertThat(production.reason()).isEqualTo(harness.reason());
            assertThat(StatementPairVerification.quoteFound(judgment, note))
                    .isEqualTo(VerifierDecision.quoteFound(judgment, note));
            JsonNode result = mapper.createObjectNode().set("statement1", judgment);
            ((com.fasterxml.jackson.databind.node.ObjectNode) result).set("statement2", judgment);
            ArrayNode keyed = mapper.createArrayNode().add(true).add(true);
            StatementPairVerifierBenchmark.Item item = new StatementPairVerifierBenchmark.Item("x", "N", "batch",
                    "note", "synthetic", keyed, mapper.nullNode(), new StatementPairExtractor.Pair("one", "two"));
            JsonNode harnessRow = VerifierDecision.decide(item, result, note, mapper);
            StatementPairVerification.Decision decision = StatementPairVerification.decide(
                    new StatementPairVerification.Item("1", new StatementPairVerification.Pair("one", "two"), true, true),
                    result, result, note);
            assertThat(decision.accepted()).isEqualTo("ACCEPT".equals(harnessRow.path("decision").asText()));
            assertThat(decision.reason()).isEqualTo(harnessRow.path("primaryReason").isNull()
                    ? null : harnessRow.path("primaryReason").asText());
        }
    }

    @Test
    void extractorStripsOnlyAFinalWhichQuestionFromStatementTwo() {
        String prefix = "Statement 1: Water expands. Statement 2: Water is a liquid.";
        for (String closing : List.of("Which of the following statements are correct?",
                "Which of the statements is true?", "Which is correct?",
                "which of the statements is true?")) {
            String stem = prefix + " " + closing;
            StatementPairVerification.Pair expected = new StatementPairVerification.Pair(
                    "Water expands.", "Water is a liquid.");
            assertThat(StatementPairVerification.extract(stem)).isEqualTo(expected);
            assertThat(StatementPairExtractor.extract(stem)).isEqualTo(
                    new StatementPairExtractor.Pair(expected.statement1(), expected.statement2()));
        }
        String midSentence = "Statement 1: Water expands. Statement 2: The rule which applies remains valid.";
        assertThat(StatementPairVerification.extract(midSentence).statement2())
                .isEqualTo("The rule which applies remains valid.");
        assertThat(StatementPairExtractor.extract(midSentence).statement2())
                .isEqualTo("The rule which applies remains valid.");
        String nonFinal = prefix + " Which is correct? Explain your answer.";
        assertThat(StatementPairVerification.extract(nonFinal).statement2())
                .isEqualTo("Water is a liquid. Which is correct? Explain your answer.");
    }

    @Test
    void invalidCountsAndItemIdsFailTheWholeCheckerCall() throws Exception {
        String judgment = "{\"sourceSupport\":\"SILENT\",\"evidenceQuote\":null,\"reason\":\"Known.\",\"noteConflictsWithStandardKnowledge\":false,\"verdict\":\"TRUE\"}";
        String one = "{\"itemId\":\"1\",\"statement1\":" + judgment
                + ",\"statement2\":" + judgment + "}";
        String duplicate = "{\"results\":[" + one + "," + one + "]}";
        String extra = "{\"results\":[" + one + ","
                + one.replace("\"itemId\":\"1\"", "\"itemId\":\"3\"") + "]}";
        assertThatThrownBy(() -> StatementPairVerification.validate("not json", 1, mapper))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> StatementPairVerification.validate("{\"results\":[]}", 1, mapper))
                .isInstanceOf(StatementPairVerification.InvalidVerifierResponseException.class);
        assertThatThrownBy(() -> StatementPairVerification.validate(duplicate, 2, mapper))
                .isInstanceOf(StatementPairVerification.InvalidVerifierResponseException.class);
        assertThatThrownBy(() -> StatementPairVerification.validate(extra, 2, mapper))
                .isInstanceOf(StatementPairVerification.InvalidVerifierResponseException.class);
    }

    @Test
    void eachDeterministicRejectionReasonIsStable() throws Exception {
        String note = "A pump moves water. A valve controls flow.";
        StatementPairVerification.Item item = new StatementPairVerification.Item("1",
                new StatementPairVerification.Pair("A pump moves water", "A valve controls flow"), true, true);
        JsonNode trueJudgment = judgment("SUPPORTED", "A pump moves water", "TRUE");
        JsonNode falseJudgment = judgment("SILENT", null, "FALSE");
        JsonNode uncertainJudgment = judgment("SILENT", null, "UNCERTAIN");
        JsonNode contradictoryJudgment = judgment("SUPPORTED", "A pump moves water", "FALSE");
        JsonNode inventedQuote = judgment("SUPPORTED", "A pump transports water", "TRUE");
        JsonNode accepted = result(trueJudgment, trueJudgment);
        assertThat(StatementPairVerification.decide(item, accepted, accepted, note).accepted()).isTrue();
        assertThat(StatementPairVerification.decide(item, result(falseJudgment, falseJudgment),
                result(falseJudgment, falseJudgment), note).reason()).isEqualTo("MISMATCH");
        assertThat(StatementPairVerification.decide(item, result(uncertainJudgment, trueJudgment),
                accepted, note).reason()).isEqualTo("UNCERTAIN");
        assertThat(StatementPairVerification.decide(item, result(contradictoryJudgment, trueJudgment),
                accepted, note).reason()).isEqualTo("SELF_CONTRADICTORY");
        assertThat(StatementPairVerification.decide(item, result(inventedQuote, trueJudgment),
                accepted, note).reason()).isEqualTo("UNVERIFIED_EVIDENCE");
        assertThat(StatementPairVerification.decide(item, accepted, result(falseJudgment, trueJudgment),
                note).reason()).isEqualTo("CHECKERS_DISAGREE");
        assertThat(StatementPairVerification.decide(item, null, accepted, note).reason()).isEqualTo("CALL_FAILED");
        assertThat(StatementPairVerification.decide(item, accepted, accepted, " ").reason())
                .isEqualTo("EXTRACTION_FAILED");
    }

    private JsonNode result(JsonNode one, JsonNode two) {
        var result = mapper.createObjectNode();
        result.set("statement1", one);
        result.set("statement2", two);
        return result;
    }

    private JsonNode judgment(String support, String quote, String verdict) {
        var node = mapper.createObjectNode().put("sourceSupport", support).put("reason", "Synthetic reason")
                .put("noteConflictsWithStandardKnowledge", false).put("verdict", verdict);
        if (quote == null) node.putNull("evidenceQuote"); else node.put("evidenceQuote", quote);
        return node;
    }
}
