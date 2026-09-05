package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per Note in a bulk regeneration batch, written AS THAT ITEM RESOLVES rather than as a
 * terminal blob at the end.
 *
 * <p>⚠️ THE ASYMMETRY WORTH KNOWING BEFORE READING ONE OF THESE ROWS. {@code GenerationRecoveryService}
 * sweeps a note stuck in {@code GENERATING} for more than 120 minutes, so the NOTE self-heals — but
 * nothing sweeps this table's {@code RUNNING} rows. That is a deliberate decision, not an omission: a
 * partial sweeper would have to guess whether an item that never reported back actually completed, and
 * guessing wrong in the "completed" direction is the exact defect this table exists to prevent. Stale
 * rows expire under the same 24 h TTL as the existing bulk receipt, and a {@code RUNNING} row older
 * than the TTL must be rendered as indeterminate.
 *
 * <p>⚠️ There is intentionally no foreign key to {@code notes}: a note deleted mid-batch must leave a
 * readable {@code NOT_RUN} row behind.
 */
@Entity
@Table(name = "note_bulk_regeneration_item")
@Getter
@Setter
@NoArgsConstructor
public class NoteBulkRegenerationItemEntity {
    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "note_id", nullable = false)
    private UUID noteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private NoteRegenerationScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private NoteBulkRegenerationItemState state;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason")
    private String reason;

    /**
     * Whether this item turned off at least one live shared-quiz link. Recorded as "the note had an
     * active share link when the item started AND the item reached {@code REGENERATED}" — the
     * deactivation itself happens inside the primitive's single commit transaction and is not
     * separately observable from out here.
     */
    @Column(name = "share_link_deactivated", nullable = false)
    private Boolean shareLinkDeactivated;

    /**
     * The TTL clock, and it is the BATCH's clock rather than the row's. Sweeping on {@code updatedAt}
     * would expire a long batch's early items while its late ones survived, handing the curator a
     * receipt with holes in it.
     */
    @Column(name = "batch_created_at", nullable = false)
    private OffsetDateTime batchCreatedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
