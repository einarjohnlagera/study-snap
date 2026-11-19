package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementFreezeRecordTest {
    static final String EXPORT_ID = "CLASS_B_PRIMARY";
    static final String COMMITMENT = "1".repeat(64);
    private static final String WORKFLOW = "STUDY_TIP";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;

    static FreezeRecord record(Map<String, String> placement) {
        Map<String, String> suites = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) suites.put("suite" + index, WORKFLOW);
        return new FreezeRecord("a".repeat(40), "b".repeat(64), "c".repeat(64),
                Map.of("/synthetic/manifest", "d".repeat(64)), Map.of("prompt", "e".repeat(64)),
                Map.of("BASELINE", new FreezeRecord.Arm("synthetic", List.of(WORKFLOW))),
                Map.of("route", "synthetic"), LocalDate.of(2026, 11, 19), Set.of(),
                "f".repeat(64), suites, Map.of(WORKFLOW, BigDecimal.ONE), placement);
    }

    static Path write(Path root, FreezeRecord record) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve("freeze.json");
        byte[] bytes = JSON.writeValueAsBytes(record);
        Files.write(file, bytes);
        Files.writeString(root.resolve("freeze.json.sha256"), FreezeRecord.sha256(bytes));
        return file;
    }

    private void rewrite(Path file, java.util.function.Consumer<ObjectNode> edit) throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(file.toFile());
        edit.accept(node);
        byte[] bytes = JSON.writeValueAsBytes(node);
        Files.write(file, bytes);
        Files.writeString(file.resolveSibling("freeze.json.sha256"), FreezeRecord.sha256(bytes));
    }

    @Test void legacyTwelveArgumentFreezeRetainsExactBytesAndHash() throws Exception {
        FreezeRecord withTwelve = new FreezeRecord(record(null).gitCommit(), record(null).preregistrationSha256(),
                record(null).parametersSha256(), record(null).manifests(), record(null).prompts(),
                record(null).arms(), record(null).baselineModelsByRoute(), record(null).ownerApprovalDate(),
                record(null).recheckEligibleFailureClasses(), record(null).evidenceMappingSha256(),
                record(null).compatibilitySuites(), record(null).costForecastUsdPerDeliveredCase());
        Path file = write(temp, withTwelve);
        byte[] before = Files.readAllBytes(file);
        String hash = FreezeRecord.sha256(before);
        String pinned = "{\"gitCommit\":\"" + "a".repeat(40)
                + "\",\"preregistrationSha256\":\"" + "b".repeat(64)
                + "\",\"parametersSha256\":\"" + "c".repeat(64)
                + "\",\"manifests\":{\"/synthetic/manifest\":\"" + "d".repeat(64)
                + "\"},\"prompts\":{\"prompt\":\"" + "e".repeat(64)
                + "\"},\"arms\":{\"BASELINE\":{\"model\":\"synthetic\",\"workflows\":[\"STUDY_TIP\"]}}"
                + ",\"baselineModelsByRoute\":{\"route\":\"synthetic\"},\"ownerApprovalDate\":[2026,11,19]"
                + ",\"recheckEligibleFailureClasses\":[],\"evidenceMappingSha256\":\"" + "f".repeat(64)
                + "\",\"compatibilitySuites\":{\"suite0\":\"STUDY_TIP\",\"suite1\":\"STUDY_TIP\""
                + ",\"suite2\":\"STUDY_TIP\",\"suite3\":\"STUDY_TIP\",\"suite4\":\"STUDY_TIP\""
                + ",\"suite5\":\"STUDY_TIP\",\"suite6\":\"STUDY_TIP\",\"suite7\":\"STUDY_TIP\""
                + ",\"suite8\":\"STUDY_TIP\",\"suite9\":\"STUDY_TIP\"}"
                + ",\"costForecastUsdPerDeliveredCase\":{\"STUDY_TIP\":1}}";
        assertThat(new String(before)).isEqualTo(pinned);
        assertThat(JSON.writeValueAsBytes(FreezeRecord.load(file))).containsExactly(before);
        assertThat(FreezeRecord.sha256(file)).isEqualTo(hash);
    }

    @Test void legacyEightAndNineArgumentConstructorsKeepOptionalComponentAbsent() throws Exception {
        FreezeRecord base = record(null);
        FreezeRecord eight = new FreezeRecord(base.gitCommit(), base.preregistrationSha256(),
                base.parametersSha256(), base.manifests(), base.prompts(), base.arms(),
                base.baselineModelsByRoute(), base.ownerApprovalDate());
        FreezeRecord nine = new FreezeRecord(base.gitCommit(), base.preregistrationSha256(),
                base.parametersSha256(), base.manifests(), base.prompts(), base.arms(),
                base.baselineModelsByRoute(), base.ownerApprovalDate(), Set.of());
        assertThat(JSON.writeValueAsString(eight)).doesNotContain("placementCommitments");
        assertThat(JSON.writeValueAsString(nine)).doesNotContain("placementCommitments");
    }

    @Test void anchoredFreezeLoadsAndRejectsUnknownFieldsMalformedIdsAndDuplicateCommitments() throws Exception {
        Path file = write(temp, record(Map.of(EXPORT_ID, COMMITMENT)));
        assertThat(FreezeRecord.load(file).placementCommitments()).containsEntry(EXPORT_ID, COMMITMENT);
        rewrite(file, node -> node.put("unknown", true));
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
        write(temp, record(Map.of("BAD", COMMITMENT)));
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
        write(temp, record(Map.of(EXPORT_ID, "bad")));
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
        write(temp, record(Map.of(EXPORT_ID, COMMITMENT,
                "CLASS_B_RESERVE:STUDY_TIP", COMMITMENT)));
        assertThatThrownBy(() -> FreezeRecord.load(file)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
    }
}
