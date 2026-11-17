package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/** Exact-count ratchet for direct readers in this test-package harness. */
class ArmKeyReaderGuardTest {
    private static final String DIRECTORY = "backend/src/test/java/com/studysnap/backend/benchmark";
    private static final String LEGACY_RETIRE = "Retire at owner's final FRB approval and legacy gate deletion";
    private static final String TOOL_RETIRE = "Retire when v0.176 evaluation tooling is archived";
    private static final String FIXTURE_RETIRE = "Retire when the corresponding fixture is removed";
    private static final String GATE_RETIRE = "Retain while ArmKeyAccess is the locked, logged gate";
    private static final String WRITER_RETIRE = "Retain while sealed exports and lock records exist";
    private static final Map<String, String> REGEX = Map.ofEntries(
            Map.entry("QUALITY_KEYED", "\\bQualityScoreFiles\\s*\\.\\s*keyed\\s*\\("),
            Map.entry("QUALITY_SCORES", "\\bQualityScoreFiles\\s*\\.\\s*scores\\s*\\("),
            Map.entry("QUALITY_LINES", "\\bQualityScoreFiles\\s*\\.\\s*lines\\s*\\("),
            Map.entry("CLASS_A_LOAD", "\\bClassAScoreFilesV2\\s*\\.\\s*load\\s*\\("),
            Map.entry("CLASS_B_KEY_PARSE", "\\breadValue\\s*\\([^;\\n]*ClassBScoreAggregator\\s*\\.\\s*Key(?:\\s*\\.\\s*class|\\s*>)"),
            Map.entry("RAW_KEY_READ", "\\bFiles\\s*\\.\\s*(?:readAllLines|lines|readString|readAllBytes)\\s*\\([^;\\n)]*(?i:(?<!api_)(?<!api)key)[^;\\n)]*\\)"),
            Map.entry("BLIND_KEY_LITERAL", "blind-key\\.jsonl"),
            Map.entry("SCORE_EVIDENCE_LOAD", "\\bScoreEvidence\\s*\\.\\s*load\\s*\\("),
            Map.entry("EXPORT_VALIDATE_SCORES", "\\bClassABlindPacketExport\\s*\\.\\s*loadAndValidateScores\\s*\\("),
            Map.entry("ARM_KEY_OPEN", "\\bArmKeyAccess\\s*\\.\\s*open\\s*\\("),
            Map.entry("SEAL_KEY", "\\bArmKeySeal\\s*\\.\\s*(?:seal|verifyKeyBytes)\\s*\\("),
            Map.entry("PILOT_KEY_LOAD", "\\bPilotKey\\s*\\.\\s*load\\s*\\("),
            Map.entry("QUALITY_PLAN", "\\bQualityReviewPlan\\s*\\.\\s*plan\\s*\\("),
            Map.entry("QUALITY_BATCHES", "\\bQualityReviewBatches\\s*\\.\\s*batches\\s*\\("),
            Map.entry("QUALITY_REPORT", "\\bQualityGateReport\\s*\\.\\s*report\\s*\\("),
            Map.entry("RESULTS_RUN", "\\bResultsPipeline\\s*\\.\\s*run\\s*\\("),
            Map.entry("T2_RECOMPUTE", "\\bT2TriggerRecord\\s*\\.\\s*recompute\\s*\\("),
            Map.entry("READ_KEY_HELPER", "\\breadKey\\s*\\("),
            Map.entry("PARSE_KEY_HELPER", "\\bparseKey\\s*\\("));
    private static final Map<String, Pattern> PATTERNS = compile();
    private static final Map<String, String> SAMPLES = Map.ofEntries(
            Map.entry("QUALITY_KEYED", "QualityScoreFiles.keyed(key);"),
            Map.entry("QUALITY_SCORES", "QualityScoreFiles.scores(key, p, r, a);"),
            Map.entry("QUALITY_LINES", "QualityScoreFiles.lines(key);"),
            Map.entry("CLASS_A_LOAD", "ClassAScoreFilesV2.load(key, packets, p, s, h, mapping);"),
            Map.entry("CLASS_B_KEY_PARSE", "JSON.readValue(line, ClassBScoreAggregator.Key.class);"),
            Map.entry("RAW_KEY_READ", "Files.readAllLines(armKey);"),
            Map.entry("BLIND_KEY_LITERAL", "Path.of(\"blind-key.jsonl\");"),
            Map.entry("SCORE_EVIDENCE_LOAD", "ScoreEvidence.load(a, b, mapping);"),
            Map.entry("EXPORT_VALIDATE_SCORES", "ClassABlindPacketExport.loadAndValidateScores(key, scores);"),
            Map.entry("ARM_KEY_OPEN", "ArmKeyAccess.open(key, seal, locks, kinds, log, time, purpose);"),
            Map.entry("SEAL_KEY", "ArmKeySeal.seal(key, packets, seal, time);"),
            Map.entry("PILOT_KEY_LOAD", "PilotKey.load(key);"),
            Map.entry("QUALITY_PLAN", "QualityReviewPlan.plan(key, out, seed);"),
            Map.entry("QUALITY_BATCHES", "QualityReviewBatches.batches(blind, plan, scores, out);"),
            Map.entry("QUALITY_REPORT", "QualityGateReport.report(key, p, r, a, d, m, out, flags);"),
            Map.entry("RESULTS_RUN", "ResultsPipeline.run(input);"),
            Map.entry("T2_RECOMPUTE", "T2TriggerRecord.recompute(paths, rechecks, hash);"),
            Map.entry("READ_KEY_HELPER", "readKey(key);"),
            Map.entry("PARSE_KEY_HELPER", "parseKey(file);"));
    private static final Set<String> KEY_FREE = Set.of("FrbProvenance", "FrbEvidenceBundle",
            "AdjudicatedConverter", "ClassBAdjudicatedConverter", "AdjudicatedLoaded", "AdjudicatedFacts",
            "ReserveDecisionRecord", "RouteVerdictCalculator", "RouteT2", "FrbGates", "ClassBReserve",
            "ExactPairedTest", "SolRule", "SolT2Improvement", "SolDecisionInputs", "DeadlineRule",
            "LockReadiness", "PairEvidence", "QuestionStatus", "ReviewSets", "Expansion");
    private static final Set<String> LEGACY_READERS = Set.of("ScoreEvidence.java", "ResultsPipeline.java",
            "T2TriggerRecord.java");
    private static final Map<String, String> OPERATOR_TEST_EXCLUSIONS = Map.of(
            "ArmKeyReaderGuardTest.java", "The scanner's synthetic pattern strings are not an operator entry point");
    private static final Set<String> KNOWN_OPERATOR_TESTS = Set.of("QualityEvaluationTest.java",
            "QualityGateG5Test.java", "StageRunnerFreezeEntryTest.java");
    private static final Map<String, Entry> ALLOWLIST = allowlist();

    private record Count(int value, String meaning) {}
    private record Entry(String group, String reason, String retire, Map<String, Count> counts) {}
    private static Count key(int n) { return new Count(n, "KEY READ"); }
    private static Count score(int n) { return new Count(n, "SCORE-FILE READ"); }
    private static Count mixed(int keyReads, int scoreReads) {
        return new Count(keyReads + scoreReads, keyReads + " KEY READ; " + scoreReads + " SCORE-FILE READ");
    }
    private static Entry entry(String group, String reason, String retire, Map<String, Count> counts) {
        return new Entry(group, reason, retire, counts);
    }
    private static Map<String, Entry> allowlist() {
        Map<String, Entry> rows = new LinkedHashMap<>();
        rows.put("ArmKeyAccess.java", entry("GATED", "Opened key is lock-bound and access-logged", GATE_RETIRE,
                Map.of("SEAL_KEY", key(1), "PARSE_KEY_HELPER", key(2))));
        rows.put("ArmKeySeal.java", entry("WRITER_OR_SEAL", "Hashes the key bytes for the seal", WRITER_RETIRE,
                Map.of("RAW_KEY_READ", key(1))));
        rows.put("ArmKeyGateFixtures.java", entry("TEST_FIXTURE", "Builds locked test openings and id-only scoring", FIXTURE_RETIRE,
                Map.of("CLASS_A_LOAD", key(1), "SEAL_KEY", key(1))));
        rows.put("BlindExport.java", entry("V0176_EVALUATION_TOOLING", "Names the earlier blind-key output", TOOL_RETIRE,
                Map.of("BLIND_KEY_LITERAL", key(1))));
        rows.put("ClassAAdjudicationFixture.java", entry("TEST_FIXTURE", "Loads an id-only key for fixture scores", FIXTURE_RETIRE,
                Map.of("CLASS_A_LOAD", key(1))));
        rows.put("ClassABlindPacketExport.java", entry("WRITER_OR_SEAL", "Writes sealed key and validates legacy score coverage", WRITER_RETIRE,
                Map.of("QUALITY_KEYED", mixed(1, 1), "RAW_KEY_READ", score(1))));
        rows.put("ClassAScoreFilesV2.java", entry("LEGACY_UNTIL_FRB_FINAL", "Old loader still accepts a real key", LEGACY_RETIRE,
                Map.of("READ_KEY_HELPER", key(2))));
        rows.put("ClassBBlindPacketExport.java", entry("WRITER_OR_SEAL", "Reads back written packet for leakage", WRITER_RETIRE,
                Map.of("RAW_KEY_READ", score(1))));
        rows.put("PilotKey.java", entry("LEGACY_UNTIL_FRB_FINAL", "Pilot key loader predates the gate", LEGACY_RETIRE,
                Map.of("RAW_KEY_READ", key(2))));
        rows.put("PilotTestKeys.java", entry("TEST_FIXTURE", "Loads pilot key in fixture setup", FIXTURE_RETIRE,
                Map.of("PILOT_KEY_LOAD", key(1))));
        rows.put("QualityBlindExport.java", entry("V0176_EVALUATION_TOOLING", "Writes the v0.176 blind key", TOOL_RETIRE,
                Map.of("BLIND_KEY_LITERAL", key(1))));
        rows.put("QualityEvaluationTest.java", entry("TEST_FIXTURE", "Opt-in quality evaluation test reads a named key", FIXTURE_RETIRE,
                Map.of("RAW_KEY_READ", key(1), "BLIND_KEY_LITERAL", key(1))));
        rows.put("QualityGateG5Test.java", entry("TEST_FIXTURE", "Operator-property test calls earlier report", FIXTURE_RETIRE,
                Map.of("QUALITY_REPORT", key(1))));
        rows.put("QualityGateReport.java", entry("V0176_EVALUATION_TOOLING", "Earlier gate reads key and scores", TOOL_RETIRE,
                Map.of("QUALITY_KEYED", key(1), "QUALITY_SCORES", key(1), "QUALITY_LINES", score(1),
                        "BLIND_KEY_LITERAL", key(1))));
        rows.put("QualityReviewBatches.java", entry("V0176_EVALUATION_TOOLING", "Earlier review joins key and score rows", TOOL_RETIRE,
                Map.of("QUALITY_KEYED", mixed(2, 1), "QUALITY_LINES", score(1),
                        "BLIND_KEY_LITERAL", key(1))));
        rows.put("QualityReviewPlan.java", entry("V0176_EVALUATION_TOOLING", "Earlier plan samples a key", TOOL_RETIRE,
                Map.of("QUALITY_KEYED", key(1), "BLIND_KEY_LITERAL", key(1))));
        rows.put("ResultsPipeline.java", entry("LEGACY_UNTIL_FRB_FINAL", "Legacy run loads scores and recomputes keyed trigger", LEGACY_RETIRE,
                Map.of("SCORE_EVIDENCE_LOAD", key(2), "T2_RECOMPUTE", key(1))));
        rows.put("ScoreEvidence.java", entry("LEGACY_UNTIL_FRB_FINAL", "Legacy score loader directly opens class-A and class-B keys", LEGACY_RETIRE,
                Map.of("QUALITY_KEYED", key(1), "QUALITY_SCORES", key(1), "QUALITY_LINES", score(1),
                        "CLASS_B_KEY_PARSE", key(1), "RAW_KEY_READ", key(1))));
        rows.put("StatementPairStage2Benchmark.java", entry("V0176_EVALUATION_TOOLING", "Names an earlier blind key", TOOL_RETIRE,
                Map.of("BLIND_KEY_LITERAL", key(1))));
        rows.put("T2TriggerRecord.java", entry("LEGACY_UNTIL_FRB_FINAL", "Legacy trigger recomputation calls score loader", LEGACY_RETIRE,
                Map.of("SCORE_EVIDENCE_LOAD", key(1))));
        return Map.copyOf(rows);
    }
    private static Map<String, Pattern> compile() {
        Map<String, Pattern> compiled = new LinkedHashMap<>();
        REGEX.forEach((name, regex) -> compiled.put(name, Pattern.compile(regex)));
        return Map.copyOf(compiled);
    }
    private static Path directory() {
        Path path = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize()).resolve(DIRECTORY);
        if (!Files.isDirectory(path)) fail("Arm key guard scan directory missing: " + path);
        return path;
    }
    private static boolean operatorEntryPoint(String source) {
        String text = stripComments(source);
        return text.contains("llm.benchmark") && (text.contains("System.getProperty(")
                || text.contains("required(") || text.contains("assumeTrue(Boolean.getBoolean("));
    }
    private static Map<String, Integer> scan(String source) {
        String code = stripComments(source);
        Map<String, Integer> counts = new LinkedHashMap<>();
        PATTERNS.forEach((name, pattern) -> {
            int n = 0;
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) n++;
            if (n > 0) counts.put(name, n);
        });
        return Map.copyOf(counts);
    }
    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int state = 0; // code, line comment, block comment, string, character, text block
        while (i < source.length()) {
            if (state == 0 && source.startsWith("\"\"\"", i)) {
                result.append("\"\"\""); i += 3; state = 5;
            } else if (state == 0 && source.startsWith("//", i)) { i += 2; state = 1;
            } else if (state == 0 && source.startsWith("/*", i)) { i += 2; state = 2;
            } else if (state == 2 && source.startsWith("*/", i)) { i += 2; state = 0;
            } else if (state == 5 && source.startsWith("\"\"\"", i)) {
                result.append("\"\"\""); i += 3; state = 0;
            } else if ((state == 3 || state == 4) && source.charAt(i) == '\\' && i + 1 < source.length()) {
                result.append(source, i, i + 2); i += 2;
            } else {
                char c = source.charAt(i++);
                if (state == 1 || state == 2) { if (c == '\n') result.append(c); if (state == 1 && c == '\n') state = 0; }
                else { result.append(c); if (state == 0 && c == '"') state = 3;
                    else if (state == 0 && c == '\'') state = 4;
                    else if (state == 3 && c == '"' || state == 4 && c == '\'') state = 0; }
            }
        }
        return result.toString();
    }

    @Test void everyHarnessReaderHasAnExactReasonedCount() throws IOException {
        Path dir = directory();
        List<Path> files;
        try (Stream<Path> paths = Files.list(dir)) { files = paths.filter(p -> p.toString().endsWith(".java")).toList(); }
        if (files.isEmpty()) fail("Arm key guard scanned zero Java files: " + dir);
        Map<String, Map<String, Integer>> actual = new LinkedHashMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (OPERATOR_TEST_EXCLUSIONS.containsKey(name)) {
                if (OPERATOR_TEST_EXCLUSIONS.get(name).isBlank()) fail("Operator exclusion reason missing: " + name);
                continue;
            }
            String source = Files.readString(file);
            if (KNOWN_OPERATOR_TESTS.contains(name) && !operatorEntryPoint(source))
                fail("Operator entry point escaped arm-key scan: " + name);
            if (name.endsWith("Test.java") && !operatorEntryPoint(source)) continue;
            Map<String, Integer> counts = scan(source);
            if (!counts.isEmpty()) actual.put(name, counts);
        }
        int legacy = LEGACY_READERS.stream().map(actual::get).filter(java.util.Objects::nonNull)
                .mapToInt(m -> m.values().stream().mapToInt(Integer::intValue).sum()).sum();
        if (legacy == 0) fail("Arm key guard vacuous: zero matches in known legacy readers under " + dir);
        for (var row : actual.entrySet()) {
            Entry allowed = ALLOWLIST.get(row.getKey());
            if (allowed == null) fail("Unallowlisted arm key reader: " + row.getKey() + " " + row.getValue());
            for (var match : row.getValue().entrySet()) {
                Count expected = allowed.counts().get(match.getKey());
                if (expected == null) fail("New arm key pattern: " + row.getKey() + " " + match.getKey()
                        + " count=" + match.getValue());
                if (expected.value() != match.getValue()) fail("Arm key ratchet: " + row.getKey() + " "
                        + match.getKey() + " recorded=" + expected.value() + " actual=" + match.getValue()
                        + (match.getValue() < expected.value() ? "; lower the allowlist entry" : "; new reader"));
            }
        }
        for (var row : ALLOWLIST.entrySet()) {
            if (!Files.isRegularFile(dir.resolve(row.getKey()))) fail("Stale arm key allowlist file: " + row.getKey());
            if (row.getValue().reason().isBlank() || row.getValue().retire().isBlank())
                fail("Arm key allowlist reason or retire condition missing: " + row.getKey());
            for (var expected : row.getValue().counts().entrySet()) {
                if (!PATTERNS.containsKey(expected.getKey())) fail("Unknown allowlist pattern: " + row.getKey()
                        + " " + expected.getKey());
                int found = actual.getOrDefault(row.getKey(), Map.of()).getOrDefault(expected.getKey(), 0);
                if (found != expected.getValue().value()) fail("Arm key ratchet: " + row.getKey() + " "
                        + expected.getKey() + " recorded=" + expected.getValue().value() + " actual=" + found
                        + "; lower the allowlist entry if a reader was removed");
            }
        }
    }

    @Test void scannerRecognizesEachPatternAndPreservesCodeAcrossStrings() {
        assertThat(PATTERNS.keySet()).containsExactlyInAnyOrderElementsOf(SAMPLES.keySet());
        SAMPLES.forEach((name, code) -> {
            assertThat(scan(code)).as("synthetic pattern " + name).containsKey(name);
            assertThat(scan("// " + code)).as("line comment " + name).doesNotContainKey(name);
            assertThat(scan("/* " + code + " */")).as("block comment " + name).doesNotContainKey(name);
            assertThat(scan("/** " + code + " */")).as("Javadoc " + name).doesNotContainKey(name);
        });
        assertThat(scan("String url = \"https://example.test/x\"; QualityScoreFiles.keyed(key);"))
                .containsKey("QUALITY_KEYED");
        assertThat(scan("String text = \"\"\"\nhttps://example.test/*x*/\n\"\"\";\nQualityScoreFiles.keyed(key);"))
                .containsKey("QUALITY_KEYED");
        assertThat(scan("Files.lines(keyPath); Files.readString(armKey);"))
                .containsEntry("RAW_KEY_READ", 2);
    }

    @Test void frbCalculationClassesHaveNoKeyReaderCalls() throws IOException {
        Path dir = directory();
        for (String name : KEY_FREE) {
            Path file = dir.resolve(name + ".java");
            if (!Files.isRegularFile(file)) fail("Missing FRB key-free class: " + name);
            String source = stripComments(Files.readString(file));
            Map<String, Integer> found = scan(source);
            if (!found.isEmpty()) fail("FRB key-free class has reader: " + name + " " + found);
            // ScoreEvidence and ClassAScoreFilesV2 value types are used by existing FRB classes.
            for (String forbidden : List.of("ScoreEvidence.load(", "QualityScoreFiles.",
                    "ClassAScoreFilesV2.load(", "ArmKeyAccess.open(", "ArmKeySeal.verifyKeyBytes("))
                if (source.contains(forbidden)) fail("FRB key-free class calls reader: " + name + " " + forbidden);
        }
    }

    @Test void runFrbSignaturesCannotAcceptArmKeyPaths() {
        int checked = 0;
        for (Method method : ResultsPipeline.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("runFrb") || Modifier.isPrivate(method.getModifiers())) continue;
            checked++;
            for (var parameter : method.getParameters()) {
                String descriptor = parameter.getName() + " " + parameter.getType().getTypeName();
                if (descriptor.matches("(?i).*(armKey|scoreEvidence\\.files|keyPath).*"))
                    fail("runFrb signature accepts key path: " + descriptor);
                if (parameter.getType().isRecord()) for (RecordComponent component : parameter.getType().getRecordComponents()) {
                    String nested = component.getName() + " " + component.getType().getTypeName();
                    if (nested.matches("(?i).*(armKey|scoreEvidence\\.files|keyPath).*"))
                        fail("runFrb record component accepts key path: " + nested);
                }
            }
        }
        assertThat(checked).as("public or package-private runFrb signature count").isPositive();
    }
}
