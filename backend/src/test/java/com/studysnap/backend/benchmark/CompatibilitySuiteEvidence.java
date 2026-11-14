package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;

/** Preregistration section 7 and Addendum 3: executed caller-regression evidence, never model evidence. */
final class CompatibilitySuiteEvidence {
    private CompatibilitySuiteEvidence() {}
    record State(Boolean passed, String reason) {}

    static Map<String, State> read(Path directory, Map<String, String> suites, Instant headTime) {
        if (directory == null || suites == null || headTime == null)
            throw new IllegalArgumentException("Compatibility directory, frozen suites and HEAD time required");
        List<Path> reports;
        try (var files = Files.list(directory)) { reports = files.toList(); }
        catch (IOException exception) { reports = List.of(); }
        Map<String, State> result = new HashMap<>();
        List<Path> available = reports;
        suites.forEach((suite, workflow) -> {
            Path file = available.stream().filter(path -> path.getFileName().toString()
                    .matches("TEST-(?:[A-Za-z0-9_]+\\.)*" + java.util.regex.Pattern.quote(suite) + "\\.xml"))
                    .findFirst().orElse(directory.resolve("TEST-" + suite + ".xml"));
            State state = inspect(file, headTime);
            State existing = result.get(workflow);
            if (existing == null || !Boolean.TRUE.equals(state.passed())) result.put(workflow, state);
        });
        return Map.copyOf(result);
    }

    private static State inspect(Path file, Instant headTime) {
        if (!Files.isRegularFile(file)) return new State(null, "absent: " + file);
        try {
            if (Files.getLastModifiedTime(file).toInstant().isBefore(headTime))
                return new State(null, "stale: " + file);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
            if (!"testsuite".equals(root.getTagName())) return new State(null, "invalid report: " + file);
            int tests = Integer.parseInt(root.getAttribute("tests"));
            int failures = Integer.parseInt(root.getAttribute("failures"));
            int errors = Integer.parseInt(root.getAttribute("errors"));
            int skipped = Integer.parseInt(root.getAttribute("skipped"));
            if (tests - skipped <= 0) return new State(null, "no executed tests: " + file);
            return failures == 0 && errors == 0 ? new State(true, "executed: " + file)
                    : new State(false, "suite failed: " + file);
        } catch (Exception exception) {
            return new State(null, "unreadable report: " + file);
        }
    }
}
