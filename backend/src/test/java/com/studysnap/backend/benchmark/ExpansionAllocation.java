package com.studysnap.backend.benchmark;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen workflow allocation; spare slots restart at the first priority stem. */
final class ExpansionAllocation {
    private ExpansionAllocation() {}

    static Map<String, Integer> allocate(Map<String, Integer> initial, List<String> priority,
                                          Map<String, Integer> capacity, int kReq) {
        if (initial == null || priority == null || capacity == null || kReq < 1
                || priority.isEmpty() || priority.stream().anyMatch(s -> s == null || s.isBlank())
                || new HashSet<>(priority).size() != priority.size()
                || !initial.keySet().equals(new HashSet<>(priority))
                || !capacity.keySet().equals(new HashSet<>(priority))
                || initial.values().stream().anyMatch(n -> n == null || n < 0)
                || capacity.values().stream().anyMatch(n -> n == null || n < 0)
                || initial.values().stream().mapToLong(Integer::longValue).sum() != kReq)
            throw new InvalidExpansionAllocation("Invalid frozen expansion allocation");
        Map<String, Integer> allocated = new LinkedHashMap<>();
        int total = 0;
        for (String stem : priority) {
            int count = Math.clamp(initial.get(stem), 0, capacity.get(stem));
            allocated.put(stem, count);
            total += count;
        }
        while (total < kReq) {
            String spare = null;
            for (String stem : priority) if (allocated.get(stem) < capacity.get(stem)) {
                spare = stem;
                break;
            }
            if (spare == null) break;
            allocated.put(spare, allocated.get(spare) + 1);
            total++;
        }
        return Collections.unmodifiableMap(allocated);
    }
}

final class InvalidExpansionAllocation extends RuntimeException {
    InvalidExpansionAllocation(String message) { super(message); }
}
