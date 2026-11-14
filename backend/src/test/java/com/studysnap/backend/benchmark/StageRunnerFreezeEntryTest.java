package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Section 10 real entry must reject before opening the durable ledger. */
class StageRunnerFreezeEntryTest {
    private static final String ENABLED = "llm.benchmark.stageRun";
    private static final String PROVIDER = "llm.benchmark.stageRun.provider";
    private static final String CAPS = "llm.benchmark.stageCaps";
    private static final String LEDGER = "llm.benchmark.stageLedger";
    private static final String INIT = "llm.benchmark.stageLedger.init";
    private static final String TRUE = "true";
    private static final String FAKE_MODEL = "fake-model";
    private static final String FAKE_KEY = "fake-key";
    private static final String DRY_RUN = "DRY_RUN";
    private static final String SYNTHETIC = "synthetic";
    @TempDir Path temp;

    @Test void missingFreezeRefusesBeforeLedgerCreationAndDispatch() {
        Path ledger = temp.resolve("ledger.jsonl");
        RefusingProvider.output = temp.resolve("output");
        RefusingProvider.dispatches.set(0);
        Map<String, String> old = new java.util.HashMap<>();
        for (String key : List.of(ENABLED, PROVIDER, CAPS, LEDGER, INIT))
            old.put(key, System.getProperty(key));
        System.setProperty(ENABLED, TRUE);
        System.setProperty(PROVIDER, RefusingProvider.class.getName());
        System.setProperty(CAPS, "STAGE_1:1");
        System.setProperty(LEDGER, ledger.toString());
        System.setProperty(INIT, TRUE);
        try {
            StageRunner runner = new StageRunner();
            assertThatThrownBy(runner::stageRun).isInstanceOf(FreezeRecord.InvalidFreeze.class);
            assertThat(Files.exists(ledger)).isFalse();
            assertThat(RefusingProvider.dispatches.get()).isZero();
        } finally {
            old.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    @Test void directRunnerRejectsRealRequestFactoryWithoutVerifiedFreeze() {
        var input = new StageRunner.RunInput(FAKE_MODEL, FAKE_KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE), Map.of(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("real-factory-output"));
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        List<StageRunner.CaseCall> none = List.of();
        assertThatThrownBy(() -> StageRunner.run(input, factory, none))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThat(Files.exists(input.outputDir())).isFalse();
    }

    @Test void dryEvidenceCannotAuthorizeTheRealFactory() {
        var input = new StageRunner.RunInput(FAKE_MODEL, FAKE_KEY, StageCapLedger.Stage.STAGE_1,
                Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE), Map.of(),
                BigDecimal.ONE, BigDecimal.ONE, null, temp.resolve("dry-evidence-output"));
        var ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        var dry = FreezeGuard.dryEvidence(null, DRY_RUN, DRY_RUN);
        List<StageRunner.CaseCall> none = List.of();
        assertThatThrownBy(() -> StageRunner.runVerified(input, none, ledger, dry))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThat(Files.exists(input.outputDir())).isFalse();
    }

    public static final class RefusingProvider implements StageRunner.RunPlanProvider {
        static Path output;
        static final AtomicInteger dispatches = new AtomicInteger();
        public RefusingProvider() {}
        @Override public StageRunner.RunInput input() {
            return new StageRunner.RunInput(FAKE_MODEL, FAKE_KEY, StageCapLedger.Stage.STAGE_1,
                    null, Map.of(SYNTHETIC, new ForecastReservation(1_000, 1_000,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
                    BigDecimal.ONE, BigDecimal.ONE, null, output);
        }
        @Override public FreezeGuard.Verified verify() {
            throw new FreezeRecord.InvalidFreeze("Freeze record missing");
        }
        @Override public List<StageRunner.CaseCall> cases() {
            return List.of(new StageRunner.CaseCall() {
                @Override public String id() { return SYNTHETIC; }
                @Override public String cellId() { return SYNTHETIC; }
                @Override public String generationId() { return SYNTHETIC; }
                @Override public StageRunner.CallResult execute(org.springframework.web.client.RestClient client,
                        com.studysnap.backend.config.StudySnapProperties properties,
                        ArmAssertingInterceptor path) {
                    dispatches.incrementAndGet();
                    return new StageRunner.CallResult(true, Set.of(), null);
                }
            });
        }
    }
}
