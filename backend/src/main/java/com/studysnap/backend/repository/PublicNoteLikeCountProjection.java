package com.studysnap.backend.repository;

import java.util.UUID;

public interface PublicNoteLikeCountProjection {
    UUID getNoteId();
    long getLikeCount();
}
