package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementProvenanceTest {
    private static final String EXPORT_ID = "CLASS_B_PRIMARY";
    private static final String COMMITMENT = "3".repeat(64);
    @TempDir Path temp;

    private FrbPipelineTest.Fixture fixture() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        return builder.fixture(false, 0);
    }

    private static ResultsPipeline.FrbRunInput realCopy(ResultsPipeline.FrbRunInput old) {
        return new ResultsPipeline.FrbRunInput(old.repoRoot(), old.freeze(), old.parameters(), old.mapping(),
                old.manifests(), old.baselineMini(), old.baseline41(), old.candidate(), old.reserves(),
                old.compatibilityReports(), old.headCommitTime(), old.ownerDispositions(), old.report(), false);
    }

    private static void stamp(Path seal, String exportId, String commitment) throws Exception {
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(seal.toFile());
        node.put("placementCommitmentSha256", commitment);
        node.put("placementSidecarSha256", "2".repeat(64));
        node.put("placementExportId", exportId);
        Files.writeString(seal, node.toString());
        Files.writeString(IntegritySupport.detached(seal), FreezeRecord.sha256(seal) + "\n");
    }

    private static void stampFreeze(Path freeze, Map<String, String> commitments) throws Exception {
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(freeze.toFile());
        node.set("placementCommitments", IntegritySupport.JSON.valueToTree(commitments));
        Files.writeString(freeze, node.toString());
        Files.writeString(IntegritySupport.detached(freeze), FreezeRecord.sha256(freeze) + "\n");
    }

    @Test void directRealRunRefusesUnsealedPlacementWhileSyntheticFixturePasses() throws Exception {
        var fixture = fixture();
        assertThat(FrbProvenance.verify(fixture.run(), fixture.frb())).isNotNull();
        ResultsPipeline.FrbRunInput real = realCopy(fixture.run());
        ResultsPipeline.FrbInput input = fixture.frb();
        assertThatThrownBy(() -> FrbProvenance.verify(real, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessage("Placement seed not sealed");
    }

    @Test void sealCommitmentDifferentFromFreezeIsRefused() throws Exception {
        var fixture = fixture();
        Path seal = fixture.frb().provenance().primaries().getFirst().sealFile();
        stamp(seal, EXPORT_ID, COMMITMENT);
        stampFreeze(fixture.run().freeze(), Map.of(EXPORT_ID, "4".repeat(64)));
        ResultsPipeline.FrbRunInput run = fixture.run();
        ResultsPipeline.FrbInput input = fixture.frb();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Placement commitment differs from freeze");
    }

    @Test void twoSealsUsingSameExportIdAreRefused() throws Exception {
        var fixture = fixture();
        stampFreeze(fixture.run().freeze(), Map.of(EXPORT_ID, COMMITMENT));
        stamp(fixture.frb().provenance().primaries().get(0).sealFile(), EXPORT_ID, COMMITMENT);
        stamp(fixture.frb().provenance().primaries().get(1).sealFile(), EXPORT_ID, COMMITMENT);
        ResultsPipeline.FrbRunInput run = fixture.run();
        ResultsPipeline.FrbInput input = fixture.frb();
        assertThatThrownBy(() -> FrbProvenance.verify(run, input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class)
                .hasMessageContaining("Duplicate placement export id");
    }

    @Test void realPlacementGateAcceptsDistinctSealsMatchingTheFreeze() throws Exception {
        var fixture = fixture();
        Map<String, String> commitments = new LinkedHashMap<>();
        int index = 0;
        for (ResultsPipeline.PrimaryExport export : fixture.frb().provenance().primaries()) {
            String id = "CLASS_A:ARM" + index + "-OTHER" + index;
            String commitment = "%064x".formatted(index + 1);
            commitments.put(id, commitment);
            stamp(export.sealFile(), id, commitment);
            index++;
        }
        stampFreeze(fixture.run().freeze(), commitments);
        FrbProvenance.verifyPlacement(realCopy(fixture.run()), fixture.frb().provenance());
        assertThat(commitments).hasSize(index);
    }
}
