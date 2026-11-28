package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicit, offline operator entry point; never receives the canary. */
class PilotFixtureOperatorTest {
    private static final String PREFIX = "pilot.fixtures.";
    @Test void operatorCheckOrAssemble() {
        boolean check = Boolean.getBoolean(PREFIX + "check");
        boolean assemble = Boolean.getBoolean(PREFIX + "assemble");
        assumeTrue(check || assemble);
        if (check && assemble) throw new PilotFixtureAssembly.InvalidAssembly("Choose one pilot fixture mode");
        try {
            Path dir = required("dir");
            if (check) {
                PilotFixtureAssembly.check(dir);
                System.out.println("pilot fixture check PASS");
                return;
            }
            long seed;
            try { seed = Long.parseLong(System.getProperty(PREFIX + "seed")); }
            catch (Exception exception) { throw new PilotFixtureAssembly.InvalidAssembly("Pilot fixture seed required", exception); }
            var result = PilotFixtureAssembly.assembleWithFamilies(dir, required("labels"), required("packets"),
                    required("sealed"), seed, required("report"), requiredFamilies());
            PilotKey loaded = PilotKey.load(result.sealed());
            loaded.requireRevision4Composition();
            var artifacts = java.util.Map.of(PilotLeakCheck.ArtifactClass.PACKET, result.packets(),
                    PilotLeakCheck.ArtifactClass.PROMPT, result.packets(),
                    PilotLeakCheck.ArtifactClass.OUTPUT, result.packets());
            if (!PilotLeakCheck.scan(artifacts, loaded, result.sealed(), java.util.Set.of()).isEmpty())
                throw new PilotFixtureAssembly.InvalidAssembly("Packet leak detected");
            System.out.println("pilot fixture assembly PASS: " + result.report());
        } catch (Exception exception) {
            System.out.println("pilot fixture operation FAIL: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            throw new AssertionError("pilot fixture operation failed");
        }
    }
    static Path requiredFamilies() {
        Path path = required("families");
        if (!path.isAbsolute()) throw new PilotFixtureAssembly.InvalidAssembly("Absolute pilot fixture path required: families");
        return path;
    }
    private static Path required(String name) {
        String value = System.getProperty(PREFIX + name);
        if (value == null || value.isBlank()) throw new PilotFixtureAssembly.InvalidAssembly("Missing pilot fixture path: " + name);
        return Path.of(value);
    }
}
