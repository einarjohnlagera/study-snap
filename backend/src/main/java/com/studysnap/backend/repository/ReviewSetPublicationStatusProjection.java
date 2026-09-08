package com.studysnap.backend.repository;

public interface ReviewSetPublicationStatusProjection {
    boolean getUnpublishedChanges();

    long getTopicsAdded();

    long getSubjectPlansAdded();
}
