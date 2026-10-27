package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityEvaluationTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String HASH_A = "a".repeat(64), HASH_B = "b".repeat(64);

    @Test void qualityPreflightRejectsEveryBadLineBeforeARequest() throws Exception {
        Path input = temp.resolve("input.jsonl");
        List<ObjectNode> lines = inputLines();
        write(input, lines);
        String oldExpected = System.getProperty("llm.benchmark.expectedNotes");
        String oldPrompt = System.getProperty("llm.benchmark.challengePromptFile");
        try {
            System.setProperty("llm.benchmark.expectedNotes","20");
            System.clearProperty("llm.benchmark.challengePromptFile");
            assertThat(LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2")).isEqualTo("COLLEGE");
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S10"),"A2"))
                    .hasMessageContaining("cells=S15");
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),""))
                    .hasMessageContaining("arm");
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"B2"))
                    .hasMessageContaining("challengePromptFile");
            System.setProperty("llm.benchmark.challengePromptFile",temp.resolve("missing.txt").toString());
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("missing");
            System.clearProperty("llm.benchmark.challengePromptFile");
            lines.get(19).remove("officialOwner"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("officialOwner");
            lines.get(19).put("officialOwner",true);
            lines.get(19).remove("noteLevel"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("noteLevel");
            lines.get(19).put("noteLevel","BOARD_EXAM_REVIEW");
            lines.get(19).put("effectiveLevel","COLLEGE"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("matching noteLevel");
            lines.get(19).put("effectiveLevel","BOARD_EXAM_REVIEW");
            lines.get(19).put("noteLevel","COLLEGE");
            ((ObjectNode)lines.get(19).path("sources").get(0)).put("noteLearnerLevel","COLLEGE"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("matching noteLevel");
            lines.get(19).put("noteLevel","BOARD_EXAM_REVIEW");
            ((ObjectNode)lines.get(19).path("sources").get(0)).put("noteLearnerLevel","BOARD_EXAM_REVIEW");
            lines.get(19).put("effectiveLevel","BOARD_EXAM_REVIEW");
            lines.get(9).put("noteLevel","BOARD_EXAM_REVIEW");
            lines.get(9).put("effectiveLevel","BOARD_EXAM_REVIEW");
            ((ObjectNode)lines.get(9).path("sources").get(0)).put("noteLearnerLevel","BOARD_EXAM_REVIEW"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("exactly 10");
            lines.get(9).put("noteLevel","COLLEGE");
            lines.get(9).put("effectiveLevel","COLLEGE");
            ((ObjectNode)lines.get(9).path("sources").get(0)).put("noteLearnerLevel","COLLEGE");
            lines.get(19).put("cell","S10"); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("cell=S15");
            lines.removeLast(); write(input,lines);
            assertThatThrownBy(() -> LlmModelBenchmark.qualityPreflight(config(input),mapper,filter("S15"),"A2"))
                    .hasMessageContaining("20 lines");
        } finally { restore("llm.benchmark.expectedNotes",oldExpected); restore("llm.benchmark.challengePromptFile",oldPrompt); }
    }

    @Test void generationCountLogParserReadsNonzeroValidatorCounts() {
        String log = "action=challenge_quiz_generation_counts accepted=13 rejected=2 retried=1 outcome=returned";
        assertThat(LlmModelBenchmark.loggedCount(log,"accepted")).isEqualTo(13);
        assertThat(LlmModelBenchmark.loggedCount(log,"rejected")).isEqualTo(2);
        assertThat(LlmModelBenchmark.loggedCount(log,"retried")).isEqualTo(1);
    }

    @Test void wrongLevelSplitStopsTheRunBeforeHttp() throws Exception {
        Path input = temp.resolve("wrong-level-input.jsonl");
        List<ObjectNode> lines = inputLines();
        lines.get(0).put("noteLevel","BOARD_EXAM_REVIEW");
        lines.get(0).put("effectiveLevel","BOARD_EXAM_REVIEW");
        ((ObjectNode)lines.get(0).path("sources").get(0)).put("noteLearnerLevel","BOARD_EXAM_REVIEW");
        write(input,lines);
        String[] names = {"llm.benchmark.expectedNotes","llm.benchmark.cells","llm.benchmark.qualityEval","llm.benchmark.arm"};
        java.util.Map<String,String> old = new java.util.HashMap<>();
        for (String name : names) old.put(name,System.getProperty(name));
        try {
            System.setProperty("llm.benchmark.expectedNotes","20"); System.setProperty("llm.benchmark.cells","S15");
            System.setProperty("llm.benchmark.qualityEval","true"); System.setProperty("llm.benchmark.arm","A2");
            var cfg = new LlmModelBenchmark.Config("gpt-6-luna",input,temp.resolve("wrong-level-out"),
                    EnumSet.of(LlmModelBenchmark.Leg.E),1,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,"fake-key");
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            assertThatThrownBy(() -> LlmModelBenchmark.run(cfg,(uri,method) -> {
                calls.incrementAndGet(); throw new AssertionError("HTTP must not be called");
            })).hasMessageContaining("exactly 10 COLLEGE and 10 BOARD_EXAM_REVIEW");
            assertThat(calls.get()).isZero();
        } finally { for (String name : names) restore(name,old.get(name)); }
    }

    @Test void outgoingRequestsUseTheIntendedArmAndNotesOwnLevel() throws Exception {
        Path input = temp.resolve("prompt-input.jsonl"); write(input,inputLines());
        String[] names = {"llm.benchmark.expectedNotes","llm.benchmark.cells","llm.benchmark.runs",
                "llm.benchmark.qualityEval","llm.benchmark.arm","llm.benchmark.challengePromptFile"};
        java.util.Map<String,String> old = new java.util.HashMap<>();
        for (String name : names) old.put(name,System.getProperty(name));
        try {
            System.setProperty("llm.benchmark.expectedNotes","20"); System.setProperty("llm.benchmark.cells","S15");
            System.setProperty("llm.benchmark.qualityEval","true");
            String proposed = "src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt";
            List<String> insertedPrefixes = List.of(
                    "- Write every incorrect choice as a plausible answer",
                    "- Each incorrect choice must be clearly wrong",
                    "- Do not use absurd, unrelated, joke, or obviously false choices",
                    "- For standard MCQ and MULTI_SELECT questions",
                    "- No two choices may mean the same thing");
            for (int run : new int[]{1,11}) {
                JsonNode a = capturedRequest(input,run,"A2",null);
                JsonNode b = capturedRequest(input,run,"B2",proposed);
                JsonNode aMessages = a.path("input"), bMessages = b.path("input");
                assertThat(aMessages.size()).isEqualTo(bMessages.size());
                String developerA = "", developerB = "";
                for (int i=0; i<aMessages.size(); i++) {
                    String role = aMessages.get(i).path("role").asText();
                    assertThat(bMessages.get(i).path("role").asText()).isEqualTo(role);
                    if ("developer".equals(role)) {
                        developerA = aMessages.get(i).toString(); developerB = bMessages.get(i).toString();
                    } else if (Set.of("system","user").contains(role))
                        assertThat(aMessages.get(i).toString()).isEqualTo(bMessages.get(i).toString());
                }
                assertThat(developerA).contains("Difficulty target: hard").doesNotContain("Cognitive demand:");
                assertThat(developerB).contains("Cognitive demand:").doesNotContain("Difficulty target");
                for (String prefix : insertedPrefixes) assertThat(developerB).contains(prefix.substring(0,40));
                String boardGuidance = "Use board-exam style questions with situational framing";
                if (run == 11) assertThat(developerA).contains(boardGuidance,"plausible distractors");
                else assertThat(developerA).doesNotContain(boardGuidance);
                if (run == 11) assertThat(developerB).contains(boardGuidance);
                else assertThat(developerB).doesNotContain(boardGuidance);
            }
        } finally { for (String name : names) restore(name,old.get(name)); }
    }

    private JsonNode capturedRequest(Path input, int run, String arm, String prompt) throws Exception {
        System.setProperty("llm.benchmark.runs",String.valueOf(run)); System.setProperty("llm.benchmark.arm",arm);
        if (prompt == null) System.clearProperty("llm.benchmark.challengePromptFile");
        else System.setProperty("llm.benchmark.challengePromptFile",prompt);
        ObjectNode payload = mapper.createObjectNode(); var questions = payload.putArray("questions");
        for (int i=0;i<15;i++) {
            ObjectNode q = questions.addObject(); q.put("question","Question " + i + "?");
            q.putArray("choices").add("First").add("Second").add("Third").add("Fourth");
            q.put("answer","A"); q.put("explanation","First is supported by the note."); q.put("concept","Concept");
        }
        ObjectNode response = mapper.createObjectNode(); response.put("output_text",mapper.writeValueAsString(payload));
        response.putObject("usage").put("input_tokens",100).put("output_tokens",100);
        List<MockClientHttpRequest> requests = new ArrayList<>();
        var factory = (org.springframework.http.client.ClientHttpRequestFactory)(uri,method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method,uri);
            request.setResponse(new MockClientHttpResponse(mapper.writeValueAsBytes(response),200));
            requests.add(request); return request;
        };
        Path out = temp.resolve("request-" + arm + "-" + run);
        var cfg = new LlmModelBenchmark.Config("gpt-6-luna",input,out,EnumSet.of(LlmModelBenchmark.Leg.E),
                10,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,"fake-key");
        LlmModelBenchmark.run(cfg,factory);
        assertThat(requests).hasSize(1);
        return mapper.readTree(requests.getFirst().getBodyAsBytes());
    }

    @Test void successfulStartCapturesInfoTelemetryAndMissingEventStaysMissing() throws Exception {
        Path input = temp.resolve("telemetry-input.jsonl"); write(input,inputLines());
        String[] names = {"llm.benchmark.expectedNotes","llm.benchmark.cells","llm.benchmark.runs",
                "llm.benchmark.qualityEval","llm.benchmark.arm","llm.benchmark.challengePromptFile"};
        java.util.Map<String,String> old = new java.util.HashMap<>();
        for (String name : names) old.put(name,System.getProperty(name));
        var logger = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(
                com.studysnap.backend.service.impl.OpenAiLlmStudyPackService.class);
        var oldLevel = logger.getLevel();
        try {
            System.setProperty("llm.benchmark.expectedNotes","20"); System.setProperty("llm.benchmark.cells","S15");
            System.setProperty("llm.benchmark.qualityEval","true");
            capturedRequest(input,1,"A2",null);
            JsonNode captured = mapper.readTree(Files.readAllLines(temp.resolve("request-A2-1/results.jsonl")).getFirst());
            assertThat(captured.path("success").asBoolean()).isTrue();
            assertThat(captured.path("studyPackSummary").asText()).isEqualTo("Summary");
            assertThat(captured.path("keyConcepts").size()).isEqualTo(2);
            assertThat(captured.path("validatorTelemetry").asText()).isEqualTo("CAPTURED");
            assertThat(captured.path("validatorAccepted").isInt()).isTrue();
            logger.setLevel(ch.qos.logback.classic.Level.OFF);
            capturedRequest(input,11,"A2",null);
            JsonNode missing = mapper.readTree(Files.readAllLines(temp.resolve("request-A2-11/results.jsonl")).getFirst());
            assertThat(missing.path("success").asBoolean()).isTrue();
            assertThat(missing.path("validatorTelemetry").asText()).isEqualTo("MISSING");
            assertThat(missing.path("validatorAccepted").isNull()).isTrue();
            assertThat(missing.path("validatorRejected").isNull()).isTrue();
            assertThat(missing.path("validatorRetried").isNull()).isTrue();
            List<String> summary = Files.readAllLines(temp.resolve("request-A2-11/summary.csv"));
            assertThat(summary.getFirst()).contains("telemetryMissingStarts");
            assertThat(summary.stream().filter(s -> s.contains(",S15,")).findFirst().orElseThrow()).endsWith(",1");
            Path report = temp.resolve("missing-report");
            QualityDescriptors.report(List.of(temp.resolve("request-A2-11")),report);
            assertThat(Files.readString(report.resolve("descriptors.md"))).contains("G7 validator-rejection: MISSING for 1 starts");
        } finally {
            logger.setLevel(oldLevel);
            for (String name : names) restore(name,old.get(name));
        }
    }

    @Test void productionAndProposedHashesAreRecordedOnFailedStartsWithoutNetwork() throws Exception {
        Path input = temp.resolve("input.jsonl"); write(input,inputLines());
        String expected = System.getProperty("llm.benchmark.expectedNotes"), cells = System.getProperty("llm.benchmark.cells");
        String runs = System.getProperty("llm.benchmark.runs"), quality = System.getProperty("llm.benchmark.qualityEval");
        String arm = System.getProperty("llm.benchmark.arm"), prompt = System.getProperty("llm.benchmark.challengePromptFile");
        try {
            System.setProperty("llm.benchmark.expectedNotes","20"); System.setProperty("llm.benchmark.cells","S15");
            System.setProperty("llm.benchmark.runs","1"); System.setProperty("llm.benchmark.qualityEval","true");
            Path proposed = Path.of("src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt");
            String[] labels = {"A2","B2"};
            String[] hashes = new String[2];
            for (int i=0; i<2; i++) {
                System.setProperty("llm.benchmark.arm",labels[i]);
                if (i == 0) System.clearProperty("llm.benchmark.challengePromptFile");
                else System.setProperty("llm.benchmark.challengePromptFile",proposed.toString());
                Path out = temp.resolve(labels[i]);
                var cfg = new LlmModelBenchmark.Config("gpt-6-luna",input,out,EnumSet.of(LlmModelBenchmark.Leg.E),
                        2,new BigDecimal("0.04"),BigDecimal.ONE,BigDecimal.ONE,"fake-key");
                var noNetwork = (org.springframework.http.client.ClientHttpRequestFactory)(uri,method) -> {
                    throw new java.net.ConnectException("offline fixture");
                };
                LlmModelBenchmark.run(cfg,noNetwork);
                JsonNode row = mapper.readTree(Files.readAllLines(out.resolve("results.jsonl")).getFirst());
                assertThat(row.path("success").asBoolean()).isFalse();
                assertThat(row.path("arm").asText()).isEqualTo(labels[i]);
                assertThat(row.path("promptSha256").asText()).hasSize(64);
                assertThat(row.path("noteLevel").asText()).isEqualTo("COLLEGE");
                assertThat(row.path("validatorRejected").isInt()).isTrue();
                assertThat(Files.readString(out.resolve("summary.csv"))).contains(labels[i],"COLLEGE",row.path("promptSha256").asText());
                hashes[i] = row.path("promptSha256").asText();
            }
            assertThat(hashes[0]).isNotEqualTo(hashes[1]);
            assertThat(hashes[0]).isEqualTo(LlmModelBenchmark.sha256(LlmModelBenchmark.challengeResources(mapper,null).challengeQuizDeveloperPromptTemplate()));
        } finally {
            restore("llm.benchmark.expectedNotes",expected); restore("llm.benchmark.cells",cells);
            restore("llm.benchmark.runs",runs); restore("llm.benchmark.qualityEval",quality);
            restore("llm.benchmark.arm",arm); restore("llm.benchmark.challengePromptFile",prompt);
        }
    }

    @Test void qualityRecordsEverySpendStoppedStartWithoutRetryingOrCallingNetworkAgain() throws Exception {
        Path input = temp.resolve("input.jsonl"); write(input,inputLines());
        String expected = System.getProperty("llm.benchmark.expectedNotes"), cells = System.getProperty("llm.benchmark.cells");
        String runs = System.getProperty("llm.benchmark.runs"), quality = System.getProperty("llm.benchmark.qualityEval");
        String arm = System.getProperty("llm.benchmark.arm"), prompt = System.getProperty("llm.benchmark.challengePromptFile");
        try {
            System.setProperty("llm.benchmark.expectedNotes","20"); System.setProperty("llm.benchmark.cells","S15");
            System.setProperty("llm.benchmark.runs","1-20"); System.setProperty("llm.benchmark.qualityEval","true");
            System.setProperty("llm.benchmark.arm","A2"); System.clearProperty("llm.benchmark.challengePromptFile");
            Path out = temp.resolve("stopped");
            var cfg = new LlmModelBenchmark.Config("gpt-6-luna",input,out,EnumSet.of(LlmModelBenchmark.Leg.E),
                    1,new BigDecimal("0.04"),BigDecimal.ONE,BigDecimal.ONE,"fake-key");
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            var noNetwork = (org.springframework.http.client.ClientHttpRequestFactory)(uri,method) -> {
                calls.incrementAndGet(); throw new java.net.ConnectException("offline fixture");
            };
            LlmModelBenchmark.run(cfg,noNetwork);
            List<String> results = Files.readAllLines(out.resolve("results.jsonl"));
            assertThat(results).hasSize(20);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(mapper.readTree(results.getFirst()).path("success").asBoolean()).isFalse();
            for (String line : results.subList(1,results.size())) {
                JsonNode row = mapper.readTree(line);
                assertThat(row.path("failureClass").asText()).isEqualTo("SPEND_STOP");
                assertThat(row.path("attempts").asInt()).isZero();
            }
        } finally {
            restore("llm.benchmark.expectedNotes",expected); restore("llm.benchmark.cells",cells);
            restore("llm.benchmark.runs",runs); restore("llm.benchmark.qualityEval",quality);
            restore("llm.benchmark.arm",arm); restore("llm.benchmark.challengePromptFile",prompt);
        }
    }

    @Test void blindExportHasOneShapeNoLeakAllItemsAndIsRepeatableAndResumable() throws Exception {
        Path a=temp.resolve("a"), b=temp.resolve("b"), stock=temp.resolve("stock.jsonl"), out=temp.resolve("blind");
        Files.createDirectories(a); Files.createDirectories(b);
        List<String> as = new ArrayList<>(), bs = new ArrayList<>(), stocks = new ArrayList<>();
        for (int i=1; i<=20; i++) {
            as.add(result("A2",HASH_A,i,true)); bs.add(result("B2",HASH_B,i,true));
            for (int position=1; position<=5; position++) {
                ObjectNode stockRow=mapper.createObjectNode(); stockRow.put("packId","pack-"+i);
                stockRow.put("generatedAt","2026-10-27T00:00:00Z");
                ObjectNode stored = question("Stock " + i + " item " + position);
                stored.remove("statementVerdicts");
                stored.put("questionType","STANDARD"); stored.put("workingSolution","");
                stored.putArray("acceptableAnswers"); stored.put("questionGroup","");
                stored.put("keyConcept","Concept"); stored.putArray("correctIndices");
                stored.put("concept","Concept"); stored.putArray("acceptableAnswerGroups");
                if (position % 2 == 0) stored.remove("sourceStudyPackId");
                assertThat(stored.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(
                        position % 2 == 0
                                ? List.of("questionFormat","questionType","choices","workingSolution","question",
                                        "acceptableAnswers","questionGroup","correctIndex","keyConcept","correctIndices",
                                        "explanation","concept","acceptableAnswerGroups")
                                : List.of("questionFormat","questionType","choices","workingSolution","question",
                                        "acceptableAnswers","questionGroup","correctIndex","keyConcept","correctIndices",
                                        "explanation","concept","acceptableAnswerGroups","sourceStudyPackId"));
                stockRow.set("question",stored);
                stocks.add(mapper.writeValueAsString(stockRow));
            }
        }
        Files.write(a.resolve("results.jsonl"),as); Files.write(b.resolve("results.jsonl"),bs); Files.write(stock,stocks);
        QualityBlindExport.export(List.of(a,b),stock,out,176,17);
        List<String> items=Files.readAllLines(out.resolve("blind-items-001.jsonl"));
        List<String> keys=Files.readAllLines(out.resolve("blind-key.jsonl"));
        JsonNode manifest=mapper.readTree(out.resolve("blind-manifest.json").toFile());
        assertThat(manifest.path("totalItems").asInt()).isEqualTo(140);
        assertThat(manifest.path("countsPerArm").path("A2").asInt()).isEqualTo(20);
        assertThat(manifest.path("countsPerArm").path("B2").asInt()).isEqualTo(20);
        assertThat(manifest.path("countsPerArm").path("S").asInt()).isEqualTo(100);
        assertThat(keys).hasSize(140);
        List<String> all = new ArrayList<>();
        for (JsonNode chunk : manifest.path("chunks")) all.addAll(Files.readAllLines(out.resolve(chunk.asText())));
        assertThat(all).hasSize(140);
        Set<String> ids = new java.util.HashSet<>();
        for (String item : all) {
            assertThat(item).doesNotMatch("(?i).*(gpt|luna|sol|mini|proposed|current|A2|B2|[a-f0-9]{64}).*");
            JsonNode node=mapper.readTree(item);
            assertThat(node.fieldNames()).toIterable().containsExactly("id","noteRef","question","choices","questionFormat","keyedAnswer","explanation");
            assertThat(node.has("noteLevel")).isFalse();
            assertThat(node.has("generatedAt")).isFalse();
            ids.add(node.path("id").asText());
        }
        assertThat(ids).hasSize(140);
        assertThat(keys.stream().map(k -> { try { return mapper.readTree(k).path("id").asText(); } catch(Exception e) { throw new RuntimeException(e); } })).containsExactlyInAnyOrderElementsOf(ids);
        List<JsonNode> keyRows = new ArrayList<>();
        for (String key : keys) keyRows.add(mapper.readTree(key));
        assertThat(keyRows.stream().filter(k -> "A2".equals(k.path("arm").asText())).allMatch(k ->
                "gpt-6-luna".equals(k.path("model").asText()) && HASH_A.equals(k.path("promptSha256").asText())
                        && "MCQ".equals(k.path("questionFormat").asText()) && k.path("positionInStart").asInt() == 1)).isTrue();
        assertThat(keyRows.stream().filter(k -> "B2".equals(k.path("arm").asText())).allMatch(k ->
                "gpt-6-luna".equals(k.path("model").asText()) && HASH_B.equals(k.path("promptSha256").asText()))).isTrue();
        assertThat(keyRows.stream().filter(k -> k.path("isStock").asBoolean()).count()).isEqualTo(100);
        assertThat(keyRows.stream().filter(k -> k.path("isStock").asBoolean()).allMatch(k ->
                "2026-10-27T00:00:00Z".equals(k.path("generatedAt").asText()))).isTrue();
        assertThat(keyRows.stream().allMatch(k -> Set.of("COLLEGE","BOARD_EXAM_REVIEW").contains(k.path("noteLevel").asText()))).isTrue();
        assertThat(Files.readAllLines(out.resolve("notes.jsonl"))).hasSize(20);
        for (String note : Files.readAllLines(out.resolve("notes.jsonl")))
            assertThat(mapper.readTree(note).path("noteLevel").asText()).isIn("COLLEGE","BOARD_EXAM_REVIEW");
        for (String note : Files.readAllLines(out.resolve("notes.jsonl"))) {
            JsonNode value = mapper.readTree(note);
            assertThat(value.path("studyPackSummary").asText()).isEqualTo("Summary");
            assertThat(value.path("keyConcepts").size()).isEqualTo(2);
            assertThat(value.path("sourceNoteText").asText()).startsWith("Official note ");
        }
        assertThat(keyRows.stream().map(k -> k.path("noteRef").asText()).distinct().count()).isEqualTo(20);
        QualityBlindExport.export(List.of(a,b),stock,out,176,17);
        assertThat(Files.readAllLines(out.resolve("blind-items-001.jsonl"))).isEqualTo(items);
        Files.writeString(out.resolve("blind-items-001.jsonl"),"resume-marker\n");
        QualityBlindExport.export(List.of(a,b),stock,out,176,17);
        assertThat(Files.readString(out.resolve("blind-items-001.jsonl"))).isEqualTo("resume-marker\n");
        bs.set(0,"{}"); Files.write(b.resolve("results.jsonl"),bs);
        assertThatThrownBy(() -> QualityBlindExport.export(List.of(a,b),stock,temp.resolve("invalid"),176,17))
                .hasMessageContaining("Missing arm");
    }

    @Test void descriptorsListMissingAndFailedPairSidesAndTelemetry() throws Exception {
        Path a=temp.resolve("a"), b=temp.resolve("b"), out=temp.resolve("report");
        Files.createDirectories(a); Files.createDirectories(b);
        List<String> as=new ArrayList<>(), bs=new ArrayList<>();
        for (int i=1;i<=20;i++) {
            as.add(result("A2",HASH_A,i,true));
            if (i<20) bs.add(result("B2",HASH_B,i,i!=19));
        }
        Files.write(a.resolve("results.jsonl"),as); Files.write(b.resolve("results.jsonl"),bs);
        QualityDescriptors.report(List.of(a,b),out);
        JsonNode report=mapper.readTree(out.resolve("descriptors.json").toFile());
        JsonNode pair=report.path("pairs").path("A2/B2");
        assertThat(pair.path("pairedNotes").asInt()).isEqualTo(18);
        assertThat(pair.path("pairedArms").path("A2").path("starts").asInt()).isEqualTo(18);
        assertThat(pair.path("pairedArms").path("B2").path("starts").asInt()).isEqualTo(18);
        assertThat(pair.path("missingOrFailedSides").size()).isEqualTo(2);
        assertThat(report.path("arms").path("B2").path("failedStarts").asInt()).isEqualTo(1);
        assertThat(report.path("arms").path("A2").path("g6").path("validatorRejected").asInt()).isEqualTo(20);
        assertThat(report.path("arms").path("B2").path("g7").path("validatorRejected").asInt()).isEqualTo(18);
        assertThat(report.path("arms").path("A2").path("g7").path("allFormatValidatorRetryRate").asDouble()).isEqualTo(.5);
        assertThat(report.path("arms").path("B2").path("g6").path("starts").asInt()).isEqualTo(18);
        assertThat(report.path("arms").path("A2").path("g7").path("formatMix").path("MCQ").asInt()).isEqualTo(20);
        assertThat(report.path("arms").path("A2").path("perNoteLevel").path("COLLEGE").path("starts").asInt()).isEqualTo(10);
        assertThat(report.path("arms").path("A2").path("perNoteLevel").path("BOARD_EXAM_REVIEW").path("starts").asInt()).isEqualTo(10);
        assertThat(report.path("pairs").path("A2/B2").path("perNoteLevel").path("COLLEGE").path("pairedNotes").asInt()).isEqualTo(10);
        assertThat(Files.readString(out.resolve("descriptors.md"))).contains("MISSING","FAILED","15 of 20");
        ObjectNode missing=(ObjectNode)mapper.readTree(as.getFirst());
        missing.remove("validatorAccepted"); missing.remove("validatorRejected"); missing.remove("validatorRetried");
        as.set(0,mapper.writeValueAsString(missing));
        Files.write(a.resolve("results.jsonl"),as);
        QualityDescriptors.report(List.of(a,b),out);
        report=mapper.readTree(out.resolve("descriptors.json").toFile());
        assertThat(report.path("arms").path("A2").path("g7").path("telemetryMissingStarts").asInt()).isEqualTo(1);
        assertThat(report.path("arms").path("A2").path("g7").path("validatorAccepted").asInt()).isEqualTo(19);
        assertThat(report.path("arms").path("A2").path("g7").path("allFormatValidatorRetryRate").asDouble()).isEqualTo(.5);
    }

    private LlmModelBenchmark.Config config(Path input) { return new LlmModelBenchmark.Config("test",input,temp,
            EnumSet.of(LlmModelBenchmark.Leg.E),1,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,"fake"); }
    private LlmModelBenchmark.EFilter filter(String cell) { return new LlmModelBenchmark.EFilter(Set.of(cell),1,20,java.util.Map.of()); }
    private List<ObjectNode> inputLines() {
        List<ObjectNode> result = new ArrayList<>();
        for (int i=1;i<=20;i++) {
            ObjectNode line=mapper.createObjectNode(); line.put("id","Q-"+i); line.put("leg","E");
            line.put("cell","S15"); line.put("run",i); line.put("packId","pack-"+i);
            line.put("officialOwner",true);
            String level = i <= 10 ? "COLLEGE" : "BOARD_EXAM_REVIEW";
            line.put("noteLevel",level); line.put("effectiveLevel",level);
            ObjectNode source=line.putArray("sources").addObject();
            source.put("studyPackTitle","Title"); source.put("studyPackSummary","Summary");
            source.put("sourceNoteText","Official note " + i);
            source.putArray("keyConcepts").add("one").add("two"); source.putArray("disallowedQuestions");
            source.putArray("tags"); source.put("learnerLevel","COLLEGE");
            source.put("noteLearnerLevel",level);
            result.add(line);
        }
        return result;
    }
    private void write(Path path,List<ObjectNode> rows) throws Exception {
        List<String> lines=new ArrayList<>(); for (ObjectNode row:rows) lines.add(mapper.writeValueAsString(row));
        Files.write(path,lines);
    }
    private ObjectNode question(String stem) {
        ObjectNode q=mapper.createObjectNode(); q.put("question",stem); q.putArray("choices").add("Choice one").add("Choice two");
        q.put("correctIndex",0); q.put("questionFormat","MCQ"); q.put("explanation","Because of the source.");
        q.putArray("statementVerdicts").add(true).add(false); q.put("sourceStudyPackId","hidden");
        return q;
    }
    private String result(String arm,String hash,int i,boolean success) throws Exception {
        ObjectNode row=mapper.createObjectNode(); row.put("arm",arm); row.put("model","gpt-6-luna");
        row.put("promptSha256",hash); row.put("packId","pack-"+i); row.put("sourceNoteText","Official note " + i);
        row.put("studyPackSummary","Summary"); row.putArray("keyConcepts").add("one").add("two");
        row.put("noteLevel",i <= 10 ? "COLLEGE" : "BOARD_EXAM_REVIEW");
        row.put("success",success); row.put("failureClass",success ? "SUCCESS" : "SPEND_STOP");
        row.put("attempts",1); row.put("latencyMs",100); row.put("inputTokens",100); row.put("outputTokens",50);
        row.put("validatorAccepted",success ? 1 : 0); row.put("validatorRejected",1); row.put("validatorRetried",1);
        if (success) row.putArray("questions").add(question("Question " + i)); else row.putArray("questions");
        return mapper.writeValueAsString(row);
    }
    private void restore(String name,String value) { if (value==null) System.clearProperty(name); else System.setProperty(name,value); }
}
