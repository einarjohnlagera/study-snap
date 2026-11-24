package com.studysnap.backend.benchmark;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpansionAllocationTest {
    private static final String DIRECT = "LONG_EXAM_DIRECT";
    private static final String LONG_POOL = "LONG_EXAM_LONG_EXAM_POOL";
    private static final String BOARD_POOL = "LONG_EXAM_BOARD_EXAM_POOL";
    private static final String STEM_A = "A";
    private static final String STEM_B = "B";
    private static final String STEM_C = "C";
    private static final String STEM_D = "D";
    private static final List<String> PRIORITY = List.of(DIRECT, LONG_POOL, BOARD_POOL);
    private static final Map<String, Integer> INITIAL = counts(1, 1, 1);

    private static Map<String, Integer> counts(int direct, int longPool, int boardPool) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(DIRECT, direct);
        values.put(LONG_POOL, longPool);
        values.put(BOARD_POOL, boardPool);
        return values;
    }

    @Test void allocationTable() {
        int[][] cases = {
                {5, 5, 5, 1, 1, 1}, {5, 5, 0, 2, 1, 0},
                {0, 5, 5, 0, 2, 1}, {5, 0, 5, 2, 0, 1},
                {1, 0, 2, 1, 0, 2}, {1, 1, 0, 1, 1, 0},
                {2, 0, 0, 2, 0, 0}, {0, 1, 0, 0, 1, 0},
                {0, 0, 0, 0, 0, 0}, {1, 1, 1, 1, 1, 1},
                {5, 1, 5, 1, 1, 1},
                {3, 0, 0, 3, 0, 0}
        };
        for (int[] c : cases) {
            Map<String, Integer> capacity = counts(c[0], c[1], c[2]);
            Map<String, Integer> allocated = ExpansionAllocation.allocate(INITIAL, PRIORITY, capacity, 3);
            assertThat(allocated).isEqualTo(counts(c[3], c[4], c[5]));
            assertThat(allocated.values().stream().mapToInt(Integer::intValue).sum())
                    .isEqualTo(Math.min(3, c[0] + c[1] + c[2]));
            for (String stem : PRIORITY) assertThat(allocated.get(stem)).isLessThanOrEqualTo(capacity.get(stem));
        }
    }

    @Test void oneStemTable() {
        for (int capacity : List.of(5, 3, 2, 1, 0))
            assertThat(ExpansionAllocation.allocate(Map.of(DIRECT, 3), List.of(DIRECT),
                    Map.of(DIRECT, capacity), 3)).isEqualTo(Map.of(DIRECT, Math.min(3, capacity)));
    }

    @Test void priorityAndMapOrderAreIndependent() {
        Map<String, Integer> reversed = new LinkedHashMap<>();
        reversed.put(BOARD_POOL, 5);
        reversed.put(LONG_POOL, 0);
        reversed.put(DIRECT, 5);
        assertThat(ExpansionAllocation.allocate(INITIAL, PRIORITY, reversed, 3))
                .isEqualTo(counts(2, 0, 1));
        assertThat(ExpansionAllocation.allocate(INITIAL, List.of(BOARD_POOL, LONG_POOL, DIRECT),
                reversed, 3)).isEqualTo(counts(1, 0, 2));
    }

    @Test void eachRedistributedSlotRestartsAtFirstSpareStem() {
        List<String> priority = List.of(STEM_A, STEM_B, STEM_C, STEM_D);
        Map<String, Integer> initial = Map.of(STEM_A, 1, STEM_B, 1, STEM_C, 1, STEM_D, 2);
        Map<String, Integer> capacity = Map.of(STEM_A, 0, STEM_B, 0, STEM_C, 5, STEM_D, 5);
        assertThat(ExpansionAllocation.allocate(initial, priority, capacity, 5))
                .isEqualTo(Map.of(STEM_A, 0, STEM_B, 0, STEM_C, 3, STEM_D, 2));
    }

    @Test void invalidInputsHaveNamedFailure() {
        assertThatThrownBy(() -> ExpansionAllocation.allocate(INITIAL, PRIORITY, counts(-1, 1, 1), 3))
                .isInstanceOf(InvalidExpansionAllocation.class);
        assertThatThrownBy(() -> ExpansionAllocation.allocate(counts(2, 1, 1), PRIORITY, counts(5, 5, 5), 3))
                .isInstanceOf(InvalidExpansionAllocation.class);
        assertThatThrownBy(() -> ExpansionAllocation.allocate(INITIAL, List.of(DIRECT, DIRECT, BOARD_POOL),
                counts(5, 5, 5), 3)).isInstanceOf(InvalidExpansionAllocation.class);
        assertThatThrownBy(() -> ExpansionAllocation.allocate(INITIAL, PRIORITY, Map.of(DIRECT, 5), 3))
                .isInstanceOf(InvalidExpansionAllocation.class);
        // A duplicate that leaves the key sets equal, so ONLY the duplicate rule can refuse it.
        assertThatThrownBy(() -> ExpansionAllocation.allocate(INITIAL, List.of(DIRECT, PRIORITY.get(1), DIRECT,
                BOARD_POOL), counts(5, 5, 5), 3)).isInstanceOf(InvalidExpansionAllocation.class);
        // kReq below one with a zero initial sum, so ONLY the kReq rule can refuse it.
        assertThatThrownBy(() -> ExpansionAllocation.allocate(counts(0, 0, 0), PRIORITY, counts(5, 5, 5), 0))
                .isInstanceOf(InvalidExpansionAllocation.class);
    }
}
