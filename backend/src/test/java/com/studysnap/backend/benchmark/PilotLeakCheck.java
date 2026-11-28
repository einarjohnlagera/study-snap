package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed blind-isolation scan over every prompt, packet and scorer output. */
final class PilotLeakCheck {
    enum ArtifactClass { PROMPT, PACKET, OUTPUT }
    record Hit(Path path, ArtifactClass artifactClass, String kind, String evidence) {}
    private record Needle(String kind, String text) {}
    static final class InvalidLeakScan extends RuntimeException {
        InvalidLeakScan(String message) { super(message); }
        InvalidLeakScan(String message, Throwable cause) { super(message, cause); }
    }
    static final class LeakDetected extends RuntimeException {
        LeakDetected(List<Hit> hits) { super("Pilot leak hits: " + hits); }
    }
    private PilotLeakCheck() {}

    static List<Hit> scan(Map<ArtifactClass, List<Path>> artifacts, PilotKey key, Path keyPath,
                          Set<String> forbiddenExtras) {
        if (artifacts == null || key == null || keyPath == null || keyPath.getFileName() == null
                || forbiddenExtras == null)
            throw new InvalidLeakScan("Leak scan inputs required");
        for (ArtifactClass type : ArtifactClass.values())
            if (!artifacts.containsKey(type) || artifacts.get(type) == null || artifacts.get(type).isEmpty())
                throw new InvalidLeakScan("Missing or empty artifact class " + type);
        for (String extra : forbiddenExtras)
            if (extra == null || extra.length() < 8)
                throw new InvalidLeakScan("Forbidden extra shorter than 8 characters");
        List<Hit> hits = new ArrayList<>();
        List<Needle> needles = new ArrayList<>();
        needles.add(new Needle("CANARY", key.canary()));
        needles.add(new Needle("KEY_PATH", keyPath.toAbsolutePath().normalize().toString()));
        needles.add(new Needle("KEY_NAME", keyPath.getFileName().toString()));
        for (PlantedType type : PlantedType.values()) needles.add(new Needle("PLANTED_TYPE", type.name()));
        needles.add(new Needle("PLANTED_FIELD", "planted"));
        for (ModelFamily author : ModelFamily.values())
            needles.add(new Needle("AUTHOR", author.name()));
        for (String legacy : List.of("CODEX", "CLAUDE_OR_OWNER"))
            needles.add(new Needle("AUTHOR", legacy));
        for (String extra : forbiddenExtras) needles.add(new Needle("FORBIDDEN_EXTRA", extra));
        for (ArtifactClass type : ArtifactClass.values()) for (Path path : artifacts.get(type)) {
            try {
                if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path))
                    throw new InvalidLeakScan("Unreadable artifact " + path);
                String body = Files.readString(path);
                String normalized = normalize(body);
                var matcher = ClassBBlindPacketExport.LEAK.matcher(body);
                if (matcher.find()) hits.add(new Hit(path, type, "STAGE1_LEAK", matcher.group()));
                for (Needle needle : needles) {
                    if (body.contains(needle.text()) || normalized.contains(normalize(needle.text())))
                        hits.add(new Hit(path, type, needle.kind(), needle.text()));
                }
            } catch (InvalidLeakScan exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InvalidLeakScan("Cannot read artifact " + path, exception);
            }
        }
        return List.copyOf(hits);
    }
    static void assertClean(Map<ArtifactClass, List<Path>> artifacts, PilotKey key, Path keyPath,
                            Set<String> forbiddenExtras) {
        List<Hit> hits = scan(artifacts, key, keyPath, forbiddenExtras);
        if (!hits.isEmpty()) throw new LeakDetected(hits);
    }
    private static String normalize(String text) {
        return ClassAScoreSchemaV2.normalize(text);
    }
}
