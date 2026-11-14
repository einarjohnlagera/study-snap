package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;

/** Sections 5, 6 and 10 ordered primary and triggered-reserve execution provider. */
final class Stage1RunPlan implements StageRunner.RunPlanProvider {
    static final class InvalidRunPlan extends RuntimeException {
        InvalidRunPlan(String message) { super(message); }
        InvalidRunPlan(String message, Throwable cause) { super(message, cause); }
    }
    interface OfflineTransport {
        MockClientHttpRequest createMockRequest(java.net.URI uri, HttpMethod method) throws IOException;
    }
    record Config(StageRunner.RunInput input, String arm, Path freezeFile, Path parametersFile,
                  Map<String, Path> manifests, String reserveWorkflow, boolean explicitlySelected,
                  List<Path> baselineReceipts, Path receiptFile, FreezeRecord dryRecord) {}
    private record RunFile(String arm, String model, StageCapLedger.Stage stage,
                           BigDecimal priceIn, BigDecimal priceOut,
                           Map<String, ForecastReservation> forecasts, Path outputDir,
                           Path receiptFile, Path freezeFile, Path parametersFile,
                           Map<String, Path> manifests, String reserveWorkflow,
                           boolean explicitlySelected, List<Path> baselineReceipts) {}
    private record Row(String id, String workflow, boolean reserve, boolean classA, String hash, String raw) {}
    private static final String CLASS_A_PRIMARY = "classA.primary";
    private static final String CLASS_B_PRIMARY = "classB.primary";
    private static final String CLASS_A_RESERVE = "classA.reserve";
    private static final String CLASS_B_RESERVE = "classB.reserve";
    private static final String DRY_RUN = "DRY_RUN";
    private static final String LUNA = "LUNA";
    private static final String SOL = "SOL";
    private static final String RESERVE = "reserve";
    private static final List<String> FILE_ORDER = List.of(CLASS_A_PRIMARY, CLASS_B_PRIMARY,
            CLASS_A_RESERVE, CLASS_B_RESERVE);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final Config config;
    private FreezeGuard.Verified verified;
    private FrozenGateParameters parameters;
    private List<Row> planned;
    private String caseSetHash;
    private Instant started;

    Stage1RunPlan() {
        this(readConfig());
    }

    Stage1RunPlan(Config config) { this.config = config; }

    @Override public StageRunner.RunInput input() { return config.input(); }

    @Override public FreezeGuard.Verified verify() {
        if (config.freezeFile() == null) throw new FreezeRecord.InvalidFreeze("Real run requires freeze record");
        return prepare(false);
    }

    FreezeGuard.Verified verifyFresh() {
        if (config.freezeFile() == null)
            throw new FreezeRecord.InvalidFreeze("Real recheck requires freeze record");
        prepare(false);
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        List<String> workflows = planned.stream().map(Row::workflow).distinct().toList();
        FreezeGuard.Verified fresh = FreezeGuard.verify(config.freezeFile(), repo, config.arm(),
                input().model(), config.manifests(), workflows);
        if (!fresh.sha256().equals(verified.sha256()) || !fresh.head().equals(verified.head()))
            throw new FreezeRecord.InvalidFreeze("Freeze changed since run-plan preparation");
        verified = fresh;
        return fresh;
    }

    private FreezeGuard.Verified prepare(boolean dry) {
        if (planned != null) return verified;
        try {
            var input = config.input();
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            if (config.receiptFile() == null || config.receiptFile().getParent() == null
                    || !Files.isDirectory(config.receiptFile().getParent()))
                throw new InvalidRunPlan("Receipt directory must exist before dispatch");
            LlmModelBenchmark.Config.rejectInsideRepoForTest(config.receiptFile(), repo);
            parameters = FrozenGateParameters.load(config.parametersFile());
            Map<String, List<Row>> byFile = new LinkedHashMap<>();
            for (String key : FILE_ORDER) {
                Path file = config.manifests().get(key);
                if (file == null) throw new InvalidRunPlan("Missing manifest " + key);
                byFile.put(key, readRows(file, key));
            }
            List<Row> allPrimary = new ArrayList<>();
            allPrimary.addAll(byFile.get(CLASS_A_PRIMARY));
            allPrimary.addAll(byFile.get(CLASS_B_PRIMARY));
            if (!dry) validateCounts(byFile);
            StringBuilder signature = new StringBuilder();
            for (Row row : allPrimary) signature.append(row.id()).append(':').append(row.hash()).append('\n');
            caseSetHash = FreezeRecord.sha256(signature.toString().getBytes(StandardCharsets.UTF_8));
            FreezeRecord record = dry ? config.dryRecord() : FreezeRecord.load(config.freezeFile());
            if (record == null) throw new InvalidRunPlan("DRY_RUN requires a synthetic arm table");
            if (!dry && !parameters.sha256.equals(record.parametersSha256()))
                throw new FreezeRecord.InvalidFreeze("External parameter bytes differ from frozen parameters");
            ArmPlan arms = new ArmPlan(record, parameters);
            StageCapLedger capCheck = new StageCapLedger(input.approvedCaps() == null
                    ? StageRunner.parseCaps(System.getProperty("llm.benchmark.stageCaps")) : input.approvedCaps());
            List<String> selected = arms.workflows(config.arm(), input.model(), config.explicitlySelected(), capCheck);
            boolean reserve = config.reserveWorkflow() != null;
            if (reserve && input.stage() != StageCapLedger.Stage.STAGE_2)
                throw new InvalidRunPlan("Reserve confirmation requires STAGE_2");
            if (config.arm().startsWith(SOL) && input.stage() != StageCapLedger.Stage.STAGE_3 && !reserve)
                throw new InvalidRunPlan("SOL primary run requires STAGE_3");
            if (!reserve && !config.arm().startsWith(SOL) && input.stage() != StageCapLedger.Stage.STAGE_1)
                throw new InvalidRunPlan("Primary baseline and LUNA require STAGE_1");
            if (reserve && !selected.contains(config.reserveWorkflow()))
                throw new InvalidRunPlan("Reserve workflow is not on selected arm");
            if (reserve) requireTrigger(config.reserveWorkflow(), record, dry, allPrimary);
            if (config.arm().equals(LUNA) && !reserve) {
                List<Stage1Receipt> baselines = config.baselineReceipts().stream().map(Stage1Receipt::read).toList();
                Map<String, String> plannedHashes = new HashMap<>();
                for (Row row : allPrimary) plannedHashes.put(row.id(), row.hash());
                Stage1Receipt.requireCompleteBaselines(baselines, caseSetHash, plannedHashes,
                        record, dry ? DRY_RUN : FreezeRecord.sha256(config.freezeFile()),
                        dry ? DRY_RUN : record.gitCommit());
            }
            List<Row> candidates = reserve ? List.of() : allPrimary;
            if (reserve) {
                candidates = new ArrayList<>();
                candidates.addAll(byFile.get(CLASS_A_RESERVE));
                candidates.addAll(byFile.get(CLASS_B_RESERVE));
            }
            List<Row> selectedRows = candidates.stream().filter(row -> selected.contains(row.workflow())
                    && (!reserve || row.workflow().equals(config.reserveWorkflow()))).toList();
            if (selectedRows.isEmpty()) throw new InvalidRunPlan("No cases for selected arm");
            if (!dry) {
                verified = FreezeGuard.verify(config.freezeFile(), repo, config.arm(), input.model(),
                        config.manifests(), selected);
                if (!verified.record().equals(record)) throw new FreezeRecord.InvalidFreeze("Freeze changed during verification");
            }
            planned = selectedRows;
            return verified;
        } catch (IOException exception) {
            throw new InvalidRunPlan("Cannot prepare run plan", exception);
        }
    }

    private void requireTrigger(String workflow, FreezeRecord record, boolean dry,
                                List<Row> allPrimary) throws IOException {
        if (config.baselineReceipts() == null || config.baselineReceipts().size() != 3)
            throw new Stage1Receipt.ProtocolDeviation("Reserve requires baseline-versus-candidate receipts");
        List<Stage1Receipt> receipts = config.baselineReceipts().stream().map(Stage1Receipt::read).toList();
        Map<String, String> hashes = new HashMap<>();
        allPrimary.forEach(row -> hashes.put(row.id(), row.hash()));
        String freezeHash = dry ? DRY_RUN : FreezeRecord.sha256(config.freezeFile());
        String head = dry ? DRY_RUN : record.gitCommit();
        Stage1Receipt.requireCompleteBaselines(receipts.subList(0, 2), caseSetHash,
                hashes, record, freezeHash, head);
        Stage1Receipt candidate = receipts.get(2);
        if (!candidate.arm().equals(LUNA) || !candidate.complete()
                || !caseSetHash.equals(candidate.caseSetSha256())
                || !freezeHash.equals(candidate.freezeSha256()) || !head.equals(candidate.head())
                || candidate.cases().size() != hashes.size()
                || !candidate.cases().stream().map(Stage1Receipt.CaseRow::id).collect(java.util.stream.Collectors.toSet())
                    .equals(hashes.keySet())
                || candidate.cases().stream().anyMatch(row -> !row.manifestRowSha256().equals(hashes.get(row.id())))
                || candidate.triggers().get(workflow) != RouteVerdictCalculator.Tier.T2)
            throw new Stage1Receipt.ProtocolDeviation("Reserve lacks recorded T2 trigger for " + workflow);
        Stage1Receipt baseline = receipts.subList(0, 2).stream()
                .filter(receipt -> receipt.cases().stream().anyMatch(row -> workflow.equals(row.workflow())))
                .findFirst().orElseThrow(() -> new Stage1Receipt.ProtocolDeviation(
                        "No baseline for reserve workflow " + workflow));
        Set<String> paired = new HashSet<>();
        for (Stage1Receipt.CaseRow row : candidate.cases())
            if (workflow.equals(row.workflow())) paired.add(row.id());
        if (paired.isEmpty() || baseline.cases().stream().noneMatch(row -> paired.contains(row.id()))
                || Stage1Receipt.compare(baseline, candidate).stream()
                    .anyMatch(result -> paired.contains(result.caseId())
                            && result.result() != Stage1Receipt.Comparison.MATCH))
            throw new Stage1Receipt.ProtocolDeviation("Reserve trigger lacks comparable paired inputs for " + workflow);
    }

    @Override public List<StageRunner.CaseCall> cases() {
        if (planned == null || config.freezeFile() != null && verified == null)
            throw new FreezeRecord.InvalidFreeze("Verify freeze before building adapters");
        started = Instant.now();
        List<StageRunner.CaseCall> calls = new ArrayList<>();
        for (Row row : planned) {
            if (!row.classA()) {
                ClassBCaseManifest.Case item;
                try { item = JSON.readValue(row.raw(), ClassBCaseManifest.Case.class); }
                catch (IOException exception) { throw new InvalidRunPlan("Malformed class-B case " + row.id(), exception); }
                calls.add(switch (item.workflow()) {
                    case STUDY_TIP -> new QuickReviewStudyTipAdapter(item);
                    case COLLECTION_COMPANION -> new CollectionCompanionAdapter(item);
                    case ASK_COMPANION -> new AskCompanionAdapter(item);
                    case INTERVIEW_CRITIQUE -> new InterviewCritiqueAdapter(item);
                });
            } else {
                ClassACaseManifest.Case item;
                try { item = JSON.readValue(row.raw(), ClassACaseManifest.Case.class); }
                catch (IOException exception) { throw new InvalidRunPlan("Malformed class-A case " + row.id(), exception); }
                calls.add(ClassAAdapters.from(item));
            }
        }
        return List.copyOf(calls);
    }

    Map<String, String> currentManifestHashes() {
        if (planned == null) throw new FreezeRecord.InvalidFreeze("Verify freeze before reading manifest hashes");
        Map<String, String> hashes = new LinkedHashMap<>();
        planned.forEach(row -> hashes.put(row.id(), row.hash()));
        return Map.copyOf(hashes);
    }

    @Override public void afterRun(StageRunner.RunSummary summary) throws IOException {
        Map<String, String> hashes = new HashMap<>();
        Map<String, String> workflows = new HashMap<>();
        planned.forEach(row -> { hashes.put(row.id(), row.hash()); workflows.put(row.id(), row.workflow()); });
        DeliveredOutputs.Written delivered = DeliveredOutputs.write(
                input().outputDir().resolve("delivered-outputs.jsonl"), config.arm(), 0,
                summary.outcomes(), workflows, hashes, summary.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(verified, config.arm(), input().model(),
                input().stage(), started == null ? Instant.now() : started,
                caseSetHash, hashes, workflows, summary, delivered);
        receipt.write(config.receiptFile());
    }

    Stage1Receipt dryRun(OfflineTransport transport, StageCapLedger ledger) throws Exception {
        if (config.freezeFile() != null || config.dryRecord() == null
                || !input().key().startsWith("fake-"))
            throw new InvalidRunPlan("DRY_RUN needs a fake key, synthetic arm table and no freeze file");
        prepare(true);
        started = Instant.now();
        ClientHttpRequestFactory sealed = (uri, method) -> {
            if (!"offline.invalid".equals(uri.getHost()))
                throw new InvalidRunPlan("DRY_RUN cannot use a real base URL");
            return transport.createMockRequest(uri, method);
        };
        StageRunner.RunSummary summary = StageRunner.run(input(), sealed, cases(), ledger);
        Map<String, String> hashes = new HashMap<>();
        Map<String, String> workflows = new HashMap<>();
        planned.forEach(row -> { hashes.put(row.id(), row.hash()); workflows.put(row.id(), row.workflow()); });
        DeliveredOutputs.Written delivered = DeliveredOutputs.write(
                input().outputDir().resolve("delivered-outputs.jsonl"), config.arm(), 0,
                summary.outcomes(), workflows, hashes, summary.deliveredByCase());
        Stage1Receipt receipt = Stage1Receipt.fromRun(
                FreezeGuard.dryEvidence(config.dryRecord(), DRY_RUN, DRY_RUN),
                config.arm(), input().model(), input().stage(), started, caseSetHash, hashes, workflows,
                summary, delivered);
        if (config.receiptFile() != null) receipt.write(config.receiptFile());
        return receipt;
    }

    private static List<Row> readRows(Path file, String key) throws IOException {
        List<Row> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean isA = key.startsWith("classA");
        boolean reserve = key.endsWith(RESERVE);
        if (isA) ClassACaseManifest.load(file);
        else ClassBCaseManifest.loadRows(file);
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            JsonNode json = JSON.readTree(line);
            String id = json.path(isA ? "caseId" : "fixtureId").asText();
            if (!id.matches("[A-Za-z0-9._:-]{1,100}"))
                throw new InvalidRunPlan("Case identifier must be opaque and short: " + file);
            String workflow = json.path("workflow").asText();
            workflow = workflowName(workflow);
            if (json.path(RESERVE).asBoolean(!reserve) != reserve || !ids.add(id))
                throw new InvalidRunPlan("Invalid reserve flag or duplicate case in " + file);
            rows.add(new Row(id, workflow, reserve, isA,
                    FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)), line));
        }
        return List.copyOf(rows);
    }

    static String workflowName(String workflow) {
        return switch (workflow) {
                case "BOARD_EXAM_DIRECT" -> "BOARD_EXAM";
                case "INTERVIEW_PRACTICE" -> "INTERVIEW_GENERATION";
                default -> workflow;
        };
    }

    private void validateCounts(Map<String, List<Row>> byFile) throws IOException {
        Set<String> ids = new HashSet<>();
        for (String key : FILE_ORDER) for (Row row : byFile.get(key))
            if (!ids.add(row.id())) throw new InvalidRunPlan("Duplicate case across manifests: " + row.id());
        for (var entry : parameters.workflows.entrySet()) {
            String workflow = entry.getKey();
            int primary = byFile.get(CLASS_A_PRIMARY).stream().filter(row -> workflow.equals(row.workflow())).toList().size()
                    + byFile.get(CLASS_B_PRIMARY).stream().filter(row -> workflow.equals(row.workflow())).toList().size();
            int reserve = byFile.get(CLASS_A_RESERVE).stream().filter(row -> workflow.equals(row.workflow())).toList().size()
                    + byFile.get(CLASS_B_RESERVE).stream().filter(row -> workflow.equals(row.workflow())).toList().size();
            if (primary != entry.getValue().primarySize() || reserve != parameters.reserveSize)
                throw new InvalidRunPlan("Manifest counts differ from frozen parameters: " + workflow);
            if (!entry.getValue().subgroups().isEmpty()) {
                for (var subgroup : entry.getValue().subgroups().entrySet()) {
                    int primarySubgroup = subgroupCount(byFile.get(CLASS_A_PRIMARY), workflow, subgroup.getKey());
                    int reserveSubgroup = subgroupCount(byFile.get(CLASS_A_RESERVE), workflow, subgroup.getKey());
                    if (primarySubgroup != subgroup.getValue() || reserveSubgroup != subgroup.getValue())
                        throw new InvalidRunPlan("Subgroup allocation differs from freeze: "
                                + workflow + "/" + subgroup.getKey());
                }
            }
        }
        Map<String, List<Row>> aWorkflows = new HashMap<>();
        for (Row row : byFile.get(CLASS_A_PRIMARY))
            aWorkflows.computeIfAbsent(row.workflow(), ignored -> new ArrayList<>()).add(row);
        for (var entry : aWorkflows.entrySet()) {
            int college = 0;
            int boardReview = 0;
            Map<String, Integer> strata = new HashMap<>();
            for (Row row : entry.getValue()) {
                JsonNode json = JSON.readTree(row.raw());
                String level = json.path("noteLevel").asText();
                if ("COLLEGE".equals(level)) college++;
                if ("BOARD_EXAM_REVIEW".equals(level)) boardReview++;
                strata.merge(json.path("stratum").asText(), 1, Integer::sum);
            }
            int expectedPerLevel = parameters.workflows.get(entry.getKey()).primarySize() / 2;
            if (college != expectedPerLevel || boardReview != expectedPerLevel
                    || strata.values().stream().anyMatch(count -> count > 10))
                throw new InvalidRunPlan("Class-A level or stratum allocation differs from preregistration: "
                        + entry.getKey());
        }
    }

    private static int subgroupCount(List<Row> rows, String workflow, String subgroup) throws IOException {
        int count = 0;
        for (Row row : rows)
            if (workflow.equals(row.workflow()) && subgroup.equals(JSON.readTree(row.raw()).path("subgroup").asText()))
                count++;
        return count;
    }

    private static Config readConfig() {
        String location = System.getProperty("llm.benchmark.stageRun.plan");
        if (location == null || location.isBlank())
            throw new InvalidRunPlan("llm.benchmark.stageRun.plan is required");
        return readConfig(Path.of(location), System.getenv("LLM_API_KEY"));
    }

    static Config readConfig(Path path, String key) {
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
            RunFile file = JSON.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(path.toFile(), RunFile.class);
            if (file.arm() == null || file.model() == null || file.stage() == null
                    || file.priceIn() == null || file.priceOut() == null || file.forecasts() == null
                    || file.outputDir() == null || file.receiptFile() == null || file.freezeFile() == null
                    || file.parametersFile() == null || file.manifests() == null
                    || file.baselineReceipts() == null)
                throw new InvalidRunPlan("Incomplete real-run input");
            var input = new StageRunner.RunInput(file.model(), key, file.stage(),
                    null, file.forecasts(), file.priceIn(), file.priceOut(), path, file.outputDir());
            return new Config(input, file.arm(), file.freezeFile(), file.parametersFile(), file.manifests(),
                    file.reserveWorkflow(), file.explicitlySelected(), file.baselineReceipts(),
                    file.receiptFile(), null);
        } catch (IOException exception) {
            throw new InvalidRunPlan("Cannot read real-run input", exception);
        }
    }
}
