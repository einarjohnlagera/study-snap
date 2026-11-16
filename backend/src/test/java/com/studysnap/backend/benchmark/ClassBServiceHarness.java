package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.*;
import com.studysnap.backend.repository.*;
import com.studysnap.backend.service.*;
import com.studysnap.backend.service.impl.OpenAiAskCompanionLlmService;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Production collaborator fixtures for preregistration sections 3 and 6, Revision 2.2. */
final class ClassBServiceHarness {
    private static final String SESSION_SUFFIX = ":session";
    private final Map<Class<?>, Object> collaborators = new HashMap<>();
    final UUID userId;
    final UUID sourceId;
    final UserEntity user;

    ClassBServiceHarness(ClassBCaseManifest.Case item, RestClient client, StudySnapProperties properties) {
        userId = id(item.fixtureId() + ":user");
        sourceId = id(item.fixtureId() + ":source");
        user = new UserEntity();
        user.setId(userId);
        user.setRole(item.workflow() == ClassBCaseManifest.Workflow.COLLECTION_COMPANION
                ? UserRole.ADMIN : UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        user.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(mocked(UserRepository.class).findById(userId)).thenReturn(Optional.of(user));
        var prompts = new OpenAiLlmConfig().openAiPromptResources(new ObjectMapper(), properties);
        var llm = new OpenAiLlmStudyPackService(properties, new ObjectMapper(), client, prompts,
                new LlmModelRouter(properties, mocked(SubscriptionService.class), mocked(UserRepository.class)));
        collaborators.put(LlmStudyPackService.class, llm);
        collaborators.put(QuizGenerationService.class, new QuizGenerationService(llm, properties));
        collaborators.put(AskCompanionLlmService.class,
                new OpenAiAskCompanionLlmService(properties, new ObjectMapper(), client, prompts));
        collaborators.put(StudySnapProperties.class, properties);
        TransactionOperations transactions = mocked(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        // answerQuestion has two transaction phases; the same real session object is re-fetched in each.
        collaborators.put(EntityManager.class, mocked(EntityManager.class));
    }

    static UUID id(String text) {
        return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    <T> T mocked(Class<T> type) {
        return (T) collaborators.computeIfAbsent(type, ignored -> mock(type));
    }

    <T> T service(Class<T> type) {
        Constructor<?> constructor = java.util.Arrays.stream(type.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] arguments = java.util.Arrays.stream(constructor.getParameterTypes())
                .map(this::mocked).toArray();
        try {
            constructor.setAccessible(true);
            return type.cast(constructor.newInstance(arguments));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Cannot wire production caller " + type.getSimpleName(), ex);
        }
    }

    void studyPack() {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(sourceId);
        pack.setOwnerUserId(userId);
        when(mocked(StudyPackRepository.class).findByIdAndOwnerUserId(sourceId, userId))
                .thenReturn(Optional.of(pack));
    }

    void collection(CompanionGenerationContext context) {
        NoteCollectionEntity root = new NoteCollectionEntity();
        root.setId(sourceId);
        root.setOwnerUserId(userId);
        root.setTitle(context.title());
        root.setDescription(context.description());
        root.setCourseProgram(context.courseProgram());
        when(mocked(NoteCollectionRepository.class).findByIdAndOwnerUserId(sourceId, userId))
                .thenReturn(Optional.of(root));
        List<NoteCollectionEntity> children = new ArrayList<>();
        for (int index = 0; index < context.subjectPlans().size(); index++) {
            var child = context.subjectPlans().get(index);
            NoteCollectionEntity row = new NoteCollectionEntity();
            row.setId(id(sourceId + ":child:" + index));
            row.setOwnerUserId(userId);
            row.setParentCollectionId(sourceId);
            row.setSiblingPosition(index);
            row.setTitle(child.title());
            row.setDescription(child.description());
            children.add(row);
        }
        when(mocked(NoteCollectionRepository.class)
                .findOrderedChildrenByParentCollectionIdAndOwnerUserId(sourceId, userId))
                .thenReturn(children);
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        List<NoteCollectionNoteProjection> notes = new ArrayList<>();
        for (int index = 0; index < context.notes().size(); index++) {
            var note = context.notes().get(index);
            UUID noteId = id(sourceId + ":note:" + index);
            NoteCollectionItemEntity row = new NoteCollectionItemEntity();
            row.setCollectionId(sourceId);
            row.setNoteId(noteId);
            row.setPosition(index);
            items.add(row);
            notes.add(new NoteCollectionNoteProjection(noteId, note.title(), note.description(), null,
                    null, null, null, null, OffsetDateTime.now(ZoneOffset.UTC)));
        }
        when(mocked(NoteCollectionItemRepository.class).findByCollectionIdOrderByPositionAsc(sourceId))
                .thenReturn(items);
        when(mocked(NoteRepository.class).findCollectionNoteProjectionsByIdIn(any())).thenReturn(notes);
    }

    AskCompanionSessionEntity askSession(ClassBCaseManifest.Case item) {
        collectionForAsk(item);
        AskCompanionSessionEntity session = new AskCompanionSessionEntity();
        session.setId(id(item.fixtureId() + SESSION_SUFFIX));
        session.setUserId(userId);
        session.setCollectionId(sourceId);
        session.setStatus(AskCompanionSessionStatus.ACTIVE);
        session.setTurnCount(item.priorTurns().size());
        session.setTurns(new ArrayList<>(item.priorTurns()));
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(mocked(AskCompanionSessionRepository.class).findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(mocked(AskCompanionSessionRepository.class).save(any(AskCompanionSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mocked(SubscriptionService.class).resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(mocked(UserUsageService.class).getMonthlyUsage(any(), any()))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        return session;
    }

    private void collectionForAsk(ClassBCaseManifest.Case item) {
        NoteCollectionEntity root = new NoteCollectionEntity();
        root.setId(sourceId);
        root.setOwnerUserId(userId);
        root.setTitle("Synthetic companion collection");
        root.setCompanion(item.companion());
        when(mocked(NoteCollectionRepository.class).findByIdAndOwnerUserId(sourceId, userId))
                .thenReturn(Optional.of(root));
    }

    QuickReviewSessionEntity interviewSession(ClassBCaseManifest.Case item) {
        ClassBCaseManifest.Question source = item.interviewQuestion();
        QuizItem question = new QuizItem(source.question(), source.choices(), source.correctIndex(),
                source.concept(), source.explanation());
        List<QuizItem> quiz = new ArrayList<>();
        quiz.add(question);
        for (int index = 1; index < 5; index++) {
            quiz.add(new QuizItem(source.question() + " (scenario " + (index + 1) + ")",
                    source.choices(), source.correctIndex(), source.concept(), source.explanation()));
        }
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(id(item.fixtureId() + SESSION_SUFFIX));
        session.setUserId(userId);
        session.setNoteId(sourceId);
        session.setStudyPackId(id(item.fixtureId() + ":pack"));
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(quiz.size());
        session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(quiz, "INTERVIEW", 120));
        when(mocked(QuickReviewSessionRepository.class).findByIdAndUserIdAndSessionModeForUpdate(
                session.getId(), userId, QuickReviewSessionMode.ADAPTIVE)).thenReturn(Optional.of(session));
        when(mocked(QuickReviewSessionRepository.class).save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return session;
    }
}
