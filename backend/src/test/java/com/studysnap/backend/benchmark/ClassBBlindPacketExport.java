package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/** Arm-blind class-B human packet for preregistration section 7, Revision 2.2. */
final class ClassBBlindPacketExport {
    static final class InvalidPacket extends RuntimeException {
        InvalidPacket(String message) { super(message); }
        InvalidPacket(String message, Throwable cause) { super(message, cause); }
    }
    record Output(String caseId, ClassBScoreAggregator.Arm arm, String text) {}
    record Export(List<ClassBScoreFiles.BlindItem> manifest, List<ClassBScoreAggregator.Key> armKey) {}

    static final Pattern LEAK = Pattern.compile("(?i)(?:\\bbaseline\\b|\\bcandidate\\b|\\bluna\\b|"
            + "\\bsol\\b|\\bgpt(?:[-_. ]?[0-9])?\\b|\\b(?:codex|claude|gemini|openai|model|"
            + "modelName|modelId|token|tokens|tokenCount|token_count|inputTokens|outputTokens|"
            + "input_tokens|output_tokens|reasoning_tokens|usage|costUsd)\\b)");
    private static final String NEWLINE = "\n";
    private static final String RESERVE_EXPORT_PREFIX = "CLASS_B_RESERVE:";
    private static final String PRIVATE_DIRECTORY = "Private evaluation directory is unavailable to this exporter";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @FunctionalInterface private interface PlacementChoice { boolean firstInA(String group, String caseId); }
    private ClassBBlindPacketExport() {}

    static Export export(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                         Path packetDirectory, Path armKeyPath, long seed) {
        return exportBody(fixtures, outputs, packetDirectory, armKeyPath, seed,
                null, null, null, PlacementSidecar.FILE_WRITER, ClassBCaseManifest.Workflow::ordinal, null);
    }

    static Export export(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                         Path packetDirectory, Path armKeyPath, String exportId,
                         PlacementSecret secret, String expectedCommitment) {
        return export(fixtures, outputs, packetDirectory, armKeyPath, exportId, secret,
                expectedCommitment, null);
    }

    static Export export(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                         Path packetDirectory, Path armKeyPath, String exportId,
                         PlacementSecret secret, String expectedCommitment, String freezeSha256OrNull) {
        return export(fixtures, outputs, packetDirectory, armKeyPath, exportId, secret,
                expectedCommitment, freezeSha256OrNull, PlacementSidecar.FILE_WRITER);
    }

    static Export export(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                         Path packetDirectory, Path armKeyPath, String exportId,
                         PlacementSecret secret, String expectedCommitment, String freezeSha256OrNull,
                         PlacementSidecar.Writer writer) {
        if (secret == null || !IntegritySupport.digest(expectedCommitment)
                || !secret.commitment().equals(expectedCommitment) || fixtures == null
                || fixtures.isEmpty() || writer == null)
            throw new InvalidPlacementSeal();
        if ("CLASS_B_PRIMARY".equals(exportId)) {
            if (fixtures.stream().anyMatch(fixture -> fixture == null || fixture.reserve()))
                throw new InvalidPlacementSeal();
        } else if (exportId != null && exportId.startsWith(RESERVE_EXPORT_PREFIX)) {
            String workflow = exportId.substring(RESERVE_EXPORT_PREFIX.length());
            if (fixtures.isEmpty() || fixtures.stream().anyMatch(fixture -> fixture == null
                    || !fixture.reserve() || !fixture.workflow().name().equals(workflow)))
                throw new InvalidPlacementSeal();
        } else throw new InvalidPlacementSeal();
        Path sidecar = PlacementSidecar.beside(armKeyPath);
        if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) throw new InvalidPlacementSeal();
        PlacementSidecar.requireOutsidePackets(sidecar, packetDirectory);
        return exportBody(fixtures, outputs, packetDirectory, armKeyPath, secret.seedLong(),
                secret, exportId, freezeSha256OrNull, writer,
                workflow -> workflow.name().hashCode(),
                (group, caseId) -> PlacementPlan.firstArmInA(secret, exportId, group, caseId));
    }

    private static Export exportBody(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                                     Path packetDirectory, Path armKeyPath, long seed,
                                     PlacementSecret secret, String exportId, String freezeSha256OrNull,
                                     PlacementSidecar.Writer writer,
                                     java.util.function.ToIntFunction<ClassBCaseManifest.Workflow> groupKey,
                                     PlacementChoice placement) {
        boolean sealed = secret != null;
        List<Path> created = new ArrayList<>();
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            guarded(packetDirectory, repo);
            guarded(armKeyPath, repo);
            if (armKeyPath.toAbsolutePath().normalize().startsWith(packetDirectory.toAbsolutePath().normalize()))
                throw new InvalidPacket("Arm key must be separate from packet directory");
            Map<String, Map<ClassBScoreAggregator.Arm, String>> textByCase = new HashMap<>();
            for (Output output : outputs) {
                if (output == null || output.caseId() == null || output.arm() == null || output.text() == null
                        || textByCase.computeIfAbsent(output.caseId(), unused -> new HashMap<>())
                            .putIfAbsent(output.arm(), output.text()) != null)
                    throw new InvalidPacket("Missing or duplicate output");
            }
            Set<String> fixtureIds = new java.util.HashSet<>();
            for (ClassBCaseManifest.Case fixture : fixtures)
                if (fixture == null || !fixtureIds.add(fixture.fixtureId()))
                    throw new InvalidPacket("Duplicate fixture ID");
            if (!fixtureIds.equals(textByCase.keySet())) throw new InvalidPacket("Output and fixture sets differ");
            Map<ClassBCaseManifest.Workflow, List<ClassBCaseManifest.Case>> grouped = new LinkedHashMap<>();
            for (ClassBCaseManifest.Workflow workflow : ClassBCaseManifest.Workflow.values())
                grouped.put(workflow, new ArrayList<>());
            fixtures.forEach(fixture -> grouped.get(fixture.workflow()).add(fixture));
            List<ClassBScoreFiles.BlindItem> manifest = new ArrayList<>();
            List<ClassBScoreAggregator.Key> key = new ArrayList<>();
            Map<Path, String> packetBytes = new LinkedHashMap<>();
            List<PlacementSidecar.Group> sealedGroups = new ArrayList<>();
            int serial = 0;
            for (Map.Entry<ClassBCaseManifest.Workflow, List<ClassBCaseManifest.Case>> group : grouped.entrySet()) {
                List<ClassBCaseManifest.Case> cases = group.getValue();
                if (sealed && cases.isEmpty()) continue;
                cases.sort(java.util.Comparator.comparing(ClassBCaseManifest.Case::fixtureId));
                Random random = new Random(seed ^ groupKey.applyAsInt(group.getKey()));
                if (sealed) {
                    List<String> ids = cases.stream().map(ClassBCaseManifest.Case::fixtureId).toList();
                    sealedGroups.add(new PlacementSidecar.Group(group.getKey().name(), ids));
                    Map<String, ClassBCaseManifest.Case> byId = new HashMap<>();
                    cases.forEach(fixture -> byId.put(fixture.fixtureId(), fixture));
                    List<ClassBCaseManifest.Case> ordered = PlacementPlan.orderWithHash(ids, seed,
                            groupKey.applyAsInt(group.getKey())).stream().map(byId::get).toList();
                    cases.clear();
                    cases.addAll(ordered);
                } else Collections.shuffle(cases, random);
                List<String> lines = new ArrayList<>();
                for (ClassBCaseManifest.Case fixture : cases) {
                    Map<ClassBScoreAggregator.Arm, String> arms = textByCase.get(fixture.fixtureId());
                    if (arms.size() != ClassBScoreAggregator.Arm.values().length)
                        throw new InvalidPacket("Unpaired class-B output");
                    ClassBScoreAggregator.Arm first = (placement == null ? random.nextBoolean()
                            : placement.firstInA(group.getKey().name(), fixture.fixtureId()))
                            ? ClassBScoreAggregator.Arm.BASELINE : ClassBScoreAggregator.Arm.CANDIDATE;
                    ClassBScoreAggregator.Arm second = first == ClassBScoreAggregator.Arm.BASELINE
                            ? ClassBScoreAggregator.Arm.CANDIDATE : ClassBScoreAggregator.Arm.BASELINE;
                    List<Map<String, Object>> blindedOutputs = new ArrayList<>();
                    for (ClassBScoreAggregator.Arm arm : List.of(first, second)) {
                        String itemId = "blind-%05d".formatted(++serial);
                        manifest.add(new ClassBScoreFiles.BlindItem(itemId, fixture.workflow(), fixture.reserve()));
                        key.add(new ClassBScoreAggregator.Key(itemId, fixture.workflow(), fixture.fixtureId(),
                                arm, fixture.reserve()));
                        Map<String, Object> shown = new LinkedHashMap<>();
                        shown.put("itemId", itemId);
                        shown.put("placement", arm == first ? "A" : "B");
                        shown.put("output", arms.get(arm));
                        blindedOutputs.add(shown);
                    }
                    Map<String, Object> packet = new LinkedHashMap<>();
                    ObjectNode blindedFixture = JSON.valueToTree(fixture);
                    blindedFixture.remove("fixtureId");
                    packet.put("fixture", blindedFixture);
                    packet.put("outputs", blindedOutputs);
                    lines.add(JSON.writeValueAsString(packet));
                }
                String body = String.join(NEWLINE, lines) + (lines.isEmpty() ? "" : NEWLINE);
                if (LEAK.matcher(body).find()) throw new InvalidPacket("Packet would reveal an arm, model or token count");
                packetBytes.put(packetDirectory.resolve(group.getKey().name() + ".jsonl"), body);
            }
            List<String> keyLines = new ArrayList<>();
            for (ClassBScoreAggregator.Key item : key) keyLines.add(JSON.writeValueAsString(item));
            if (sealed) {
                createTrackedDirectories(packetDirectory, created);
                createTrackedDirectories(armKeyPath.getParent(), created);
            } else {
                Files.createDirectories(packetDirectory);
                Files.createDirectories(armKeyPath.getParent());
            }
            if (sealed) {
                List<Path> targets = new ArrayList<>(packetBytes.keySet());
                targets.add(armKeyPath);
                for (Path target : targets)
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new InvalidPlacementSeal();
            }
            for (Map.Entry<Path, String> packet : packetBytes.entrySet()) {
                if (sealed) {
                    created.add(packet.getKey());
                    Files.writeString(packet.getKey(), packet.getValue(), StandardOpenOption.CREATE_NEW);
                } else Files.writeString(packet.getKey(), packet.getValue());
                if (LEAK.matcher(Files.readString(packet.getKey())).find())
                    throw new InvalidPacket("Written packet contains private arm metadata");
            }
            if (sealed) created.add(armKeyPath);
            Files.writeString(armKeyPath, String.join(NEWLINE, keyLines) + (keyLines.isEmpty() ? "" : NEWLINE),
                    sealed ? StandardOpenOption.CREATE_NEW : StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, sealed ? StandardOpenOption.WRITE : StandardOpenOption.TRUNCATE_EXISTING);
            if (sealed) {
                Path sidecar = PlacementSidecar.beside(armKeyPath);
                created.add(sidecar);
                PlacementSidecar.write(sidecar, new PlacementSidecar.Reveal(PlacementSidecar.SCHEMA_VERSION,
                        PlacementSidecar.CLASS_B, exportId, secret.commitment(), secret,
                        System.getProperty("java.version"), "BASELINE", "CANDIDATE",
                        sealedGroups.stream().sorted(java.util.Comparator.comparing(PlacementSidecar.Group::groupKey))
                                .toList(), List.of(), freezeSha256OrNull), writer);
            }
            return new Export(List.copyOf(manifest), List.copyOf(key));
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof InvalidPacket invalid) throw invalid;
            if (sealed) { rollback(created); throw new InvalidPlacementSeal(); }
            throw new InvalidPacket("Cannot export class-B packet", ex);
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidPacket invalid) {
                if (sealed) rollback(created);
                throw invalid;
            }
            if (sealed) { rollback(created); throw new InvalidPlacementSeal(); }
            throw exception;
        }
    }

    private static void rollback(List<Path> created) {
        for (int i = created.size() - 1; i >= 0; i--) {
            try { Files.deleteIfExists(created.get(i)); }
            catch (IOException ignored) { /* Preserve the original failure. */ }
        }
    }
    private static void createTrackedDirectories(Path path, List<Path> created) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path current = path; current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS);
             current = current.getParent()) missing.add(current);
        for (int i = missing.size() - 1; i >= 0; i--) {
            Path directory = missing.get(i);
            Files.createDirectory(directory);
            created.add(directory);
        }
    }

    static void guarded(Path path, Path repo) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(repo.resolve(ClassBCaseManifest.PRIVATE_PATH)))
            throw new InvalidPacket(PRIVATE_DIRECTORY);
        LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
        try {
            Path ancestor = absolute;
            while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
            Path resolved = ancestor.toRealPath().resolve(ancestor.relativize(absolute));
            if (resolved.startsWith(repo.resolve(ClassBCaseManifest.PRIVATE_PATH)))
                throw new InvalidPacket(PRIVATE_DIRECTORY);
        } catch (IOException ex) {
            throw new InvalidPacket("Cannot validate export path", ex);
        }
    }
}
