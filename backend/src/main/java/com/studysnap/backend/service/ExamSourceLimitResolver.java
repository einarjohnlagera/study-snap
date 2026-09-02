package com.studysnap.backend.service;

/**
 * The one formula that turns an exam question count into its safe number of source notes.
 *
 * <p>Both Long Exam and multi-note Challenge Quiz use it so Plus/Pro cannot drift from the
 * level-derived 6 / 8 / 10 ceiling that guarantees each source receives enough questions.
 */
public final class ExamSourceLimitResolver {
    private static final int MIN_QUESTIONS_PER_SOURCE = 3;

    private ExamSourceLimitResolver() {
    }

    public static int resolveMaxSourceNotes(int questionCount) {
        return questionCount / MIN_QUESTIONS_PER_SOURCE;
    }

    public static int minimumQuestionsPerSource() {
        return MIN_QUESTIONS_PER_SOURCE;
    }
}
