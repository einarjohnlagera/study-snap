package com.studysnap.backend.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.AppConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.LlmModelRouter;
import com.studysnap.backend.service.LlmStudyPackService.ChallengeVerifierPath;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.LlmRouting;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.StringNormalizationUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StatementPairVerifierProductionTest {
    private static final String NOTE = "Water expands when it freezes. Water is a liquid, not a metal.";
    private static final String STEM = "Statement 1: Water expands when it freezes. Statement 2: Water is a metal. Which is correct?";
    private static final String REPLACEMENT = "What happens to water when it freezes?";
    private final ObjectMapper mapper = new ObjectMapper();
    private final StudySnapProperties properties = new StudySnapProperties();
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Test
    void switchesOffMakeOnlyTheGeneratorCallAndKeepItsOutput() throws Exception {
        OpenAiLlmStudyPackService service = service(body -> response(generated(pair()), HttpStatus.OK));
        assertThat(properties.getLlm().getStatementPairVerifier().isLiveStartEnabled()).isFalse();
        assertThat(properties.getLlm().getStatementPairVerifier().isTemplateSeedEnabled()).isFalse();
        assertThat(properties.getLlm().getStatementPairVerifier().getWaitTimeoutSeconds()).isEqualTo(390);
        List<QuizItem> result = start(service);
        List<QuizItem> template = service.generateChallengeQuiz("Water", "Water summary", List.of("Water"),
                List.of(), 1, "medium", context(), NOTE, ChallengeVerifierPath.TEMPLATE_SEED).quizItems();
        assertThat(result).extracting(QuizItem::question).containsExactly(STEM);
        assertThat(template).extracting(QuizItem::question).containsExactly(STEM);
        assertThat(requests).hasSize(2);
        for (JsonNode request : requests) {
            assertThat(request.path("text").path("format").path("name").asText())
                    .isEqualTo("note_lib_challenge_quiz");
            assertThat(request.path("input").toString()).doesNotContain("For this batch only");
        }
    }

    @Test
    void enabledLiveStartSwitchDoesNotCheckTheMultiNotePath() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> response(generated(pair()), HttpStatus.OK));

        List<QuizItem> result = service.generateChallengeQuiz("Water", "Water summary", List.of("Water"),
                List.of(), 1, "medium", context(), NOTE, ChallengeVerifierPath.NONE).quizItems();

        assertThat(result).extracting(QuizItem::question).containsExactly(STEM);
        assertThat(requests).hasSize(1);
        assertThat(requests).extracting(this::schema).containsExactly("note_lib_challenge_quiz");
    }

    @Test
    void bothModelsAgreeWithBlindRequestsAndTheItemIsAccepted() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            return response("note_lib_statement_pair_verifier".equals(schema) ? verdict(true) : generated(pair()),
                    HttpStatus.OK);
        });
        assertThat(start(service)).extracting(QuizItem::question).containsExactly(STEM);
        assertThat(requests).hasSize(3);
        List<JsonNode> verifierRequests = requests.stream().filter(r ->
                "note_lib_statement_pair_verifier".equals(schema(r))).toList();
        assertThat(verifierRequests).hasSize(2);
        assertThat(verifierRequests).extracting(r -> r.path("model").asText())
                .containsExactlyInAnyOrder("gpt-6.1-sol", "gpt-6-luna");
        for (JsonNode request : verifierRequests) {
            assertThat(request.has("reasoning")).isFalse();
            String user = request.path("input").get(2).path("content").get(0).path("text").asText();
            assertThat(user).contains(NOTE, "Water expands when it freezes.", "Water is a metal.");
            assertThat(user).doesNotContain(STEM, "Both statements are correct", "Only Statement 1",
                    "Neither statement is correct", "statementVerdicts", "correctIndex",
                    "A correct answer explanation");
            JsonNode item = mapper.readTree(user.substring(user.indexOf("Items:\n") + 7).trim()).get(0);
            assertThat(item.size()).isEqualTo(3);
            assertThat(item.path("itemId").asText()).isEqualTo("1");
        }
    }

    @Test
    void notePlaceholdersAndReplacementMetacharactersReachBothCheckersLiterally() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        String note = NOTE + " Literal {ITEMS_JSON} and {STUDY_PACK_SUMMARY}; price $5; path C:\\notes.";
        String summary = "Summary {ITEMS_JSON}, $7, C:\\summary.";
        OpenAiLlmStudyPackService service = service(body -> response(
                schema(body).equals("note_lib_statement_pair_verifier") ? verdict(true) : generated(pair()),
                HttpStatus.OK));
        assertThat(service.generateChallengeQuiz("Water", summary, List.of("Water"), List.of(),
                1, "medium", context(), note, ChallengeVerifierPath.LIVE_START).quizItems())
                .extracting(QuizItem::question).containsExactly(STEM);
        List<JsonNode> checkerRequests = requests.stream()
                .filter(request -> schema(request).equals("note_lib_statement_pair_verifier")).toList();
        assertThat(checkerRequests).hasSize(2);
        for (JsonNode request : checkerRequests) {
            String user = request.path("input").get(2).path("content").get(0).path("text").asText();
            assertThat(user).contains(note, summary);
        }
    }

    @Test
    void disagreementTriggersExactlyOneReplacementCallAndOmitsAnotherPair() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema))
                return response(verdict(!"gpt-6-luna".equals(body.path("model").asText())), HttpStatus.OK);
            if ("note_lib_challenge_verifier_replacement".equals(schema))
                return response(generated(normal()), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).extracting(QuizItem::question).containsExactly(REPLACEMENT);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_challenge_verifier_replacement")))
                .hasSize(1);
        JsonNode replacementRequest = requests.stream().filter(r ->
                schema(r).equals("note_lib_challenge_verifier_replacement")).findFirst().orElseThrow();
        assertThat(replacementRequest.path("model").asText()).isEqualTo("gpt-4.1-mini");
        assertThat(replacementRequest.path("input").toString()).contains("untrusted", "Concept: Water");
    }

    @Test
    void bothCheckerTasksAreSubmittedToTheParallelExecutorBeforeEitherCallRuns() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        List<Runnable> submitted = new ArrayList<>();
        AsyncTaskExecutor queued = task -> {
            submitted.add(task);
            if (submitted.size() == 2) List.copyOf(submitted).forEach(Runnable::run);
        };
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_statement_pair_verifier")) {
                assertThat(submitted).hasSize(2);
                return response(verdict(true), HttpStatus.OK);
            }
            return response(generated(pair()), HttpStatus.OK);
        }, queued);
        assertThat(start(service)).hasSize(1);
        assertThat(submitted).hasSize(2);
    }

    @Test
    void checkerCallsRunOnDedicatedExecutor() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ThreadPoolTaskExecutor verifierPool = (ThreadPoolTaskExecutor)
                new AppConfig().statementPairVerifierTaskExecutor();
        try {
            assertThat(verifierPool.getCorePoolSize()).isEqualTo(4);
            assertThat(verifierPool.getMaxPoolSize()).isEqualTo(8);
            assertThat(verifierPool.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(20);
            OpenAiLlmStudyPackService service = service(body -> {
                if (schema(body).equals("note_lib_statement_pair_verifier")) {
                    assertThat(Thread.currentThread().getName()).startsWith("statement-pair-verifier-");
                    return response(verdict(true), HttpStatus.OK);
                }
                return response(generated(pair()), HttpStatus.OK);
            }, verifierPool);
            assertThat(start(service)).hasSize(1);
        } finally {
            verifierPool.shutdown();
        }
    }

    @Test
    void rejectedCheckerSubmissionsFailClosedAndBatchReturns() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        AsyncTaskExecutor rejecting = task -> { throw new RejectedExecutionException("full"); };
        OpenAiLlmStudyPackService service = service(body ->
                response(schema(body).equals("note_lib_challenge_verifier_replacement")
                        ? generated(normal()) : generated(pair()), HttpStatus.OK), rejecting);
        assertCallFailedDecision(() -> assertThat(start(service)).extracting(QuizItem::question)
                .containsExactly(REPLACEMENT));
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier"))).isEmpty();
    }

    @Test
    void timedOutCheckerFuturesFailClosedAndBatchReturns() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        properties.getLlm().getStatementPairVerifier().setWaitTimeoutSeconds(0);
        List<Runnable> submitted = new ArrayList<>();
        AsyncTaskExecutor queued = submitted::add;
        OpenAiLlmStudyPackService service = service(body ->
                response(schema(body).equals("note_lib_challenge_verifier_replacement")
                        ? generated(normal()) : generated(pair()), HttpStatus.OK), queued);
        assertCallFailedDecision(() -> assertThat(start(service)).extracting(QuizItem::question)
                .containsExactly(REPLACEMENT));
        assertThat(submitted).hasSize(2);
        submitted.forEach(Runnable::run);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier"))).isEmpty();
    }

    @Test
    void bothCheckerWaitsShareOneDeadlineAndTheLateSecondaryFailsClosed() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        properties.getLlm().getStatementPairVerifier().setWaitTimeoutSeconds(2);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger submissions = new AtomicInteger();
        List<Runnable> pending = new ArrayList<>();
        AsyncTaskExecutor delayed = task -> {
            if (submissions.incrementAndGet() == 1) scheduler.schedule(task, 1400, TimeUnit.MILLISECONDS);
            else pending.add(task);
        };
        try {
            OpenAiLlmStudyPackService service = service(body -> {
                if (schema(body).equals("note_lib_statement_pair_verifier"))
                    return response(verdict(true), HttpStatus.OK);
                if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                    return response(generated(normal()), HttpStatus.OK);
                return response(generated(pair()), HttpStatus.OK);
            }, delayed);
            long started = System.nanoTime();
            assertCallFailedDecision(() -> assertThat(start(service)).extracting(QuizItem::question)
                    .containsExactly(REPLACEMENT));
            long elapsed = System.nanoTime() - started;
            assertThat(elapsed).isLessThan(TimeUnit.MILLISECONDS.toNanos(3000));
            assertThat(submissions).hasValue(2);
            assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                    .hasSize(1);
            assertThat(pending).hasSize(1);
            pending.getFirst().run();
            assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                    .hasSize(1);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void failedFirstAttemptAfterDeadlineDoesNotStartAnotherHttpCall() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        properties.getLlm().getStatementPairVerifier().setWaitTimeoutSeconds(1);
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        CountDownLatch verifierTasksDone = new CountDownLatch(2);
        AsyncTaskExecutor tracked = task -> new Thread(() -> {
            try {
                task.run();
            } finally {
                verifierTasksDone.countDown();
            }
        }, "verifier-deadline-test").start();
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) {
                if ("gpt-6.1-sol".equals(body.path("model").asText())) {
                    firstCallEntered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try {
                            releaseFirstCall.await();
                            break;
                        } catch (InterruptedException exception) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    return response("{}", HttpStatus.BAD_GATEWAY);
                }
                return response(verdict(true), HttpStatus.OK);
            }
            return response("note_lib_challenge_verifier_replacement".equals(schema)
                    ? generated(normal()) : generated(pair()), HttpStatus.OK);
        }, tracked);
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CompletableFuture<List<QuizItem>> generation = CompletableFuture.supplyAsync(() -> start(service));
            assertThat(firstCallEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(generation.orTimeout(5, TimeUnit.SECONDS).join())
                    .extracting(QuizItem::question).containsExactly(REPLACEMENT);
            releaseFirstCall.countDown();
            assertThat(verifierTasksDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6.1-sol")).hasValue(1);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("reason=CALL_FAILED"))
                    .anySatisfy(message -> assertThat(message).contains("reason=deadline_passed"));
        } finally {
            releaseFirstCall.countDown();
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    @Test
    void saturatedGenerationPoolDoesNotBlockSeedStyleCallers() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ThreadPoolTaskExecutor generationPool = new ThreadPoolTaskExecutor();
        generationPool.setThreadNamePrefix("llm-parallel-saturated-test-");
        generationPool.setCorePoolSize(2);
        generationPool.setMaxPoolSize(2);
        generationPool.setQueueCapacity(10);
        generationPool.initialize();
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            OpenAiLlmStudyPackService service = service(body -> response(
                    schema(body).equals("note_lib_statement_pair_verifier") ? verdict(true) : generated(pair()),
                    HttpStatus.OK), generationPool, new SimpleAsyncTaskExecutor("statement-pair-verifier-test-"));
            var first = generationPool.submit(() -> {
                callersReady.countDown();
                start.await();
                return start(service);
            });
            var second = generationPool.submit(() -> {
                callersReady.countDown();
                start.await();
                return start(service);
            });
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).hasSize(1);
        } finally {
            start.countDown();
            generationPool.shutdown();
        }
    }

    @Test
    void checkerFailuresRetryOnceAndThenOmitWhenReplacementFails() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) return response("{}", HttpStatus.OK);
            if ("note_lib_challenge_verifier_replacement".equals(schema)) return response("{}", HttpStatus.BAD_GATEWAY);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).isEmpty();
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6.1-sol").get()).isEqualTo(2);
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6-luna").get()).isEqualTo(2);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_challenge_verifier_replacement")))
                .hasSize(1);
    }

    @Test
    void oneFailedCheckerIsFailClosedButGenerationReturnsNormallyForAsyncCaller() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) {
                if ("gpt-6-luna".equals(body.path("model").asText())) return response("{}", HttpStatus.OK);
                return response(verdict(true), HttpStatus.OK);
            }
            if ("note_lib_challenge_verifier_replacement".equals(schema))
                return response(generated(normal()), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).extracting(QuizItem::question).containsExactly(REPLACEMENT);
        assertThat(calls.get("note_lib_challenge_quiz:gpt-4.1-mini").get()).isEqualTo(1);
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6-luna").get()).isEqualTo(2);
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6.1-sol").get()).isEqualTo(1);
        assertThat(calls.get("note_lib_challenge_verifier_replacement:gpt-4.1-mini").get()).isEqualTo(1);
    }

    @Test
    void checkerHttpFailureRetriesOnceAndRejectsEvenWhenTheOtherCheckerAgrees() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) {
                if ("gpt-6-luna".equals(body.path("model").asText()))
                    return response("{\"error\":\"unavailable\"}", HttpStatus.BAD_GATEWAY);
                return response(verdict(true), HttpStatus.OK);
            }
            if ("note_lib_challenge_verifier_replacement".equals(schema))
                return response(generated(normal()), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).extracting(QuizItem::question).containsExactly(REPLACEMENT);
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6-luna").get()).isEqualTo(2);
        assertThat(calls.get("note_lib_statement_pair_verifier:gpt-6.1-sol").get()).isEqualTo(1);
    }

    @Test
    void r1RetryCarriesItsOwnVerdictsIntoTheVerifier() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode invalid = pair();
        ((ObjectNode) invalid.get("statementVerdicts")).put("statement1Correct", false);
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_challenge_quiz_consistency_retry"))
                return response(generated(pair()), HttpStatus.OK);
            if (schema(body).equals("note_lib_statement_pair_verifier"))
                return response(verdict(true), HttpStatus.OK);
            return response(generated(invalid), HttpStatus.OK);
        });
        assertThat(start(service)).extracting(QuizItem::question).containsExactly(STEM);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                .hasSize(2);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_challenge_verifier_replacement")))
                .isEmpty();
    }

    @Test
    void r1RetryWithoutVerdictsIsOmittedWithMissingVerdictsReason() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode invalid = pair();
        ((ObjectNode) invalid.get("statementVerdicts")).put("statement1Correct", false);
        ObjectNode missing = pair();
        missing.putNull("statementVerdicts");
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpenAiLlmStudyPackService service = service(body -> response(
                    schema(body).equals("note_lib_challenge_quiz_consistency_retry")
                            ? generated(missing) : generated(invalid), HttpStatus.OK));
            assertThat(start(service)).isEmpty();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("reason=MISSING_VERDICTS"));
            assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                    .isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void statementPairReplacementIsOmittedWithoutSecondVerifierRound() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) return response(verdict(false), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).isEmpty();
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                .hasSize(2);
    }

    @Test
    void replacementWithOnlyStatementOneMarkerIsOmitted() throws Exception {
        assertPartialMarkerReplacementIsOmitted("Statement 1: What happens to water when it freezes?");
    }

    @Test
    void replacementWithOnlyStatementTwoRomanMarkerIsOmitted() throws Exception {
        assertPartialMarkerReplacementIsOmitted("Statement II: What happens to water when it freezes?");
    }

    private void assertPartialMarkerReplacementIsOmitted(String question) throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema)) return response(verdict(false), HttpStatus.OK);
            if ("note_lib_challenge_verifier_replacement".equals(schema))
                return response(generated(normal().put("question", question)), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });

        assertThat(start(service)).isEmpty();
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_challenge_verifier_replacement")))
                .hasSize(1);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                .hasSize(2);
    }

    @Test
    void twoRejectedItemsUseOneBatchedReplacementCall() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode initial = mapper.createObjectNode();
        initial.putArray("questions").add(pair()).add(pair());
        ObjectNode replacements = mapper.createObjectNode();
        replacements.putArray("questions").add(normal()).add(normal());
        OpenAiLlmStudyPackService service = service(body -> {
            String schema = schema(body);
            if ("note_lib_statement_pair_verifier".equals(schema))
                return response(verdictForCount(2, false), HttpStatus.OK);
            if ("note_lib_challenge_verifier_replacement".equals(schema))
                return response(replacements.toString(), HttpStatus.OK);
            return response(initial.toString(), HttpStatus.OK);
        });
        List<QuizItem> result = service.generateChallengeQuiz("Water", "Water summary", List.of("Water"),
                List.of(), 2, "medium", context(), NOTE, ChallengeVerifierPath.LIVE_START).quizItems();
        assertThat(result).hasSize(2).allMatch(item -> item.question().equals(REPLACEMENT));
        List<JsonNode> replacementCalls = requests.stream().filter(r ->
                schema(r).equals("note_lib_challenge_verifier_replacement")).toList();
        assertThat(replacementCalls).hasSize(1);
        assertThat(replacementCalls.getFirst().path("input").toString())
                .contains("exactly 2 replacement", "1. Concept: Water", "2. Concept: Water");
        assertThat(replacementCalls.getFirst().path("text").path("format").path("schema")
                .path("properties").path("questions").path("minItems").asInt()).isEqualTo(2);
    }

    @Test
    void blankNoteRejectsWithoutCallingEitherChecker() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                return response(generated(normal()), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        List<QuizItem> result = service.generateChallengeQuiz("Water", "Water summary", List.of("Water"),
                List.of(), 1, "medium", context(), " ", ChallengeVerifierPath.LIVE_START).quizItems();
        assertThat(result).extracting(QuizItem::question).containsExactly(REPLACEMENT);
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier"))).isEmpty();
    }

    @Test
    void unparseablePairBeforeValidPairDoesNotLeaveAGapInCheckerIds() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode unparseable = pair().put("question",
                "Statement 1: Statement 2: Water is a metal. Which is correct?");
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_statement_pair_verifier"))
                return response(verdict(true), HttpStatus.OK);
            if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                return response(generated(normal()), HttpStatus.OK);
            return response(generated(unparseable, pair()), HttpStatus.OK);
        });

        List<QuizItem> result = startBatch(service, 2);
        assertThat(result).extracting(QuizItem::question).contains(STEM).doesNotContain(unparseable.path("question").asText());
        assertSubmittedIds("1");
    }

    @Test
    void missingVerdictsRetryBeforeValidPairIsOmittedWithoutRejectingValidPair() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode missing = pair();
        missing.putNull("statementVerdicts");
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_challenge_quiz_consistency_retry"))
                return response(generated(missing), HttpStatus.OK);
            if (schema(body).equals("note_lib_statement_pair_verifier"))
                return response(verdict(true), HttpStatus.OK);
            return response(generated(missing, pair()), HttpStatus.OK);
        });

        assertThat(startBatch(service, 2)).extracting(QuizItem::question).containsExactly(STEM);
        assertSubmittedIds("1");
        assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_challenge_verifier_replacement")))
                .isEmpty();
    }

    @Test
    void blankNoteRejectsEveryStatementPairInBatchWithoutCheckerCalls() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpenAiLlmStudyPackService service = service(body -> {
                if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                    return response("{}", HttpStatus.BAD_GATEWAY);
                return response(generated(pair(), pair()), HttpStatus.OK);
            });

            assertThat(service.generateChallengeQuiz("Water", "Water summary", List.of("Water"), List.of(),
                    2, "medium", context(), " ", ChallengeVerifierPath.LIVE_START).quizItems()).isEmpty();
            assertThat(requests.stream().filter(r -> schema(r).equals("note_lib_statement_pair_verifier")))
                    .isEmpty();
            assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.startsWith("action=statement_pair_verifier_decision "))
                    .filter(message -> message.contains("reason=EXTRACTION_FAILED"))).hasSize(2);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    @Test
    void checkerResultsMapToTheTwoValidPairsAcrossAnUnparseablePair() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode unparseable = pair().put("question",
                "Statement 1: Statement 2: Water is a metal. Which is correct?");
        ObjectNode pairB = pair().put("question",
                "Statement 1: Water is a metal. Statement 2: Water expands when it freezes. Which is correct?")
                .put("answer", "C");
        pairB.putObject("statementVerdicts").put("statement1Correct", false).put("statement2Correct", true);
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpenAiLlmStudyPackService service = service(body -> {
                if (schema(body).equals("note_lib_statement_pair_verifier"))
                    return response(verdictForCount(2, true), HttpStatus.OK);
                if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                    return response(generated(normal(), normal()), HttpStatus.OK);
                return response(generated(pair(), unparseable, pairB), HttpStatus.OK);
            });

            List<QuizItem> result = startBatch(service, 3);
            assertSubmittedIds("1", "2");
            assertThat(result).extracting(QuizItem::question).contains(STEM)
                    .doesNotContain(pairB.path("question").asText(), unparseable.path("question").asText());
            String pairBHash = String.valueOf(StringNormalizationUtils.normalizeForDuplicateCheck(
                    pairB.path("question").asText()).hashCode());
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("outcome=rejected", "reason=MISMATCH", "questionHash=" + pairBHash));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    @Test
    void replacementStillPassesTheH4AnswerExplanationCheck() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        ObjectNode invalid = mapper.createObjectNode().put("question", "What is 5 plus 5?")
                .put("answer", "A").put("explanation", "The answer is 20.")
                .put("concept", "Arithmetic").put("questionFormat", "MCQ");
        invalid.putArray("choices").add("10").add("20").add("30").add("40");
        invalid.putNull("statementVerdicts");
        OpenAiLlmStudyPackService service = service(body -> {
            if (schema(body).equals("note_lib_statement_pair_verifier"))
                return response(verdictForCount(1, false), HttpStatus.OK);
            if (schema(body).equals("note_lib_challenge_verifier_replacement"))
                return response(generated(invalid), HttpStatus.OK);
            return response(generated(pair()), HttpStatus.OK);
        });
        assertThat(start(service)).isEmpty();
        assertThat(calls.get("note_lib_challenge_verifier_replacement:gpt-4.1-mini").get()).isEqualTo(1);
    }

    @Test
    void moreHasExclusionOnlyAndNeverCallsVerifierEvenWithSwitchesOn() throws Exception {
        properties.getLlm().getStatementPairVerifier().setLiveStartEnabled(true);
        properties.getLlm().getStatementPairVerifier().setTemplateSeedEnabled(true);
        OpenAiLlmStudyPackService service = service(body -> response(generated(pair()), HttpStatus.OK));
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<QuizItem> result = service.generateMoreChallengeQuiz("Water", "Water summary", List.of("Water"),
                    List.of(), List.of(), 1, "medium", context()).quizItems();
            assertThat(result).hasSize(1);
            assertThat(requests).hasSize(1);
            assertThat(requests.getFirst().path("input").toString()).contains("For this batch only");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("statementPairLeaked=1"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    @Test
    void moreLeakCountIncludesStatementPairReturnedByConsistencyRetry() throws Exception {
        ObjectNode inconsistent = mapper.createObjectNode().put("question", "What is 5 plus 5?")
                .put("answer", "A").put("explanation", "The answer is 20.")
                .put("concept", "Arithmetic").put("questionFormat", "MCQ");
        inconsistent.putArray("choices").add("10").add("20").add("30").add("40");
        inconsistent.putNull("statementVerdicts");
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpenAiLlmStudyPackService service = service(body -> response(
                    schema(body).equals("note_lib_challenge_quiz_consistency_retry")
                            ? generated(pair()) : generated(inconsistent), HttpStatus.OK));
            List<QuizItem> result = service.generateMoreChallengeQuiz("Water", "Water summary",
                    List.of("Water"), List.of(), List.of(), 1, "medium", context()).quizItems();
            assertThat(result).extracting(QuizItem::question).containsExactly(STEM);
            assertThat(requests).hasSize(2);
            assertThat(requests).extracting(this::schema)
                    .containsExactly("note_lib_challenge_quiz", "note_lib_challenge_quiz_consistency_retry");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("action=challenge_quiz_generation_counts", "statementPairLeaked=1"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    private List<QuizItem> start(OpenAiLlmStudyPackService service) {
        return service.generateChallengeQuiz("Water", "Water summary", List.of("Water"), List.of(),
                1, "medium", context(), NOTE, ChallengeVerifierPath.LIVE_START).quizItems();
    }

    private List<QuizItem> startBatch(OpenAiLlmStudyPackService service, int count) {
        return service.generateChallengeQuiz("Water", "Water summary", List.of("Water"), List.of(),
                count, "medium", context(), NOTE, ChallengeVerifierPath.LIVE_START).quizItems();
    }

    private void assertSubmittedIds(String... expectedIds) throws Exception {
        List<JsonNode> verifierRequests = requests.stream()
                .filter(request -> schema(request).equals("note_lib_statement_pair_verifier")).toList();
        assertThat(verifierRequests).hasSize(2);
        for (JsonNode request : verifierRequests) {
            String user = request.path("input").get(2).path("content").get(0).path("text").asText();
            JsonNode items = mapper.readTree(user.substring(user.indexOf("Items:\n") + 7).trim());
            assertThat(items).extracting(item -> item.path("itemId").asText()).containsExactly(expectedIds);
        }
    }

    private void assertCallFailedDecision(Runnable verification) {
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            verification.run();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("reason=CALL_FAILED"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(priorLevel);
            appender.stop();
        }
    }

    private StudyPackGenerationContext context() {
        return new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Science", "Physics", List.of())
                .withRouting(LlmRouting.interactiveQuiz());
    }

    private OpenAiLlmStudyPackService service(Function<JsonNode, MockClientHttpResponse> responder) {
        return service(responder, new SimpleAsyncTaskExecutor("verifier-test-"));
    }

    private OpenAiLlmStudyPackService service(Function<JsonNode, MockClientHttpResponse> responder,
            AsyncTaskExecutor executor) {
        return service(responder, new SimpleAsyncTaskExecutor("llm-parallel-test-"), executor);
    }

    private OpenAiLlmStudyPackService service(Function<JsonNode, MockClientHttpResponse> responder,
            AsyncTaskExecutor generationExecutor, AsyncTaskExecutor verifierExecutor) {
        properties.getLlm().getApi().setApiKey("stub-key");
        RestClient client = RestClient.builder()
                .requestFactory((uri, method) -> new MockClientHttpRequest(method, uri))
                .requestInterceptor((request, body, execution) -> {
                    JsonNode parsed = mapper.readTree(new String(body, StandardCharsets.ISO_8859_1));
                    requests.add(parsed);
                    calls.computeIfAbsent(schema(parsed) + ":" + parsed.path("model").asText(),
                            ignored -> new AtomicInteger()).incrementAndGet();
                    return responder.apply(parsed);
                }).build();
        return new OpenAiLlmStudyPackService(properties, mapper, client,
                new OpenAiLlmConfig().openAiPromptResources(mapper, properties),
                new LlmModelRouter(properties, mock(SubscriptionService.class), mock(UserRepository.class)),
                generationExecutor, verifierExecutor);
    }

    private String schema(JsonNode body) {
        return body.path("text").path("format").path("name").asText();
    }

    private MockClientHttpResponse response(String output, HttpStatus status) {
        try {
            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("model", "stub").put("output_text", output);
            return new MockClientHttpResponse(mapper.writeValueAsBytes(envelope), status);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String generated(ObjectNode... items) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode questions = root.putArray("questions");
            for (ObjectNode item : items) questions.add(item);
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ObjectNode pair() {
        ObjectNode item = mapper.createObjectNode().put("question", STEM).put("answer", "B")
                .put("explanation", "Water expands on freezing, and it is not a metal.").put("concept", "Water")
                .put("questionFormat", "MCQ");
        ArrayNode choices = item.putArray("choices");
        choices.add("Both statements are correct").add("Only Statement 1 is correct")
                .add("Only Statement 2 is correct").add("Neither statement is correct");
        item.putObject("statementVerdicts").put("statement1Correct", true).put("statement2Correct", false);
        return item;
    }

    private ObjectNode normal() {
        ObjectNode item = mapper.createObjectNode().put("question", REPLACEMENT).put("answer", "A")
                .put("explanation", "Water expands when it freezes.").put("concept", "Water")
                .put("questionFormat", "MCQ");
        item.putArray("choices").add("It expands").add("It becomes metal").add("It vanishes").add("It burns");
        item.putNull("statementVerdicts");
        return item;
    }

    private String verdict(boolean correct) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ObjectNode row = root.putArray("results").addObject().put("itemId", "1");
            row.set("statement1", judgment("SUPPORTED", "Water expands when it freezes", correct ? "TRUE" : "FALSE"));
            row.set("statement2", judgment("CONTRADICTED", "Water is a liquid, not a metal", "FALSE"));
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String verdictForCount(int count, boolean correct) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode results = root.putArray("results");
        for (int i = 1; i <= count; i++) {
            ObjectNode row = results.addObject().put("itemId", String.valueOf(i));
            row.set("statement1", judgment(correct ? "SUPPORTED" : "SILENT",
                    correct ? "Water expands when it freezes" : null, correct ? "TRUE" : "FALSE"));
            row.set("statement2", judgment("CONTRADICTED", "Water is a liquid, not a metal", "FALSE"));
        }
        return root.toString();
    }

    private ObjectNode judgment(String support, String quote, String verdict) {
        ObjectNode node = mapper.createObjectNode().put("sourceSupport", support)
                .put("reason", "The note decides it.");
        if (quote == null) node.putNull("evidenceQuote"); else node.put("evidenceQuote", quote);
        return node
                .put("noteConflictsWithStandardKnowledge", false).put("verdict", verdict);
    }
}
