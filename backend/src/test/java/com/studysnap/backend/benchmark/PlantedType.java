package com.studysnap.backend.benchmark;

enum PlantedType {
    WRONG_KEY(Severity.CRITICAL),
    EXPLANATION_CONTRADICTS_KEY(Severity.CRITICAL),
    SECOND_DEFENSIBLE_ANSWER(Severity.CRITICAL),
    MATERIAL_AMBIGUITY_MULTI(Severity.CRITICAL),
    MATERIAL_AMBIGUITY_ZERO(Severity.CRITICAL),
    SOURCE_CONTRADICTION(Severity.CRITICAL),
    MINOR_WORDING(Severity.NONCRITICAL),
    NUMERIC_INCONSISTENCY(Severity.NONCRITICAL),
    WEAK_DISTRACTOR(Severity.NONCRITICAL),
    UNHELPFUL_EXPLANATION(Severity.NONCRITICAL),
    CLEAN(Severity.CLEAN);

    enum Severity { CRITICAL, NONCRITICAL, CLEAN }
    private final Severity severity;
    PlantedType(Severity severity) { this.severity = severity; }
    Severity severity() { return severity; }
}
