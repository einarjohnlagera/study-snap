package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Pattern LEAK = Pattern.compile("(?i)(?:\\bbaseline\\b|\\bcandidate\\b|\\bluna\\b|"
            + "\\bsol\\b|\\bgpt(?:[-_. ]?[0-9])?\\b|\\b(?:codex|claude|gemini|openai|model|"
            + "modelName|modelId|token|tokens|tokenCount|token_count|inputTokens|outputTokens|"
            + "input_tokens|output_tokens|reasoning_tokens|usage|costUsd)\\b)");
    private static final String NEWLINE = "\n";
    private static final String PRIVATE_DIRECTORY = "Private evaluation directory is unavailable to this exporter";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private ClassBBlindPacketExport() {}

    static Export export(List<ClassBCaseManifest.Case> fixtures, List<Output> outputs,
                         Path packetDirectory, Path armKeyPath, long seed) {
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
            int serial = 0;
            for (Map.Entry<ClassBCaseManifest.Workflow, List<ClassBCaseManifest.Case>> group : grouped.entrySet()) {
                List<ClassBCaseManifest.Case> cases = group.getValue();
                cases.sort(java.util.Comparator.comparing(ClassBCaseManifest.Case::fixtureId));
                Random random = new Random(seed ^ group.getKey().ordinal());
                Collections.shuffle(cases, random);
                List<String> lines = new ArrayList<>();
                for (ClassBCaseManifest.Case fixture : cases) {
                    Map<ClassBScoreAggregator.Arm, String> arms = textByCase.get(fixture.fixtureId());
                    if (arms.size() != ClassBScoreAggregator.Arm.values().length)
                        throw new InvalidPacket("Unpaired class-B output");
                    ClassBScoreAggregator.Arm first = random.nextBoolean()
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
            Files.createDirectories(packetDirectory);
            Files.createDirectories(armKeyPath.getParent());
            for (Map.Entry<Path, String> packet : packetBytes.entrySet()) {
                Files.writeString(packet.getKey(), packet.getValue());
                if (LEAK.matcher(Files.readString(packet.getKey())).find())
                    throw new InvalidPacket("Written packet contains private arm metadata");
            }
            Files.writeString(armKeyPath, String.join(NEWLINE, keyLines) + (keyLines.isEmpty() ? "" : NEWLINE));
            return new Export(List.copyOf(manifest), List.copyOf(key));
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof InvalidPacket invalid) throw invalid;
            throw new InvalidPacket("Cannot export class-B packet", ex);
        }
    }

    private static void guarded(Path path, Path repo) {
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
