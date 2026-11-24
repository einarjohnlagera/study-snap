package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** File-backed, single-export class-A evidence assembly. */
final class ClassAEvidenceAssembler {
    private static final String PRIMARY = "classA.primary";
    private static final String PLAN = "classA.spotCheckPlan";
    private static final String PAID = "BASELINE_41";
    private static final String MINI = "BASELINE_MINI";
    private static final String HUMAN_FILE = "human.jsonl";
    private static final String SPOT = SeedStreamRegistry.SPOT_CHECK;
    private static final String EXPANSION = SeedStreamRegistry.EXPANSION;
    private static final String PACKET_SUFFIX = ".jsonl";
    private static final String ROLE_FREEZE = "freeze";
    private static final String ROLE_PARAMETERS = "parameters";
    private static final String ROLE_MAPPING = "mapping";
    private static final String ROLE_PACKETS = "classA.packets";
    private static final String ROLE_SEAL = "classA.seal";
    private static final String ROLE_LOCK = "classA.lock";
    private static final String ROLE_P = "classA.scores.p";
    private static final String ROLE_S = "classA.scores.s";
    private static final String ROLE_H = "classA.scores.h";
    private static final String ROLE_DRAW = "classA.drawlog";
    private static final String ROLE_REGISTRY = "classA.registry";
    private static final String ROLE_EXCLUSIONS = "classA.exclusions";
    private static final String ROLE_FINDING_PREFIX = "classA.lock.finding.";
    private ClassAEvidenceAssembler() {}

    static ClassAAssembly assemble(ClassASources sources) {
        require(sources);
        ClassAExport export = sources.export();
        SourceDigests.Builder digests = new SourceDigests.Builder(ClassAEvidenceAssembler::packetName,
                InvalidClassAAssembly::new);
        try {
            FreezeRecord freeze = digests.file(ROLE_FREEZE, sources.freezeFile(),
                    () -> FreezeRecord.load(sources.freezeFile()));
            FrozenGateParameters parameters = digests.file(ROLE_PARAMETERS, sources.parametersFile(),
                    () -> FrozenGateParameters.load(sources.parametersFile()));
            EvidenceMapping mapping = digests.file(ROLE_MAPPING, sources.mappingFile(),
                    () -> EvidenceMapping.load(sources.mappingFile()));
            if (!parameters.frbEnabled || !parameters.sha256.equals(freeze.parametersSha256())
                    || !mapping.sha256.equals(freeze.evidenceMappingSha256()))
                throw new InvalidClassAAssembly("Frozen parameters or mapping differ");
            Path manifestFile = pinned(sources, freeze, PRIMARY);
            Map<String, FrozenRow> all = digests.file("manifest." + PRIMARY, manifestFile,
                    () -> manifest(manifestFile));
            Path planFile = pinned(sources, freeze, PLAN);
            Map<String, Integer> plan = digests.file("manifest." + PLAN, planFile,
                    () -> spotPlan(planFile));
            ArmKeySeal.Seal seal = digests.file(ROLE_SEAL, export.sealFile(),
                    () -> ArmKeySeal.load(export.sealFile()));
            LockRecord lock = digests.file(ROLE_LOCK, export.lockFile(),
                    () -> LockRecord.load(export.lockFile()));
            verifySealAndLock(export, sources, seal, lock);
            for (int i = 0; i < lock.findings().size(); i++) {
                LockRecord.Finding finding = lock.findings().get(i);
                digests.file(ROLE_FINDING_PREFIX + i,
                        export.pScores().getParent().resolve(finding.name()), () -> null);
            }
            ClassAPacketIndex index = digests.directory(ROLE_PACKETS, export.packetDirectory(),
                    () -> ClassAPacketIndex.load(export.packetDirectory()));
            PairMap pairs = digests.directory(ROLE_PACKETS, export.packetDirectory(),
                    () -> PairMap.fromPackets(export.packetDirectory()));
            Map<String, ClassAScoreFilesV2.ItemScores> scores = digests.file(ROLE_P, export.pScores(),
                    () -> ClassAScoreFilesV2.loadBlind(index, export.pScores(), export.sScores(),
                            export.hScores(), mapping));
            digests.file(ROLE_S, export.sScores(), () -> null);
            digests.file(ROLE_H, export.hScores(), () -> null);
            ReviewSets sets = ReviewSets.compute(pairs, scores, mapping);
            SeedStreamRegistry registry = digests.file(ROLE_REGISTRY, sources.seedRegistryFile(),
                    () -> SeedStreamRegistry.load(sources.seedRegistryFile()));
            List<DrawLog.Entry> entries = digests.file(ROLE_DRAW, sources.drawLogFile(),
                    () -> new DrawLog(sources.drawLogFile()).entries());
            Set<String> stems = seal.workflowStems();
            if (!plan.keySet().equals(stems) || !sets.frameByStem().keySet().equals(stems))
                throw new InvalidClassAAssembly("Spot-check plan or packet stems differ from sealed export");
            verifyDraws(entries, plan, sets, pairs, registry, stems);
            Set<String> selected = new LinkedHashSet<>();
            entries.forEach(entry -> selected.addAll(entry.selectedPairIds()));
            Set<String> required = ReviewSets.reviewRequiredPairs(sets, selected);
            Map<String, QuestionStatus> statuses = new LinkedHashMap<>();
            for (var item : scores.values())
                statuses.put(item.id(), FinalStatus.of(item, mapping, required.contains(pairs.pairOf(item.id()))));
            Scope scope = scope(all, freeze, export.key(), stems);
            if (export.exclusionsFileOrNull() != null)
                digests.file(ROLE_EXCLUSIONS, export.exclusionsFileOrNull(), () -> null);
            AdjudicatedLoaded facts = AdjudicatedConverter.classA(export.key(), Map.copyOf(statuses), pairs,
                    stems, scope.ids(), export.exclusionsFileOrNull(), export.expectedExclusionsSha256OrNull(),
                    scope.widths());
            digests.file(ROLE_FREEZE, sources.freezeFile(), () -> null);
            digests.file(ROLE_PARAMETERS, sources.parametersFile(), () -> null);
            digests.file(ROLE_MAPPING, sources.mappingFile(), () -> null);
            digests.file("manifest." + PRIMARY, manifestFile, () -> null);
            digests.file("manifest." + PLAN, planFile, () -> null);
            digests.directory(ROLE_PACKETS, export.packetDirectory(), () -> null);
            digests.file(ROLE_SEAL, export.sealFile(), () -> null);
            digests.file(ROLE_LOCK, export.lockFile(), () -> null);
            digests.file(ROLE_P, export.pScores(), () -> null);
            digests.file(ROLE_S, export.sScores(), () -> null);
            digests.file(ROLE_H, export.hScores(), () -> null);
            digests.file(ROLE_DRAW, sources.drawLogFile(), () -> null);
            digests.file(ROLE_REGISTRY, sources.seedRegistryFile(), () -> null);
            if (export.exclusionsFileOrNull() != null)
                digests.file(ROLE_EXCLUSIONS, export.exclusionsFileOrNull(), () -> null);
            for (int i = 0; i < lock.findings().size(); i++) {
                LockRecord.Finding finding = lock.findings().get(i);
                digests.file(ROLE_FINDING_PREFIX + i,
                        export.pScores().getParent().resolve(finding.name()), () -> null);
            }
            pinned(sources, freeze, PRIMARY);
            pinned(sources, freeze, PLAN);
            verifySealAndLock(export, sources, seal, lock);
            return new ClassAAssembly(facts, Map.copyOf(statuses), pairs, sets, required,
                    List.copyOf(entries), digests.build());
        } catch (IOException exception) {
            throw new InvalidClassAAssembly("Cannot read class-A assembly source", exception);
        }
    }

    private static void require(ClassASources sources) {
        if (sources == null || sources.freezeFile() == null || sources.parametersFile() == null
                || sources.mappingFile() == null || sources.manifests() == null || sources.export() == null
                || sources.drawLogFile() == null || sources.seedRegistryFile() == null)
            throw new InvalidClassAAssembly("Complete class-A sources required");
        ClassAExport export = sources.export();
        if (export.packetDirectory() == null || export.pScores() == null || export.sScores() == null
                || export.hScores() == null || export.key() == null || export.sealFile() == null
                || export.lockFile() == null || export.pScores().getParent() == null
                || (export.exclusionsFileOrNull() == null) != (export.expectedExclusionsSha256OrNull() == null))
            throw new InvalidClassAAssembly("Complete class-A export required");
        Path directory = export.pScores().toAbsolutePath().normalize().getParent();
        Set<String> names = new HashSet<>();
        for (Path path : List.of(export.pScores(), export.sScores(), export.hScores(),
                sources.drawLogFile(), sources.seedRegistryFile())) {
            if (!directory.equals(path.toAbsolutePath().normalize().getParent())
                    || !names.add(path.getFileName().toString()))
                throw new InvalidClassAAssembly("Findings must have distinct names in one directory");
        }
        if (!HUMAN_FILE.equals(export.hScores().getFileName().toString()))
            throw new InvalidClassAAssembly("Human finding filename differs from required convention");
    }

    private static boolean packetName(String name) {
        if (!name.endsWith(PACKET_SUFFIX)) return false;
        String stem = name.substring(0, name.length() - PACKET_SUFFIX.length());
        if (stem.startsWith("LONG_EXAM_"))
            return java.util.Arrays.stream(ClassACaseManifest.Subgroup.values())
                    .anyMatch(group -> stem.equals("LONG_EXAM_" + group.name()));
        return java.util.Arrays.stream(ClassACaseManifest.Workflow.values())
                .anyMatch(workflow -> stem.equals(workflow.name()));
    }

    private static Path pinned(ClassASources sources, FreezeRecord freeze, String role) throws IOException {
        Path path = sources.manifests().get(role);
        if (path == null || !FreezeRecord.sha256(path).equals(freeze.manifests().get(path.toAbsolutePath().toString())))
            throw new InvalidClassAAssembly("Unpinned class-A manifest: " + role);
        return path;
    }

    private record FrozenRow(String workflow, String stem, int width) {}
    private record Scope(Set<String> ids, Map<String, Integer> widths) {}

    private static Map<String, FrozenRow> manifest(Path path) throws IOException {
        Map<String, FrozenRow> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            JsonNode row = IntegritySupport.JSON.readTree(line);
            if (row == null || !row.isObject() || !row.path("caseId").isTextual()
                    || !row.path("workflow").isTextual() || !row.path("requestedQuestionCount").isInt())
                throw new InvalidClassAAssembly("Malformed class-A manifest row");
            String id = row.path("caseId").asText();
            String workflow = Stage1RunPlan.workflowName(row.path("workflow").asText());
            String subgroup = row.path("subgroup").isTextual() ? row.path("subgroup").asText() : null;
            String stem = workflow + (subgroup == null ? "" : "_" + subgroup);
            int width = row.path("requestedQuestionCount").asInt();
            if (id.isBlank() || width < 1 || !packetName(stem + PACKET_SUFFIX)
                    || result.putIfAbsent(id, new FrozenRow(workflow, stem, width)) != null)
                throw new InvalidClassAAssembly("Invalid or duplicate class-A frozen case");
        }
        if (result.isEmpty()) throw new InvalidClassAAssembly("Empty class-A manifest");
        return Map.copyOf(result);
    }

    private static Map<String, Integer> spotPlan(Path path) throws IOException {
        JsonNode root = IntegritySupport.JSON.readTree(Files.readAllBytes(path));
        if (root == null || !root.isObject() || root.size() != 1 || !root.path("stems").isArray())
            throw new InvalidClassAAssembly("Malformed spot-check plan");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (JsonNode row : root.path("stems")) {
            if (!row.isObject() || row.size() != 2 || !row.path("stem").isTextual()
                    || !row.path("count").isInt() || row.path("count").asInt() < 1
                    || result.putIfAbsent(row.path("stem").asText(), row.path("count").asInt()) != null)
                throw new InvalidClassAAssembly("Invalid spot-check plan stem");
        }
        if (result.isEmpty()) throw new InvalidClassAAssembly("Empty spot-check plan");
        return Map.copyOf(result);
    }

    private static Scope scope(Map<String, FrozenRow> all, FreezeRecord freeze,
            ArmKeyAccess.OpenedKey opened, Set<String> stems) {
        Set<String> labels = new HashSet<>();
        opened.rows().forEach(row -> labels.add(row.arm()));
        String baseline = labels.contains(PAID) ? PAID : MINI;
        if (!labels.contains(baseline) || labels.size() != 2 || labels.contains(PAID) && labels.contains(MINI)
                || freeze.arms().get(PAID) == null || freeze.arms().get(MINI) == null)
            throw new InvalidClassAAssembly("Export must contain one frozen baseline and one candidate arm");
        if (labels.stream().anyMatch(label -> !freeze.arms().containsKey(label)))
            throw new InvalidClassAAssembly("Export arm is absent from freeze");
        Set<String> ids = new LinkedHashSet<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        Set<String> expectedStems = new HashSet<>();
        all.forEach((id, row) -> {
            String mapped = freeze.arms().get(PAID).workflows().contains(row.workflow()) ? PAID : MINI;
            if (mapped.equals(baseline)) {
                ids.add(id);
                widths.put(id, row.width());
                expectedStems.add(row.stem());
            }
        });
        if (ids.isEmpty() || !expectedStems.equals(stems))
            throw new InvalidClassAAssembly("Export stems differ from frozen baseline scope");
        return new Scope(Set.copyOf(ids), Map.copyOf(widths));
    }

    private static void verifySealAndLock(ClassAExport export, ClassASources sources,
            ArmKeySeal.Seal seal, LockRecord lock) throws IOException {
        ArmKeySeal.verifyPackets(export.packetDirectory(), seal);
        if (lock.kind() != LockRecord.Kind.PRIMARY
                || !lock.sealSha256().equals(FreezeRecord.sha256(export.sealFile()))
                || !lock.armKeySha256().equals(seal.armKeySha256())
                || !lock.packetFiles().equals(seal.packetFiles().stream()
                    .map(packet -> new LockRecord.PacketDigest(packet.name(), packet.sha256())).toList())
                || !Set.copyOf(lock.workflows()).equals(seal.workflowStems()))
            throw new InvalidClassAAssembly("Class-A lock differs from sealed export");
        Path directory = export.pScores().getParent();
        LockRecord.verifyFindingsUnchanged(lock, directory);
        for (Path path : List.of(export.pScores(), export.sScores(), export.hScores(),
                sources.drawLogFile(), sources.seedRegistryFile())) {
            LockRecord.Finding finding = lock.findings().stream()
                    .filter(item -> item.name().equals(path.getFileName().toString())).findFirst().orElse(null);
            if (finding == null || !finding.sha256().equals(FreezeRecord.sha256(path)))
                throw new InvalidClassAAssembly("Class-A finding differs from lock");
        }
    }

    private static void verifyDraws(List<DrawLog.Entry> entries, Map<String, Integer> plan,
            ReviewSets sets, PairMap pairs, SeedStreamRegistry registry, Set<String> stems) throws IOException {
        Map<String, Integer> spotCount = new HashMap<>(), expansionRound = new HashMap<>();
        Set<String> spotSelections = new HashSet<>();
        for (DrawLog.Entry entry : entries) {
            if (!entry.registrySha256().equals(registry.sha256()))
                throw new InvalidClassAAssembly("Draw registry SHA-256 differs");
            if (entry.purpose().equals(SPOT)) spotSelections.addAll(entry.selectedPairIds());
        }
        for (DrawLog.Entry entry : entries) {
            if (!stems.contains(entry.workflow())) continue;
            Set<String> frame = sets.frameByStem().get(entry.workflow());
            if (!registry.isDeclared(entry.workflow(), entry.purpose(), entry.round()))
                throw new InvalidClassAAssembly("Draw stream not declared in registry");
            if (entry.purpose().equals(SPOT)) {
                if (entry.round() == 1 && (entry.count() != plan.get(entry.workflow())
                        || spotCount.putIfAbsent(entry.workflow(), 1) != null))
                    throw new InvalidClassAAssembly("Spot-check draw differs from frozen plan");
                Path replayFile = Files.createTempFile("class-a-draw-replay-", ".jsonl");
                try {
                    Files.delete(replayFile);
                    DrawLog.Entry replay = PairSampler.draw(registry, new DrawLog(replayFile), pairs,
                            entry.workflow(), SPOT, entry.round(), new ArrayList<>(frame),
                            entry.count(), entry.clockUtc());
                    if (!entry.frameSha256().equals(replay.frameSha256())
                            || entry.frameSize() != replay.frameSize()
                            || !entry.selectedPairIds().equals(replay.selectedPairIds())
                            || entry.exhausted() != replay.exhausted())
                        throw new InvalidClassAAssembly("Spot-check draw replay differs");
                } finally { Files.deleteIfExists(replayFile); }
            } else {
                int previous = expansionRound.getOrDefault(entry.workflow(), 0);
                if (entry.round() <= previous || entry.frameSize() > frame.size() -
                        frame.stream().filter(spotSelections::contains).count()
                        || !frame.containsAll(entry.selectedPairIds())
                        || !java.util.Collections.disjoint(spotSelections, entry.selectedPairIds()))
                    throw new InvalidClassAAssembly("Expansion draw outside provable frame or round order");
                expansionRound.put(entry.workflow(), entry.round());
            }
        }
        if (!spotCount.keySet().equals(stems))
            throw new InvalidClassAAssembly("Missing frozen spot-check draw");
    }
}

record ClassASources(Path freezeFile, Path parametersFile, Path mappingFile,
        Map<String, Path> manifests, ClassAExport export, Path drawLogFile, Path seedRegistryFile) {}
record ClassAExport(Path packetDirectory, Path pScores, Path sScores, Path hScores,
        ArmKeyAccess.OpenedKey key, Path sealFile, Path lockFile,
        Path exclusionsFileOrNull, String expectedExclusionsSha256OrNull) {}
record ClassAAssembly(AdjudicatedLoaded facts, Map<String, QuestionStatus> statuses, PairMap pairs,
        ReviewSets sets, Set<String> reviewRequiredPairs, List<DrawLog.Entry> drawEntries, SourceDigests digests) {
    ClassAAssembly {
        statuses = Map.copyOf(statuses);
        reviewRequiredPairs = Set.copyOf(reviewRequiredPairs);
        drawEntries = List.copyOf(drawEntries);
    }
}
final class InvalidClassAAssembly extends RuntimeException {
    InvalidClassAAssembly(String message) { super(message); }
    InvalidClassAAssembly(String message, Throwable cause) { super(message, cause); }
}
