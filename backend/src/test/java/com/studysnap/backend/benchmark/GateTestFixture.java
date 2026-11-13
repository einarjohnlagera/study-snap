package com.studysnap.backend.benchmark;

import java.nio.file.Path;

/** Shared section 1 frozen-parameter oracle fixture location. */
final class GateTestFixture {
    private GateTestFixture() {}
    static final Path SOURCE = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json");
    static final String FILE_NAME = "gates.json";
    static final String SHA_SUFFIX = ".sha256";
    static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    static final String PAID = "PAID_INTERACTIVE";
    static final String CRITIQUE = "CRITIQUE";
}
