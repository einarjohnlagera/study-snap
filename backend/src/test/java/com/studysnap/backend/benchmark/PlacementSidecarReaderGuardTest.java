package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class PlacementSidecarReaderGuardTest {
    private static final Set<String> ALLOWED = Set.of("PlacementSidecar.java", "ArmKeyAccess.java",
            "ArmKeySeal.java", "ClassABlindPacketExport.java", "ClassBBlindPacketExport.java");

    @Test void sidecarReadersRemainWithinTheGateAndExporters() throws Exception {
        Path dir = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize())
                .resolve("backend/src/test/java/com/studysnap/backend/benchmark");
        int calls = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith("Test.java")) continue;
                String source = Files.readString(path);
                boolean reader = source.contains("PlacementSidecar.load(") || source.contains(".placement.json");
                if (reader && !ALLOWED.contains(name)) fail("Unallowlisted placement reader: " + name);
                if (source.contains("PlacementSidecar.load(")) calls++;
            }
        }
        assertThat(calls).isEqualTo(2);
    }
}
