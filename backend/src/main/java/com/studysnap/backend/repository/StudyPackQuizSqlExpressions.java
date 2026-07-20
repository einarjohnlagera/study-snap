package com.studysnap.backend.repository;

final class StudyPackQuizSqlExpressions {
    private static final String POSTGRES_QUIZ_COUNT_EXPRESSION =
            "case when %s.quiz is null then 0 else jsonb_array_length(%s.quiz) end";
    private static final String FALLBACK_QUIZ_COUNT_EXPRESSION =
            "case when %s.quiz is null or trim(cast(%s.quiz as varchar)) in ('', '[]', 'null') then 0 else 1 end";

    private StudyPackQuizSqlExpressions() {
    }

    static String quizCount(String studyPackAlias, boolean postgres) {
        String expression = postgres ? POSTGRES_QUIZ_COUNT_EXPRESSION : FALLBACK_QUIZ_COUNT_EXPRESSION;
        return expression.formatted(studyPackAlias, studyPackAlias);
    }
}
