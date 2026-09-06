package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.PublicQuizItem;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.dto.SharedQuizResultItem;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.CombinedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.exception.GeneratedQuizNotFoundException;
import com.studysnap.backend.exception.CombinedQuizNotFoundException;
import com.studysnap.backend.exception.InvalidSharedQuizAnswersException;
import com.studysnap.backend.exception.QuizShareLinkNotAllowedException;
import com.studysnap.backend.exception.QuizShareLinkNotFoundException;
import com.studysnap.backend.exception.QuizShareLinkTokenGenerationException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.CombinedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizShareLinkService {
    private static final String MATCHING_FORMAT = "MATCHING";
    private static final char[] BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int TOKEN_LENGTH = 16;
    private static final int MAX_TOKEN_ATTEMPTS = 10;
    private static final String QUIZ_SHARE_PATH_PREFIX = "/quiz/";
    private static final String DEFAULT_NOTE_TITLE = "Shared quiz";

    private final QuizShareLinkRepository quizShareLinkRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final CombinedQuizRepository combinedQuizRepository;
    private final NoteRepository noteRepository;
    private final OnboardingGuardService onboardingGuardService;
    private final QuizShareLimitService quizShareLimitService;
    private final UserUsageService userUsageService;
    private final AuthService authService;
    private final StudySnapProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public QuizShareLinkResponse createShareLink(UUID generatedQuizId, UUID ownerUserId) {
        authService.requireEmailVerified(ownerUserId);
        onboardingGuardService.assertProfileComplete(ownerUserId);
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findByIdAndOwnerUserId(generatedQuizId, ownerUserId)
                .orElseThrow(GeneratedQuizNotFoundException::new);
        QuizShareLinkEntity existing = quizShareLinkRepository
                .findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuiz.getId(), ownerUserId)
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getActive())) {
            return toResponse(existing);
        }
        quizShareLimitService.assertShareLinkQuotaNotExceeded(ownerUserId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QuizShareLinkEntity entity = new QuizShareLinkEntity();
        entity.setId(UUID.randomUUID());
        entity.setGeneratedQuizId(generatedQuiz.getId());
        entity.setOwnerUserId(ownerUserId);
        entity.setToken(generateUniqueToken());
        entity.setActive(true);
        entity.setCreatedAt(now);
        QuizShareLinkEntity saved = quizShareLinkRepository.save(entity);
        userUsageService.incrementQuizShareLinkCreated(ownerUserId, now);
        return toResponse(saved);
    }

    /**
     * Sibling of the single-note creation path. Do not merge the nullable target ids: each target needs its
     * own idempotency lookup, or an existing link on the other arc would be checked accidentally.
     */
    @Transactional
    public QuizShareLinkResponse createCombinedQuizShareLink(UUID combinedQuizId, UUID ownerUserId) {
        authService.requireEmailVerified(ownerUserId);
        onboardingGuardService.assertProfileComplete(ownerUserId);
        CombinedQuizEntity combinedQuiz = combinedQuizRepository.findByIdAndOwnerUserId(combinedQuizId, ownerUserId)
                .orElseThrow(CombinedQuizNotFoundException::new);
        QuizShareLinkEntity existing = quizShareLinkRepository
                .findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(combinedQuiz.getId(), ownerUserId)
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getActive())) {
            return toResponse(existing);
        }
        quizShareLimitService.assertShareLinkQuotaNotExceeded(ownerUserId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QuizShareLinkEntity entity = new QuizShareLinkEntity();
        entity.setId(UUID.randomUUID());
        entity.setCombinedQuizId(combinedQuiz.getId());
        entity.setOwnerUserId(ownerUserId);
        entity.setToken(generateUniqueToken());
        entity.setActive(true);
        entity.setCreatedAt(now);
        QuizShareLinkEntity saved = quizShareLinkRepository.save(entity);
        userUsageService.incrementQuizShareLinkCreated(ownerUserId, now);
        return toResponse(saved);
    }

    /**
     * Sibling of {@link #getShareLinkByQuizId}. Without it a client can only recover an existing link by
     * POSTing, which is a write to read state -- and on a link the owner has toggled OFF that POST mints a
     * NEW link and spends share-link quota, because the idempotent early return requires an ACTIVE link.
     */
    @Transactional(readOnly = true)
    public QuizShareLinkResponse getCombinedQuizShareLink(UUID combinedQuizId, UUID ownerUserId) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        return quizShareLinkRepository
                .findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(combinedQuizId, ownerUserId)
                .map(this::toResponse)
                .orElseThrow(QuizShareLinkNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public QuizShareLinkResponse getShareLinkByQuizId(UUID generatedQuizId, UUID ownerUserId) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        return quizShareLinkRepository
                .findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuizId, ownerUserId)
                .map(this::toResponse)
                .orElseThrow(QuizShareLinkNotFoundException::new);
    }

    public QuizShareLinkResponse toggleShareLink(String token, UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        QuizShareLinkEntity link = quizShareLinkRepository.findByToken(token)
                .orElseThrow(QuizShareLinkNotFoundException::new);
        if (!Objects.equals(link.getOwnerUserId(), callerUserId)) {
            throw new QuizShareLinkNotAllowedException();
        }
        link.setActive(!Boolean.TRUE.equals(link.getActive()));
        return toResponse(quizShareLinkRepository.save(link));
    }

    @Transactional(readOnly = true)
    public PublicSharedQuizResponse getActivePublicQuiz(String token) {
        QuizShareLinkEntity link = findActiveLink(token);
        SharedQuiz sharedQuiz = resolveSharedQuiz(link);
        List<PublicQuizItem> questions = sharedQuiz.questions().stream()
                .map(question -> new PublicQuizItem(
                        question.question(),
                        question.choices(),
                        question.concept(),
                        question.questionFormat()
                ))
                .toList();
        return new PublicSharedQuizResponse(
                sharedQuiz.id(),
                sharedQuiz.title(),
                questions
        );
    }

    /**
     * Grades a recipient's submission.
     *
     * <p>⚠️ Grading MUST route through {@link QuizSessionReviewUtils#isAnswerCorrect}, the same rule every
     * in-app mode uses. The bespoke {@code answer == correctIndex} comparison this replaced silently
     * mis-graded every MULTI_SELECT question: {@code QuizItem.correctIndex()} falls back to
     * {@code correctIndices.getFirst()} for that format, so on correct answers {@code [0, 2]} a recipient
     * picking 2 scored zero and one picking only 0 scored full marks. {@code teacher-quiz-developer.txt}
     * instructs 1-2 MULTI_SELECT questions per quiz, so it was live in effectively every shared quiz.
     */
    @Transactional(readOnly = true)
    public SharedQuizResultsResponse getSharedQuizResults(
            String token,
            List<Integer> answers,
            List<List<Integer>> multiAnswers
    ) {
        QuizShareLinkEntity link = findActiveLink(token);
        List<QuizItem> questions = resolveSharedQuestions(link);
        if (answers == null || answers.size() != questions.size()) {
            throw new InvalidSharedQuizAnswersException();
        }
        // multiAnswers is optional -- a recipient on the pre-fix bundle sends none -- but when it is sent it
        // is read positionally, so a length mismatch would silently grade one question against another's
        // selections rather than failing.
        if (multiAnswers != null && multiAnswers.size() != questions.size()) {
            throw new InvalidSharedQuizAnswersException();
        }

        Map<Integer, Integer> selectedChoices = new HashMap<>();
        Map<Integer, List<Integer>> selectedMultiChoices = new HashMap<>();
        for (int index = 0; index < questions.size(); index++) {
            Integer answer = answers.get(index);
            if (answer != null) {
                selectedChoices.put(index, answer);
            }
            List<Integer> selectedMultiChoice = normalizeSelectedIndices(
                    multiAnswers == null ? null : multiAnswers.get(index),
                    questions.get(index)
            );
            if (!selectedMultiChoice.isEmpty()) {
                selectedMultiChoices.put(index, selectedMultiChoice);
            }
        }

        List<SharedQuizResultItem> items = new ArrayList<>();
        int score = 0;
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);
            boolean correct = QuizSessionReviewUtils.isAnswerCorrect(question, index, selectedChoices, selectedMultiChoices);
            if (correct) {
                score++;
            }
            int correctIndex = question.correctIndex() == null ? -1 : question.correctIndex();
            items.add(new SharedQuizResultItem(
                    correct,
                    correctIndex,
                    resolveCorrectIndices(question),
                    question.explanation()
            ));
        }
        return new SharedQuizResultsResponse(score, questions.size(), items);
    }

    /**
     * Only MULTI_SELECT questions disclose a correct-answer set, so the review screen can read
     * "non-empty means use these" without re-deriving the format. Every other format keeps its single
     * {@code correctIndex}.
     */
    private List<Integer> resolveCorrectIndices(QuizItem question) {
        if (!question.isMultiSelect() || question.correctIndices() == null) {
            return List.of();
        }
        return List.copyOf(question.correctIndices());
    }

    /** Mirrors {@code QuizSessionStateUtils.resolveSelectedChoiceIndexes}: in-range and de-duplicated. */
    private List<Integer> normalizeSelectedIndices(List<Integer> selectedIndices, QuizItem question) {
        if (selectedIndices == null || selectedIndices.isEmpty() || question.choices() == null) {
            return List.of();
        }
        int choiceCount = question.choices().size();
        return selectedIndices.stream()
                .filter(Objects::nonNull)
                .filter(index -> index >= 0 && index < choiceCount)
                .distinct()
                .toList();
    }

    private QuizShareLinkEntity findActiveLink(String token) {
        QuizShareLinkEntity link = quizShareLinkRepository.findByToken(token)
                .orElseThrow(QuizShareLinkNotFoundException::new);
        if (!Boolean.TRUE.equals(link.getActive())) {
            throw new QuizShareLinkNotFoundException();
        }
        return link;
    }

    private SharedQuiz resolveSharedQuiz(QuizShareLinkEntity link) {
        if (link.getGeneratedQuizId() != null) {
            GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findById(link.getGeneratedQuizId())
                    .orElseThrow(QuizShareLinkNotFoundException::new);
            NoteEntity note = noteRepository.findById(generatedQuiz.getNoteId())
                    .orElseThrow(QuizShareLinkNotFoundException::new);
            return new SharedQuiz(
                    generatedQuiz.getId(),
                    resolveNoteTitle(note),
                    excludeUnanswerableFormats(generatedQuiz.getQuestions())
            );
        }
        if (link.getCombinedQuizId() != null) {
            CombinedQuizEntity combinedQuiz = combinedQuizRepository.findById(link.getCombinedQuizId())
                    .orElseThrow(QuizShareLinkNotFoundException::new);
            return new SharedQuiz(
                    combinedQuiz.getId(),
                    combinedQuiz.getTitle(),
                    excludeUnanswerableFormats(flattenCombinedQuestions(combinedQuiz))
            );
        }
        // V132's exclusive-arc check rejects this in PostgreSQL; keep public lookup fail-closed for corrupt rows.
        throw new QuizShareLinkNotFoundException();
    }

    /**
     * ⚠️ Delegates on purpose. This used to be an INDEPENDENT derivation of the same question list, and the
     * duplication was load-bearing in the worst way: {@code getActivePublicQuiz} projects what the recipient
     * SEES while {@code getSharedQuizResults} walks what the grader SCORES. Filtering only the projection
     * left the grader on a longer list, so {@code answers.size() != questions.size()} threw and every submit
     * 400'd -- the shape {@code v0.110.2} already shipped once.
     *
     * <p>⚠️ The extra {@code noteRepository.findById} that {@code resolveSharedQuiz} performs for the title
     * is an ACCEPTED cost of that guarantee. Do NOT re-split these methods to avoid one lookup; re-splitting
     * silently reintroduces the divergence.
     */
    private List<QuizItem> resolveSharedQuestions(QuizShareLinkEntity link) {
        return resolveSharedQuiz(link).questions();
    }

    /**
     * Drops question formats a shared recipient has no control to answer.
     *
     * <p>A MATCHING block is 2-4 consecutive items sharing one option set, identified by {@code questionGroup}
     * -- which {@link com.studysnap.backend.dto.PublicQuizItem} does not carry, and which the recipient page
     * has no control for. {@code teacher-quiz-developer.txt} instructs the model to emit one such block, so
     * these reach real shared quizzes and are scored as if they were independent MCQs.
     *
     * <p>⚠️ Excludes on {@code questionFormat}, never on {@code questionGroup} -- a non-matching item may
     * legitimately carry a group, and filtering on the group would drop valid questions.
     *
     * <p>⚠️ Carrying matching through instead would change the GRADING CONTRACT on a {@code permitAll} route
     * scored for an anonymous recipient. Recorded as a follow-up, not rejected on merit.
     */
    private List<QuizItem> excludeUnanswerableFormats(List<QuizItem> questions) {
        List<QuizItem> answerable = questions == null
                ? List.of()
                : questions.stream()
                        .filter(question -> !MATCHING_FORMAT.equalsIgnoreCase(question.questionFormat()))
                        .toList();
        if (answerable.isEmpty()) {
            // Fail closed rather than serve a zero-question quiz that would score 0/0. The recipient meets
            // the already-built "no longer active" screen.
            throw new QuizShareLinkNotFoundException();
        }
        return answerable;
    }

    private List<QuizItem> flattenCombinedQuestions(CombinedQuizEntity combinedQuiz) {
        return combinedQuiz.getSections() == null ? List.of() : combinedQuiz.getSections().stream()
                .filter(Objects::nonNull)
                .flatMap(section -> section.questions() == null ? java.util.stream.Stream.empty() : section.questions().stream())
                .toList();
    }

    private record SharedQuiz(UUID id, String title, List<QuizItem> questions) {
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            String token = generateToken();
            if (quizShareLinkRepository.findByToken(token).isEmpty()) {
                return token;
            }
        }
        throw new QuizShareLinkTokenGenerationException();
    }

    private String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int index = 0; index < TOKEN_LENGTH; index++) {
            token.append(BASE62_CHARS[secureRandom.nextInt(BASE62_CHARS.length)]);
        }
        return token.toString();
    }

    private QuizShareLinkResponse toResponse(QuizShareLinkEntity entity) {
        return new QuizShareLinkResponse(
                entity.getId(),
                entity.getToken(),
                buildShareUrl(entity.getToken()),
                Boolean.TRUE.equals(entity.getActive()),
                entity.getCreatedAt()
        );
    }

    private String buildShareUrl(String token) {
        String baseUrl = properties.getBilling().getFrontendBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + QUIZ_SHARE_PATH_PREFIX + token;
    }

    private String resolveNoteTitle(NoteEntity note) {
        String title = note.getTitle();
        if (title == null || title.isBlank()) {
            return DEFAULT_NOTE_TITLE;
        }
        return title.trim();
    }
}
