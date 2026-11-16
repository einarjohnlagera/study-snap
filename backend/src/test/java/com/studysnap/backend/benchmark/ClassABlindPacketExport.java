package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Arm-blind keyed-question packets for preregistration section 7 and Revision 2.2. */
final class ClassABlindPacketExport {
    record Output(String caseId, String arm, JsonNode questions) {}
    /** {@code exclusions} (arm-side, private, next to the arm key) lists every fixture left out of the packet and why;
     * the results pipeline must treat each as INCONCLUSIVE, never as a pass or a failure of the candidate. */
    record Export(List<Path> packets, Path armKey, int itemCount, Path exclusions, Map<String, String> excluded) {}
    static final class InvalidPacket extends RuntimeException {
        InvalidPacket(String message) { super(message); }
        InvalidPacket(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String NEWLINE = "\n";
    private ClassABlindPacketExport() {}

    static List<Output> load(Stage1Receipt receipt, Path deliveredPath) {
        List<Output> result = new ArrayList<>();
        for (DeliveredOutputs.Line line : DeliveredOutputs.read(deliveredPath, receipt)) {
            if (line.state() == CaseArmOutcome.State.COMPLETE && line.content().isArray())
                result.add(new Output(line.caseId(), line.arm(), line.content()));
        }
        return List.copyOf(result);
    }

    static List<Output> loadScored(Stage1Receipt original, Path originalDeliveredPath,
                                   RecheckReceipt rechecks, Path recheckDirectory) {
        Map<String, Output> selected = new LinkedHashMap<>();
        load(original, originalDeliveredPath).forEach(output -> selected.put(output.caseId(), output));
        Map<String, Stage1Receipt.CaseRow> originalRows = new HashMap<>();
        original.cases().forEach(row -> originalRows.put(row.id(), row));
        if (rechecks != null) {
            if (!original.arm().equals(rechecks.arm()) || !original.freezeSha256().equals(rechecks.freezeSha256()))
                throw new InvalidPacket("Recheck arm or freeze differs from original");
            for (RecheckReceipt.Case caseRow : rechecks.cases()) {
                Stage1Receipt.CaseRow originalRow = originalRows.get(caseRow.caseId());
                if (originalRow == null || originalRow.state() != CaseArmOutcome.State.FAILED
                        || !java.util.Objects.equals(originalRow.failureClass(), caseRow.originalFailureClass()))
                    throw new InvalidPacket("Recheck does not match original failed case");
                if (selected.containsKey(caseRow.caseId()))
                    throw new InvalidPacket("Successful original cannot be replaced by a recheck");
                for (RecheckReceipt.Repeat repeat : caseRow.repeats()) {
                    if (repeat.evidence() != RouteVerdictCalculator.Evidence.SCORED) continue;
                    Path path = recheckDirectory.resolve(caseRow.caseId())
                            .resolve("recheck-" + repeat.recheckIndex()).resolve("delivered-outputs.jsonl");
                    DeliveredOutputs.Line line = DeliveredOutputs.readRecheck(path, rechecks, caseRow, repeat);
                    if (!originalRow.manifestRowSha256().equals(line.manifestRowSha256())
                            || line.state() != CaseArmOutcome.State.COMPLETE
                            || line.failureClass() != null || line.content() == null || !line.content().isArray())
                        throw new InvalidPacket("Scored recheck has no questions");
                    selected.put(caseRow.caseId(), new Output(caseRow.caseId(), line.arm(), line.content()));
                    break;
                }
            }
        }
        return List.copyOf(selected.values());
    }

    static Export export(List<ClassACaseManifest.Case> fixtures, List<Output> outputs,
                         String firstArm, String secondArm, Path packetDirectory,
                         Path armKeyPath, long seed) {
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            ClassBBlindPacketExport.guarded(packetDirectory, repo);
            ClassBBlindPacketExport.guarded(armKeyPath, repo);
            if (armKeyPath.toAbsolutePath().normalize().startsWith(packetDirectory.toAbsolutePath().normalize()))
                throw new InvalidPacket("Arm key must be separate from packet directory");
            if (firstArm == null || secondArm == null || firstArm.equals(secondArm))
                throw new InvalidPacket("Two different arms required");
            Map<String, Map<String, JsonNode>> byCase = new HashMap<>();
            for (Output output : outputs) {
                if (output == null || output.caseId() == null || output.arm() == null
                        || output.questions() == null || !output.questions().isArray()
                        || byCase.computeIfAbsent(output.caseId(), ignored -> new HashMap<>())
                            .putIfAbsent(output.arm(), output.questions()) != null)
                    throw new InvalidPacket("Missing or duplicate class-A output");
            }
            Set<String> fixtureIds = new HashSet<>();
            Map<String, List<ClassACaseManifest.Case>> groups = new java.util.TreeMap<>();
            for (ClassACaseManifest.Case fixture : fixtures) {
                if (fixture == null || !fixtureIds.add(fixture.caseId()))
                    throw new InvalidPacket("Duplicate class-A fixture");
                String group = Stage1RunPlan.workflowName(fixture.workflow().name());
                if (fixture.subgroup() != null) group += "_" + fixture.subgroup().name();
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(fixture);
            }
            if (!fixtureIds.containsAll(byCase.keySet())) throw new InvalidPacket("Output for an unknown fixture");
            // A failed, missing or short arm cannot be scored blind; it must not abort the export of every other
            // pair. Such a fixture is excluded and recorded (arm-side, private) so the pipeline marks it INCONCLUSIVE.
            Map<String, String> excluded = new java.util.TreeMap<>();
            for (List<ClassACaseManifest.Case> group : groups.values())
                group.removeIf(fixture -> {
                    Map<String, JsonNode> arms = byCase.get(fixture.caseId());
                    String reason = null;
                    if (arms == null || !arms.containsKey(firstArm) || !arms.containsKey(secondArm))
                        reason = "MISSING_ARM_OUTPUT";
                    else if (arms.get(firstArm).size() < fixture.requestedQuestionCount()
                            || arms.get(secondArm).size() < fixture.requestedQuestionCount())
                        reason = "SHORT_DELIVERY";
                    if (reason != null) excluded.put(fixture.caseId(), reason);
                    return reason != null;
                });
            groups.values().removeIf(List::isEmpty);
            List<String> keys = new ArrayList<>();
            Map<Path, String> packets = new LinkedHashMap<>();
            int serial = 0;
            for (var group : groups.entrySet()) {
                List<ClassACaseManifest.Case> cases = group.getValue();
                cases.sort(Comparator.comparing(ClassACaseManifest.Case::caseId));
                Random random = new Random(seed ^ group.getKey().hashCode());
                Collections.shuffle(cases, random);
                List<String> packetLines = new ArrayList<>();
                for (ClassACaseManifest.Case fixture : cases) {
                    Map<String, JsonNode> arms = byCase.get(fixture.caseId());
                    if (arms.size() != 2 || !arms.containsKey(firstArm) || !arms.containsKey(secondArm))
                        throw new InvalidPacket("Unpaired class-A output");
                    String first = random.nextBoolean() ? firstArm : secondArm;
                    String second = first.equals(firstArm) ? secondArm : firstArm;
                    List<Map<String, Object>> shown = new ArrayList<>();
                    for (String arm : List.of(first, second)) {
                        JsonNode questions = arms.get(arm);
                        if (questions.size() < fixture.requestedQuestionCount())
                            throw new InvalidPacket("Delivered count is short of frozen requested count");
                        List<Map<String, Object>> items = new ArrayList<>();
                        for (JsonNode question : questions) {
                            String id = "blind-%05d".formatted(++serial);
                            String format = QualityBlindExport.format(question);
                            Object answer = QualityBlindExport.answer(question);
                            if (!question.path("explanation").isTextual())
                                throw new InvalidPacket("Delivered explanation missing");
                            ObjectNode full = question.deepCopy();
                            full.remove("sourceStudyPackId");
                            full.put("questionFormat", format);
                            full.set("keyedAnswer", JSON.valueToTree(answer));
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("itemId", id);
                            item.put("question", full);
                            items.add(item);
                            Map<String, Object> key = new LinkedHashMap<>();
                            key.put("id", id);
                            key.put("arm", arm);
                            key.put("caseId", fixture.caseId());
                            key.put("workflow", group.getKey());
                            key.put("questionFormat", format);
                            keys.add(JSON.writeValueAsString(key));
                        }
                        Map<String, Object> placed = new LinkedHashMap<>();
                        placed.put("placement", arm.equals(first) ? "A" : "B");
                        placed.put("items", items);
                        shown.add(placed);
                    }
                    Map<String, Object> packet = new LinkedHashMap<>();
                    packet.put("requestedQuestionCount", fixture.requestedQuestionCount());
                    packet.put("sourceNoteTexts", fixture.sources().stream()
                            .map(ClassACaseManifest.Source::noteText).toList());
                    packet.put("outputs", shown);
                    packetLines.add(JSON.writeValueAsString(packet));
                }
                String body = String.join(NEWLINE, packetLines) + NEWLINE;
                if (ClassBBlindPacketExport.LEAK.matcher(body).find())
                    throw new InvalidPacket("Packet would reveal arm, model or token metadata");
                packets.put(packetDirectory.resolve(group.getKey() + ".jsonl"), body);
            }
            Files.createDirectories(packetDirectory);
            Files.createDirectories(armKeyPath.getParent());
            for (var packet : packets.entrySet()) {
                Files.writeString(packet.getKey(), packet.getValue());
                if (ClassBBlindPacketExport.LEAK.matcher(Files.readString(packet.getKey())).find())
                    throw new InvalidPacket("Written packet leaks arm, model or token metadata");
            }
            Files.writeString(armKeyPath, String.join(NEWLINE, keys) + NEWLINE);
            Path exclusionsPath = armKeyPath.resolveSibling(armKeyPath.getFileName() + ".exclusions.jsonl");
            List<String> excludedLines = new ArrayList<>();
            for (var entry : excluded.entrySet())
                excludedLines.add(JSON.writeValueAsString(Map.of("caseId", entry.getKey(), "reason", entry.getValue())));
            Files.writeString(exclusionsPath, excludedLines.isEmpty() ? "" : String.join(NEWLINE, excludedLines) + NEWLINE);
            return new Export(List.copyOf(packets.keySet()), armKeyPath, serial, exclusionsPath,
                    Collections.unmodifiableMap(new java.util.TreeMap<>(excluded)));
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof InvalidPacket invalid) throw invalid;
            throw new InvalidPacket("Cannot export class-A packet", exception);
        }
    }

    static List<JsonNode> loadAndValidateScores(Path armKey, Path scoreFile) throws Exception {
        Map<String, JsonNode> keys = QualityScoreFiles.keyed(armKey);
        Map<String, JsonNode> scores = QualityScoreFiles.keyed(scoreFile);
        if (!keys.keySet().equals(scores.keySet()))
            throw new InvalidPacket("Score coverage differs from blind arm key");
        for (var entry : scores.entrySet())
            QualityScoreFiles.validate(entry.getValue(), QualityScoreFiles.choice(keys.get(entry.getKey())));
        return List.copyOf(scores.values());
    }
}
