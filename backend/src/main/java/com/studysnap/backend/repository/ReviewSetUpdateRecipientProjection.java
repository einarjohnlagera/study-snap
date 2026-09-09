package com.studysnap.backend.repository;

import java.util.UUID;

public interface ReviewSetUpdateRecipientProjection {
    UUID getRecipientUserId();

    UUID getAdoptedCollectionId();
}
