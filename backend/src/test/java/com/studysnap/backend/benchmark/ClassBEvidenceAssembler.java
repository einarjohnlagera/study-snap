package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/** File-backed class-B half of the FRB evidence assembly. */
final class ClassBEvidenceAssembler {
    private static final String PRIMARY_MANIFEST = "classB.primary";
    private static final String RESERVE_MANIFEST = "classB.reserve";
    private static final String PAID = "BASELINE_41";
    private static final String MINI = "BASELINE_MINI";

    private ClassBEvidenceAssembler() {}

    static ClassBAssembly assemble(ClassBSources sources) {
        if (sources == null) throw new InvalidClassBAssembly("Class-B sources required");
        if (sources.freezeFile() == null || sources.parametersFile() == null || sources.mappingFile() == null
                || sources.candidateReceiptFile() == null || sources.reserveRecordFile() == null
                || sources.primarySealFile() == null || sources.primaryLockFile() == null)
            throw new InvalidClassBAssembly("Complete class-B source paths required");
        try {
            SourceDigests.Builder digests = new SourceDigests.Builder();
            FreezeRecord freeze = digests.file("freeze", sources.freezeFile(),
                    () -> FreezeRecord.load(sources.freezeFile()));
            FrozenGateParameters parameters = digests.file("parameters", sources.parametersFile(),
                    () -> FrozenGateParameters.load(sources.parametersFile()));
            EvidenceMapping mapping = digests.file("mapping", sources.mappingFile(),
                    () -> EvidenceMapping.load(sources.mappingFile()));
            if (!parameters.frbEnabled || !parameters.sha256.equals(freeze.parametersSha256())
                    || !mapping.sha256.equals(freeze.evidenceMappingSha256()))
                throw new InvalidClassBAssembly("Class-B freeze parameter or mapping SHA-256 differs");
            Map<String, Set<String>> primaryIds = manifest(sources.manifests(), PRIMARY_MANIFEST, freeze, digests);
            Map<String, Set<String>> reserveIds = manifest(sources.manifests(), RESERVE_MANIFEST, freeze, digests);
            Set<String> workflows = Set.copyOf(parameters.frbClassBWorkflows);
            if (workflows.isEmpty() || !workflows.containsAll(primaryIds.keySet())
                    || !workflows.containsAll(reserveIds.keySet()) || !primaryIds.keySet().equals(workflows))
                throw new InvalidClassBAssembly("Class-B manifests differ from frozen workflows");
            Set<String> allIds = new HashSet<>();
            primaryIds.values().forEach(allIds::addAll);
            for (Set<String> ids : reserveIds.values())
                for (String id : ids)
                    if (!allIds.add(id)) throw new InvalidClassBAssembly("Case in primary and reserve manifests");

            ClassBExport primary = requireExport(sources.primary());
            List<ClassBScoreFiles.BlindItem> packetItems = digests.directory("primary.packets",
                    primary.packetDirectory(), () -> ReserveDecisionRecord.manifestFromPackets(primary.packetDirectory()));
            ClassBScoreFiles.Validated validated = digests.file("primary.scores", primary.scoresFile(),
                    () -> ClassBScoreFiles.validate(primary.scoresFile(), packetItems, primary.scorers()));
            validated.requireFullHumanCoverage();
            LockRecord lock = digests.file("primary.lock", sources.primaryLockFile(),
                    () -> LockRecord.load(sources.primaryLockFile()));
            ReserveDecisionRecord decision = digests.file("reserve.record", sources.reserveRecordFile(),
                    () -> ReserveDecisionRecord.load(sources.reserveRecordFile()));
            List<Path> otherFindings = lock.findings().stream()
                    .map(item -> primary.scoresFile().getParent().resolve(item.name()))
                    .filter(path -> !path.equals(primary.scoresFile())).toList();
            digests.checkedDirectory("primary.packets", primary.packetDirectory(), () -> {
                digests.checkedFile("primary.scores", primary.scoresFile(), () -> {
                    digests.checkedFile("primary.seal", sources.primarySealFile(), () -> {
                        digests.checkedFile("primary.lock", sources.primaryLockFile(), () ->
                                digests.checkedFiles("primary.finding", otherFindings, () ->
                                ReserveDecisionRecord.verify(decision, primary.packetDirectory(),
                                        sources.primarySealFile(), validated, mapping, workflows,
                                        primary.scoresFile(), lock, sources.primaryLockFile())));
                    });
                });
            });
            String candidate = digests.file("candidate.receipt", sources.candidateReceiptFile(),
                    () -> Stage1Receipt.read(sources.candidateReceiptFile())).arm();
            if (!freeze.arms().containsKey(candidate) || freeze.arms().get(PAID) == null
                    || freeze.arms().get(MINI) == null)
                throw new InvalidClassBAssembly("Class-B receipt or baseline arm absent from freeze");
            ClassBAdjudicatedConverter.ArmLabels labels = new ClassBAdjudicatedConverter.ArmLabels(
                    candidate, workflow -> freeze.arms().get(PAID).workflows().contains(workflow) ? PAID : MINI);
            PairMap primaryPairs = digests.directory("primary.packets", primary.packetDirectory(),
                    () -> PairMap.fromPackets(primary.packetDirectory()));
            AdjudicatedLoaded primaryFacts = ClassBAdjudicatedConverter.classB(primary.key(),
                    ClassBAdjudicatedConverter.ExportKind.PRIMARY, validated, mapping, primaryPairs, workflows,
                    union(primaryIds), labels);

            Map<String, ClassBExport> supplied = sources.reservesByWorkflow();
            if (supplied == null) throw new InvalidClassBAssembly("Reserve source map required");
            Map<String, Boolean> drawn = decision.reserveDrawnByWorkflow();
            if (!drawn.keySet().equals(workflows))
                throw new InvalidClassBAssembly("Reserve decisions differ from frozen workflows");
            Set<Path> packetPaths = new HashSet<>(Set.of(primary.packetDirectory().toAbsolutePath().normalize()));
            Set<Path> scorePaths = new HashSet<>(Set.of(primary.scoresFile().toAbsolutePath().normalize()));
            List<FrbEvidenceBundle.ReservePart> parts = new ArrayList<>();
            for (var entry : supplied.entrySet()) {
                String workflow = entry.getKey();
                if (workflow == null || !Boolean.TRUE.equals(drawn.get(workflow)))
                    throw new InvalidClassBAssembly("Unknown or undrawn reserve source: " + workflow);
                ClassBExport source = requireExport(entry.getValue());
                if (!packetPaths.add(source.packetDirectory().toAbsolutePath().normalize())
                        || !scorePaths.add(source.scoresFile().toAbsolutePath().normalize()))
                    throw new InvalidClassBAssembly("Duplicate class-B export source");
                String prefix = "reserve." + workflow;
                List<ClassBScoreFiles.BlindItem> reserveItems = digests.directory(prefix + ".packets",
                        source.packetDirectory(), () -> ReserveDecisionRecord.manifestFromPackets(source.packetDirectory()));
                ClassBScoreFiles.Validated reserveScores = digests.file(prefix + ".scores", source.scoresFile(),
                        () -> ClassBScoreFiles.validate(source.scoresFile(), reserveItems, source.scorers()));
                ReserveLockReadiness.check(reserveScores, reserveItems);
                PairMap pairs = digests.directory(prefix + ".packets", source.packetDirectory(),
                        () -> PairMap.fromPackets(source.packetDirectory()));
                AdjudicatedLoaded facts = ClassBAdjudicatedConverter.classB(source.key(),
                        ClassBAdjudicatedConverter.ExportKind.RESERVE, reserveScores, mapping, pairs,
                        Set.of(workflow), reserveIds.getOrDefault(workflow, Set.of()), labels);
                parts.add(new FrbEvidenceBundle.ReservePart(workflow, facts,
                        reserveIds.getOrDefault(workflow, Set.of())));
            }
            parts.sort(java.util.Comparator.comparing(FrbEvidenceBundle.ReservePart::workflow));
            return new ClassBAssembly(primaryFacts, List.copyOf(parts), decision, workflows, digests.build());
        } catch (IOException exception) {
            throw new InvalidClassBAssembly("Cannot read class-B assembly source", exception);
        }
    }

    private static ClassBExport requireExport(ClassBExport export) {
        if (export == null || export.packetDirectory() == null || export.scoresFile() == null
                || export.scoresFile().getParent() == null
                || export.scorers() == null || export.key() == null)
            throw new InvalidClassBAssembly("Complete class-B export required");
        return export;
    }

    private static Set<String> union(Map<String, Set<String>> byWorkflow) {
        Set<String> result = new LinkedHashSet<>();
        byWorkflow.values().forEach(result::addAll);
        return Set.copyOf(result);
    }

    private static Map<String, Set<String>> manifest(Map<String, Path> files, String key,
            FreezeRecord freeze, SourceDigests.Builder digests) throws IOException {
        if (files == null || !files.containsKey(key) || files.get(key) == null)
            throw new InvalidClassBAssembly("Missing class-B manifest: " + key);
        Path file = files.get(key);
        return digests.file("manifest." + key, file, () -> {
            String expected = freeze.manifests().get(file.toAbsolutePath().toString());
            if (expected == null || !expected.equals(FreezeRecord.sha256(file)))
                throw new InvalidClassBAssembly("Unpinned class-B manifest: " + key);
            Map<String, Set<String>> result = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                JsonNode row = IntegritySupport.JSON.readTree(line);
                if (row == null || !row.isObject())
                    throw new InvalidClassBAssembly("Invalid class-B manifest row");
                String id = row.path("fixtureId").asText();
                String workflow = Stage1RunPlan.workflowName(row.path("workflow").asText());
                if (id.isBlank() || workflow.isBlank() || !seen.add(id))
                    throw new InvalidClassBAssembly("Invalid or duplicate class-B frozen case");
                result.computeIfAbsent(workflow, ignored -> new LinkedHashSet<>()).add(id);
            }
            return Map.copyOf(result);
        });
    }
}

record ClassBSources(Path freezeFile, Path parametersFile, Path mappingFile, Path candidateReceiptFile,
        Map<String, Path> manifests, ClassBExport primary, Map<String, ClassBExport> reservesByWorkflow,
        Path reserveRecordFile, Path primarySealFile, Path primaryLockFile) {}

record ClassBExport(Path packetDirectory, Path scoresFile,
        Map<String, ClassBScoreFiles.ScorerKind> scorers, ArmKeyAccess.OpenedKey key) {}

record ClassBAssembly(AdjudicatedLoaded primary, List<FrbEvidenceBundle.ReservePart> reserveParts,
        ReserveDecisionRecord decision, Set<String> classBWorkflowNames, SourceDigests digests) {}

record SourceDigests(SortedMap<String, String> values) {
    SourceDigests { values = Collections.unmodifiableSortedMap(new TreeMap<>(values)); }

    @FunctionalInterface interface Source<T> { T load() throws IOException; }
    @FunctionalInterface interface Action { void run() throws IOException; }

    static final class Builder {
        private static final String PACKET_SUFFIX = ".jsonl";
        private final SortedMap<String, String> values = new TreeMap<>();

        <T> T file(String role, Path path, Source<T> source) throws IOException {
            String before = FreezeRecord.sha256(path);
            Path detached = IntegritySupport.detached(path);
            String detachedBefore = Files.exists(detached) ? FreezeRecord.sha256(detached) : null;
            T loaded = source.load();
            String after = FreezeRecord.sha256(path);
            if (!before.equals(after)) throw new InvalidClassBAssembly("Class-B source changed while loading: " + role);
            if (detachedBefore != null) {
                if (!detachedBefore.equals(FreezeRecord.sha256(detached)))
                    throw new InvalidClassBAssembly("Class-B detached digest changed while loading: " + role);
                put(role + ".sha256", detachedBefore);
            }
            put(role, before);
            return loaded;
        }

        <T> T directory(String role, Path path, Source<T> source) throws IOException {
            String before = directoryHash(path);
            T loaded = source.load();
            String after = directoryHash(path);
            if (!before.equals(after)) throw new InvalidClassBAssembly("Class-B packets changed while loading: " + role);
            put(role, before);
            return loaded;
        }

        void checkedFile(String role, Path path, Action action) throws IOException {
            file(role, path, () -> { action.run(); return null; });
        }

        void checkedDirectory(String role, Path path, Action action) throws IOException {
            directory(role, path, () -> { action.run(); return null; });
        }

        void checkedFiles(String prefix, List<Path> files, Action action) throws IOException {
            if (files.isEmpty()) { action.run(); return; }
            Path first = files.getFirst();
            checkedFile(prefix + ".0", first,
                    () -> checkedFilesFrom(prefix, files, 1, action));
        }

        private void checkedFilesFrom(String prefix, List<Path> files, int index, Action action) throws IOException {
            if (index == files.size()) { action.run(); return; }
            checkedFile(prefix + "." + index, files.get(index),
                    () -> checkedFilesFrom(prefix, files, index + 1, action));
        }

        private void put(String role, String hash) {
            String previous = values.putIfAbsent(role, hash);
            if (previous != null && !previous.equals(hash))
                throw new InvalidClassBAssembly("Class-B source changed between reads: " + role);
        }

        SourceDigests build() { return new SourceDigests(values); }

        private static String directoryHash(Path directory) throws IOException {
            try (Stream<Path> paths = Files.walk(directory)) {
                return directoryHash(directory, paths.toList());
            }
        }

        static String directoryHash(Path directory, List<Path> entries) throws IOException {
            try {
                if (entries.stream().anyMatch(path -> Files.isSymbolicLink(path)
                        || !Files.isDirectory(path) && !Files.isRegularFile(path)))
                    throw new InvalidClassBAssembly("Invalid class-B packet directory entry");
                List<Path> files = entries.stream().filter(Files::isRegularFile)
                        .sorted(java.util.Comparator.comparing(path -> directory.relativize(path).toString())).toList();
                if (files.isEmpty()) throw new InvalidClassBAssembly("Empty class-B packet directory");
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    if (!directory.equals(file.getParent()) || !name.endsWith(PACKET_SUFFIX)
                            || java.util.Arrays.stream(ClassBCaseManifest.Workflow.values())
                                    .noneMatch(workflow -> name.equals(workflow.name() + PACKET_SUFFIX)))
                        throw new InvalidClassBAssembly("Unexpected class-B packet filename");
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (Path file : files) {
                    byte[] name = directory.relativize(file).toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(name.length).array());
                    digest.update(name);
                    digest.update(java.util.HexFormat.of().parseHex(FreezeRecord.sha256(file)));
                }
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException exception) {
                throw new InvalidClassBAssembly("SHA-256 unavailable", exception);
            }
        }
    }
}

final class InvalidClassBAssembly extends RuntimeException {
    InvalidClassBAssembly(String message) { super(message); }
    InvalidClassBAssembly(String message, Throwable cause) { super(message, cause); }
}
