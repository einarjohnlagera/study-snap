package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

public final class QuizVersionShuffleUtils {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String SEED_SEPARATOR = "|";
    private static final String QUESTION_SEED_SCOPE = "questions";
    private static final String CHOICE_SEED_SCOPE = "choices";
    private static final String TRUE_FALSE_FORMAT = "TRUE_FALSE";
    private static final String MATCHING_FORMAT = "MATCHING";

    private QuizVersionShuffleUtils() {
    }

    public static List<QuizItem> shuffleQuestionsAndChoices(
            List<QuizItem> questions,
            String versionLetter,
            String quizIdSeed
    ) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }

        List<QuestionBlock> questionBlocks = buildQuestionBlocks(questions);
        List<Integer> questionOrder = IntStream.range(0, questionBlocks.size())
                .boxed()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(questionOrder, randomFor(quizIdSeed, versionLetter, QUESTION_SEED_SCOPE));

        List<QuizItem> shuffled = new ArrayList<>();
        for (Integer blockIndex : questionOrder) {
            shuffled.addAll(shuffleBlockChoices(questionBlocks.get(blockIndex), versionLetter, quizIdSeed));
        }
        return shuffled;
    }

    private static List<QuestionBlock> buildQuestionBlocks(List<QuizItem> questions) {
        List<QuestionBlock> blocks = new ArrayList<>();
        int index = 0;
        while (index < questions.size()) {
            QuizItem question = questions.get(index);
            if (isMatchingQuestion(question)) {
                String questionGroup = question.questionGroup();
                int endIndex = index + 1;
                while (endIndex < questions.size()
                        && isMatchingQuestion(questions.get(endIndex))
                        && Objects.equals(questionGroup, questions.get(endIndex).questionGroup())) {
                    endIndex += 1;
                }
                if (endIndex - index > 1) {
                    blocks.add(new QuestionBlock(index, questions.subList(index, endIndex)));
                    index = endIndex;
                    continue;
                }
            }
            blocks.add(new QuestionBlock(index, List.of(question)));
            index += 1;
        }
        return blocks;
    }

    private static List<QuizItem> shuffleBlockChoices(
            QuestionBlock block,
            String versionLetter,
            String quizIdSeed
    ) {
        if (!block.isMatchingGroup()) {
            return List.of(shuffleChoices(block.questions().getFirst(), block.startIndex(), versionLetter, quizIdSeed));
        }
        if (!hasSharedMatchingChoices(block.questions())) {
            return block.questions();
        }
        List<String> choices = block.questions().getFirst().choices() == null
                ? List.of()
                : block.questions().getFirst().choices();
        List<Integer> choiceOrder = shuffledChoiceOrder(choices.size(), block.startIndex(), versionLetter, quizIdSeed);
        return block.questions().stream()
                .map(question -> shuffleChoices(question, choiceOrder))
                .toList();
    }

    private static QuizItem shuffleChoices(
            QuizItem question,
            int questionIndex,
            String versionLetter,
            String quizIdSeed
    ) {
        List<String> choices = question.choices() == null ? List.of() : question.choices();
        if (TRUE_FALSE_FORMAT.equals(question.questionFormat())) {
            return copyQuizItem(question, choices, question.correctIndex(), question.correctIndices());
        }
        return shuffleChoices(question, shuffledChoiceOrder(choices.size(), questionIndex, versionLetter, quizIdSeed));
    }

    private static QuizItem shuffleChoices(QuizItem question, List<Integer> choiceOrder) {
        List<String> choices = question.choices() == null ? List.of() : question.choices();
        List<Integer> expectedOrder = IntStream.range(0, choices.size())
                .boxed()
                .toList();
        if (choices.size() != choiceOrder.size() || !choiceOrder.containsAll(expectedOrder)) {
            return copyQuizItem(question, choices, question.correctIndex(), question.correctIndices());
        }
        List<String> shuffledChoices = new ArrayList<>(choiceOrder.size());
        List<Integer> shuffledCorrectIndices = new ArrayList<>();
        Integer shuffledCorrectIndex = null;
        for (int shuffledIndex = 0; shuffledIndex < choiceOrder.size(); shuffledIndex++) {
            int originalIndex = choiceOrder.get(shuffledIndex);
            shuffledChoices.add(choices.get(originalIndex));
            if (Objects.equals(question.correctIndex(), originalIndex)) {
                shuffledCorrectIndex = shuffledIndex;
            }
            if (question.correctIndices() != null && question.correctIndices().contains(originalIndex)) {
                shuffledCorrectIndices.add(shuffledIndex);
            }
        }
        return copyQuizItem(question, shuffledChoices, shuffledCorrectIndex, shuffledCorrectIndices);
    }

    private static List<Integer> shuffledChoiceOrder(
            int choiceCount,
            int questionIndex,
            String versionLetter,
            String quizIdSeed
    ) {
        List<Integer> choiceOrder = IntStream.range(0, choiceCount)
                .boxed()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(
                choiceOrder,
                randomFor(quizIdSeed, versionLetter, CHOICE_SEED_SCOPE + SEED_SEPARATOR + questionIndex)
        );
        return choiceOrder;
    }

    private static boolean isMatchingQuestion(QuizItem question) {
        return question != null
                && MATCHING_FORMAT.equals(question.questionFormat())
                && question.questionGroup() != null
                && !question.questionGroup().isBlank();
    }

    private static boolean hasSharedMatchingChoices(List<QuizItem> questions) {
        if (questions == null || questions.isEmpty()) {
            return false;
        }
        List<String> sharedChoices = questions.getFirst().choices();
        return questions.stream().allMatch(question -> Objects.equals(sharedChoices, question.choices()));
    }

    private static QuizItem copyQuizItem(
            QuizItem question,
            List<String> choices,
            Integer correctIndex,
            List<Integer> correctIndices
    ) {
        return new QuizItem(
                question.question(),
                choices,
                correctIndex,
                question.concept(),
                question.explanation(),
                null,
                question.questionFormat(),
                question.questionType(),
                question.workingSolution(),
                correctIndices,
                question.questionGroup(),
                question.keyConcept(),
                question.acceptableAnswers()
        );
    }

    private static Random randomFor(String quizIdSeed, String versionLetter, String scope) {
        String normalizedSeed = normalizeSeed(quizIdSeed)
                + SEED_SEPARATOR
                + normalizeSeed(versionLetter)
                + SEED_SEPARATOR
                + scope;
        return new Random(toLongSeed(normalizedSeed));
    }

    private static String normalizeSeed(String value) {
        return value == null ? "" : value;
    }

    private static long toLongSeed(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(bytes).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not create deterministic quiz shuffle seed.", exception);
        }
    }

    private record QuestionBlock(int startIndex, List<QuizItem> questions) {
        private QuestionBlock {
            questions = List.copyOf(questions);
        }

        private boolean isMatchingGroup() {
            return questions.size() > 1 && questions.stream().allMatch(QuizVersionShuffleUtils::isMatchingQuestion);
        }
    }
}
