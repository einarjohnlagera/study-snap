package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Durable parent-to-call reservation accounting for preregistration sections 1 and 5. */
class RecheckLedgerBudgetTest {
    private static final StageCapLedger.Stage STAGE = StageCapLedger.Stage.STAGE_1;
    private static final String CELL = "NOTE_ADAPTIVE:synthetic";
    @TempDir Path temp;

    @Test void twoCallBudgetRemainsHeldAndReplaysWithoutDoubleCharging() {
        Path ledgerFile = temp.resolve("ledger.jsonl");
        Map<StageCapLedger.Stage, BigDecimal> caps = Map.of(STAGE, BigDecimal.ONE);
        try (StageCapLedger ledger = StageCapLedger.open(ledgerFile, caps, true)) {
            StageCapLedger.Reservation budget = ledger.reserve(STAGE, CELL, new BigDecimal("0.20"));
            assertThat(ledger.remaining(STAGE)).isEqualByComparingTo("0.80");
            StageCapLedger.Reservation first = ledger.reserveFrom(budget, STAGE, CELL,
                    new BigDecimal("0.06"));
            assertThat(ledger.remaining(STAGE)).isEqualByComparingTo("0.80");
            first.settle(new BigDecimal("0.05"));
            StageCapLedger.Reservation second = ledger.reserveFrom(budget, STAGE, CELL,
                    new BigDecimal("0.06"));
            second.settle(new BigDecimal("0.07"));
            assertThat(ledger.remaining(STAGE)).isEqualByComparingTo("0.80");
            assertThatThrownBy(() -> ledger.reserveFrom(budget, STAGE, CELL, new BigDecimal("0.09")))
                    .isInstanceOf(StageCapLedger.SpendStop.class);
            budget.abandon();
            assertThat(ledger.spent(STAGE)).isEqualByComparingTo("0.12");
        }
        try (StageCapLedger replayed = StageCapLedger.open(ledgerFile, caps, false)) {
            assertThat(replayed.spent(STAGE)).isEqualByComparingTo("0.12");
            assertThat(replayed.unreconciled()).isFalse();
            assertThat(replayed.attempts(STAGE)).isEqualTo(3);
        }
    }
}
