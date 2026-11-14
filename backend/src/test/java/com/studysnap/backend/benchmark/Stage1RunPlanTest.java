package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sections 5, 6 and 10 offline three-arm composition and receipt mutation guards. */
class Stage1RunPlanTest {
    private static final String KEY = "fake-secret-key";
    private static final String MINI = "gpt-4.1-mini";
    private static final String FOUR_ONE = "gpt-4.1";
    private static final String LUNA = "gpt-6-luna";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String LUNA_ARM = "LUNA";
    private static final String SOL_ARM = "SOL_PAID_INTERACTIVE";
    private static final String SOL_MODEL = "gpt-6.1-sol";
    private static final String STRATUM = "synthetic";
    private static final String BIOLOGY = "Biology";
    private static final String MARKER = "private-note-marker";
    private static final String REQUEST_MARKER = "request-body-marker";
    private static final String ANSWER = "Synthetic answer grounded in cells.";
    private static final String QUESTION = "What is the role of a cell membrane?";
    private static final String PAID = "PAID_INTERACTIVE";
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String NOTE_ADAPTIVE = "NOTE_ADAPTIVE";
    private static final String CELLS = "Cells";
    private static final String CELL_DESCRIPTION = "Cells contain membranes.";
    private static final String MEMBRANE = "Membrane";
    private static final String RESERVE_SUFFIX = "-reserve";
    private static final String JSON_SUFFIX = ".json";
    private static final String OUT_RESERVE = "out-reserve";
    private static final String TEXT = "text";
    private static final String FORMAT = "format";
    private static final String ANSWER_FIELD = "answer";
    private static final String QUESTION_FIELD = "question";
    private static final String QUESTIONS_FIELD = "questions";
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private record Fixtures(Path parameters, Map<String, Path> manifests, FreezeRecord freeze,
                            Map<String, ForecastReservation> forecasts) {}

    private Fixtures fixtures() throws Exception {
        byte[] bytes = Files.readAllBytes(GateTestFixture.SOURCE);
        Path parameters = temp.resolve("gates.json");
        Files.write(parameters, bytes);
        Files.writeString(temp.resolve("gates.json.sha256"), FreezeRecord.sha256(bytes));
        FrozenGateParameters gates = FrozenGateParameters.load(parameters);
        List<String> mini = new ArrayList<>(gates.routes.get(INTERACTIVE).workflows());
        mini.addAll(gates.routes.get(CRITIQUE).workflows());
        FreezeRecord freeze = new FreezeRecord("a".repeat(40), "b".repeat(64), gates.sha256,
                Map.of(), Map.of(), Map.of(
                    BASELINE_MINI, new FreezeRecord.Arm(MINI, mini),
                    BASELINE_41, new FreezeRecord.Arm(FOUR_ONE, gates.routes.get(PAID).workflows()),
                    LUNA_ARM, new FreezeRecord.Arm(LUNA, new ArrayList<>(gates.workflows.keySet())),
                    SOL_ARM, new FreezeRecord.Arm(SOL_MODEL, gates.routes.get(PAID).workflows())),
                Map.of(INTERACTIVE, MINI, PAID, FOUR_ONE, CRITIQUE, MINI),
                LocalDate.of(2026, 11, 14));
        List<String> aPrimary = new ArrayList<>();
        List<String> aReserve = new ArrayList<>();
        Map<String, ForecastReservation> forecasts = new HashMap<>();
        for (ClassACaseManifest.Workflow workflow : ClassACaseManifest.Workflow.values()) {
            int count = switch (workflow) {
                case TEACHER_QUIZ -> 10;
                case BOARD_EXAM_DIRECT -> 12;
                case LONG_EXAM -> 25;
                default -> 5;
            };
            aPrimary.add(aRow(workflow, false, count));
            aReserve.add(aRow(workflow, true, count));
            forecasts.put(workflow + ":" + STRATUM, forecast());
        }
        List<String> bPrimary = new ArrayList<>();
        List<String> bReserve = new ArrayList<>();
        for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values()) {
            bPrimary.add(bRow(workflow, false));
            bReserve.add(bRow(workflow, true));
            forecasts.put(workflow + ":" + STRATUM, forecast());
        }
        Map<String, Path> manifests = Map.of(
                "classA.primary", write("a-primary.jsonl", aPrimary),
                "classB.primary", write("b-primary.jsonl", bPrimary),
                "classA.reserve", write("a-reserve.jsonl", aReserve),
                "classB.reserve", write("b-reserve.jsonl", bReserve));
        return new Fixtures(parameters, manifests, freeze, forecasts);
    }

    private ForecastReservation forecast() {
        return new ForecastReservation(1_000, 1_000, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }

    private Path write(String name, List<String> rows) throws IOException {
        Path path = temp.resolve(name);
        Files.write(path, rows);
        return path;
    }

    private String aRow(ClassACaseManifest.Workflow workflow, boolean reserve, int count) throws Exception {
        var source = new ClassACaseManifest.Source(CELLS, "Cells have membranes. " + MARKER + " " + REQUEST_MARKER,
                CELL_DESCRIPTION, List.of(MEMBRANE), List.of("cells"), BIOLOGY,
                BIOLOGY, null, LearnerLevel.COLLEGE);
        var row = new ClassACaseManifest.Case("a-" + workflow + (reserve ? RESERVE_SUFFIX : ""), workflow,
                STRATUM, LearnerLevel.COLLEGE, null, BIOLOGY, false, List.of(source), count,
                workflow == ClassACaseManifest.Workflow.BOARD_EXAM_DIRECT ? "mixed" : null,
                reserve, workflow == ClassACaseManifest.Workflow.LONG_EXAM
                        ? ClassACaseManifest.Subgroup.DIRECT : null);
        return json.writeValueAsString(row);
    }

    private String bRow(ClassBCaseManifest.Workflow workflow, boolean reserve) throws Exception {
        var context = new CompanionGenerationContext("Synthetic plan", "Foundations for a unit exam",
                BIOLOGY, List.of(new CompanionGenerationContext.CompanionContextItem(
                    CELLS, CELL_DESCRIPTION)), List.of());
        var row = new ClassBCaseManifest.Case("b-" + workflow + (reserve ? RESERVE_SUFFIX : ""),
                workflow, STRATUM, reserve,
                workflow == ClassBCaseManifest.Workflow.STUDY_TIP ? LearnerLevel.COLLEGE : null,
                workflow == ClassBCaseManifest.Workflow.STUDY_TIP
                    ? List.of(new QuickReviewIncorrectQuestionRequest(QUESTION, "Cell boundary",
                        "The membrane bounds the cell.")) : null,
                workflow == ClassBCaseManifest.Workflow.COLLECTION_COMPANION
                    ? List.of(CompanionSection.OVERVIEW) : null,
                workflow == ClassBCaseManifest.Workflow.COLLECTION_COMPANION ? context : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION
                    ? new CompanionContent("Cell membranes protect cells.", null, null, null,
                        List.of(), List.of()) : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? List.of() : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? QUESTION : null,
                workflow == ClassBCaseManifest.Workflow.ASK_COMPANION ? true : null,
                workflow == ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE
                    ? new ClassBCaseManifest.Question(QUESTION, List.of("A", "B", "C", "D"),
                        1, "Membranes", "B follows the source.") : null,
                workflow == ClassBCaseManifest.Workflow.INTERVIEW_CRITIQUE ? 1 : null);
        return json.writeValueAsString(row);
    }

    private Stage1RunPlan plan(Fixtures f, String arm, String model, List<Path> baselines) {
        Path out = temp.resolve("out-" + arm);
        Path receipt = temp.resolve(arm + JSON_SUFFIX);
        var input = new StageRunner.RunInput(model, KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN,
                        StageCapLedger.Stage.STAGE_2, BigDecimal.TEN), f.forecasts(),
                BigDecimal.ONE, BigDecimal.ONE, null, out);
        return new Stage1RunPlan(new Stage1RunPlan.Config(input, arm, null, f.parameters(),
                f.manifests(), null, false, baselines, receipt, f.freeze()));
    }

    @Test void threeArmsRunOnOneLedgerWithOrderedFrozenCasesAndContentFreeReceipts() throws Exception {
        Fixtures fixtures = fixtures();
        Path ledgerFile = temp.resolve("ledger.jsonl");
        OfflineStub stub = new OfflineStub();
        Stage1Receipt mini;
        Stage1Receipt fourOne;
        Stage1Receipt luna;
        Map<StageCapLedger.Stage, BigDecimal> caps = Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN,
                StageCapLedger.Stage.STAGE_2, BigDecimal.TEN);
        try (StageCapLedger ledger = StageCapLedger.open(ledgerFile, caps, true)) {
            mini = plan(fixtures, BASELINE_MINI, MINI, List.of()).dryRun(stub, ledger);
        }
        try (StageCapLedger ledger = StageCapLedger.open(ledgerFile, caps, false)) {
            fourOne = plan(fixtures, BASELINE_41, FOUR_ONE, List.of()).dryRun(stub, ledger);
        }
        try (StageCapLedger ledger = StageCapLedger.open(ledgerFile, caps, false)) {
            luna = plan(fixtures, LUNA_ARM, LUNA, List.of(
                    temp.resolve(BASELINE_MINI + JSON_SUFFIX), temp.resolve(BASELINE_41 + JSON_SUFFIX)))
                    .dryRun(stub, ledger);
        }
        assertThat(mini.cases()).hasSize(8);
        assertThat(fourOne.cases()).hasSize(2);
        assertThat(luna.cases()).hasSize(10);
        assertThat(mini.complete()).isTrue();
        assertThat(fourOne.complete()).isTrue();
        assertThat(luna.complete()).isTrue();
        Set<String> miniIds = mini.cases().stream().map(Stage1Receipt.CaseRow::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> fourOneIds = fourOne.cases().stream().map(Stage1Receipt.CaseRow::id)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(Stage1Receipt.compare(mini, luna).stream()
                .filter(result -> miniIds.contains(result.caseId())))
                .allSatisfy(result -> assertThat(result.result()).isEqualTo(Stage1Receipt.Comparison.MATCH));
        assertThat(Stage1Receipt.compare(fourOne, luna).stream()
                .filter(result -> fourOneIds.contains(result.caseId())))
                .allSatisfy(result -> assertThat(result.result()).isEqualTo(Stage1Receipt.Comparison.MATCH));
        assertThat(luna.ledgerRecordCount()).isGreaterThan(fourOne.ledgerRecordCount());
        assertThat(luna.cases()).extracting(Stage1Receipt.CaseRow::id)
                .containsExactly("a-" + NOTE_ADAPTIVE, "a-PLAN_ADAPTIVE", "a-TEACHER_QUIZ",
                        "a-BOARD_EXAM_DIRECT", "a-INTERVIEW_PRACTICE", "a-LONG_EXAM",
                        "b-STUDY_TIP", "b-COLLECTION_COMPANION", "b-ASK_COMPANION",
                        "b-INTERVIEW_CRITIQUE");
        assertThat(luna.cases()).noneSatisfy(row -> assertThat(row.id()).contains("reserve"));
        String receiptText = Files.readString(temp.resolve(LUNA_ARM + JSON_SUFFIX));
        assertThat(receiptText).contains("freezeSha256", "ledgerRecordCount", "head", "manifestRowSha256");
        assertThat(receiptText).doesNotContain(KEY, MARKER, REQUEST_MARKER);
        assertThat(stub.forwarded).anySatisfy(body -> assertThat(body).contains(MARKER, REQUEST_MARKER));
        assertThat(stub.hosts).containsOnly("offline.invalid");
        assertThat(Files.readString(ledgerFile)).contains("RESERVE", "SETTLE");
        var reserveInput = new StageRunner.RunInput(MINI, KEY, StageCapLedger.Stage.STAGE_2,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN,
                        StageCapLedger.Stage.STAGE_2, BigDecimal.TEN), fixtures.forecasts(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve(OUT_RESERVE));
        List<Path> untriggeredReceipts = List.of(temp.resolve(BASELINE_MINI + JSON_SUFFIX),
                temp.resolve(BASELINE_41 + JSON_SUFFIX), temp.resolve(LUNA_ARM + JSON_SUFFIX));
        var untriggeredPlan = new Stage1RunPlan(new Stage1RunPlan.Config(reserveInput,
                BASELINE_MINI, null, fixtures.parameters(), fixtures.manifests(), NOTE_ADAPTIVE,
                false, untriggeredReceipts, temp.resolve("untriggered-reserve.json"), fixtures.freeze()));
        int dispatchesBefore = stub.forwarded.size();
        StageCapLedger noTriggerLedger = new StageCapLedger(caps);
        assertThatThrownBy(() -> untriggeredPlan.dryRun(stub, noTriggerLedger))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class)
                .hasMessageContaining("recorded T2 trigger");
        assertThat(stub.forwarded).hasSize(dispatchesBefore);
        assertThat(noTriggerLedger.recordCount()).isZero();
        Stage1Receipt triggered = new Stage1Receipt(luna.head(), luna.freezeSha256(),
                luna.arm(), luna.model(), luna.stage(), luna.startedAt(), luna.endedAt(),
                luna.ledgerRecordCount(), luna.unreconciled(), luna.caseSetSha256(), luna.cases(),
                Map.of(NOTE_ADAPTIVE, RouteVerdictCalculator.Tier.T2));
        Path triggeredFile = temp.resolve("luna-triggered.json");
        triggered.write(triggeredFile);
        var reservePlan = new Stage1RunPlan(new Stage1RunPlan.Config(reserveInput,
                BASELINE_MINI, null, fixtures.parameters(), fixtures.manifests(),
                NOTE_ADAPTIVE, false, List.of(temp.resolve(BASELINE_MINI + JSON_SUFFIX),
                    temp.resolve(BASELINE_41 + JSON_SUFFIX), triggeredFile),
                temp.resolve("reserve-receipt.json"), fixtures.freeze()));
        try (StageCapLedger ledger = StageCapLedger.open(ledgerFile, caps, false)) {
            Stage1Receipt reserve = reservePlan.dryRun(stub, ledger);
            assertThat(reserve.cases()).extracting(Stage1Receipt.CaseRow::id)
                    .containsExactly("a-" + NOTE_ADAPTIVE + RESERVE_SUFFIX);
            assertThat(reserve.complete()).isTrue();
        }
    }

    @Test void lunaWithoutCompleteBaselineRefusesWithoutDispatch() throws Exception {
        Fixtures fixtures = fixtures();
        OfflineStub stub = new OfflineStub();
        var plan = plan(fixtures, LUNA_ARM, LUNA, List.of());
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN));
        assertThatThrownBy(() -> plan.dryRun(stub, ledger))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        assertThat(stub.forwarded).isEmpty();
        assertThat(ledger.recordCount()).isZero();
    }

    @Test void reserveWithoutTriggerRefusesBeforeDispatch() throws Exception {
        Fixtures fixtures = fixtures();
        OfflineStub stub = new OfflineStub();
        var input = new StageRunner.RunInput(MINI, KEY, StageCapLedger.Stage.STAGE_2,
                Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN), fixtures.forecasts(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve(OUT_RESERVE));
        var plan = new Stage1RunPlan(new Stage1RunPlan.Config(input, BASELINE_MINI, null,
                fixtures.parameters(), fixtures.manifests(), NOTE_ADAPTIVE, false,
                List.of(), temp.resolve("reserve.json"), fixtures.freeze()));
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_2, BigDecimal.TEN));
        assertThatThrownBy(() -> plan.dryRun(stub, ledger))
                .isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        assertThat(stub.forwarded).isEmpty();
        assertThat(ledger.recordCount()).isZero();
    }

    @Test void solWithoutApprovedStageThreeCapRefusesBeforeDispatch() throws Exception {
        Fixtures fixtures = fixtures();
        OfflineStub stub = new OfflineStub();
        var input = new StageRunner.RunInput(SOL_MODEL, KEY, StageCapLedger.Stage.STAGE_3,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN), fixtures.forecasts(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("out-sol"));
        var plan = new Stage1RunPlan(new Stage1RunPlan.Config(input, SOL_ARM, null,
                fixtures.parameters(), fixtures.manifests(), null, true, List.of(),
                temp.resolve("sol-receipt.json"), fixtures.freeze()));
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN));
        assertThatThrownBy(() -> plan.dryRun(stub, ledger))
                .isInstanceOf(StageCapLedger.SpendStop.class)
                .hasMessageContaining("SOL requires explicit selection and approved STAGE_3 cap");
        assertThat(stub.forwarded).isEmpty();
        assertThat(ledger.recordCount()).isZero();
    }

    @Test void dryRunRefusesNonFakeKeyBeforeAnyAdapterOrTransport() throws Exception {
        Fixtures fixtures = fixtures();
        OfflineStub stub = new OfflineStub();
        var input = new StageRunner.RunInput(MINI, "unapproved-real-key", StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN), fixtures.forecasts(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("out-unapproved"));
        var plan = new Stage1RunPlan(new Stage1RunPlan.Config(input, BASELINE_MINI, null,
                fixtures.parameters(), fixtures.manifests(), null, false, List.of(),
                temp.resolve("unapproved-receipt.json"), fixtures.freeze()));
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN));
        assertThatThrownBy(() -> plan.dryRun(stub, ledger))
                .isInstanceOf(Stage1RunPlan.InvalidRunPlan.class).hasMessageContaining("fake key");
        assertThat(stub.forwarded).isEmpty();
        assertThat(ledger.recordCount()).isZero();
    }

    @Test void ownerRunFileParsesExplicitModelsPricesForecastsAndNoKey() throws Exception {
        Fixtures fixtures = fixtures();
        ObjectNode file = json.createObjectNode();
        file.put("arm", BASELINE_MINI).put("model", MINI).put("stage", "STAGE_1")
                .put("priceIn", 1).put("priceOut", 1)
                .put("outputDir", temp.resolve("owner-output").toString())
                .put("receiptFile", temp.resolve("owner-receipt.json").toString())
                .put("freezeFile", temp.resolve("owner-freeze.json").toString())
                .put("parametersFile", fixtures.parameters().toString())
                .put("explicitlySelected", false);
        file.set("forecasts", json.valueToTree(fixtures.forecasts()));
        file.set("manifests", json.valueToTree(fixtures.manifests()));
        file.putArray("baselineReceipts");
        Path path = temp.resolve("owner-run.json");
        Files.writeString(path, file.toString());
        Stage1RunPlan.Config parsed = Stage1RunPlan.readConfig(path, KEY);
        assertThat(parsed.input().key()).isEqualTo(KEY);
        assertThat(parsed.input().model()).isEqualTo(MINI);
        assertThat(parsed.input().priceIn()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(parsed.input().forecasts()).containsKeys("NOTE_ADAPTIVE:" + STRATUM);
        assertThat(Files.readString(path)).doesNotContain(KEY);
    }

    private final class OfflineStub implements Stage1RunPlan.OfflineTransport {
        final List<String> forwarded = new CopyOnWriteArrayList<>();
        final List<String> hosts = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override public MockClientHttpRequest createMockRequest(URI uri, HttpMethod method) {
            hosts.add(uri.getHost());
            return new MockClientHttpRequest(method, uri) {
                @Override protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                    byte[] bytes = getBodyAsBytes();
                    forwarded.add(new String(bytes, StandardCharsets.UTF_8));
                    JsonNode request = json.readTree(bytes);
                    String payload = payload(request, calls.incrementAndGet());
                    ObjectNode response = json.createObjectNode().put("output_text", payload);
                    response.putObject("usage").put("input_tokens", 100).put("output_tokens", 100);
                    return new MockClientHttpResponse(json.writeValueAsBytes(response), 200);
                }
            };
        }

        private String payload(JsonNode request, int number) throws IOException {
            String name = request.path(TEXT).path(FORMAT).path("name").asText();
            return switch (name) {
                case "note_lib_study_tip" -> json.writeValueAsString(Map.of("tip", ANSWER));
                case "note_lib_ask_companion_answer" -> json.writeValueAsString(Map.of(ANSWER_FIELD, ANSWER));
                case "note_lib_interview_critique" -> json.writeValueAsString(Map.of(
                        "verdict", "STRONG", "rationale", ANSWER, "followUp", QUESTION));
                case "note_lib_companion_draft" -> {
                    ObjectNode value = json.createObjectNode();
                    value.put("overview", ANSWER).put("studyStrategy", ANSWER)
                            .put("commonMistakes", ANSWER);
                    var faq = value.putArray("faq");
                    for (int index = 0; index < 3; index++)
                        faq.addObject().put(QUESTION_FIELD, QUESTION).put(ANSWER_FIELD, ANSWER);
                    value.putArray("mentorTips").addObject().put("title", "Focus")
                            .put("body", ANSWER);
                    yield json.writeValueAsString(value);
                }
                default -> {
                    int count = request.path(TEXT).path(FORMAT).path("schema")
                            .path("properties").path(QUESTIONS_FIELD).path("minItems").asInt(5);
                    ObjectNode value = json.createObjectNode();
                    var questions = value.putArray(QUESTIONS_FIELD);
                    for (int index = 0; index < count; index++) {
                        var question = questions.addObject();
                        question.put(QUESTION_FIELD, "Synthetic generated question " + number + "-" + index + "?");
                        question.putArray("choices").add("Correct").add("Wrong B").add("Wrong C").add("Wrong D");
                        question.put(ANSWER_FIELD, "A").put("explanation", ANSWER)
                                .put("concept", MEMBRANE).put("keyConcept", MEMBRANE);
                    }
                    yield json.writeValueAsString(value);
                }
            };
        }
    }
}
