package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.MockQuizGenerationUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "stub")
public class StubLlmStudyPackService implements LlmStudyPackService {

    private static final String GENERATED_NOTE_TEMPLATE = """
            Overview
            %s is an important study topic. Use this draft as a starting point, then edit it to match your class notes.

            Core ideas
            - Define the main concept in simple terms.
            - Break the topic into the major parts or principles learners need to remember.
            - Connect the topic to why it matters in class or exams.

            Key details to review
            - Important definitions
            - Common examples or applications
            - Mistakes or misconceptions to avoid

            Quick review prompts
            - What is %s?
            - Why does %s matter?
            - How would you explain %s in your own words?
            """;

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context) {
        String preview = normalizedNotesText.length() > 80
                ? normalizedNotesText.substring(0, 80) + "..."
                : normalizedNotesText;

        return new GeneratedStudyPackContent(
                "Study Pack: " + preview,
                "These notes have been organized into a concise study summary to support focused revision.",
                "General Studies",
                List.of("Core Concepts", "Study Skills", "Review Practice"),
                List.of(
                        "Main topic and scope",
                        "Core definitions and relationships",
                        "Important formulas or rules"
                ),
                List.of(
                        new QuizItem(
                                "What is the main topic of these notes?",
                                List.of("Topic A", "Topic B", "Topic C", "Topic D"),
                                "Topic A",
                                "Main Topic",
                                "The topic comes directly from the provided notes."
                        ),
                        new QuizItem(
                                "Which concept should be studyPacked first?",
                                List.of("Background idea", "Core definition", "Edge case", "Advanced exception"),
                                "Core definition",
                                "Core Definitions",
                                "Foundational definitions are best reviewed first."
                        )
                ),
                "stub-model",
                null,
                null,
                null,
                null
        );
    }

    @Override
    public String regenerateSummary(String normalizedNoteContent, StudyPackGenerationContext context) {
        return "These official notes have been refreshed into a concise enriched summary for focused review.";
    }

    @Override
    public String generateNoteFromTopic(String topic, StudyPackGenerationContext context) {
        String normalizedTopic = topic == null || topic.isBlank() ? "this topic" : topic.trim();
        return GENERATED_NOTE_TEMPLATE.formatted(normalizedTopic, normalizedTopic, normalizedTopic, normalizedTopic);
    }

    @Override
    public CompanionContent generateCompanion(CompanionGenerationContext context, Set<CompanionSection> sections) {
        String title = context == null || context.title() == null || context.title().isBlank()
                ? "this study plan"
                : context.title().trim();
        boolean overview = sections != null && sections.contains(CompanionSection.OVERVIEW);
        boolean studyStrategy = sections != null && sections.contains(CompanionSection.STUDY_STRATEGY);
        boolean commonMistakes = sections != null && sections.contains(CompanionSection.COMMON_MISTAKES);
        boolean faq = sections != null && sections.contains(CompanionSection.FAQ);
        return new CompanionContent(
                overview ? "Use " + title + " as the learner's home base for the major concepts and practice expectations." : null,
                studyStrategy ? "Work through one subject area at a time, then return to mixed review before the final check." : null,
                commonMistakes ? "Do not skip practice after reading summaries; learners should confirm each topic with retrieval." : null,
                null,
                faq ? List.of(
                        new CompanionFaqItem("Where should learners start?", "Start with the first subject plan, then continue in order."),
                        new CompanionFaqItem("How often should learners review?", "Use short daily review blocks and revisit missed concepts."),
                        new CompanionFaqItem("What should learners do before the exam?", "Finish mixed practice and revisit the weakest topics.")
                ) : List.of()
        );
    }

    @Override
    public String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries) {
        if (incorrectQuestionSummaries == null || incorrectQuestionSummaries.isEmpty()) {
            return null;
        }
        String first = incorrectQuestionSummaries.getFirst();
        return "Review this concept again: " + first;
    }

    @Override
    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        if (weakConcepts == null || weakConcepts.isEmpty()) {
            return List.of();
        }
        int normalizedCount = Math.clamp(questionCount, 5, 10);
        return IntStream.range(0, normalizedCount)
                .mapToObj(index -> {
                    String concept = weakConcepts.get(index % weakConcepts.size());
                    String correctAnswer = concept + " core principle";
                    return new QuizItem(
                            "Which statement best explains " + concept + "?",
                            List.of(
                                    correctAnswer,
                                    concept + " unrelated detail",
                                    concept + " common misconception",
                                    concept + " less accurate interpretation"
                            ),
                            correctAnswer,
                            concept,
                            "Review the " + concept + " concept in your notes."
                    );
                })
                .toList();
    }

    @Override
    public List<QuizItem> generateInterviewPracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        return MockQuizGenerationUtils.generateChallengeQuiz(
                studyPackTitle,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                "mixed",
                context
        );
    }

    @Override
    public InterviewPracticeCritique generateInterviewCritique(QuizItem question, int selectedChoiceIndex) {
        String verdict = question.correctIndex() != null && question.correctIndex() == selectedChoiceIndex
                ? "STRONG"
                : "RECONSIDER";
        return new InterviewPracticeCritique(
                verdict,
                "This answer is evaluated against the strongest interview approach.",
                "How would you explain your reasoning to a senior interviewer?"
        );
    }

    @Override
    public List<QuizItem> generateChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        List<String> concepts = keyConcepts == null || keyConcepts.isEmpty()
                ? List.of("Core Concept")
                : keyConcepts;
        int normalizedCount = Math.clamp(questionCount, 10, 15);
        return IntStream.range(0, normalizedCount)
                .mapToObj(index -> {
                    String concept = concepts.get(index % concepts.size());
                    if (index == 0) {
                        return buildStubIdentificationItem(concept);
                    }
                    if (index == 1 && normalizedCount > 1) {
                        return buildStubEnumerationItem(concept);
                    }
                    String correctAnswer = concept + " applied understanding";
                    return new QuizItem(
                            "In a " + difficulty + " scenario, what best represents " + concept + "?",
                            List.of(
                                    correctAnswer,
                                    concept + " superficial detail",
                                    concept + " unrelated association",
                                    concept + " incorrect assumption"
                            ),
                            correctAnswer,
                            concept,
                            "Review the " + concept + " concept in your notes."
                    );
                })
                .toList();
    }

    private QuizItem buildStubIdentificationItem(String concept) {
        return new QuizItem(
                "Identify the term most closely associated with " + concept + ".",
                List.of(),
                null,
                concept,
                "This term names the " + concept + " concept from your notes.",
                null,
                "IDENTIFICATION",
                null,
                null,
                null,
                null,
                concept,
                List.of(concept),
                null
        );
    }

    private QuizItem buildStubEnumerationItem(String concept) {
        return new QuizItem(
                "Name the two key aspects of " + concept + ".",
                List.of(),
                null,
                concept,
                "These are the two key aspects of " + concept + " covered in your notes.",
                null,
                "ENUMERATION",
                null,
                null,
                null,
                null,
                concept,
                null,
                List.of(
                        List.of(concept + " aspect one"),
                        List.of(concept + " aspect two")
                )
        );
    }

    @Override
    public List<QuizItem> generateLongExam(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        return MockQuizGenerationUtils.generateChallengeQuiz(
                studyPackTitle,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                difficulty,
                context
        );
    }

    @Override
    public List<QuizItem> generateBoardExamQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        return MockQuizGenerationUtils.generateChallengeQuiz(
                studyPackTitle,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                difficulty,
                context
        );
    }

    @Override
    public List<QuizItem> generateLongExamParallel(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int totalQuestions,
            String difficulty,
            StudyPackGenerationContext context,
            AsyncTaskExecutor taskExecutor
    ) {
        return generateLongExam(
                studyPackTitle,
                studyPackSummary,
                keyConcepts,
                disallowedQuestions,
                totalQuestions,
                difficulty,
                context
        );
    }

    @Override
    public List<QuizItem> generateTeacherQuiz(
            String noteTitle,
            String noteContent,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        int normalizedCount = Math.clamp(questionCount, 5, 12);
        String concept = context != null && context.subject() != null && !context.subject().isBlank()
                ? context.subject()
                : "Core Concept";
        return IntStream.range(0, normalizedCount)
                .mapToObj(index -> {
                    String correctAnswer = concept + " best-practice understanding";
                    return new QuizItem(
                            "Which teacher-ready question best checks " + concept + " from " + (noteTitle == null ? "this note" : noteTitle) + "?",
                            List.of(
                                    correctAnswer,
                                    concept + " unrelated detail",
                                    concept + " vague prompt",
                                    concept + " unsupported assumption"
                            ),
                            correctAnswer,
                            concept,
                            "This answer is clear, note-based, and suitable for review or export."
                    );
                })
                .toList();
    }
}
