package com.studysnap.backend.benchmark;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.LlmModelRouter;
import com.studysnap.backend.service.LlmStudyPackService.ChallengeVerifierPath;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import com.studysnap.backend.service.model.LlmRouting;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizValidationUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/** Owner-run Stage 2 legs. Normal tests skip this class's network path. */
class StatementPairStage2Benchmark {
    private static final String ROOT = "target/llm-benchmark/verifier";
    private static final String REPLACEMENT_SCHEMA = "note_lib_challenge_verifier_replacement";
    private static final String VERIFIER_SCHEMA = "note_lib_statement_pair_verifier";
    private static final String CHALLENGE_SCHEMA = "note_lib_challenge_quiz";
    private static final Set<String> LEGS = Set.of("P", "R");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Config(String leg, String model, Path input, Path outDir, int batches, int baselineBatches,
            int maxCalls, BigDecimal inputPrice, BigDecimal outputPrice, BigDecimal spendStop, String apiKey) {
        static Config system() {
            String leg = required("llm.stage2.leg").toUpperCase(Locale.ROOT);
            String model = required("llm.stage2.model");
            Path input = localPath("llm.stage2.input");
            Path out = localPath("llm.stage2.outDir");
            int batches = Integer.parseInt(System.getProperty("llm.stage2.batches", "40"));
            int baseline = Integer.parseInt(System.getProperty("llm.stage2.baselineBatches", "10"));
            int maxCalls = Integer.parseInt(required("llm.stage2.maxCalls"));
            BigDecimal inputPrice = new BigDecimal(required("llm.stage2.priceInPerM"));
            BigDecimal outputPrice = new BigDecimal(required("llm.stage2.priceOutPerM"));
            BigDecimal stop = new BigDecimal(required("llm.stage2.spendStopUsd"));
            return new Config(leg, model, input, out, batches, baseline, maxCalls,
                    inputPrice, outputPrice, stop, requiredKey(System.getenv("LLM_API_KEY")));
        }
    }

    record Case(String id, String noteText, String summary, List<String> keyConcepts,
            String subject, String courseProgram, DomainContext domainContext, LearnerLevel learnerLevel,
            List<String> disallowedQuestions, List<String> existingConcepts,
            String difficulty, List<Rejected> rejected) { }
    record Rejected(String question, String concept, boolean statement1Correct, boolean statement2Correct) { }

    @Test void stage2() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.stage2"));
        run(Config.system(), null);
    }

    static void run(Config config, ClientHttpRequestFactory requestFactory) throws Exception {
        // Every input and spending constraint is checked before RestClient or the meter exists.
        List<Case> cases = preflight(config);
        Files.createDirectories(config.outDir());
        MeteringInterceptor meter = new MeteringInterceptor(MAPPER, config.outDir(), config.model(),
                config.inputPrice(), config.outputPrice(), config.spendStop(), config.maxCalls());
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLlm().getApi().setApiKey(config.apiKey());
        properties.getSettings().setModelInteractiveQuiz(config.model());
        properties.getLlm().getStatementPairVerifier().setTemplateSeedEnabled(true);
        SimpleClientHttpRequestFactory productionFactory = new SimpleClientHttpRequestFactory();
        productionFactory.setConnectTimeout(Duration.ofSeconds(10));
        productionFactory.setReadTimeout(Duration.ofSeconds(180));
        AtomicInteger generatorCalls = new AtomicInteger();
        ObjectNode[] initialPayload = {null};
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory == null ? productionFactory : requestFactory)
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    ObjectNode json = (ObjectNode) MAPPER.readTree(new String(body, StandardCharsets.ISO_8859_1));
                    String schema = json.path("text").path("format").path("name").asText();
                    if ("R".equals(config.leg()) && CHALLENGE_SCHEMA.equals(schema))
                        return stub(initialPayload[0]);
                    if ("R".equals(config.leg()) && VERIFIER_SCHEMA.equals(schema))
                        return stub(uncertainResults(json));
                    if (CHALLENGE_SCHEMA.equals(schema)) generatorCalls.incrementAndGet();
                    return meter.intercept(request, body, execution);
                }).build();
        OpenAiPromptResources prompts = new OpenAiLlmConfig().openAiPromptResources(MAPPER, properties);
        List<ObjectNode> results = new ArrayList<>();
        List<ObjectNode> blind = new ArrayList<>();
        List<ObjectNode> blindKey = new ArrayList<>();
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            int count = config.batches();
            for (int i = 0; i < count; i++) {
                Case sample = cases.get(i);
                if ("P".equals(config.leg())) {
                    runP(config, sample, "rule", prompts, properties, client, meter,
                            generatorCalls, results, blind, blindKey, appender);
                    if (meter.stopped()) break;
                    if (i < config.baselineBatches()) {
                        runP(config, sample, "baseline", prompts, properties, client, meter,
                                generatorCalls, results, blind, blindKey, appender);
                    }
                } else {
                    initialPayload[0] = initialQuestions(sample);
                    runR(config, sample, prompts, properties, client, meter, results, blind, blindKey);
                }
                if (meter.stopped()) break;
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
        writeJsonl(config.outDir().resolve("results.jsonl"), results);
        writeJsonl(config.outDir().resolve("blind-items.jsonl"), blind);
        writeJsonl(config.outDir().resolve("blind-key.jsonl"), blindKey);
        ObjectNode summary = MAPPER.createObjectNode().put("leg", config.leg()).put("model", config.model())
                .put("batchesRequested", config.batches()).put("rows", results.size())
                .put("meteredCalls", meter.calls().size()).put("spendUsd", meter.total())
                .put("stopped", meter.stopped()).put("unmetered", meter.unmetered());
        if ("P".equals(config.leg())) {
            long leaked = results.stream().filter(r -> "rule".equals(r.path("arm").asText()))
                    .mapToLong(r -> r.path("statementPairLeaked").asLong()).sum();
            long complete = results.stream().filter(r -> "rule".equals(r.path("arm").asText()))
                    .filter(r -> r.path("returned").asInt() == 5).count();
            summary.put("ruleLeaked", leaked).put("ruleCompleteBatches", complete);
            for (String arm : List.of("rule", "baseline")) {
                long armBatches = results.stream().filter(r -> arm.equals(r.path("arm").asText())).count();
                long armComplete = results.stream().filter(r -> arm.equals(r.path("arm").asText()))
                        .filter(r -> r.path("returned").asInt() == 5).count();
                long h4 = results.stream().filter(r -> arm.equals(r.path("arm").asText()))
                        .mapToLong(r -> r.path("h4Rejections").asLong()).sum();
                long invalid = results.stream().filter(r -> arm.equals(r.path("arm").asText()))
                        .mapToLong(r -> r.path("invalidOutputRetries").asLong()).sum();
                summary.put(arm + "Batches", armBatches).put(arm + "Complete", armComplete)
                        .put(arm + "H4Rejections", h4).put(arm + "InvalidOutputRetries", invalid)
                        .put(arm + "H4PerBatch", armBatches == 0 ? 0.0 : h4 / (double) armBatches)
                        .put(arm + "InvalidPerBatch", armBatches == 0 ? 0.0 : invalid / (double) armBatches);
            }
        } else {
            long requested = results.stream().mapToLong(r -> r.path("requested").asLong()).sum();
            long replaced = results.stream().mapToLong(r -> r.path("returned").asLong()).sum();
            summary.put("requestedReplacements", requested).put("returnedReplacements", replaced)
                    .put("replacementFailureRate", requested == 0 ? 0.0 : (requested - replaced) / (double) requested);
            ObjectNode formats = summary.putObject("replacementFormats");
            java.util.Map<String, Integer> distribution = new java.util.TreeMap<>();
            for (ObjectNode row : results) for (JsonNode question : row.path("questions")) {
                distribution.merge(question.path("questionFormat").asText("MCQ"), 1, Integer::sum);
            }
            distribution.forEach(formats::put);
        }
        Files.writeString(config.outDir().resolve("summary.json"), MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(summary));
    }

    private static void runP(Config config, Case sample, String arm, OpenAiPromptResources prompts,
            StudySnapProperties properties, RestClient client, MeteringInterceptor meter,
            AtomicInteger generatorCalls, List<ObjectNode> results, List<ObjectNode> blind,
            List<ObjectNode> blindKey, ListAppender<ILoggingEvent> appender) {
        properties.getLlm().getStatementPairVerifier().setPlus5ExclusionEnabled("rule".equals(arm));
        String id = sample.id() + "-" + arm;
        meter.begin(id, "P");
        int beforeCalls = generatorCalls.get();
        int beforeLogs = appender.list.size();
        OpenAiLlmStudyPackService service = service(properties, prompts, client);
        List<QuizItem> questions = List.of();
        String failure = null;
        try {
            questions = service.generateMoreChallengeQuiz(sample.subject(), sample.summary(),
                    sample.keyConcepts(), sample.disallowedQuestions(), sample.existingConcepts(),
                    5, sample.difficulty(), context(sample)).quizItems();
        } catch (RuntimeException exception) {
            failure = exception.getClass().getSimpleName();
        }
        List<String> messages = appender.list.subList(beforeLogs, appender.list.size()).stream()
                .map(ILoggingEvent::getFormattedMessage).toList();
        ObjectNode row = MAPPER.createObjectNode().put("id", id).put("arm", arm)
                .put("returned", questions.size()).put("statementPairLeaked",
                        loggedCount(messages, "statementPairLeaked"))
                .put("generationCalls", generatorCalls.get() - beforeCalls)
                .put("invalidOutputRetries", Math.max(0, generatorCalls.get() - beforeCalls - 1))
                .put("h4Rejections", messages.stream()
                        .filter(message -> message.contains("action=quiz_answer_explanation_consistency"))
                        .count());
        if (failure == null) row.putNull("failure"); else row.put("failure", failure);
        ArrayNode questionArray = row.putArray("questions");
        for (QuizItem question : questions) questionArray.add(MAPPER.valueToTree(question));
        results.add(row);
        String label = ((sample.id().hashCode() & 1) == 0) == "rule".equals(arm) ? "A" : "B";
        String blindId = sample.id() + "-" + label;
        ObjectNode blindBatch = MAPPER.createObjectNode().put("id", blindId).put("pairId", sample.id())
                .put("noteText", sample.noteText()).put("summary", sample.summary())
                .put("difficulty", sample.difficulty());
        blindBatch.set("keyConcepts", MAPPER.valueToTree(sample.keyConcepts()));
        blindBatch.set("questions", questionArray.deepCopy());
        blind.add(blindBatch);
        blindKey.add(MAPPER.createObjectNode().put("id", blindId).put("arm", arm)
                .put("model", config.model()));
    }

    private static int loggedCount(List<String> messages, String field) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String message = messages.get(i);
            if (!message.contains("action=challenge_quiz_generation_counts")) continue;
            java.util.regex.Matcher match = java.util.regex.Pattern.compile("\\b" + field + "=(\\d+)").matcher(message);
            if (match.find()) return Integer.parseInt(match.group(1));
        }
        return 0;
    }

    private static void runR(Config config, Case sample, OpenAiPromptResources prompts,
            StudySnapProperties properties, RestClient client, MeteringInterceptor meter,
            List<ObjectNode> results, List<ObjectNode> blind, List<ObjectNode> blindKey) {
        meter.begin(sample.id(), "R");
        List<QuizItem> questions = List.of();
        String failure = null;
        try {
            questions = service(properties, prompts, client).generateChallengeQuiz(sample.subject(),
                    sample.summary(), sample.keyConcepts(), sample.disallowedQuestions(),
                    sample.rejected().size(), sample.difficulty(), context(sample), sample.noteText(),
                    ChallengeVerifierPath.TEMPLATE_SEED).quizItems();
        } catch (RuntimeException exception) {
            failure = exception.getClass().getSimpleName();
        }
        ObjectNode row = MAPPER.createObjectNode().put("id", sample.id())
                .put("requested", sample.rejected().size()).put("returned", questions.size())
                .put("statementPairReplacements", questions.stream()
                        .filter(q -> QuizValidationUtils.isStatementPair(q.question())).count());
        if (failure == null) row.putNull("failure"); else row.put("failure", failure);
        ArrayNode questionArray = row.putArray("questions");
        for (QuizItem question : questions) {
            questionArray.add(MAPPER.valueToTree(question));
            String blindId = sample.id() + "-" + questionArray.size();
            blind.add(MAPPER.createObjectNode().put("id", blindId).put("noteText", sample.noteText())
                    .put("sourceConcept", sample.rejected().getFirst().concept())
                    .put("rejectedQuestion", sample.rejected().getFirst().question())
                    .put("difficulty", sample.difficulty())
                    .set("replacement", MAPPER.valueToTree(question)));
            blindKey.add(MAPPER.createObjectNode().put("id", blindId).put("model", config.model()));
        }
        results.add(row);
    }

    private static OpenAiLlmStudyPackService service(StudySnapProperties properties,
            OpenAiPromptResources prompts, RestClient client) {
        return new OpenAiLlmStudyPackService(properties, MAPPER, client, prompts,
                new LlmModelRouter(properties, mock(SubscriptionService.class), mock(UserRepository.class)),
                new SimpleAsyncTaskExecutor("stage2-verifier-stub-"));
    }

    private static ObjectNode initialQuestions(Case sample) {
        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        for (Rejected rejected : sample.rejected()) {
            String answer = rejected.statement1Correct()
                    ? (rejected.statement2Correct() ? "A" : "B")
                    : (rejected.statement2Correct() ? "C" : "D");
            ObjectNode item = questions.addObject().put("question", rejected.question())
                    .put("answer", answer).put("concept", rejected.concept())
                    .put("explanation", "The keyed choice follows the two stated verdicts.")
                    .put("questionFormat", "MCQ");
            item.putArray("choices").add("Both statements are correct")
                    .add("Only Statement 1 is correct").add("Only Statement 2 is correct")
                    .add("Neither statement is correct");
            item.putObject("statementVerdicts").put("statement1Correct", rejected.statement1Correct())
                    .put("statement2Correct", rejected.statement2Correct());
        }
        return payload;
    }

    private static ObjectNode uncertainResults(JsonNode request) {
        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode results = payload.putArray("results");
        String user = request.path("input").get(2).path("content").get(0).path("text").asText();
        try {
            JsonNode items = MAPPER.readTree(user.substring(user.indexOf("Items:\n") + 7).trim());
            for (JsonNode item : items) {
                ObjectNode row = results.addObject().put("itemId", item.path("itemId").asText());
                for (String key : List.of("statement1", "statement2")) {
                    row.set(key, MAPPER.createObjectNode().put("sourceSupport", "SILENT")
                            .putNull("evidenceQuote").put("reason", "Reserved for the Stage 2 replacement test.")
                            .put("noteConflictsWithStandardKnowledge", false).put("verdict", "UNCERTAIN"));
                }
            }
            return payload;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MockClientHttpResponse stub(ObjectNode payload) throws IOException {
        ObjectNode response = MAPPER.createObjectNode().put("model", "stub");
        response.put("output_text", MAPPER.writeValueAsString(payload));
        return new MockClientHttpResponse(MAPPER.writeValueAsBytes(response), HttpStatus.OK);
    }

    private static StudyPackGenerationContext context(Case sample) {
        return new StudyPackGenerationContext(sample.learnerLevel(), sample.courseProgram(),
                sample.subject(), List.of(), sample.domainContext(), sample.learnerLevel(),
                LlmRouting.interactiveQuiz());
    }

    private static List<Case> preflight(Config config) throws Exception {
        if (!LEGS.contains(config.leg()) || config.model() == null || config.model().isBlank()
                || config.apiKey() == null || config.apiKey().isBlank()
                || config.batches() < 1 || config.baselineBatches() < 0
                || config.baselineBatches() > config.batches() || config.maxCalls() < 1
                || config.inputPrice().signum() < 0 || config.outputPrice().signum() < 0
                || config.spendStop().signum() <= 0
                || config.spendStop().compareTo(new BigDecimal("3.50")) > 0)
            throw new IllegalArgumentException("Invalid Stage 2 leg, model, counts or spending limit");
        Path root = Path.of(ROOT).toAbsolutePath().normalize();
        if (!config.input().toAbsolutePath().normalize().startsWith(root)
                || !config.outDir().toAbsolutePath().normalize().startsWith(root))
            throw new IllegalArgumentException("Stage 2 paths must be under " + root);
        if (Files.exists(config.outDir())) {
            try (var contents = Files.list(config.outDir())) {
                if (contents.findAny().isPresent())
                    throw new IllegalArgumentException("Stage 2 outDir must be fresh and empty");
            }
        }
        List<Case> cases = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();
        int line = 0;
        for (String text : Files.readAllLines(config.input())) {
            line++;
            if (text.isBlank()) continue;
            try {
                JsonNode row = MAPPER.readTree(text);
                String id = requiredText(row, "id");
                if (!ids.add(id) || !id.matches("[A-Za-z0-9_-]+"))
                    throw new IllegalArgumentException("invalid or duplicate id");
                String noteText = requiredText(row, "noteText");
                String summary = requiredText(row, "summary");
                String subject = requiredText(row, "subject");
                List<String> concepts = strings(row.path("keyConcepts"));
                if (concepts.isEmpty()) throw new IllegalArgumentException("keyConcepts required");
                List<Rejected> rejected = new ArrayList<>();
                if ("R".equals(config.leg())) {
                    for (JsonNode source : row.path("rejected")) {
                        if (!source.path("statement1Correct").isBoolean()
                                || !source.path("statement2Correct").isBoolean())
                            throw new IllegalArgumentException("verdict pair required");
                        String question = requiredText(source, "question");
                        if (!QuizValidationUtils.isStatementPair(question))
                            throw new IllegalArgumentException("rejected item must be a statement pair");
                        rejected.add(new Rejected(question, requiredText(source, "concept"),
                                source.path("statement1Correct").asBoolean(),
                                source.path("statement2Correct").asBoolean()));
                    }
                    if (rejected.size() != 1)
                        throw new IllegalArgumentException("R requires one rejected item per batch for blind alignment");
                }
                DomainContext domain = row.path("domainContext").isTextual()
                        ? DomainContext.valueOf(row.path("domainContext").asText()) : null;
                LearnerLevel level = LearnerLevel.valueOf(requiredText(row, "learnerLevel"));
                cases.add(new Case(id, noteText, summary, List.copyOf(concepts), subject,
                        row.path("courseProgram").isTextual() ? row.path("courseProgram").asText() : null,
                        domain, level, strings(row.path("disallowedQuestions")),
                        strings(row.path("existingConcepts")),
                        row.path("difficulty").asText("medium"), List.copyOf(rejected)));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Stage 2 preflight line " + line + ": " + exception.getMessage(), exception);
            }
        }
        if (cases.size() < config.batches()) throw new IllegalArgumentException("Too few Stage 2 cases");
        return List.copyOf(cases);
    }

    private static List<String> strings(JsonNode array) {
        if (array.isMissingNode() || array.isNull()) return List.of();
        if (!array.isArray()) throw new IllegalArgumentException("Expected array");
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.asText().isBlank()) throw new IllegalArgumentException("Expected nonblank text");
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(key + " required");
        return value.asText();
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " required");
        return value;
    }

    private static String requiredKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("LLM_API_KEY required");
        return key;
    }

    private static Path localPath(String key) {
        Path path = Path.of(required(key)).toAbsolutePath().normalize();
        if (!path.startsWith(Path.of(ROOT).toAbsolutePath().normalize()))
            throw new IllegalArgumentException(key + " must be under " + ROOT);
        return path;
    }

    private static void writeJsonl(Path path, List<ObjectNode> rows) throws IOException {
        StringBuilder output = new StringBuilder();
        for (ObjectNode row : rows) output.append(MAPPER.writeValueAsString(row)).append('\n');
        Files.writeString(path, output.toString());
    }
}
