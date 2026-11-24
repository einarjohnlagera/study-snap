package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Preregistration sections 4, 7 and 8 mapping and detached-hash mutation checks. */
class EvidenceMappingTest {
    private static final Path SOURCE = Path.of("src/test/java/com/studysnap/backend/benchmark/evidence-mapping.json");
    private static final String NEW_HASH = "7b2cb6866e3f341529ab256799a876cca137c72ea32ef6124e04f2eca3672224";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    private Path mapping() throws Exception {
        Path copy = temp.resolve("mapping.json");
        byte[] bytes = Files.readAllBytes(SOURCE);
        Files.write(copy, bytes);
        Files.writeString(temp.resolve("mapping.json.sha256"), FreezeRecord.sha256(bytes));
        return copy;
    }

    private static ObjectNode score(String criticalType) {
        ObjectNode row = JSON.createObjectNode();
        row.put("critical", true);
        row.put("criticalType", criticalType);
        row.put("factuallyCorrect", "YES");
        row.put("grounding", "GROUNDED");
        row.put("clarity", 5);
        row.put("faithfulness", 5);
        row.put("seriousGiveaway", false);
        return row;
    }

    @Test void listedAndUnlistedCriticalTypesSplitT2FromT3() throws Exception {
        EvidenceMapping map = EvidenceMapping.load(mapping());
        ObjectNode listed = score("WRONG_KEY");
        ObjectNode unlisted = score("OTHER");
        assertThat(map.criticalA(listed)).isTrue();
        assertThat(map.criticalA(score("KEY_NOT_DEFENSIBLE"))).isTrue();
        assertThat(map.qualityA(score("KEY_NOT_DEFENSIBLE"))).isFalse();
        assertThat(map.qualityA(listed)).isFalse();
        assertThat(map.criticalA(unlisted)).isFalse();
        assertThat(map.qualityA(unlisted)).isTrue();
    }

    @Test void shippedMappingHashAndNewCriticalTypeArePinned() throws Exception {
        EvidenceMapping shipped = EvidenceMapping.load(SOURCE);
        assertThat(shipped.sha256).isEqualTo(NEW_HASH);
        assertThat(FreezeRecord.sha256(SOURCE)).isEqualTo(NEW_HASH);
        assertThat(shipped.classACriticalTypes).contains("KEY_NOT_DEFENSIBLE");
        assertThat(shipped.costFigure).isEqualTo("billedUsd");
    }

    @Test void mappingBytesControlInterpretationAndDetachedHash() throws Exception {
        Path file = mapping();
        String changed = Files.readString(file).replace("\"WRONG_KEY\", ", "");
        Files.writeString(file, changed);
        assertThatThrownBy(() -> EvidenceMapping.load(file))
                .isInstanceOf(EvidenceMapping.InvalidMapping.class).hasMessageContaining("SHA-256");
        Files.writeString(file.resolveSibling("mapping.json.sha256"), FreezeRecord.sha256(file));
        EvidenceMapping amended = EvidenceMapping.load(file);
        assertThat(amended.criticalA(score("WRONG_KEY"))).isFalse();
        assertThat(amended.qualityA(score("WRONG_KEY"))).isTrue();
    }

    @Test void costToleranceAndFigureComeFromMappingFile() throws Exception {
        Path file = mapping();
        String changed = Files.readString(file).replace("1.25", "1.50")
                .replace("billedUsd", "conservativeUsd");
        Files.writeString(file, changed);
        Files.writeString(file.resolveSibling("mapping.json.sha256"), FreezeRecord.sha256(file));
        EvidenceMapping amended = EvidenceMapping.load(file);
        assertThat(amended.costTolerance).isEqualByComparingTo("1.50");
        assertThat(amended.costFigure).isEqualTo("conservativeUsd");
    }

    @Test void unknownMappingFieldRefusesEvenWhenRehashed() throws Exception {
        Path file = mapping();
        String body = Files.readString(file);
        Files.writeString(file, body.substring(0, body.lastIndexOf('}')) + ",\"unknown\":true}");
        Files.writeString(file.resolveSibling("mapping.json.sha256"), FreezeRecord.sha256(file));
        assertThatThrownBy(() -> EvidenceMapping.load(file))
                .isInstanceOf(EvidenceMapping.InvalidMapping.class).hasMessageContaining("unknown mapping fields");
    }
}
