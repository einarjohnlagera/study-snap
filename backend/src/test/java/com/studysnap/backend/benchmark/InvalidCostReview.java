package com.studysnap.backend.benchmark;

/** Malformed primary receipt facts cannot produce a partial operational cost review. */
final class InvalidCostReview extends RuntimeException {
    InvalidCostReview(String message) { super(message); }
}
