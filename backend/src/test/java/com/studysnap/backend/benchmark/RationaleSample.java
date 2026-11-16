package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class RationaleSample {
    record NonCleanRow(String itemId, String scorerRole) {}
    private RationaleSample() {}
    static List<NonCleanRow> select(List<NonCleanRow> candidates, long seed, int count) {
        if (candidates == null || count <= 0) throw new PilotBars.InvalidPilotBars("Invalid rationale sample request");
        List<NonCleanRow> selected = new ArrayList<>();
        for (String role : List.of("P", "S")) {
            List<NonCleanRow> pool = new ArrayList<>(candidates.stream()
                    .filter(row -> role.equals(row.scorerRole())).sorted(Comparator.comparing(NonCleanRow::itemId)).toList());
            if (pool.size() < count) throw new PilotBars.InvalidPilotBars("Too few rationale candidates for " + role);
            Collections.shuffle(pool, new Random(seed));
            selected.addAll(pool.subList(0, count));
        }
        return List.copyOf(selected);
    }
}
