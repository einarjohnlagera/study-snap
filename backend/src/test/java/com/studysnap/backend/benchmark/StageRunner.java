package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in section 5/7 spend-safe runner for already-built case calls. */
final class StageRunner {
    private static final String MALFORMED_CAPS = "Malformed llm.benchmark.stageCaps";
    private static final String FALSE = "false";
    record RunInput(String model, String key, StageCapLedger.Stage stage,
                    Map<StageCapLedger.Stage, BigDecimal> approvedCaps,
                    Map<String, ForecastReservation> forecasts, BigDecimal priceIn,
                    BigDecimal priceOut, Path inputPath, Path outputDir) {
        RunInput(String model, String key, StageCapLedger.Stage stage,
                 Map<String, ForecastReservation> forecasts, BigDecimal priceIn,
                 BigDecimal priceOut, Path inputPath, Path outputDir) {
            this(model, key, stage, null, forecasts, priceIn, priceOut, inputPath, outputDir);
        }
    }
    record AttemptRef(String generationId, int attempt) {}
    record CallResult(boolean delivered, Set<AttemptRef> parseSchemaFailures, String failureClass,
                      List<String> callGroupIds, Set<Integer> parseFailureCallIndices,
                      List<AttemptOutcome.Kind> signalKinds, List<Integer> signalAttempts) {
        CallResult(boolean delivered, Set<AttemptRef> parseSchemaFailures, String failureClass) {
            this(delivered, parseSchemaFailures, failureClass, List.of(), Set.of(), List.of(), List.of());
        }
    }
    interface CaseCall {
        String id();
        String cellId();
        String generationId();
        CallResult execute(RestClient client, StudySnapProperties properties,
                           ArmAssertingInterceptor path) throws Exception;
    }
    interface RunPlanProvider {
        RunInput input();
        List<CaseCall> cases();
    }
    record RunSummary(List<CaseArmOutcome> outcomes, StageCapLedger ledger) {}

    @Test void stageRun() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.stageRun"), "opt-in stage runner skipped");
        String providerClass = System.getProperty("llm.benchmark.stageRun.provider");
        if (providerClass == null || providerClass.isBlank())
            throw new IllegalArgumentException("llm.benchmark.stageRun.provider is required");
        Map<StageCapLedger.Stage, BigDecimal> caps = parseCaps(requiredProperty("llm.benchmark.stageCaps"));
        Path file = Path.of(requiredProperty("llm.benchmark.stageLedger"));
        String initProperty = System.getProperty("llm.benchmark.stageLedger.init", FALSE);
        if (!"true".equals(initProperty) && !FALSE.equals(initProperty))
            throw new IllegalArgumentException("llm.benchmark.stageLedger.init must be true or false");
        RunPlanProvider provider = (RunPlanProvider) Class.forName(providerClass).getDeclaredConstructor().newInstance();
        RunInput input = provider.input();
        LlmModelBenchmark.Config.requireKeyForTest(input.key());
        if (input.approvedCaps() != null)
            throw new IllegalArgumentException("RunPlanProvider must not supply caps; use llm.benchmark.stageCaps");
        try (StageCapLedger ledger = StageCapLedger.open(file, caps, Boolean.parseBoolean(initProperty))) {
            run(input, null, provider.cases(), ledger);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
    static Map<StageCapLedger.Stage, BigDecimal> parseCaps(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("llm.benchmark.stageCaps is required");
        Map<StageCapLedger.Stage, BigDecimal> caps = new EnumMap<>(StageCapLedger.Stage.class);
        for (String entry : text.split(",", -1)) {
            String[] parts = entry.split(":", -1);
            if (parts.length != 2 || !parts[1].matches("[0-9]+(\\.[0-9]+)?"))
                throw new IllegalArgumentException(MALFORMED_CAPS);
            StageCapLedger.Stage stage;
            try { stage = StageCapLedger.Stage.valueOf(parts[0]); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException(MALFORMED_CAPS, ex); }
            BigDecimal cap = new BigDecimal(parts[1]);
            if (cap.signum() <= 0 || caps.putIfAbsent(stage, cap) != null)
                throw new IllegalArgumentException(MALFORMED_CAPS);
        }
        return Map.copyOf(caps);
    }

    static RunSummary run(RunInput input, ClientHttpRequestFactory requestFactory,
                          List<CaseCall> cases) throws Exception {
        if (input.approvedCaps() == null)
            throw new IllegalArgumentException("File-backed ledger required when RunInput carries no caps");
        return run(input, requestFactory, cases, new StageCapLedger(input.approvedCaps()));
    }

    static RunSummary run(RunInput input, ClientHttpRequestFactory requestFactory,
                          List<CaseCall> cases, StageCapLedger ledger) throws Exception {
        LlmModelBenchmark.Config.requireKeyForTest(input.key());
        if (input.approvedCaps() != null
                && !ledger.approvedCaps().equals(new StageCapLedger(input.approvedCaps()).approvedCaps()))
            throw new IllegalArgumentException("Ledger caps differ from run input");
        if (input.priceIn() == null || input.priceOut() == null
                || input.priceIn().signum() < 0 || input.priceOut().signum() < 0)
            throw new IllegalArgumentException("Prices must be nonnegative");
        for (ForecastReservation forecast : input.forecasts().values())
            if (forecast.priceIn().compareTo(input.priceIn()) != 0
                    || forecast.priceOut().compareTo(input.priceOut()) != 0)
                throw new IllegalArgumentException("Forecast prices differ from meter prices");
        if (ledger.cap(input.stage()) == null) throw new StageCapLedger.SpendStop("No approved cap for " + input.stage());
        Path repo = findRepo(Path.of("").toAbsolutePath().normalize());
        if (input.inputPath() != null) LlmModelBenchmark.Config.rejectInsideRepoForTest(input.inputPath(), repo);
        LlmModelBenchmark.Config.rejectInsideRepoForTest(input.outputDir(), repo);
        StudySnapProperties properties = ArmRouting.forModel(input.model(), input.key());
        requireAllSlots(properties);
        Files.createDirectories(input.outputDir());
        ObjectMapper mapper = new ObjectMapper();
        ArmAssertingInterceptor path = new ArmAssertingInterceptor(mapper, input.model(), input.key(),
                input.outputDir(), ledger, input.stage(), input.priceIn(), input.priceOut(), input.forecasts());
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory == null ? productionFactory(properties) : requestFactory)
                .baseUrl(properties.getLlm().getApi().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + input.key())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(path).build();
        List<CaseArmOutcome> outcomes = new ArrayList<>();
        for (CaseCall item : cases) {
            if (ledger.stopped()) {
                outcomes.add(CaseArmOutcome.classify(item.id(), List.of(), false, true, "INCONCLUSIVE"));
                continue;
            }
            int before = path.calls().size();
            CallResult result = null;
            String failure = null;
            try {
                path.begin(item.generationId(), item.cellId());
                result = item.execute(client, properties, path);
            } catch (Exception ex) {
                failure = ex instanceof ArmAssertingInterceptor.ArmMismatch ? "ARM_MISMATCH" : "HTTP_ERROR";
            } finally {
                path.end();
            }
            List<MeteringInterceptor.Call> newCalls = path.calls().subList(before, path.calls().size());
            Set<AttemptRef> parseFailures = result == null ? Set.of() : result.parseSchemaFailures();
            List<AttemptOutcome> attempts = new ArrayList<>();
            for (int index = 0; index < newCalls.size(); index++) {
                MeteringInterceptor.Call call = newCalls.get(index);
                boolean parseFailure = parseFailures.contains(new AttemptRef(call.generationId(), call.attempt()))
                        || result != null && result.parseFailureCallIndices().contains(index);
                AttemptOutcome original = AttemptOutcome.from(call, parseFailure);
                String groupId = result != null && index < result.callGroupIds().size()
                        ? result.callGroupIds().get(index) : original.generationId();
                AttemptOutcome.Kind signalKind = result != null && index < result.signalKinds().size()
                        ? result.signalKinds().get(index) : AttemptOutcome.Kind.OK;
                AttemptOutcome.Kind kind = signalKind == AttemptOutcome.Kind.OK ? original.kind() : signalKind;
                int signalAttempt = result != null && index < result.signalAttempts().size()
                        ? result.signalAttempts().get(index) : original.attempt();
                attempts.add(new AttemptOutcome(groupId, signalAttempt, kind,
                        original.parseSchemaFailure(), original.conservativeUsd(), original.billedUsd()));
            }
            boolean stoppedBeforeCompletion = ledger.stopped()
                    && !("Three consecutive HTTP errors".equals(ledger.stopReason()) && result == null);
            if (result != null && "INCONCLUSIVE_SIGNAL_MISMATCH".equals(result.failureClass()))
                stoppedBeforeCompletion = true;
            String failureClass = result != null ? result.failureClass() : failure;
            outcomes.add(CaseArmOutcome.classify(item.id(), attempts,
                    result != null && result.delivered(), stoppedBeforeCompletion, failureClass));
        }
        writeSummary(input, ledger, outcomes, mapper);
        return new RunSummary(List.copyOf(outcomes), ledger);
    }

    /** Same connect and read timeouts as production (preregistration section 3): a latency or timeout
     * result is only comparable if the benchmark client carries the configured deadlines. */
    static SimpleClientHttpRequestFactory productionFactory(StudySnapProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(properties.getLlm().getApi().getConnectTimeoutSeconds()));
        factory.setReadTimeout(java.time.Duration.ofSeconds(properties.getLlm().getApi().getReadTimeoutSeconds()));
        return factory;
    }

    private static void requireAllSlots(StudySnapProperties properties) {
        var settings = properties.getSettings();
        for (String model : List.of(settings.getModelFree(), settings.getModelPremium(),
                settings.getModelInteractiveQuiz(), settings.getModelPaidInteractive(), settings.getModelCritique()))
            if (model == null || model.isBlank()) throw new IllegalArgumentException("Every arm model slot is required");
    }
    static Path findRepo(Path start) {
        for (Path path = start; path != null; path = path.getParent())
            if (Files.exists(path.resolve(".git"))) return path;
        throw new IllegalArgumentException("Cannot locate git working tree");
    }
    private static void writeSummary(RunInput input, StageCapLedger ledger,
                                     List<CaseArmOutcome> outcomes, ObjectMapper mapper) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> stages = new LinkedHashMap<>();
        for (StageCapLedger.Stage stage : StageCapLedger.Stage.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spent", ledger.spent(stage)); row.put("billed", ledger.billed(stage));
            row.put("inFlight", ledger.inFlight(stage)); row.put("attempts", ledger.attempts(stage));
            row.put("remaining", ledger.remaining(stage)); stages.put(stage.name(), row);
        }
        summary.put("stages", stages);
        summary.put("cellSpent", ledger.cellSpending());
        summary.put("largestSingleCallOvershoot", ledger.largestOvershoot());
        summary.put("ledgerRecordCount", ledger.recordCount());
        summary.put("unreconciled", ledger.unreconciled());
        if (ledger.createdAt() != null) summary.put("ledgerCreatedAt", ledger.createdAt().toString());
        summary.put("connectTimeoutSeconds", ArmRouting.forModel(input.model(), input.key()).getLlm().getApi().getConnectTimeoutSeconds());
        summary.put("readTimeoutSeconds", ArmRouting.forModel(input.model(), input.key()).getLlm().getApi().getReadTimeoutSeconds());
        summary.put("stopReason", ledger.stopReason());
        summary.put("outcomes", outcomes.stream().map(outcome -> Map.of(
                "caseArmId", outcome.caseArmId(), "state", outcome.state().name(),
                "firstAttemptClean", outcome.firstAttemptClean(), "finalSuccess", outcome.finalSuccess(),
                "retried", outcome.retried())).toList());
        summary.put("inconclusive", outcomes.stream().filter(o -> o.state() == CaseArmOutcome.State.INCONCLUSIVE)
                .map(CaseArmOutcome::caseArmId).toList());
        String text = mapper.writeValueAsString(summary).replace(input.key(), "[REDACTED]");
        Files.writeString(input.outputDir().resolve("summary.json"), text);
    }
}
