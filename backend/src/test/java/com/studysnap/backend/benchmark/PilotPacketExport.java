package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Single-arm blind pilot packet exporter; key and packet bytes are prepared before writing. */
final class PilotPacketExport {
    record Export(List<Path> packets, Path keyPath, int itemCount, String keySha256) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NEWLINE = "\n";
    private PilotPacketExport() {}

    static Export export(List<Path> fixtureFiles, Path packetDirectory, Path keyPath, String canary, long seed) {
        try {
            if (fixtureFiles == null || fixtureFiles.isEmpty() || packetDirectory == null || keyPath == null
                    || canary == null || !canary.matches("CANARY-[A-Z0-9]{16}"))
                throw new PilotFixtureSource.InvalidPilotFixture("Invalid pilot export inputs or canary");
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            ClassBBlindPacketExport.guarded(packetDirectory, repo);
            ClassBBlindPacketExport.guarded(keyPath, repo);
            Path packetsAbsolute = packetDirectory.toAbsolutePath().normalize();
            Path keyAbsolute = keyPath.toAbsolutePath().normalize();
            if (keyAbsolute.startsWith(packetsAbsolute) || packetsAbsolute.startsWith(keyAbsolute)
                    || resolved(keyAbsolute).startsWith(resolved(packetsAbsolute))
                    || resolved(packetsAbsolute).startsWith(resolved(keyAbsolute)))
                throw new PilotFixtureSource.InvalidPilotFixture("Key must be separate from packet directory");
            Map<Path, byte[]> packets = new LinkedHashMap<>();
            List<String> keyRows = new ArrayList<>();
            Set<String> packetIds = new HashSet<>();
            int serial = 0;
            for (Path file : fixtureFiles) {
                PilotFixtureSource.Fixture fixture = PilotFixtureSource.load(file);
                if (!packetIds.add(fixture.packetId()))
                    throw new PilotFixtureSource.InvalidPilotFixture("Duplicate packet id " + fixture.packetId());
                List<PilotFixtureSource.Item> shuffled = new ArrayList<>(fixture.questions());
                Collections.shuffle(shuffled, new Random(seed ^ fixture.packetId().hashCode()));
                List<Map<String, Object>> shown = new ArrayList<>();
                for (PilotFixtureSource.Item item : shuffled) {
                    String id = "pilot-%05d".formatted(++serial);
                    ObjectNode question = item.question().deepCopy();
                    question.remove("sourceStudyPackId");
                    question.put("questionFormat", QualityBlindExport.format(item.question()));
                    question.set("keyedAnswer", JSON.valueToTree(QualityBlindExport.answer(item.question())));
                    shown.add(Map.of("itemId", id, "question", question));
                    Map<String, Object> keyRow = new LinkedHashMap<>();
                    keyRow.put("id", id);
                    keyRow.put("packetId", fixture.packetId());
                    keyRow.put("plantedType", item.plantedType().name());
                    keyRow.put("severity", item.plantedType().severity().name());
                    keyRow.put("authoringSource", item.authoringSource().name());
                    keyRow.put("rootCauseInput", item.rootCauseInput());
                    keyRow.put("canary", canary);
                    keyRows.add(JSON.writeValueAsString(keyRow));
                }
                Map<String, Object> packet = new LinkedHashMap<>();
                packet.put("requestedQuestionCount", shown.size());
                packet.put("sourceNoteTexts", fixture.sourceNoteTexts());
                packet.put("outputs", List.of(Map.of("placement", "A", "items", shown)));
                String body = JSON.writeValueAsString(packet) + NEWLINE;
                if (ClassBBlindPacketExport.LEAK.matcher(body).find()
                        || body.contains("planted") || body.contains("CODEX") || body.contains("CLAUDE_OR_OWNER")
                        || body.contains(canary) || body.contains(keyAbsolute.toString()))
                    throw new PilotFixtureSource.InvalidPilotFixture("Pilot packet leaks metadata: " + fixture.packetId());
                packets.put(packetDirectory.resolve(fixture.packetId() + ".jsonl"),
                        body.getBytes(StandardCharsets.UTF_8));
            }
            byte[] keyBytes = (String.join(NEWLINE, keyRows) + NEWLINE).getBytes(StandardCharsets.UTF_8);
            String hash = FreezeRecord.sha256(keyBytes);
            Files.createDirectories(packetDirectory);
            Files.createDirectories(keyPath.toAbsolutePath().getParent());
            for (var entry : packets.entrySet()) Files.write(entry.getKey(), entry.getValue());
            Files.write(keyPath, keyBytes);
            Files.writeString(keyPath.resolveSibling(keyPath.getFileName() + ".sha256"), hash + NEWLINE);
            return new Export(List.copyOf(packets.keySet()), keyPath, serial, hash);
        } catch (PilotFixtureSource.InvalidPilotFixture exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PilotFixtureSource.InvalidPilotFixture("Cannot export pilot packets", exception);
        }
    }

    private static Path resolved(Path path) throws java.io.IOException {
        Path ancestor = path;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        return ancestor.toRealPath().resolve(ancestor.relativize(path));
    }
}
