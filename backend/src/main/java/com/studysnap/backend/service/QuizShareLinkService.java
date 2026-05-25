package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.PublicQuizItem;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.dto.SharedQuizResultItem;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GeneratedQuizNotFoundException;
import com.studysnap.backend.exception.InvalidSharedQuizAnswersException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.QuizShareLinkNotAllowedException;
import com.studysnap.backend.exception.QuizShareLinkNotFoundException;
import com.studysnap.backend.exception.QuizShareLinkTokenGenerationException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizShareLinkService {
    private static final char[] BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int TOKEN_LENGTH = 16;
    private static final int MAX_TOKEN_ATTEMPTS = 10;
    private static final String QUIZ_SHARE_PATH_PREFIX = "/quiz/";
    private static final String DEFAULT_NOTE_TITLE = "Shared quiz";

    private final QuizShareLinkRepository quizShareLinkRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final QuizShareLimitService quizShareLimitService;
    private final UserUsageService userUsageService;
    private final AuthService authService;
    private final StudySnapProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public QuizShareLinkResponse createShareLink(UUID generatedQuizId, UUID ownerUserId) {
        authService.requireEmailVerified(ownerUserId);
        requireTeacherOrAdmin(ownerUserId);
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

    @Transactional(readOnly = true)
    public QuizShareLinkResponse getShareLinkByQuizId(UUID generatedQuizId, UUID ownerUserId) {
        requireTeacherOrAdmin(ownerUserId);
        return quizShareLinkRepository
                .findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuizId, ownerUserId)
                .map(this::toResponse)
                .orElseThrow(QuizShareLinkNotFoundException::new);
    }

    public QuizShareLinkResponse toggleShareLink(String token, UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        requireTeacherOrAdmin(callerUserId);
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
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findById(link.getGeneratedQuizId())
                .orElseThrow(QuizShareLinkNotFoundException::new);
        NoteEntity note = noteRepository.findById(generatedQuiz.getNoteId())
                .orElseThrow(NoteNotFoundException::new);
        List<PublicQuizItem> questions = generatedQuiz.getQuestions().stream()
                .map(question -> new PublicQuizItem(
                        question.question(),
                        question.choices(),
                        question.concept()
                ))
                .toList();
        return new PublicSharedQuizResponse(
                generatedQuiz.getId(),
                resolveNoteTitle(note),
                questions
        );
    }

    @Transactional(readOnly = true)
    public SharedQuizResultsResponse getSharedQuizResults(String token, List<Integer> answers) {
        QuizShareLinkEntity link = findActiveLink(token);
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findById(link.getGeneratedQuizId())
                .orElseThrow(QuizShareLinkNotFoundException::new);
        List<QuizItem> questions = generatedQuiz.getQuestions();
        if (answers == null || answers.size() != questions.size()) {
            throw new InvalidSharedQuizAnswersException();
        }
        List<SharedQuizResultItem> items = new ArrayList<>();
        int score = 0;
        for (int index = 0; index < questions.size(); index++) {
            QuizItem question = questions.get(index);
            int correctIndex = question.correctIndex() == null ? -1 : question.correctIndex();
            Integer answer = answers != null && index < answers.size() ? answers.get(index) : null;
            boolean correct = answer != null && answer == correctIndex;
            if (correct) {
                score++;
            }
            items.add(new SharedQuizResultItem(correct, correctIndex, question.explanation()));
        }
        return new SharedQuizResultsResponse(score, questions.size(), items);
    }

    private QuizShareLinkEntity findActiveLink(String token) {
        QuizShareLinkEntity link = quizShareLinkRepository.findByToken(token)
                .orElseThrow(QuizShareLinkNotFoundException::new);
        if (!Boolean.TRUE.equals(link.getActive())) {
            throw new QuizShareLinkNotFoundException();
        }
        return link;
    }

    private UserEntity requireTeacherOrAdmin(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getProfileType() == ProfileType.TEACHER || user.getRole() == UserRole.ADMIN) {
            return user;
        }
        throw new QuizShareLinkNotAllowedException();
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
