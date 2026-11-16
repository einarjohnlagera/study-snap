package com.studysnap.backend.benchmark;

/** A reserve decision or its locked evidence is missing, inconsistent, or altered. */
final class InvalidReserveRecord extends RuntimeException {
    InvalidReserveRecord(String message) { super(message); }
    InvalidReserveRecord(String message, Throwable cause) { super(message, cause); }
}
