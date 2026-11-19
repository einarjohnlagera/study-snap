package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final human status for every blind reserve item is required before either reserve lock. */
final class ReserveLockReadiness {
    private ReserveLockReadiness() {}

    static void check(ClassBScoreFiles.Validated validated, List<ClassBScoreFiles.BlindItem> reserveManifest) {
        if (validated == null || reserveManifest == null || !validated.manifest().equals(reserveManifest))
            throw new InvalidEvidence("Validated manifest differs from reserve manifest");
        if (reserveManifest.isEmpty() || reserveManifest.stream().anyMatch(item -> item == null
                || !item.reserve() || item.workflow() == null)
                || reserveManifest.stream().map(ClassBScoreFiles.BlindItem::workflow).distinct().count() != 1)
            throw new InvalidEvidence("One non-empty reserve workflow manifest required");

        // finalHumanScores rejects malformed adjudication; a disagreement without one stays pending.
        // Its single-human rule is final even when borderline is true; that flag is not a lock gate.
        Map<String, ClassBScoreFiles.Score> finalScores = validated.finalHumanScores();
        Set<String> humanItems = new HashSet<>();
        validated.scores().stream().filter(row -> row.scorerKind() == ClassBScoreFiles.ScorerKind.HUMAN)
                .forEach(row -> humanItems.add(row.itemId()));
        List<String> unscored = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : reserveManifest) {
            if (!humanItems.contains(item.itemId())) unscored.add(item.itemId());
            else if (!finalScores.containsKey(item.itemId())) pending.add(item.itemId());
        }
        if (!unscored.isEmpty() || !pending.isEmpty()) {
            unscored.sort(String::compareTo);
            pending.sort(String::compareTo);
            throw new InvalidEvidence("Reserve human status incomplete: unscored=" + unscored.size() + " "
                    + unscored + "; pending=" + pending.size() + " " + pending);
        }
    }

    static LockRecord.FindingsValidator asValidator(Path reservePacketDirectory,
            Map<String, ClassBScoreFiles.ScorerKind> scorers, String scoresFileName) {
        if (reservePacketDirectory == null || scorers == null || scoresFileName == null
                || scoresFileName.isBlank()) throw new InvalidEvidence("Reserve validator inputs required");
        return findingsFiles -> {
            List<ClassBScoreFiles.BlindItem> manifest =
                    ReserveDecisionRecord.manifestFromPackets(reservePacketDirectory);
            List<Path> matches = findingsFiles == null ? List.of() : findingsFiles.stream()
                    .filter(path -> path != null && path.getFileName().toString().equals(scoresFileName)).toList();
            if (matches.size() != 1)
                throw new InvalidEvidence("Expected one reserve score file, found " + matches.size());
            ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(matches.getFirst(), manifest, scorers);
            check(validated, manifest);
        };
    }
}
