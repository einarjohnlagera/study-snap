package com.studysnap.backend.util;

import com.studysnap.backend.entity.DomainContext;

public final class NoteCourseProgramShadowing {
    private NoteCourseProgramShadowing() {
    }

    public static boolean isShadowed(int joinRowCount, DomainContext domainContext) {
        return joinRowCount == 1 || domainContext != null;
    }
}
