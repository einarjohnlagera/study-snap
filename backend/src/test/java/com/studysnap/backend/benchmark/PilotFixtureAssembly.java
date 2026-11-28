package com.studysnap.backend.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Offline composition, authorship and verification gate before private pilot export. */
final class PilotFixtureAssembly {
    static final class InvalidAssembly extends RuntimeException {
        InvalidAssembly(String message) { super(message); }
        InvalidAssembly(String message, Throwable cause) { super(message, cause); }
    }
    static final Map<PlantedType, Integer> REVISION_4 = Map.ofEntries(
            Map.entry(PlantedType.WRONG_KEY, 6), Map.entry(PlantedType.EXPLANATION_CONTRADICTS_KEY, 3),
            Map.entry(PlantedType.SECOND_DEFENSIBLE_ANSWER, 5), Map.entry(PlantedType.MATERIAL_AMBIGUITY_MULTI, 2),
            Map.entry(PlantedType.MATERIAL_AMBIGUITY_ZERO, 2), Map.entry(PlantedType.SOURCE_CONTRADICTION, 3),
            Map.entry(PlantedType.MINOR_WORDING, 3), Map.entry(PlantedType.NUMERIC_INCONSISTENCY, 2),
            Map.entry(PlantedType.WEAK_DISTRACTOR, 4), Map.entry(PlantedType.UNHELPFUL_EXPLANATION, 3),
            Map.entry(PlantedType.CLEAN, 93));
    private static final List<Integer> SIZES = List.of(7, 7, 10, 24, 30, 48);
    private static final int TOTAL = 126;
    private static final String SEALED_NAME = "pilot-sealed.jsonl";
    private static final String JSON_EXT = ".json";
    private static final SecureRandom RANDOM = new SecureRandom();
    record Checked(List<Path> files, Map<String, PilotFixtureSource.Fixture> fixtures,
                   Map<String, String> hashes, Map<PlantedType, Integer> counts,
                   Map<ModelFamily, Map<PlantedType, Integer>> bySource,
                   Map<ModelFamily, Integer> critical,
                   Map<ModelFamily, Integer> planted) {}
    record Assembled(Path report, List<Path> packets, Path sealed) {}
    private PilotFixtureAssembly() {}

    static Checked check(Path fixtureDir) {
        try {
            guard(fixtureDir);
            if (!Files.isDirectory(fixtureDir)) throw new InvalidAssembly("Fixture directory missing");
            List<Path> found;
            try (Stream<Path> stream = Files.list(fixtureDir)) { found = stream.toList(); }
            if (found.size() != SIZES.size()) throw new InvalidAssembly("Fixture file count must be six");
            List<Path> files = new ArrayList<>();
            Map<String, PilotFixtureSource.Fixture> fixtures = new LinkedHashMap<>();
            Map<String, String> hashes = new LinkedHashMap<>();
            Map<PlantedType, Integer> counts = zeroCounts();
            Map<ModelFamily, Map<PlantedType, Integer>> bySource = new EnumMap<>(ModelFamily.class);
            Map<ModelFamily, Integer> critical = new EnumMap<>(ModelFamily.class);
            Map<ModelFamily, Integer> planted = new EnumMap<>(ModelFamily.class);
            for (var source : ModelFamily.values()) bySource.put(source, zeroCounts());
            int rootCount = 0;
            int total = 0;
            for (int n = 0; n < SIZES.size(); n++) {
                String id = "pilot-%02d".formatted(n + 1);
                Path file = fixtureDir.resolve(id + JSON_EXT);
                if (!Files.isRegularFile(file) || !found.contains(file))
                    throw new InvalidAssembly("Missing or unexpected fixture file: " + id);
                PilotFixtureSource.Fixture fixture;
                try { fixture = PilotFixtureSource.load(file); }
                catch (Exception exception) { throw new InvalidAssembly("Invalid fixture format: " + id); }
                if (!id.equals(fixture.packetId())) throw new InvalidAssembly("Fixture stem and packetId differ: " + id);
                if (fixture.questions().size() != SIZES.get(n)) throw new InvalidAssembly("Packet size differs: " + id);
                var expected = n == 0 || n == 1 || n == 5 ? ModelFamily.OPENAI
                        : ModelFamily.ANTHROPIC;
                for (var item : fixture.questions()) {
                    if (item.authoringFamily() != expected) throw new InvalidAssembly("Packet authorship allocation or mixed authors: " + id);
                    counts.merge(item.plantedType(), 1, Integer::sum);
                    bySource.get(expected).merge(item.plantedType(), 1, Integer::sum);
                    if (item.plantedType().severity() == PlantedType.Severity.CRITICAL) critical.merge(expected, 1, Integer::sum);
                    if (item.plantedType() != PlantedType.CLEAN) planted.merge(expected, 1, Integer::sum);
                    if (item.rootCauseInput()) {
                        rootCount++;
                        if (item.plantedType() != PlantedType.WRONG_KEY)
                            throw new InvalidAssembly("Input root cause has wrong category");
                    }
                }
                files.add(file);
                fixtures.put(id, fixture);
                hashes.put(id, FreezeRecord.sha256(file));
                total += fixture.questions().size();
            }
            if (total != TOTAL) throw new InvalidAssembly("Total question count differs");
            for (var entry : REVISION_4.entrySet())
                if (!entry.getValue().equals(counts.get(entry.getKey())))
                    throw new InvalidAssembly("Composition count differs");
            if (rootCount != 1) throw new InvalidAssembly("Input root cause count must be one");
            for (var source : ModelFamily.values()) {
                if (planted.getOrDefault(source, 0) < 15)
                    throw new InvalidAssembly("Authorship planted below band: " + source);
                if (planted.getOrDefault(source, 0) > 18)
                    throw new InvalidAssembly("Authorship planted above band: " + source);
                if (critical.getOrDefault(source, 0) < 9)
                    throw new InvalidAssembly("Authorship critical below band: " + source);
                if (critical.getOrDefault(source, 0) > 12)
                    throw new InvalidAssembly("Authorship critical above band: " + source);
                for (var type : List.of(PlantedType.MATERIAL_AMBIGUITY_MULTI, PlantedType.MATERIAL_AMBIGUITY_ZERO))
                    if (bySource.get(source).get(type) != 1)
                        throw new InvalidAssembly("Material ambiguity allocation differs");
                for (var type : PlantedType.values())
                    if (type.severity() == PlantedType.Severity.CRITICAL && REVISION_4.get(type) >= 2
                            && type != PlantedType.MATERIAL_AMBIGUITY_MULTI
                            && type != PlantedType.MATERIAL_AMBIGUITY_ZERO
                            && bySource.get(source).get(type) == 0)
                        throw new InvalidAssembly("Critical category must appear in both sources");
            }
            return new Checked(List.copyOf(files), Map.copyOf(fixtures), Map.copyOf(hashes), Map.copyOf(counts),
                    Map.copyOf(bySource), Map.copyOf(critical), Map.copyOf(planted));
        } catch (InvalidAssembly exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAssembly("Cannot check fixture directory"); }
    }

    static Assembled assemble(Path fixtureDir, Path labels, Path packetOut, Path sealedOut, long seed, Path reportOut) {
        Checked checked = check(fixtureDir);
        try {
            guard(labels); guard(packetOut); guard(sealedOut); guard(reportOut);
            if (labels == null || packetOut == null || sealedOut == null || reportOut == null)
                throw new InvalidAssembly("Assembly paths required");
            Path packetPath = packetOut.toAbsolutePath().normalize();
            Path sealedPath = sealedOut.toAbsolutePath().normalize();
            if (packetPath.equals(sealedPath) || packetPath.startsWith(sealedPath) || sealedPath.startsWith(packetPath))
                throw new InvalidAssembly("Sealed output must be separate from packets");
            if (reportOut.toAbsolutePath().normalize().startsWith(packetPath)
                    || reportOut.toAbsolutePath().normalize().startsWith(sealedPath))
                throw new InvalidAssembly("Report must be separate from export directories");
            if (Files.exists(packetOut) && !emptyDirectory(packetOut)
                    || Files.exists(sealedOut) && !emptyDirectory(sealedOut)
                    || Files.exists(reportOut) || Files.exists(IntegritySupport.detached(reportOut)))
                throw new InvalidAssembly("Assembly output already exists");
            var rows = PilotLabelVerification.load(labels);
            var summary = PilotLabelVerification.requireConfirmed(rows, checked.fixtures(), checked.hashes());
            String labelsHash = FreezeRecord.sha256(labels);
            byte[] random = new byte[10];
            RANDOM.nextBytes(random);
            String canary = "CANARY-" + java.util.HexFormat.of().withUpperCase().formatHex(random).substring(0, 16);
            Path sealed = sealedOut.resolve(SEALED_NAME);
            var export = PilotPacketExport.export(checked.files(), packetOut, sealed, canary, seed);
            Map<String, Object> report = new LinkedHashMap<>();
            Map<String, Integer> sizes = new LinkedHashMap<>();
            Map<String, String> packetHashes = new LinkedHashMap<>();
            for (int n = 0; n < checked.files().size(); n++) {
                String id = "pilot-%02d".formatted(n + 1);
                sizes.put(id, SIZES.get(n));
                packetHashes.put(id, FreezeRecord.sha256(export.packets().get(n)));
            }
            report.put("packetSizes", sizes);
            report.put("compositionByType", checked.counts());
            report.put("compositionBySource", checked.bySource());
            report.put("authoringCriticalTotals", checked.critical());
            report.put("authoringPlantedTotals", checked.planted());
            report.put("fixtureSha256", checked.hashes());
            report.put("packetSha256", packetHashes);
            report.put("sealedSha256", export.keySha256());
            report.put("labelVerificationSha256", labelsHash);
            report.put("seed", seed);
            report.put("confirmedByVerifier", summary.confirmed());
            report.put("disputesRemaining", summary.disputes());
            byte[] bytes = (IntegritySupport.JSON.writeValueAsString(report) + "\n").getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(reportOut.toAbsolutePath().getParent());
            IntegritySupport.create(reportOut, bytes);
            return new Assembled(reportOut, export.packets(), sealed);
        } catch (PilotLabelVerification.InvalidLabelVerification | InvalidAssembly exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAssembly("Cannot assemble pilot fixtures"); }
    }

    private static Map<PlantedType, Integer> zeroCounts() {
        Map<PlantedType, Integer> counts = new EnumMap<>(PlantedType.class);
        for (var type : PlantedType.values()) counts.put(type, 0);
        return counts;
    }
    private static void guard(Path path) {
        if (path == null) throw new InvalidAssembly("Assembly path required");
        ClassBBlindPacketExport.guarded(path, StageRunner.findRepo(Path.of("").toAbsolutePath().normalize()));
    }
    private static boolean emptyDirectory(Path directory) throws java.io.IOException {
        if (!Files.isDirectory(directory)) return false;
        try (Stream<Path> stream = Files.list(directory)) { return stream.findAny().isEmpty(); }
    }
}
