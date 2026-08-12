package com.studysnap.backend.repository;

public interface OfficialStudyPlanDemandProjection {
    String getCourseProgram();

    long getRequestCount();

    long getDistinctLearners();
}
