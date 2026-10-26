package com.studysnap.backend.benchmark;

import com.studysnap.backend.service.impl.StatementPairVerification;

final class StatementPairExtractor {
    record Pair(String statement1, String statement2) {}
    static Pair extract(String stem) {
        StatementPairVerification.Pair pair = StatementPairVerification.extract(stem);
        return pair == null ? null : new Pair(pair.statement1(), pair.statement2());
    }
}
