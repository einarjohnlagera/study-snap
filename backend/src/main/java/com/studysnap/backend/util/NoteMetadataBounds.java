package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NoteMetadataBounds {
    public static final int SUBJECT_MAX_LENGTH = 64;
    public static final int COURSE_PROGRAM_MAX_LENGTH = 120;
    public static final String SUBJECT_TOO_LONG_MESSAGE = "Subject must be 64 characters or less.";
    public static final String COURSE_PROGRAM_TOO_LONG_MESSAGE = "Course/program must be 120 characters or less.";
}
