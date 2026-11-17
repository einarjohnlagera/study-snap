package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A confirmed critical comes from locked human evidence, never a post-lock re-score. Class B's
 * scorer registry and class A's evidence mapping are unlocked caller inputs. If a critical cannot
 * be established, the dispute is refused; the concern must be refiled as a safety finding.
 * Any integrity failure throws InvalidCritical and means BLOCKED; an open result exists only for
 * an intact, indexed dispute whose resolution is absent or semantically nonclosing.
 */
interface ConfirmedCriticalSource {
    final class InvalidCritical extends RuntimeException {
        InvalidCritical(String message) { super(message); }
        InvalidCritical(String message, Throwable cause) { super(message, cause); }
    }
    void requireCritical(PostLockContext context, PostLockContext.Verified verified,
                         String workflow, String itemId);

    record ClassA(EvidenceMapping mapping) implements ConfirmedCriticalSource {
        @Override public void requireCritical(PostLockContext context, PostLockContext.Verified verified,
                                                String workflow, String itemId) {
            try {
                if (mapping == null) throw new InvalidCritical("Evidence mapping required");
                Path file = locked(verified.lock(), context.findingsDirectory(), "human.jsonl");
                ClassAPacketIndex packets = ClassAPacketIndex.load(context.packetDirectory());
                if (packets.get(itemId) == null) throw new InvalidCritical("Unknown class-A item");
                PairMap pairs = PairMap.fromPackets(context.packetDirectory());
                String pair = pairs.pairOf(itemId);
                if (pair == null || !workflow.equals(pairs.workflowOf(pair)))
                    throw new InvalidCritical("Class-A workflow mismatch");
                JsonNode match = null;
                Set<String> ids = new HashSet<>();
                for (String line : Files.readAllLines(file)) {
                    JsonNode row = IntegritySupport.JSON.readTree(line);
                    if (row == null || !row.path("id").isTextual() || !ids.add(row.path("id").asText()))
                        throw new InvalidCritical("Invalid or duplicate locked human row");
                    ClassAScoreSchemaV2.PacketQuestion question = packets.get(row.path("id").asText());
                    if (question == null) throw new InvalidCritical("Human row absent from packet");
                    ClassAScoreSchemaV2.validate(row, question, mapping);
                    if (itemId.equals(row.path("id").asText())) match = row;
                }
                if (match == null || !"CRITICAL".equals(match.path("humanVerdict").asText()))
                    throw new InvalidCritical("Confirmed class-A critical cannot be established");
            } catch (InvalidCritical exception) { throw exception; }
            catch (Exception exception) { throw new InvalidCritical("Cannot establish class-A critical", exception); }
        }
    }

    record ClassB(Map<String, ClassBScoreFiles.ScorerKind> scorers) implements ConfirmedCriticalSource {
        @Override public void requireCritical(PostLockContext context, PostLockContext.Verified verified,
                                                String workflow, String itemId) {
            try {
                Path file = locked(verified.lock(), context.findingsDirectory(), "scores.jsonl");
                List<ClassBScoreFiles.BlindItem> manifest = ReserveDecisionRecord.manifestFromPackets(context.packetDirectory());
                ClassBScoreFiles.BlindItem item = manifest.stream().filter(row -> row.itemId().equals(itemId))
                        .findFirst().orElseThrow(() -> new InvalidCritical("Unknown class-B item"));
                if (!item.workflow().name().equals(workflow)) throw new InvalidCritical("Class-B workflow mismatch");
                ClassBScoreFiles.Validated scores = ClassBScoreFiles.validate(file, manifest, scorers);
                ClassBScoreFiles.Score human = scores.finalHumanScores().get(itemId);
                if (human == null) throw new InvalidCritical("Human disagreement or missing adjudication: cannot establish");
                if (!human.critical()) throw new InvalidCritical("No confirmed class-B critical");
            } catch (InvalidCritical exception) { throw exception; }
            catch (Exception exception) { throw new InvalidCritical("Cannot establish class-B critical", exception); }
        }
    }

    private static Path locked(LockRecord lock, Path directory, String name) throws java.io.IOException {
        LockRecord.Finding finding = lock.findings().stream().filter(row -> row.name().equals(name))
                .findFirst().orElseThrow(() -> new InvalidCritical("Required human file absent from lock"));
        Path file = directory.resolve(name);
        if (!FreezeRecord.sha256(file).equals(finding.sha256()))
            throw new InvalidCritical("Locked human file differs");
        return file;
    }
}
