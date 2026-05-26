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

        List<Integer> questionOrder = IntStream.range(0, questions.size())
                .boxed()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(questionOrder, randomFor(quizIdSeed, versionLetter, QUESTION_SEED_SCOPE));

        return questionOrder.stream()
                .map(questionIndex -> shuffleChoices(questions.get(questionIndex), questionIndex, versionLetter, quizIdSeed))
                .toList();
    }

    private static QuizItem shuffleChoices(
            QuizItem question,
            int questionIndex,
            String versionLetter,
            String quizIdSeed
    ) {
        List<String> choices = question.choices() == null ? List.of() : question.choices();
        List<Integer> choiceOrder = IntStream.range(0, choices.size())
                .boxed()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(
                choiceOrder,
                randomFor(quizIdSeed, versionLetter, CHOICE_SEED_SCOPE + SEED_SEPARATOR + questionIndex)
        );

        List<String> shuffledChoices = new ArrayList<>(choiceOrder.size());
        Integer shuffledCorrectIndex = null;
        for (int shuffledIndex = 0; shuffledIndex < choiceOrder.size(); shuffledIndex++) {
            int originalIndex = choiceOrder.get(shuffledIndex);
            shuffledChoices.add(choices.get(originalIndex));
            if (Objects.equals(question.correctIndex(), originalIndex)) {
                shuffledCorrectIndex = shuffledIndex;
            }
        }
        return new QuizItem(
                question.question(),
                shuffledChoices,
                shuffledCorrectIndex,
                question.concept(),
                question.explanation(),
                null,
                question.questionType(),
                question.workingSolution()
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
}
