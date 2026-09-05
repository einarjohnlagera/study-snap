package com.studysnap.backend.entity;

/**
 * Per-item state of a curator bulk regeneration batch.
 *
 * <p>⚠️ The distinction between {@link #BLOCKED} and {@link #FAILED} is not cosmetic — it drives retry.
 * A {@code FAILED} item is retryable as-is; a {@code BLOCKED} item stays blocked until the curator
 * changes the blocking condition (sets a Domain Context, waits for a quota reset, lets an in-flight
 * generation finish). Neither is ever silently skipped, and neither is ever counted as
 * {@link #REGENERATED}.
 *
 * <p>⚠️ {@link #NOT_RUN} is reserved for an item whose Note could no longer be resolved for the caller
 * when its turn came — deleted, or ownership lost (failure matrix row 7). It is NOT a general
 * "we skipped it" bucket.
 *
 * <p>⚠️ {@link #RUNNING} surviving past the batch is EXPECTED and is not a bug. Nothing sweeps a lost
 * batch (see {@code NoteBulkRegenerationService}), so a driver thread killed mid-item leaves its row
 * {@code RUNNING} until the 24 h TTL removes it. A reader must render a {@code RUNNING} row older than
 * the TTL as indeterminate rather than as in-flight.
 */
public enum NoteBulkRegenerationItemState {
    PENDING,
    RUNNING,
    REGENERATED,
    BLOCKED,
    FAILED,
    NOT_RUN
}
