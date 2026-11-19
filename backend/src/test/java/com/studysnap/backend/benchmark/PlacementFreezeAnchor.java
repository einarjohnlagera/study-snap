package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Read-only preflight shared by the anchored packet exporters. */
final class PlacementFreezeAnchor {
    record Binding(String commitment, String freezeSha256) {}

    private PlacementFreezeAnchor() {}

    static Binding require(Path freeze, List<Stage1Receipt> receipts, String exportId) {
        try {
            if (freeze == null || receipts == null || receipts.isEmpty() || exportId == null)
                throw new InvalidPlacementSeal();
            String sha = FreezeRecord.sha256(freeze);
            for (Stage1Receipt receipt : receipts)
                if (receipt == null || !sha.equals(receipt.freezeSha256()))
                    throw new InvalidPlacementSeal();
            FreezeRecord record = FreezeRecord.load(freeze);
            String commitment = record.placementCommitments() == null ? null
                    : record.placementCommitments().get(exportId);
            if (!IntegritySupport.digest(commitment)) throw new InvalidPlacementSeal();
            return new Binding(commitment, sha);
        } catch (IOException | FreezeRecord.InvalidFreeze exception) {
            throw new InvalidPlacementSeal();
        }
    }
}
