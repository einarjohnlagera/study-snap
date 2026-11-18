package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Private, append-only label decisions. A later decision supersedes an earlier one. */
final class PilotLabelVerification {
    static final class InvalidLabelVerification extends RuntimeException {
        InvalidLabelVerification(String message) { super(message); }
        InvalidLabelVerification(String message, Throwable cause) { super(message, cause); }
    }
    enum Verdict { LABEL_CONFIRMED, LABEL_DISPUTED }
    enum Code { NONE, KEY_NOT_ACTUALLY_WRONG, CLEAN_ITEM_HAS_DEFECT, PLANTED_DEFECT_NOT_PRESENT,
        WRONG_PLANTED_TYPE, SOURCE_TEXT_ISSUE, OTHER }
    record Row(String packetId, int index, PilotFixtureSource.AuthoringSource verifier,
               String fixtureSha256, Verdict verdict, Code code) {}
    record Summary(Map<PilotFixtureSource.AuthoringSource, Integer> confirmed, Map<String, Integer> disputes) {}
    private record Position(String packetId, int index, PilotFixtureSource.AuthoringSource verifier) {}
    private static final Set<String> FIELDS = Set.of("packetId", "index", "verifier", "fixtureSha256", "verdict", "code");
    private PilotLabelVerification() {}

    static List<Row> load(Path record) {
        try {
            if (record == null || !Files.isRegularFile(record)) throw new InvalidLabelVerification("Verification record missing");
            List<Row> rows = new ArrayList<>();
            String[] lines = Files.readString(record, StandardCharsets.UTF_8).split("\\n", -1);
            for (int position = 0; position < lines.length; position++) {
                String line = lines[position];
                if (position == lines.length - 1 && line.isEmpty()) continue;
                if (line.isBlank()) throw new InvalidLabelVerification("Blank verification row");
                JsonNode node = IntegritySupport.JSON.readTree(line);
                IntegritySupport.fields(node, FIELDS);
                String packet = IntegritySupport.string(node, "packetId");
                JsonNode index = node.path("index");
                if (!index.isInt() || index.asInt() < 0) throw new InvalidLabelVerification("Invalid verification index");
                var verifier = PilotFixtureSource.AuthoringSource.valueOf(IntegritySupport.string(node, "verifier"));
                String hash = IntegritySupport.hash(node, "fixtureSha256");
                Verdict verdict = Verdict.valueOf(IntegritySupport.string(node, "verdict"));
                Code code = Code.valueOf(IntegritySupport.string(node, "code"));
                if (verdict == Verdict.LABEL_CONFIRMED && code != Code.NONE
                        || verdict == Verdict.LABEL_DISPUTED && code == Code.NONE)
                    throw new InvalidLabelVerification("Verification verdict and code disagree");
                rows.add(new Row(packet, index.asInt(), verifier, hash, verdict, code));
            }
            if (rows.isEmpty()) throw new InvalidLabelVerification("Verification record empty");
            return List.copyOf(rows);
        } catch (InvalidLabelVerification exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLabelVerification("Malformed verification record"); }
    }

    static Summary requireConfirmed(List<Row> rows, Map<String, PilotFixtureSource.Fixture> fixtures,
                                     Map<String, String> hashes) {
        Map<Position, Row> latest = new HashMap<>();
        for (Row row : rows) {
            PilotFixtureSource.Fixture fixture = fixtures.get(row.packetId());
            if (fixture == null || row.index() >= fixture.questions().size())
                throw new InvalidLabelVerification("Unknown verification packet or index");
            latest.put(new Position(row.packetId(), row.index(), row.verifier()), row);
        }
        Map<PilotFixtureSource.AuthoringSource, Integer> confirmed = new java.util.EnumMap<>(PilotFixtureSource.AuthoringSource.class);
        Map<String, Integer> disputes = new java.util.LinkedHashMap<>();
        for (var entry : fixtures.entrySet()) {
            int disputed = 0;
            for (int index = 0; index < entry.getValue().questions().size(); index++) {
                var item = entry.getValue().questions().get(index);
                var other = item.authoringSource() == PilotFixtureSource.AuthoringSource.CODEX
                        ? PilotFixtureSource.AuthoringSource.CLAUDE_OR_OWNER : PilotFixtureSource.AuthoringSource.CODEX;
                if (latest.containsKey(new Position(entry.getKey(), index, item.authoringSource())))
                    throw new InvalidLabelVerification("Same-source verifier recorded");
                Row row = latest.get(new Position(entry.getKey(), index, other));
                if (row == null) throw new InvalidLabelVerification("Missing cross-source verification row");
                if (!hashes.get(entry.getKey()).equals(row.fixtureSha256()))
                    throw new InvalidLabelVerification("Stale fixture SHA-256 verification");
                if (row.verdict() != Verdict.LABEL_CONFIRMED) disputed++;
                else confirmed.merge(other, 1, Integer::sum);
            }
            disputes.put(entry.getKey(), disputed);
        }
        if (disputes.values().stream().mapToInt(Integer::intValue).sum() > 0)
            throw new InvalidLabelVerification("Disputed labels remain: " + disputes.values().stream().mapToInt(Integer::intValue).sum());
        return new Summary(Map.copyOf(confirmed), Map.copyOf(disputes));
    }
}
