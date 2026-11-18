package com.studysnap.backend.benchmark;

/** A placement secret or sidecar failed validation. Never carries source content or a cause. */
final class InvalidPlacementSeal extends RuntimeException {
    InvalidPlacementSeal() { super("Invalid placement seal"); }
}
