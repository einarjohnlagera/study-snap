package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizValidationUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in, sequential benchmark. No Spring application context or database. */
public class LlmModelBenchmark {
    enum Leg { A, B, C, D }
    record Config(String model, Path input, Path outDir, Set<Leg> legs, int maxCalls,
                  BigDecimal spendStop, BigDecimal inPrice, BigDecimal outPrice, String key) {
        static Config system() {
            String key = requireKeyForTest(System.getenv("LLM_API_KEY"));
            String model = required("llm.benchmark.model");
            Path input = Path.of(required("llm.benchmark.input")).toAbsolutePath().normalize();
            Path out = Path.of(required("llm.benchmark.outDir")).toAbsolutePath().normalize();
            String legsText = System.getProperty("llm.benchmark.legs", "A,B,C,D");
            Set<Leg> legs = EnumSet.noneOf(Leg.class);
            for (String name : legsText.split(",")) legs.add(Leg.valueOf(name.trim()));
            if (legs.isEmpty()) throw new IllegalArgumentException("llm.benchmark.legs is empty");
            int maxCalls = Integer.parseInt(System.getProperty("llm.benchmark.maxCalls", "300"));
            if (maxCalls < 1) throw new IllegalArgumentException("llm.benchmark.maxCalls must be positive");
            BigDecimal stop = checkedSpendStop(System.getProperty("llm.benchmark.spendStopUsd", "8.00"));
            BigDecimal in = new BigDecimal(required("llm.benchmark.priceInPerM"));
            BigDecimal outPrice = new BigDecimal(required("llm.benchmark.priceOutPerM"));
            if (in.signum() < 0 || outPrice.signum() < 0) throw new IllegalArgumentException("prices must be nonnegative");
            Path repo = findRepo(Path.of("").toAbsolutePath().normalize());
            rejectInsideRepo(input, repo);
            rejectInsideRepo(out, repo);
            if (!Files.isRegularFile(input)) throw new IllegalArgumentException("Input JSONL does not exist: " + input);
            return new Config(model, input, out, legs, maxCalls, stop, in, outPrice, key);
        }
        static BigDecimal checkedSpendStop(String value) {
            BigDecimal stop = new BigDecimal(value);
            if (stop.signum() <= 0 || stop.compareTo(BigDecimal.TEN) > 0)
                throw new IllegalArgumentException("llm.benchmark.spendStopUsd must be > 0 and <= 10.00");
            return stop;
        }
        static String requireKeyForTest(String key) {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("LLM_API_KEY is required");
            return key;
        }
        static void rejectInsideRepoForTest(Path path, Path repo) { rejectInsideRepo(path, repo); }
        private static String required(String name) {
            String value = System.getProperty(name);
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value;
        }
        private static Path findRepo(Path path) {
            for (Path p = path; p != null; p = p.getParent()) if (Files.exists(p.resolve(".git"))) return p;
            throw new IllegalArgumentException("Cannot locate git working tree");
        }
        private static void rejectInsideRepo(Path path, Path repo) {
            try {
                Path realRepo = repo.toRealPath();
                Path ancestor = path;
                while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
                Path candidate = ancestor.toRealPath().resolve(ancestor.relativize(path));
                if (candidate.startsWith(realRepo) && !candidate.startsWith(realRepo.resolve("backend/target")))
                    throw new IllegalArgumentException("Benchmark input/output inside repo must be under backend/target: " + path);
            } catch (IOException ex) { throw new IllegalArgumentException("Cannot validate path: " + path, ex); }
        }
    }

    @Test void benchmark() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark"), "opt-in benchmark skipped");
        run(Config.system(), null);
    }

    /** Package access lets offline tests inject a stub request factory. */
    static void run(Config config, org.springframework.http.client.ClientHttpRequestFactory requestFactory) throws Exception {
        Files.createDirectories(config.outDir());
        ObjectMapper mapper = new ObjectMapper();
        MeteringInterceptor meter = new MeteringInterceptor(mapper, config.outDir(), config.model(),
                config.inPrice(), config.outPrice(), config.spendStop(), config.maxCalls());
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLlm().getApi().setApiKey(config.key());
        properties.getSettings().setModelFree(config.model());
        properties.getSettings().setModelPremium(config.model());
        SimpleClientHttpRequestFactory productionFactory = new SimpleClientHttpRequestFactory();
        productionFactory.setConnectTimeout(java.time.Duration.ofSeconds(properties.getLlm().getApi().getConnectTimeoutSeconds()));
        productionFactory.setReadTimeout(java.time.Duration.ofSeconds(properties.getLlm().getApi().getReadTimeoutSeconds()));
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory == null ? productionFactory : requestFactory)
                .baseUrl(properties.getLlm().getApi().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.key())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(meter).build();
        OpenAiLlmConfig productionConfig = new OpenAiLlmConfig();
        OpenAiLlmStudyPackService service = new OpenAiLlmStudyPackService(properties, mapper, client,
                productionConfig.openAiPromptResources(mapper, properties));
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Integer> malformed = new HashMap<>();
        int consecutiveHttpErrors = 0;
        int serial = 0;
        try (BufferedReader reader = Files.newBufferedReader(config.input());
             BufferedWriter writer = Files.newBufferedWriter(config.outDir().resolve("results.jsonl"),
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) {
                serial++;
                JsonNode input;
                String id = "line-" + serial;
                Leg leg;
                try {
                    input = mapper.readTree(line);
                    id = needed(input, "id");
                    leg = Leg.valueOf(needed(input, "leg"));
                    validateInput(input, leg);
                } catch (Exception ex) {
                    System.err.println("Malformed benchmark input " + id + ": " + ex.getMessage());
                    String malformedLeg = "UNKNOWN";
                    try { malformedLeg = mapper.readTree(line).path("leg").asText("UNKNOWN"); } catch (Exception ignored) {}
                    malformed.merge(malformedLeg, 1, Integer::sum);
                    continue;
                }
                if (!config.legs().contains(leg)) continue;
                String generationId = "g" + serial + "-" + id.replaceAll("[^A-Za-z0-9_-]", "_");
                meter.begin(generationId, leg.name());
                int before = meter.calls().size();
                List<QuizItem> questions = List.of();
                String noteContent = null;
                Throwable failure = null;
                String method = method(input, leg);
                try {
                    StudyPackGenerationContext context = context(input);
                    switch (method) {
                        case "generateStudyPack" -> questions = service.generateStudyPack(needed(input, "noteText"), context).quiz();
                        case "generateNoteFromTopic" -> noteContent = service.generateNoteFromTopic(needed(input, "topic"), context);
                        case "generateChallengeQuiz" -> questions = service.generateChallengeQuiz(needed(input,"studyPackTitle"), needed(input,"studyPackSummary"), strings(input,"keyConcepts"), strings(input,"disallowedQuestions"), 5, needed(input,"difficulty"), context).quizItems();
                        case "generateBoardExamQuiz" -> questions = service.generateBoardExamQuiz(needed(input,"studyPackTitle"), needed(input,"studyPackSummary"), strings(input,"keyConcepts"), strings(input,"disallowedQuestions"), 12, needed(input,"difficulty"), context);
                        case "generateAdaptivePracticeQuiz" -> questions = service.generateAdaptivePracticeQuiz(needed(input,"studyPackTitle"), needed(input,"studyPackSummary"), strings(input,"keyConcepts"), strings(input,"weakConcepts"), strings(input,"disallowedQuestions"), 5, context);
                        case "generateLongExam" -> questions = service.generateLongExam(needed(input,"studyPackTitle"), needed(input,"studyPackSummary"), strings(input,"keyConcepts"), strings(input,"disallowedQuestions"), 20, needed(input,"difficulty"), context);
                        default -> throw new IllegalArgumentException("Unknown method: " + method);
                    }
                } catch (Throwable ex) { failure = ex; }
                List<MeteringInterceptor.Call> calls = meter.calls().subList(before, meter.calls().size());
                if (meter.unmetered() && failure == null) failure = new IllegalStateException("Successful response had no usable token counts");
                String classification = meter.stopped() ? "SPEND_STOP" : classify(failure, calls);
                if ("HTTP_ERROR".equals(classification)) consecutiveHttpErrors++; else consecutiveHttpErrors = 0;
                Map<String, Object> result = result(config.model(), leg, method, id, input, generationId,
                        classification, failure, calls, questions, noteContent);
                writer.write(mapper.writeValueAsString(result)); writer.newLine(); writer.flush();
                results.add(result);
                if ("SPEND_STOP".equals(classification) || meter.unmetered() || consecutiveHttpErrors >= 3) break;
            }
        } finally { writeSummary(config.outDir(), config.model(), results, meter.calls(), malformed); }
    }

    private static String needed(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.asText();
    }
    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) throw new IllegalArgumentException(field + " must be an array");
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) throw new IllegalArgumentException(field + " must contain strings");
            result.add(item.asText());
        }
        return result;
    }
    private static String method(JsonNode input, Leg leg) {
        return switch (leg) {
            case A, D -> "generateStudyPack";
            case C -> "generateNoteFromTopic";
            case B -> needed(input, "method");
        };
    }
    private static void validateInput(JsonNode input, Leg leg) {
        if (leg == Leg.D && !input.path("regression").asBoolean(false)) throw new IllegalArgumentException("D requires regression=true");
        if (leg == Leg.A || leg == Leg.D) needed(input, "noteText");
        if (leg == Leg.C) needed(input, "topic");
        if (leg == Leg.B) {
            String method = needed(input, "method");
            if (!Set.of("generateChallengeQuiz", "generateBoardExamQuiz", "generateAdaptivePracticeQuiz", "generateLongExam").contains(method)) throw new IllegalArgumentException("invalid B method");
            needed(input, "studyPackTitle"); needed(input, "studyPackSummary"); needed(input, "sourceNoteText"); strings(input, "keyConcepts"); strings(input, "disallowedQuestions");
            if (method.equals("generateAdaptivePracticeQuiz") && strings(input, "weakConcepts").isEmpty()) throw new IllegalArgumentException("weakConcepts must be nonempty");
            if (!method.equals("generateAdaptivePracticeQuiz")) needed(input, "difficulty");
        }
        strings(input, "tags");
        if (input.hasNonNull("learnerLevel")) LearnerLevel.valueOf(input.path("learnerLevel").asText());
        if (input.hasNonNull("noteLearnerLevel")) LearnerLevel.valueOf(input.path("noteLearnerLevel").asText());
        if (input.hasNonNull("domainContext")) DomainContext.valueOf(input.path("domainContext").asText());
    }
    private static StudyPackGenerationContext context(JsonNode input) {
        return new StudyPackGenerationContext(
                enumOrNull(input,"learnerLevel",LearnerLevel.class), textOrNull(input,"courseProgram"),
                textOrNull(input,"subject"), strings(input,"tags"),
                enumOrNull(input,"domainContext",DomainContext.class), enumOrNull(input,"noteLearnerLevel",LearnerLevel.class));
    }
    private static String textOrNull(JsonNode n, String field) { return n.path(field).isTextual() ? n.path(field).asText() : null; }
    private static <E extends Enum<E>> E enumOrNull(JsonNode n, String field, Class<E> type) { return n.hasNonNull(field) ? Enum.valueOf(type,n.path(field).asText()) : null; }
    private static String classify(Throwable failure, List<MeteringInterceptor.Call> calls) {
        if (failure != null && causedBy(failure, MeteringInterceptor.SpendStop.class)) return "SPEND_STOP";
        // status 0 = transport failure (no response received); counted as HTTP_ERROR, not EMPTY.
        if (calls.stream().anyMatch(c -> c.status() >= 400 || c.status() == 0)) return "HTTP_ERROR";
        if (calls.stream().anyMatch(c -> c.outputTextItems() > 1)) return "MULTI_OUTPUT_TEXT";
        if (failure instanceof AppException app && "LLM_EMPTY_RESPONSE".equals(app.getCode())) return "EMPTY";
        if (calls.isEmpty() || calls.stream().allMatch(c -> c.outputTextItems() == 0 && !c.topLevelOutputText())) return "EMPTY";
        if (failure instanceof AppException app && "LLM_INVALID_OUTPUT".equals(app.getCode())) return "SCHEMA_OR_PARSE";
        return failure == null ? "SUCCESS" : "OTHER";
    }
    private static boolean causedBy(Throwable error, Class<?> type) {
        for (Throwable e = error; e != null; e = e.getCause()) if (type.isInstance(e)) return true;
        return false;
    }
    private static Map<String,Object> result(String model, Leg leg, String method, String id, JsonNode input,
            String generationId, String classification, Throwable failure, List<MeteringInterceptor.Call> calls,
            List<QuizItem> questions, String noteContent) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("model",model); row.put("leg",leg.name()); row.put("method",method); row.put("inputId",id);
        row.put("generationId",generationId); row.put("sourceNoteText",
                textOrNull(input,"noteText") != null ? textOrNull(input,"noteText")
                : textOrNull(input,"sourceNoteText") != null ? textOrNull(input,"sourceNoteText") : textOrNull(input,"topic"));
        row.put("success", "SUCCESS".equals(classification)); row.put("failureClass", "SUCCESS".equals(classification) ? null : classification);
        row.put("exceptionClass",failure == null ? null : failure.getClass().getName());
        row.put("errorMessage", "HTTP_ERROR".equals(classification) ? calls.stream().filter(c -> c.status() >= 400 || c.status() == 0).map(MeteringInterceptor.Call::errorMessage).findFirst().orElse("") : failure == null ? null : failure.getMessage());
        row.put("attempts",calls.size()); row.put("latencyMs",calls.stream().mapToLong(MeteringInterceptor.Call::latencyMs).sum());
        row.put("inputTokens",calls.stream().mapToLong(MeteringInterceptor.Call::inputTokens).sum());
        row.put("outputTokens",calls.stream().mapToLong(MeteringInterceptor.Call::outputTokens).sum());
        row.put("reasoningTokens",calls.stream().mapToLong(MeteringInterceptor.Call::reasoningTokens).sum());
        row.put("conservativeUsd",calls.stream().map(MeteringInterceptor.Call::conservativeUsd).reduce(BigDecimal.ZERO,BigDecimal::add));
        row.put("nonAdditiveUsd",calls.stream().map(MeteringInterceptor.Call::nonAdditiveUsd).reduce(BigDecimal.ZERO,BigDecimal::add));
        row.put("calls",calls); row.put("questionCount",questions.size()); row.put("noteContent",noteContent);
        List<Map<String,Object>> checks = new ArrayList<>();
        for (QuizItem q : questions) {
            Map<String,Object> check = new LinkedHashMap<>();
            check.put("choiceCount",q.choices().size());
            check.put("choicesValid",!QuizValidationUtils.hasInvalidChoices(q.choices(),q.questionFormat()));
            check.put("correctIndexInBounds",q.correctIndex() != null && q.correctIndex() >= 0 && q.correctIndex() < q.choices().size());
            check.put("correctIndicesValid",!QuizValidationUtils.hasInvalidCorrectIndices(q.correctIndices(),q.choices(),q.questionFormat()));
            check.put("exactlyOneCorrect",q.isMultiSelect() || "IDENTIFICATION".equals(q.questionFormat()) || "ENUMERATION".equals(q.questionFormat())
                    ? null : (q.correctIndex() != null && q.correctIndex() >= 0 && q.correctIndex() < q.choices().size()));
            check.put("correctInvariantValid",q.isMultiSelect() ? !QuizValidationUtils.hasInvalidCorrectIndices(q.correctIndices(),q.choices(),q.questionFormat())
                    : "IDENTIFICATION".equals(q.questionFormat()) ? !q.acceptableAnswers().isEmpty()
                    : "ENUMERATION".equals(q.questionFormat()) ? !q.acceptableAnswerGroups().isEmpty()
                    : (q.correctIndex() != null && q.correctIndex() >= 0 && q.correctIndex() < q.choices().size()));
            check.put("h4",QuizValidationUtils.isAnswerExplanationInternallyInconsistent(q.choices(),q.correctIndex(),q.questionFormat(),q.explanation(),q.workingSolution()));
            checks.add(check);
        }
        row.put("structuralChecks",checks); row.put("questions",questions);
        return row;
    }
    private static void writeSummary(Path outDir, String model, List<Map<String,Object>> rows,
                                     List<MeteringInterceptor.Call> calls, Map<String,Integer> malformed) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outDir.resolve("summary.csv"))) {
            writer.write("model,leg,calls,generations,successRate,HTTP_ERROR,EMPTY,SCHEMA_OR_PARSE,MULTI_OUTPUT_TEXT,SPEND_STOP,OTHER,meanLatencyMs,p50LatencyMs,p95LatencyMs,meanAttempts,h4FlagRate,meanInputTokens,meanOutputTokens,meanReasoningTokens,conservativeUsd,nonAdditiveUsd,malformedInputs\n");
            for (Leg leg : Leg.values()) {
                List<Map<String,Object>> selected = rows.stream().filter(r -> leg.name().equals(r.get("leg"))).toList();
                List<MeteringInterceptor.Call> legCalls = calls.stream().filter(c -> leg.name().equals(c.leg())).toList();
                int n = selected.size();
                List<Long> latency = selected.stream().map(r -> ((Number)r.get("latencyMs")).longValue()).sorted().toList();
                long questions = selected.stream().mapToLong(r -> ((List<?>)r.get("structuralChecks")).size()).sum();
                long h4 = selected.stream().flatMap(r -> ((List<Map<String,Object>>)r.get("structuralChecks")).stream()).filter(c -> Boolean.TRUE.equals(c.get("h4"))).count();
                List<String> cells = new ArrayList<>(List.of(model,leg.name(),String.valueOf(legCalls.size()),String.valueOf(n),
                        ratio(selected.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count(),n)));
                for (String failure : List.of("HTTP_ERROR","EMPTY","SCHEMA_OR_PARSE","MULTI_OUTPUT_TEXT","SPEND_STOP","OTHER"))
                    cells.add(String.valueOf(selected.stream().filter(r -> failure.equals(r.get("failureClass"))).count()));
                cells.addAll(List.of(mean(selected,"latencyMs"),percentile(latency,.5),percentile(latency,.95),mean(selected,"attempts"),ratio(h4,questions),
                        mean(selected,"inputTokens"),mean(selected,"outputTokens"),mean(selected,"reasoningTokens"),
                        sumCost(selected,"conservativeUsd"),sumCost(selected,"nonAdditiveUsd"),String.valueOf(malformed.getOrDefault(leg.name(),0))));
                writer.write(String.join(",",cells)); writer.newLine();
            }
            if (malformed.containsKey("UNKNOWN")) writer.write(model + ",UNKNOWN,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0," + malformed.get("UNKNOWN") + "\n");
        }
    }
    private static String ratio(long count,long total) { return total == 0 ? "0" : Double.toString((double)count/total); }
    private static String mean(List<Map<String,Object>> rows,String field) { return rows.isEmpty() ? "0" : Double.toString(rows.stream().mapToDouble(r -> ((Number)r.get(field)).doubleValue()).average().orElse(0)); }
    private static String percentile(List<Long> sorted,double p) { return sorted.isEmpty() ? "0" : String.valueOf(sorted.get((int)Math.ceil(p*sorted.size())-1)); }
    private static String sumCost(List<Map<String,Object>> rows,String field) { return rows.stream().map(r -> (BigDecimal)r.get(field)).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString(); }
}
