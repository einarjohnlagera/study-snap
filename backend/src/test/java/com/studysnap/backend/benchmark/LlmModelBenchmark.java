package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizValidationUtils;
import com.studysnap.backend.util.QuizDeduplicationUtils;
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
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in, sequential benchmark. No Spring application context or database. */
public class LlmModelBenchmark {
    enum Leg { A, B, C, D, E }
    record ECell(int sourceCount, int questions, String difficulty) {
        static ECell of(String cell) {
            return switch (cell) {
                case "S10" -> new ECell(1, productionInt("LOW_SCORE_QUESTION_COUNT"), productionString("DIFFICULTY_EASY"));
                case "S12" -> new ECell(1, productionInt("MID_SCORE_QUESTION_COUNT"), productionString("DEFAULT_SELECTED_DIFFICULTY"));
                case "S15", "VLOW" -> new ECell(1, productionInt("HIGH_SCORE_QUESTION_COUNT"), productionString("DIFFICULTY_HARD"));
                case "M6x3" -> new ECell(6, productionInt("MULTI_NOTE_CHALLENGE_QUESTION_COUNT"), productionString("DEFAULT_SELECTED_DIFFICULTY"));
                case "M3x6" -> new ECell(3, productionInt("MULTI_NOTE_CHALLENGE_QUESTION_COUNT"), productionString("DEFAULT_SELECTED_DIFFICULTY"));
                default -> throw new IllegalArgumentException("Unknown E cell: " + cell);
            };
        }
        private static int productionInt(String name) {
            try { var field = ChallengeQuizService.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(null); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException("Challenge profile constant unavailable: " + name, ex); }
        }
        private static String productionString(String name) {
            try { var field = ChallengeQuizService.class.getDeclaredField(name); field.setAccessible(true); return (String)field.get(null); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException("Challenge profile constant unavailable: " + name, ex); }
        }
        int allocation(int index) {
            try {
                var method = ChallengeQuizService.class.getDeclaredMethod("allocateQuestionsAcrossSources", List.class, int.class);
                method.setAccessible(true);
                List<LongExamSourceNoteRef> refs = new ArrayList<>();
                for (int i = 0; i < sourceCount; i++) refs.add(new LongExamSourceNoteRef("source-" + i,"note-" + i,"Source",0));
                ChallengeQuizService service = new org.springframework.objenesis.ObjenesisStd()
                        .newInstance(ChallengeQuizService.class);
                @SuppressWarnings("unchecked")
                List<LongExamSourceNoteRef> allocated = (List<LongExamSourceNoteRef>) method.invoke(service, refs, questions);
                return allocated.get(index).questionCount();
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Challenge source allocation unavailable", ex);
            }
        }
    }
    record EFilter(Set<String> cells, int firstRun, int lastRun, Map<String,Integer> caps) {
        static EFilter system() {
            Set<String> cells = new LinkedHashSet<>();
            for (String part : System.getProperty("llm.benchmark.cells", "S15,M6x3,S10,S12,M3x6,VLOW").split(",")) {
                String cell = part.trim(); ECell.of(cell); cells.add(cell);
            }
            String[] range = System.getProperty("llm.benchmark.runs", "1-10").split("-", -1);
            if (range.length > 2) throw new IllegalArgumentException("Invalid runs range");
            int first = Integer.parseInt(range[0]);
            int last = range.length == 1 ? first : Integer.parseInt(range[1]);
            if (first < 1 || last < first) throw new IllegalArgumentException("Invalid runs range");
            Map<String,Integer> caps = new HashMap<>();
            String raw = System.getProperty("llm.benchmark.cellRunCap", "");
            if (!raw.isBlank()) for (String part : raw.split(",")) {
                String[] pair = part.trim().split(":", -1);
                if (pair.length != 2) throw new IllegalArgumentException("Invalid cellRunCap");
                ECell.of(pair[0]); int cap = Integer.parseInt(pair[1]);
                if (cap < 1) throw new IllegalArgumentException("Invalid cellRunCap");
                caps.put(pair[0], cap);
            }
            return new EFilter(cells, first, last, caps);
        }
        boolean selects(JsonNode input) {
            String cell = input.path("cell").asText(); int run = input.path("run").asInt();
            return cells.contains(cell) && run >= firstRun && run <= lastRun && run <= caps.getOrDefault(cell, Integer.MAX_VALUE);
        }
    }
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
        EFilter eFilter = config.legs().contains(Leg.E) ? EFilter.system() : null;
        // Scan selected E lines before the first HTTP attempt. A malformed source count aborts
        // this command rather than allowing earlier lines to spend money first.
        if (eFilter != null) {
            for (String line : Files.readAllLines(config.input())) {
                JsonNode candidate;
                try { candidate = mapper.readTree(line); }
                catch (Exception ignored) { continue; }
                if ("E".equals(candidate.path("leg").asText()) && eFilter.selects(candidate)) {
                    try { validateInput(candidate, Leg.E); }
                    catch (IllegalArgumentException ex) {
                        malformed.merge("E", 1, Integer::sum);
                        writeSummary(config.outDir(), config.model(), results, meter.calls(), malformed);
                        // Fail the command, not just the line: a silent skip would let a ten-command
                        // sitting continue with a cell missing and nothing reported.
                        throw new IllegalArgumentException("Leg E preflight failed for "
                                + candidate.path("id").asText("unknown") + ": " + ex.getMessage(), ex);
                    }
                }
            }
        }
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
                if (leg == Leg.E && !eFilter.selects(input)) continue;
                String generationId = "g" + serial + "-" + id.replaceAll("[^A-Za-z0-9_-]", "_");
                meter.begin(generationId, leg.name(), leg == Leg.E && "VLOW".equals(input.path("cell").asText()));
                int before = meter.calls().size();
                List<QuizItem> questions = List.of();
                String noteContent = null;
                Throwable failure = null;
                String method = method(input, leg);
                List<Map<String,Object>> eSources = new ArrayList<>();
                Integer failedSourceIndex = null;
                try {
                    StudyPackGenerationContext context = leg == Leg.E ? null : context(input);
                    switch (method) {
                        case "generateChallengeStart" -> {
                            ECell profile = ECell.of(needed(input,"cell"));
                            Set<String> disallowed = new LinkedHashSet<>();
                            List<QuizItem> merged = new ArrayList<>();
                            for (int sourceIndex = 0; sourceIndex < profile.sourceCount(); sourceIndex++) {
                                JsonNode source = input.path("sources").get(sourceIndex);
                                List<String> avoid = profile.sourceCount() == 1 ? strings(source,"disallowedQuestions") : List.copyOf(disallowed);
                                Set<String> keys = profile.sourceCount() == 1
                                        ? QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(avoid) : disallowed;
                                int sourceBefore = meter.calls().size();
                                Throwable sourceFailure = null;
                                List<QuizItem> accepted = List.of();
                                try {
                                    List<QuizItem> generated = service.generateChallengeQuiz(needed(source,"studyPackTitle"),
                                            needed(source,"studyPackSummary"), strings(source,"keyConcepts"), avoid,
                                            profile.allocation(sourceIndex), profile.difficulty(), context(source)).quizItems();
                                    accepted = QuizDeduplicationUtils.uniqueQuestions(generated, keys);
                                    merged.addAll(accepted);
                                    questions = profile.sourceCount() == 1 ? List.copyOf(merged)
                                            : QuizDeduplicationUtils.uniqueQuestions(merged,
                                                    QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(
                                                            strings(input.path("sources").get(0),"disallowedQuestions")));
                                    if (profile.sourceCount() > 1)
                                        disallowed.addAll(QuizDeduplicationUtils.toNormalizedQuestionSet(accepted));
                                } catch (Throwable ex) { sourceFailure = ex; failedSourceIndex = sourceIndex; }
                                List<MeteringInterceptor.Call> sourceCalls = meter.calls().subList(sourceBefore, meter.calls().size());
                                Map<String,Object> sourceRow = new LinkedHashMap<>();
                                sourceRow.put("sourceIndex",sourceIndex);
                                sourceRow.put("requestedQuestions",profile.allocation(sourceIndex));
                                sourceRow.put("acceptedQuestions",accepted.size());
                                sourceRow.put("latencyMs",sourceCalls.stream().mapToLong(MeteringInterceptor.Call::latencyMs).sum());
                                sourceRow.put("attempts",sourceCalls.size());
                                sourceRow.put("failureClass",sourceFailure == null ? null : classify(sourceFailure, sourceCalls));
                                eSources.add(sourceRow);
                                if (sourceFailure != null) throw sourceFailure;
                            }
                            questions = profile.sourceCount() == 1 ? merged
                                    : QuizDeduplicationUtils.uniqueQuestions(merged,
                                            QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(
                                                    strings(input.path("sources").get(0),"disallowedQuestions")));
                        }
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
                if (leg == Leg.E) {
                    ECell profile = ECell.of(needed(input,"cell"));
                    result.put("cell",needed(input,"cell")); result.put("run",input.path("run").asInt());
                    result.put("requestedQuestions",profile.questions()); result.put("acceptedQuestions",questions.size());
                    result.put("acceptedAtLeastRequested",questions.size() >= profile.questions());
                    long startLatency = calls.stream().mapToLong(MeteringInterceptor.Call::latencyMs).sum();
                    result.put("startLatencyMs",startLatency);
                    result.put("maxSourceCallLatencyMs",eSources.stream().mapToLong(s -> ((Number)s.get("latencyMs")).longValue()).max().orElse(0));
                    result.put("sources",eSources); result.put("failedSourceIndex",failedSourceIndex);
                    result.put("reasoningEffort","VLOW".equals(input.path("cell").asText()) ? "low" : "default");
                    result.put("timeout",calls.stream().anyMatch(MeteringInterceptor.Call::timeout));
                }
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
            case E -> "generateChallengeStart";
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
        if (leg == Leg.E) {
            ECell profile = ECell.of(needed(input,"cell"));
            JsonNode run = input.path("run");
            if (!run.isIntegralNumber() || run.asInt() < 1) throw new IllegalArgumentException("run must be positive integer");
            JsonNode sources = input.path("sources");
            if (!sources.isArray() || sources.size() != profile.sourceCount())
                throw new IllegalArgumentException("E cell " + needed(input,"cell") + " requires " + profile.sourceCount() + " sources");
            for (JsonNode source : sources) {
                needed(source,"studyPackTitle"); needed(source,"studyPackSummary"); needed(source,"sourceNoteText");
                strings(source,"keyConcepts"); strings(source,"disallowedQuestions"); strings(source,"tags");
                if (source.hasNonNull("learnerLevel")) LearnerLevel.valueOf(source.path("learnerLevel").asText());
                if (source.hasNonNull("noteLearnerLevel")) LearnerLevel.valueOf(source.path("noteLearnerLevel").asText());
                if (source.hasNonNull("domainContext")) DomainContext.valueOf(source.path("domainContext").asText());
            }
            return;
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
            writer.write("model,leg,calls,generations,successRate,HTTP_ERROR,EMPTY,SCHEMA_OR_PARSE,MULTI_OUTPUT_TEXT,SPEND_STOP,OTHER,meanLatencyMs,p50LatencyMs,p95LatencyMs,meanAttempts,h4FlagRate,meanInputTokens,meanOutputTokens,meanReasoningTokens,conservativeUsd,nonAdditiveUsd,cell,runs,timeouts,startLatencyP50Ms,startLatencyP90Ms,startLatencyMaxMs,acceptedShortRuns,meanMaxSourceCallLatencyMs,malformedInputs\n");
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
                        sumCost(selected,"conservativeUsd"),sumCost(selected,"nonAdditiveUsd")));
                cells.addAll(List.of("", "", "", "", "", "", "", ""));
                cells.add(String.valueOf(malformed.getOrDefault(leg.name(),0)));
                writer.write(String.join(",",cells)); writer.newLine();
            }
            for (String cell : List.of("S15","M6x3","S10","S12","M3x6","VLOW")) {
                List<Map<String,Object>> cellRows = rows.stream().filter(r -> cell.equals(r.get("cell"))).toList();
                if (cellRows.isEmpty()) continue;
                // A run cut off by the spend stop is not a real start: it would understate latency and
                // count as a short set. Timeouts stay in, because they are real waits.
                List<Map<String,Object>> selected = cellRows.stream()
                        .filter(r -> !"SPEND_STOP".equals(r.get("failureClass"))).toList();
                if (selected.isEmpty()) continue;
                List<Long> latency = selected.stream().map(r -> ((Number)r.get("startLatencyMs")).longValue()).sorted().toList();
                writer.write(String.join(",", List.of(model,"E","","",ratio(selected.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count(),selected.size()),
                        "","","","","","","","","",mean(selected,"attempts"),"","","","",
                        sumCost(selected,"conservativeUsd"),sumCost(selected,"nonAdditiveUsd"),cell,String.valueOf(selected.size()),
                        String.valueOf(selected.stream().filter(r -> Boolean.TRUE.equals(r.get("timeout"))).count()),
                        percentile(latency,.5),percentile(latency,.9),String.valueOf(latency.getLast()),
                        String.valueOf(selected.stream().filter(r -> Boolean.FALSE.equals(r.get("acceptedAtLeastRequested"))).count()),
                        cell.startsWith("M") ? mean(selected,"maxSourceCallLatencyMs") : "","")));
                writer.newLine();
            }
            if (malformed.containsKey("UNKNOWN")) writer.write(model + ",UNKNOWN,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0," + malformed.get("UNKNOWN") + "\n");
        }
    }
    private static String ratio(long count,long total) { return total == 0 ? "0" : Double.toString((double)count/total); }
    private static String mean(List<Map<String,Object>> rows,String field) { return rows.isEmpty() ? "0" : Double.toString(rows.stream().mapToDouble(r -> ((Number)r.get(field)).doubleValue()).average().orElse(0)); }
    private static String percentile(List<Long> sorted,double p) { return sorted.isEmpty() ? "0" : String.valueOf(sorted.get((int)Math.ceil(p*sorted.size())-1)); }
    private static String sumCost(List<Map<String,Object>> rows,String field) { return rows.stream().map(r -> (BigDecimal)r.get(field)).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString(); }
}
