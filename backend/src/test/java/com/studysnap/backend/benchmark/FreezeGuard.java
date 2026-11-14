package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Section 10 pre-dispatch verification of the owner-approved freeze. */
final class FreezeGuard {
    private static final String PREREGISTRATION = "docs/claude-plans/2026-11-12-v0182-llm-migration-preregistration.md";
    private static final String PARAMETERS = "backend/src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json";
    private static final String PROMPTS = "backend/src/main/resources/prompts/study-pack-v1/";
    private static final String RESOURCE_ROOT = "backend/src/main/resources/";
    private static final String MISSING = "MISSING";
    private static final String HASH_SEPARATOR = " SHA-256 ";
    private static final String DIFFER = " != ";
    private FreezeGuard() {}

    static Verified verify(Path recordPath, Path repoRoot, String arm, String model,
                           Map<String, Path> usedManifests, Iterable<String> workflows) {
        try {
            String head = new String(new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot.toFile()).start().getInputStream().readAllBytes()).trim();
            String dirty = new String(new ProcessBuilder("git", "status", "--porcelain",
                    "--untracked-files=all", "--", "backend/src/main",
                    "backend/src/test/java/com/studysnap/backend/benchmark", "backend/pom.xml")
                    .directory(repoRoot.toFile()).start().getInputStream().readAllBytes()).trim();
            if (!dirty.isEmpty())
                throw new FreezeRecord.InvalidFreeze("Production or benchmark working tree differs from pinned commit");
            return verify(recordPath, repoRoot, arm, model, usedManifests, workflows, head, true);
        } catch (IOException exception) {
            throw new FreezeRecord.InvalidFreeze("Cannot read git HEAD", exception);
        }
    }

    static Verified verify(Path recordPath, Path repoRoot, String arm, String model,
                           Map<String, Path> usedManifests, Iterable<String> workflows, String head) {
        return verify(recordPath, repoRoot, arm, model, usedManifests, workflows, head, false);
    }

    private static Verified verify(Path recordPath, Path repoRoot, String arm, String model,
                           Map<String, Path> usedManifests, Iterable<String> workflows,
                           String head, boolean verifyLoadedResources) {
        FreezeRecord record = FreezeRecord.load(recordPath);
        try {
            if (!head.equals(record.gitCommit()))
                throw new FreezeRecord.InvalidFreeze("HEAD mismatch: " + record.gitCommit() + DIFFER + head);
            match(repoRoot, PREREGISTRATION, record.preregistrationSha256());
            match(repoRoot, PARAMETERS, record.parametersSha256());
            for (var entry : record.manifests().entrySet()) matchExternal(entry.getKey(), entry.getValue());
            for (var entry : record.prompts().entrySet()) {
                if (!entry.getKey().startsWith(PROMPTS)) throw new FreezeRecord.InvalidFreeze("Prompt path outside prompt directory: " + entry.getKey());
                match(repoRoot, entry.getKey(), entry.getValue());
                if (verifyLoadedResources) {
                    String resource = entry.getKey().substring(RESOURCE_ROOT.length());
                    var loaded = new org.springframework.core.io.ClassPathResource(resource);
                    String loadedHash = loaded.exists()
                            ? FreezeRecord.sha256(loaded.getInputStream().readAllBytes()) : MISSING;
                    if (!entry.getValue().equals(loadedHash))
                        throw new FreezeRecord.InvalidFreeze(entry.getKey() + " loaded SHA-256 "
                                + entry.getValue() + DIFFER + loadedHash);
                }
            }
            try (var paths = Files.list(repoRoot.resolve(PROMPTS))) {
                for (Path prompt : paths.filter(Files::isRegularFile).toList()) {
                    String relative = repoRoot.relativize(prompt).toString();
                    if (!record.prompts().containsKey(relative))
                        throw new FreezeRecord.InvalidFreeze("Prompt absent from freeze: " + relative);
                }
            }
            for (var entry : usedManifests.entrySet()) {
                String key = entry.getValue().toAbsolutePath().normalize().toString();
                if (!record.manifests().containsKey(key))
                    throw new FreezeRecord.InvalidFreeze("Manifest absent from freeze: " + key);
            }
            FreezeRecord.Arm selected = record.arms().get(arm);
            if (selected == null || !selected.model().equals(model))
                throw new FreezeRecord.InvalidFreeze("Arm/model absent from freeze: " + arm + "/" + model);
            for (String workflow : workflows)
                if (!selected.workflows().contains(workflow))
                    throw new FreezeRecord.InvalidFreeze("Workflow absent from arm " + arm + ": " + workflow);
            return new Verified(record, FreezeRecord.sha256(recordPath), head, verifyLoadedResources);
        } catch (IOException exception) {
            throw new FreezeRecord.InvalidFreeze("Cannot verify freeze", exception);
        }
    }

    private static void match(Path root, String relative, String expected) throws IOException {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root.normalize())) throw new FreezeRecord.InvalidFreeze("Pinned path escapes repository: " + relative);
        String actual = Files.exists(file) ? FreezeRecord.sha256(file) : MISSING;
        if (!expected.equals(actual)) throw new FreezeRecord.InvalidFreeze(relative + HASH_SEPARATOR + expected + DIFFER + actual);
    }
    private static void matchExternal(String path, String expected) throws IOException {
        Path file = Path.of(path);
        if (!file.isAbsolute()) throw new FreezeRecord.InvalidFreeze("Manifest path is not absolute: " + path);
        String actual = Files.exists(file) ? FreezeRecord.sha256(file) : MISSING;
        if (!expected.equals(actual)) throw new FreezeRecord.InvalidFreeze(path + HASH_SEPARATOR + expected + DIFFER + actual);
    }
    static Verified dryEvidence(FreezeRecord record, String sha256, String head) {
        return new Verified(record, sha256, head, false);
    }

    static final class Verified {
        private final FreezeRecord record;
        private final String sha256;
        private final String head;
        private final boolean real;
        private Verified(FreezeRecord record, String sha256, String head, boolean real) {
            this.record = record; this.sha256 = sha256; this.head = head; this.real = real;
        }
        FreezeRecord record() { return record; }
        String sha256() { return sha256; }
        String head() { return head; }
        boolean real() { return real; }
    }
}
