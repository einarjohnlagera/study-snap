package com.studysnap.backend.benchmark;

/** A switch-time report could not be recomputed from the supplied FRB inputs. */
final class InvalidReportRecomputation extends RuntimeException {
    InvalidReportRecomputation(String message, Throwable cause) { super(message, cause); }
}
