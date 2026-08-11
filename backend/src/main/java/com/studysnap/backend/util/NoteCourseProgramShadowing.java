package com.studysnap.backend.util;

import com.studysnap.backend.entity.DomainContext;

public final class NoteCourseProgramShadowing {
    private NoteCourseProgramShadowing() {
    }

    public static boolean isShadowed(int joinRowCount, DomainContext domainContext) {
        // The string is shadowed only when BOTH readers ignore it. Discovery ignores it whenever the
        // note has any join row -- every discovery query is
        // `EXISTS(join) OR (NOT EXISTS(join) AND legacy matches)`, verified across all five readers
        // (both library filters, both legacy-value lookups, and the facet count). Generation ignores it
        // when a Domain Context is set, or at exactly one row where the joined catalog name wins.
        //
        // The original form `(joinRowCount == 1) || (domainContext != null)` dropped the discovery term
        // and so returned true at ZERO join rows with a Domain Context -- precisely where the string is
        // the ONLY thing discovery has. That froze the learner's own field: hidden, un-required, and
        // skipped by update, while still driving which shelf the note appears on.
        boolean discoveryIgnoresIt = joinRowCount >= 1;
        boolean generationIgnoresIt = domainContext != null || joinRowCount == 1;
        return discoveryIgnoresIt && generationIgnoresIt;
    }
}
