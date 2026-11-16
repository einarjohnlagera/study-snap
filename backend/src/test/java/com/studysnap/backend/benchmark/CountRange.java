package com.studysnap.backend.benchmark;

record CountRange(int low, int high) {
    CountRange {
        if (low > high) throw new InvalidEvidence("Count range lower bound exceeds upper bound");
    }
}
