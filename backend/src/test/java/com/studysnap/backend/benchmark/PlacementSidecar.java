package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The only parser and writer of the private placement sidecar. */
final class PlacementSidecar {
    static final int SCHEMA_VERSION = 1;
    static final String CLASS_A = "CLASS_A";
    static final String CLASS_B = "CLASS_B";
    record Group(String groupKey, List<String> caseIds) {}
    record Excluded(String caseId, String reason) {}
    record Reveal(int schemaVersion, String kind, String exportId, String commitment,
                  PlacementSecret secret, String javaVersion, String firstArm, String secondArm,
                  List<Group> groups, List<Excluded> excluded, String freezeSha256) {
        @Override public String toString() { return "PlacementReveal[REDACTED]"; }
    }
    @FunctionalInterface interface Writer { void write(Path sidecar, byte[] bytes) throws Exception; }
    static final Writer FILE_WRITER = PlacementSidecar::writePrivate;
    private static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_EXPORT_ID = "exportId";
    private static final String FIELD_COMMITMENT = "commitment";
    private static final String FIELD_SECRET = "secret";
    private static final String FIELD_JAVA_VERSION = "javaVersion";
    private static final String FIELD_FIRST_ARM = "firstArm";
    private static final String FIELD_SECOND_ARM = "secondArm";
    private static final String FIELD_GROUPS = "groups";
    private static final String FIELD_EXCLUDED = "excluded";
    private static final String FIELD_FREEZE_SHA256 = "freezeSha256";
    private static final String FIELD_GROUP_KEY = "groupKey";
    private static final String FIELD_CASE_IDS = "caseIds";
    private static final String FIELD_CASE_ID = "caseId";
    private static final String FIELD_REASON = "reason";
    private static final Set<String> BASE_FIELDS = Set.of(FIELD_SCHEMA_VERSION, FIELD_KIND, FIELD_EXPORT_ID, FIELD_COMMITMENT,
            FIELD_SECRET, FIELD_JAVA_VERSION, FIELD_FIRST_ARM, FIELD_SECOND_ARM, FIELD_GROUPS, FIELD_EXCLUDED);
    private static final Set<String> GROUP_FIELDS = Set.of(FIELD_GROUP_KEY, FIELD_CASE_IDS);
    private static final Set<String> EXCLUDED_FIELDS = Set.of(FIELD_CASE_ID, FIELD_REASON);
    private PlacementSidecar() {}

    static Path beside(Path armKey) {
        if (armKey == null || armKey.getFileName() == null) throw new InvalidPlacementSeal();
        return armKey.resolveSibling(armKey.getFileName() + ".placement.json");
    }

    static void requireOutsidePackets(Path sidecar, Path packetDirectory) {
        try {
            if (futureRealPath(sidecar).startsWith(futureRealPath(packetDirectory)))
                throw new InvalidPlacementSeal();
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    private static Path futureRealPath(Path path) throws Exception {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
    }

    private static void writePrivate(Path sidecar, byte[] bytes) throws Exception {
        try (var channel = Files.newByteChannel(sidecar,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
        } catch (UnsupportedOperationException exception) {
            Files.write(sidecar, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    static void write(Path sidecar, Reveal reveal, Writer writer) {
        try {
            if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) throw new InvalidPlacementSeal();
            validate(reveal);
            ObjectNode root = IntegritySupport.JSON.createObjectNode();
            root.put(FIELD_SCHEMA_VERSION, reveal.schemaVersion()).put(FIELD_KIND, reveal.kind())
                    .put(FIELD_EXPORT_ID, reveal.exportId()).put(FIELD_COMMITMENT, reveal.commitment())
                    .put(FIELD_SECRET, reveal.secret().hex()).put(FIELD_JAVA_VERSION, reveal.javaVersion())
                    .put(FIELD_FIRST_ARM, reveal.firstArm()).put(FIELD_SECOND_ARM, reveal.secondArm());
            ArrayNode groups = root.putArray(FIELD_GROUPS);
            for (Group group : reveal.groups()) {
                ObjectNode row = groups.addObject().put(FIELD_GROUP_KEY, group.groupKey());
                ArrayNode ids = row.putArray(FIELD_CASE_IDS);
                group.caseIds().forEach(ids::add);
            }
            ArrayNode excluded = root.putArray(FIELD_EXCLUDED);
            for (Excluded row : reveal.excluded())
                excluded.addObject().put(FIELD_CASE_ID, row.caseId()).put(FIELD_REASON, row.reason());
            if (reveal.freezeSha256() != null) root.put(FIELD_FREEZE_SHA256, reveal.freezeSha256());
            writer.write(sidecar, IntegritySupport.JSON.writeValueAsBytes(root));
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    static Reveal load(Path sidecar) {
        try {
            JsonNode root = IntegritySupport.JSON.readTree(Files.readAllBytes(sidecar));
            Set<String> names = fields(root);
            if (!names.equals(BASE_FIELDS) && !names.equals(withFreeze())) throw new InvalidPlacementSeal();
            if (!root.path(FIELD_SCHEMA_VERSION).isInt()) throw new InvalidPlacementSeal();
            String hex = IntegritySupport.string(root, FIELD_SECRET);
            if (!hex.matches("[0-9a-f]{64}")) throw new InvalidPlacementSeal();
            PlacementSecret secret = new PlacementSecret(java.util.HexFormat.of().parseHex(hex));
            List<Group> groups = new ArrayList<>();
            if (!root.path(FIELD_GROUPS).isArray()) throw new InvalidPlacementSeal();
            for (JsonNode group : root.path(FIELD_GROUPS)) {
                if (!fields(group).equals(GROUP_FIELDS) || !group.path(FIELD_CASE_IDS).isArray())
                    throw new InvalidPlacementSeal();
                List<String> ids = new ArrayList<>();
                for (JsonNode id : group.path(FIELD_CASE_IDS)) {
                    if (!id.isTextual() || id.asText().isBlank()) throw new InvalidPlacementSeal();
                    ids.add(id.asText());
                }
                groups.add(new Group(IntegritySupport.string(group, FIELD_GROUP_KEY), List.copyOf(ids)));
            }
            List<Excluded> excluded = new ArrayList<>();
            if (!root.path(FIELD_EXCLUDED).isArray()) throw new InvalidPlacementSeal();
            for (JsonNode row : root.path(FIELD_EXCLUDED)) {
                if (!fields(row).equals(EXCLUDED_FIELDS)) throw new InvalidPlacementSeal();
                excluded.add(new Excluded(IntegritySupport.string(row, FIELD_CASE_ID),
                        IntegritySupport.string(row, FIELD_REASON)));
            }
            Reveal reveal = new Reveal(root.path(FIELD_SCHEMA_VERSION).asInt(),
                    IntegritySupport.string(root, FIELD_KIND), IntegritySupport.string(root, FIELD_EXPORT_ID),
                    IntegritySupport.hash(root, FIELD_COMMITMENT), secret,
                    IntegritySupport.string(root, FIELD_JAVA_VERSION), IntegritySupport.string(root, FIELD_FIRST_ARM),
                    IntegritySupport.string(root, FIELD_SECOND_ARM), List.copyOf(groups), List.copyOf(excluded),
                    names.contains(FIELD_FREEZE_SHA256) ? IntegritySupport.hash(root, FIELD_FREEZE_SHA256) : null);
            validate(reveal);
            return reveal;
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    private static Set<String> withFreeze() {
        Set<String> fields = new HashSet<>(BASE_FIELDS);
        fields.add(FIELD_FREEZE_SHA256);
        return fields;
    }
    private static Set<String> fields(JsonNode node) {
        if (node == null || !node.isObject()) throw new InvalidPlacementSeal();
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
    private static void validate(Reveal reveal) {
        if (reveal == null || reveal.schemaVersion() != SCHEMA_VERSION
                || !Set.of(CLASS_A, CLASS_B).contains(reveal.kind())
                || reveal.exportId() == null || reveal.exportId().isBlank()
                || reveal.secret() == null || !reveal.secret().commitment().equals(reveal.commitment())
                || reveal.javaVersion() == null || reveal.javaVersion().isBlank()
                || reveal.firstArm() == null || reveal.secondArm() == null
                || reveal.firstArm().equals(reveal.secondArm())
                || reveal.groups() == null || reveal.excluded() == null
                || reveal.freezeSha256() != null && !IntegritySupport.digest(reveal.freezeSha256()))
            throw new InvalidPlacementSeal();
        if (CLASS_B.equals(reveal.kind()) && (!reveal.excluded().isEmpty()
                || !"BASELINE".equals(reveal.firstArm()) || !"CANDIDATE".equals(reveal.secondArm())))
            throw new InvalidPlacementSeal();
        Set<String> ids = new HashSet<>();
        String previousGroup = null;
        for (Group group : reveal.groups()) {
            if (group == null || group.groupKey() == null || group.groupKey().isBlank()
                    || previousGroup != null && previousGroup.compareTo(group.groupKey()) >= 0
                    || group.caseIds() == null || group.caseIds().isEmpty()
                    || !group.caseIds().equals(group.caseIds().stream().sorted().distinct().toList()))
                throw new InvalidPlacementSeal();
            if (!ids.addAll(group.caseIds())) throw new InvalidPlacementSeal();
            previousGroup = group.groupKey();
        }
        String previousExcluded = null;
        for (Excluded row : reveal.excluded()) {
            if (row == null || row.caseId() == null || row.caseId().isBlank()
                    || row.reason() == null || row.reason().isBlank()
                    || previousExcluded != null && previousExcluded.compareTo(row.caseId()) >= 0
                    || !ids.add(row.caseId())) throw new InvalidPlacementSeal();
            previousExcluded = row.caseId();
        }
    }
}
