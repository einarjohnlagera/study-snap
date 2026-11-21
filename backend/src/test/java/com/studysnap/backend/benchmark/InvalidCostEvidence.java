package com.studysnap.backend.benchmark;

/** A cost file or replay could not establish complete stage evidence. */
final class InvalidCostEvidence extends RuntimeException {
    InvalidCostEvidence(String message) { super(message); }
    InvalidCostEvidence(String message, Throwable cause) { super(message, cause); }
}
