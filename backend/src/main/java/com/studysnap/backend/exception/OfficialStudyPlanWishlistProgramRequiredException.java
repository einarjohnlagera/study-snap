package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class OfficialStudyPlanWishlistProgramRequiredException extends AppException {
    private static final String CODE = "OFFICIAL_STUDY_PLAN_WISHLIST_PROGRAM_REQUIRED";
    private static final String MESSAGE = "Course / Program is required to record this request.";

    public OfficialStudyPlanWishlistProgramRequiredException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
