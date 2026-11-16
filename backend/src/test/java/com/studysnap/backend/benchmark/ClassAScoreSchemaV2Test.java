package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassAScoreSchemaV2Test {
    static final String QUESTION = "What surrounds the cell?";
    static final String EXPLANATION = "The membrane surrounds the cell.";
    static final String SOURCE = "The cell membrane is a boundary—protecting the cell. It has $x^2$ layers and ‘special’ channels.";
    static final String QUOTE = "membrane surrounds the cell";
    static final String MAPPING_FILE = "src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json";
    static final ObjectMapper JSON = new ObjectMapper();
    static final EvidenceMapping MAPPING = EvidenceMapping.load(Path.of(MAPPING_FILE));

    static ClassAScoreSchemaV2.PacketQuestion packet(String format) {
        ObjectNode q = JSON.createObjectNode();
        q.put("question", QUESTION).put("explanation", EXPLANATION).put("questionFormat", format);
        q.putArray("choices").add("membrane").add("nucleus").add("cytoplasm");
        if ("MULTI_SELECT".equals(format)) q.putArray("correctIndices").add(0).add(1);
        else if (List.of("MCQ", "MATCHING", "ASSERTION_REASON").contains(format)) q.put("correctIndex", 0);
        else if ("ENUMERATION".equals(format)) q.putArray("acceptableAnswerGroups").addArray().add("membrane").add("cell wall");
        else q.putArray("acceptableAnswers").add("membrane").add("cell membrane");
        return new ClassAScoreSchemaV2.PacketQuestion(q, List.of(SOURCE));
    }
    static ObjectNode scorer(String id, String role) {
        ObjectNode row = JSON.createObjectNode();
        row.put("schemaVersion", 2).put("id", id).put("reviewerId", role + "_REVIEWER")
                .put("invocationId", role + "-call-1").put("scorerRole", role)
                .put("factuallyCorrect", "YES").put("grounding", "GROUNDED")
                .put("critical", false).putNull("criticalType");
        row.put("modeAlignment", 5).put("cognitiveDemand", 5).put("clarity", 5).put("faithfulness", 5)
                .put("distractorPlausibility", 5).put("seriousGiveaway", false).putNull("giveawayPattern");
        row.putArray("defensibleChoiceIds").add(0);
        row.put("defensibleAnswerCount", 1).put("keyDefensible", true).put("ambiguityClass", "NONE")
                .put("borderline", false).putNull("rationale").putNull("rationaleQuote")
                .putNull("rootCause").putNull("inputSpanQuote");
        return row;
    }
    static ObjectNode human(String id) {
        ObjectNode row = scorer(id, "P");
        for (String field : List.of("invocationId", "factuallyCorrect", "grounding", "critical",
                "modeAlignment", "cognitiveDemand", "clarity", "faithfulness", "distractorPlausibility",
                "seriousGiveaway", "giveawayPattern")) row.remove(field);
        row.put("scorerRole", "H").put("reviewerId", "OWNER").put("humanVerdict", "CLEAN").putNull("humanClearReason");
        return row;
    }
    static void defect(ObjectNode row, String type, int... ids) {
        row.putArray("defensibleChoiceIds");
        for (int id : ids) ((com.fasterxml.jackson.databind.node.ArrayNode) row.get("defensibleChoiceIds")).add(id);
        row.put("defensibleAnswerCount", ids.length).put("keyDefensible", java.util.Arrays.stream(ids).anyMatch(id -> id == 0));
        row.put("critical", true).put("criticalType", type).put("ambiguityClass",
                ids.length == 0 || ids.length > 1 ? "MATERIAL" : "NONE");
        evidence(row);
    }
    static void evidence(ObjectNode row) {
        row.put("rationale", "The answer needs review").put("rationaleQuote", QUOTE)
                .put("rootCause", "GENERATOR").putNull("inputSpanQuote");
    }
    static void valid(ObjectNode row, ClassAScoreSchemaV2.PacketQuestion packet) {
        ClassAScoreSchemaV2.validate(row, packet, MAPPING);
    }
    static void invalid(ObjectNode row, ClassAScoreSchemaV2.PacketQuestion packet, ClassAScoreSchemaV2.Code code) {
        assertThatThrownBy(() -> ClassAScoreSchemaV2.validate(row, packet, MAPPING))
                .isInstanceOf(ClassAScoreSchemaV2.InvalidRow.class)
                .satisfies(error -> assertThat(((ClassAScoreSchemaV2.InvalidRow) error).code()).isEqualTo(code));
    }

    @Test void cleanMcqAndV1Boundaries() {
        ObjectNode row = scorer("one", "P");
        valid(row, packet("MCQ"));
        assertThat(ClassAScoreSchemaV2.verdict(row, MAPPING)).isEqualTo(ClassAScoreSchemaV2.Verdict.CLEAN);
        ObjectNode v1 = row.deepCopy();
        v1.remove(List.of("schemaVersion", "invocationId", "scorerRole", "defensibleChoiceIds", "defensibleAnswerCount",
                "keyDefensible", "ambiguityClass", "borderline", "rationale", "rationaleQuote", "rootCause", "inputSpanQuote"));
        v1.put("round", "PRIMARY");
        invalid(v1, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        assertThatThrownBy(() -> QualityScoreFiles.validate(row, true)).isInstanceOf(IllegalArgumentException.class);
        v1.put("critical", true).put("criticalType", "KEY_NOT_DEFENSIBLE");
        assertThatThrownBy(() -> QualityScoreFiles.validate(v1, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void everyRequiredScorerAndHumanFieldIsExact() {
        ObjectNode scorer = scorer("one", "P");
        ObjectNode human = human("one");
        for (ObjectNode template : List.of(scorer, human)) {
            List<String> fields = new java.util.ArrayList<>();
            template.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                ObjectNode absent = template.deepCopy();
                absent.remove(field);
                invalid(absent, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
            }
            ObjectNode extra = template.deepCopy();
            extra.put("unknown", 1);
            invalid(extra, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        }
        human.put("modeAlignment", 5);
        invalid(human, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        human.remove("modeAlignment");
        human.put("invocationId", "call");
        invalid(human, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
    }

    @Test void duplicateJsonKeysAreRejected() {
        assertThatThrownBy(() -> ClassAScoreSchemaV2.parse("{\"id\":\"one\",\"id\":\"two\"}"))
                .isInstanceOf(ClassAScoreSchemaV2.InvalidRow.class)
                .satisfies(error -> assertThat(((ClassAScoreSchemaV2.InvalidRow) error).code())
                        .isEqualTo(ClassAScoreSchemaV2.Code.SCHEMA));
        assertThatThrownBy(() -> ClassAScoreSchemaV2.parse("{}{}"))
                .isInstanceOf(ClassAScoreSchemaV2.InvalidRow.class);
    }

    @Test void zeroDefensibleChoicesRequireKeyNotDefensibleAndMaterial() {
        ObjectNode row = scorer("one", "P");
        ObjectNode falselyClean = row.deepCopy();
        falselyClean.putArray("defensibleChoiceIds");
        falselyClean.put("defensibleAnswerCount", 0).put("keyDefensible", false);
        invalid(falselyClean, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        defect(row, "KEY_NOT_DEFENSIBLE");
        valid(row, packet("MCQ"));
        assertThat(ClassAScoreSchemaV2.verdict(row, MAPPING)).isEqualTo(ClassAScoreSchemaV2.Verdict.CRITICAL);
        row.put("critical", false).putNull("criticalType");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("critical", true).put("criticalType", "WRONG_KEY");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("criticalType", "KEY_NOT_DEFENSIBLE").put("ambiguityClass", "NONE");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("ambiguityClass", "MATERIAL").put("keyDefensible", true);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("keyDefensible", false).putArray("defensibleChoiceIds").add(1);
        row.put("defensibleAnswerCount", 1);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void wrongKeyAndSecondAnswerFollowSetStructure() {
        ObjectNode row = scorer("one", "P");
        defect(row, "WRONG_KEY", 1);
        valid(row, packet("MCQ"));
        assertThat(row.path("ambiguityClass").asText()).isEqualTo("NONE");
        defect(row, "WRONG_KEY", 1, 2);
        valid(row, packet("MCQ"));
        defect(row, "SECOND_DEFENSIBLE_ANSWER", 0, 1);
        valid(row, packet("MCQ"));
        row.put("ambiguityClass", "NONE");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("ambiguityClass", "MATERIAL").putArray("defensibleChoiceIds").add(0);
        row.put("defensibleAnswerCount", 1);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("ambiguityClass", "NONE");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void minorWordingAndAmbiguousStemBoundaries() {
        ObjectNode row = scorer("one", "P");
        row.put("ambiguityClass", "MINOR_WORDING"); evidence(row);
        valid(row, packet("MCQ"));
        assertThat(ClassAScoreSchemaV2.verdict(row, MAPPING)).isEqualTo(ClassAScoreSchemaV2.Verdict.QUALITY_FAIL);
        row.put("critical", true).put("criticalType", "AMBIGUOUS_STEM");
        valid(row, packet("MCQ"));
        row.put("ambiguityClass", "NONE");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("ambiguityClass", "MATERIAL");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        defect(row, "SECOND_DEFENSIBLE_ANSWER", 0, 1);
        row.put("ambiguityClass", "MINOR_WORDING");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void multiSelectAndSingleIndexChoiceFormatsUseRawKeys() {
        ObjectNode row = scorer("one", "P");
        row.putArray("defensibleChoiceIds").add(0).add(1);
        row.put("defensibleAnswerCount", 2);
        valid(row, packet("MULTI_SELECT"));
        row.putArray("defensibleChoiceIds").add(0).add(1).add(2);
        row.put("defensibleAnswerCount", 3).put("critical", true).put("criticalType", "SECOND_DEFENSIBLE_ANSWER")
                .put("ambiguityClass", "MATERIAL"); evidence(row);
        valid(row, packet("MULTI_SELECT"));
        row.put("ambiguityClass", "NONE");
        invalid(row, packet("MULTI_SELECT"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("ambiguityClass", "MATERIAL");
        row.putArray("defensibleChoiceIds").add(0).add(2);
        row.put("defensibleAnswerCount", 2).put("keyDefensible", false).put("criticalType", "WRONG_KEY");
        valid(row, packet("MULTI_SELECT"));
        for (String format : List.of("MATCHING", "ASSERTION_REASON")) {
            ObjectNode single = scorer("one", "P");
            valid(single, packet(format));
        }
        ObjectNode badPacket = packet("MATCHING").question().deepCopy();
        badPacket.remove("correctIndex");
        invalid(scorer("one", "P"), new ClassAScoreSchemaV2.PacketQuestion(badPacket, List.of(SOURCE)),
                ClassAScoreSchemaV2.Code.PACKET);
        badPacket.put("correctIndex", 4);
        invalid(scorer("one", "P"), new ClassAScoreSchemaV2.PacketQuestion(badPacket, List.of(SOURCE)),
                ClassAScoreSchemaV2.Code.PACKET);
    }

    @Test void choiceIdsAndCountsRejectInvalidValues() {
        ObjectNode row = scorer("one", "P");
        row.putArray("defensibleChoiceIds").add(3);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        row.putArray("defensibleChoiceIds").add(0).add(0);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        row.putArray("defensibleChoiceIds").add(0.5);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        row.putArray("defensibleChoiceIds").add(0);
        row.put("defensibleAnswerCount", 2);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("defensibleAnswerCount", 1).put("keyDefensible", false);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void keyDefensibleMustMatchKeyedSetEvenWhenOtherFactsAgree() {
        ObjectNode row = scorer("one", "P");
        row.put("keyDefensible", false).put("critical", true).put("criticalType", "WRONG_KEY");
        evidence(row);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void nonChoiceCountsCompleteAnswersRatherThanVariantsOrEnumerationItems() {
        for (String format : List.of("ENUMERATION", "IDENTIFICATION")) {
            ObjectNode row = scorer("one", "P");
            row.putNull("defensibleChoiceIds").putNull("distractorPlausibility")
                    .putNull("seriousGiveaway").putNull("giveawayPattern");
            valid(row, packet(format));
            row.put("defensibleAnswerCount", 2).put("critical", true)
                    .put("criticalType", "SECOND_DEFENSIBLE_ANSWER").put("ambiguityClass", "MATERIAL"); evidence(row);
            valid(row, packet(format));
            row.put("defensibleAnswerCount", 0).put("criticalType", "KEY_NOT_DEFENSIBLE")
                    .put("keyDefensible", true);
            invalid(row, packet(format), ClassAScoreSchemaV2.Code.STRUCTURE);
            row.put("defensibleAnswerCount", 1).put("keyDefensible", true).putNull("criticalType")
                    .put("critical", false).put("ambiguityClass", "NONE");
            row.putArray("defensibleChoiceIds");
            invalid(row, packet(format), ClassAScoreSchemaV2.Code.SCHEMA);
        }
    }

    @Test void rationaleAndQuotesAreVerifiedWithNormalization() {
        ObjectNode row = scorer("one", "P");
        defect(row, "KEY_NOT_DEFENSIBLE");
        row.putNull("rationale");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        row.put("rationale", "Wrong answer").put("rationaleQuote", "absent evidence span");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.QUOTE_NOT_FOUND);
        for (String quote : List.of("boundary-protecting", "'special' channels", "x^2 layers", "THE CELL MEMBRANE",
                "cell membrane...protecting the cell")) {
            row.put("rationaleQuote", quote);
            valid(row, packet("MCQ"));
        }
        row.put("rationaleQuote", "cell...");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.QUOTE_NOT_FOUND);
    }

    @Test void inputRootCauseRequiresSourceQuoteAndGeneratorForbidsIt() {
        ObjectNode row = scorer("one", "P");
        defect(row, "KEY_NOT_DEFENSIBLE");
        row.put("rootCause", "INPUT");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
        row.put("inputSpanQuote", QUESTION);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.QUOTE_NOT_FOUND);
        row.put("inputSpanQuote", "boundary-protecting the cell");
        valid(row, packet("MCQ"));
        row.put("rootCause", "GENERATOR");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
    }

    @Test void cleanScorerCannotCarryRationale() {
        ObjectNode row = scorer("one", "P");
        evidence(row);
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
    }

    @Test void humanVerdictsAndClearReasonRespectStructure() {
        ObjectNode row = human("one");
        valid(row, packet("MCQ"));
        row.put("humanClearReason", "REVIEWED_NOT_DEFECT"); evidence(row);
        valid(row, packet("MCQ"));
        row.put("humanVerdict", "QUALITY_FAIL");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.putNull("humanClearReason").put("criticalType", "WRONG_KEY");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("humanVerdict", "CRITICAL");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.put("criticalType", "KEY_NOT_DEFENSIBLE").putArray("defensibleChoiceIds");
        row.put("defensibleAnswerCount", 0).put("keyDefensible", false).put("ambiguityClass", "MATERIAL");
        valid(row, packet("MCQ"));
        row.put("criticalType", "WRONG_KEY");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.STRUCTURE);
        row.putNull("criticalType").put("humanVerdict", "UNRESOLVED");
        valid(row, packet("MCQ"));
        row.putNull("rationale");
        invalid(row, packet("MCQ"), ClassAScoreSchemaV2.Code.SCHEMA);
    }
}
