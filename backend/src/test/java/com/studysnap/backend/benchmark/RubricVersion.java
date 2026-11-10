package com.studysnap.backend.benchmark;

import java.util.Locale;

enum RubricVersion {
    V1, V2;

    static RubricVersion parse(String value) {
        if (value!=null) {
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "v1": return V1;
                case "v2": return V2;
                default: break;
            }
        }
        throw new IllegalArgumentException("llm.benchmark.rubricVersion is required and must be v1 or v2");
    }
}
