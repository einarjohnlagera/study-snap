package com.studysnap.backend.model;

public enum PublicLibrarySort {
    FEATURED,
    POPULAR,
    COPIED,
    RECENT,
    VIEWS,
    TITLE,
    MOST_COPIED,
    RECOMMENDED;

    public boolean isSqlOrderable() {
        return this == RECENT || this == TITLE;
    }
}
