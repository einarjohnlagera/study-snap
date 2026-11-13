package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.OpenAiLlmConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.BillingUsagePeriodService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.LlmModelRouter;
import com.studysnap.backend.service.LongExamPlanSourceSampler;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.StudyPackGenerationTaskDispatcher;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
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
import java.util.concurrent.Executors;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Real LLM, router, resolver and production callers for preregistration sections 3 and 7. */
final class ClassAServiceHarness {
    private final Map<Class<?>, Object> collaborators = new HashMap<>();
    private final List<NoteEntity> notes = new ArrayList<>();
    private final List<StudyPackEntity> packs = new ArrayList<>();
    private final Map<UUID, QuickReviewSessionEntity> sessions = new HashMap<>();
    private ExamQuestionPoolEntity pool;
    private BatchTaggingExecutor batchExecutor;
    final UUID userId;
    final ClassACaseManifest.Case item;
    final StudySnapProperties properties;

    ClassAServiceHarness(ClassACaseManifest.Case item, RestClient client, StudySnapProperties properties) {
        this.item = item;
        this.properties = properties;
        this.userId = id(item.caseId() + ":user");
        UserEntity user = new UserEntity();
        user.setId(userId);
        ProfileType profile = switch (item.workflow()) {
            case TEACHER_QUIZ -> ProfileType.TEACHER;
            case BOARD_EXAM_DIRECT -> ProfileType.BOARD_EXAM;
            case INTERVIEW_PRACTICE -> ProfileType.PROFESSIONAL;
            default -> ProfileType.STUDENT;
        };
        user.setProfileType(profile);
        user.setLearnerLevel(item.noteLevel());
        user.setEmail("synthetic-" + userId + "@example.invalid");
        user.setFirstName("Synthetic");
        user.setUsername("bench" + userId.toString().substring(0, 12));
        user.setPublicProfileVisible(false);
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        user.setWeeklySummaryRemindersEnabled(false);
        user.setDueConceptsDigestRemindersEnabled(false);
        user.setMarketingEmailsEnabled(false);
        user.setThemePreference(ThemePreference.SYSTEM);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        user.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(mocked(UserRepository.class).findById(userId)).thenReturn(Optional.of(user));
        var resolver = new StudyPackGenerationContextResolver(mocked(UserRepository.class),
                mocked(NoteRepository.class), mocked(NoteCourseProgramRepository.class),
                mocked(CourseProgramCatalogRepository.class));
        collaborators.put(StudyPackGenerationContextResolver.class, spy(resolver));
        var llm = new OpenAiLlmStudyPackService(properties, new ObjectMapper(), client,
                new OpenAiLlmConfig().openAiPromptResources(new ObjectMapper(), properties),
                new LlmModelRouter(properties, mocked(SubscriptionService.class), mocked(UserRepository.class)));
        collaborators.put(QuizGenerationService.class, new QuizGenerationService(llm, properties));
        collaborators.put(StudyPackGenerationTaskDispatcher.class,
                new StudyPackGenerationTaskDispatcher((TaskExecutor) Runnable::run));
        collaborators.put(LongExamPlanSourceSampler.class, new LongExamPlanSourceSampler());
        collaborators.put(StudySnapProperties.class, properties);
        when(mocked(SubscriptionService.class).resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(mocked(UserUsageService.class).getMonthlyUsage(eq(userId), any()))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        when(mocked(BillingUsagePeriodService.class).resolveUsagePeriod(eq(userId), any()))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(PlanType.PRO, BillingCycle.MONTHLY,
                        OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1), 2026, 11));
        when(mocked(QuickReviewSessionRepository.class).save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> {
                    QuickReviewSessionEntity session = invocation.getArgument(0);
                    sessions.put(session.getId(), session);
                    return session;
                });
        when(mocked(NoteRepository.class).save(any(NoteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mocked(GeneratedQuizRepository.class).save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionOperations transactions = mocked(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        buildSources();
        clearInvocations(mocked(UserRepository.class));
    }

    @SuppressWarnings("unchecked")
    <T> T mocked(Class<T> type) {
        return (T) collaborators.computeIfAbsent(type, key -> mock(type));
    }

    <T> T service(Class<T> type) {
        Constructor<?> constructor = java.util.Arrays.stream(type.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = java.util.Arrays.stream(constructor.getParameterTypes())
                .map(this::mocked).toArray();
        try {
            constructor.setAccessible(true);
            return type.cast(constructor.newInstance(args));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Cannot wire production caller " + type.getSimpleName(), ex);
        }
    }

    NoteEntity note(int index) { return notes.get(index); }
    StudyPackEntity pack(int index) { return packs.get(index); }
    List<StudyPackEntity> packs() { return List.copyOf(packs); }
    QuickReviewSessionEntity session(UUID id) { return sessions.get(id); }
    List<QuickReviewSessionEntity> savedSessions() { return List.copyOf(sessions.values()); }
    BatchTaggingExecutor batchExecutor() { return batchExecutor; }

    void installBatchExecutor(int workers) {
        batchExecutor = new BatchTaggingExecutor(item.caseId(), Executors.newFixedThreadPool(workers));
        collaborators.put(AsyncTaskExecutor.class, batchExecutor);
    }

    void prepareLongExamDirect() {
        prepareBoardExam();
        for (StudyPackEntity pack : packs) {
            when(mocked(NoteRepository.class).findById(pack.getNoteId()))
                    .thenReturn(Optional.of(notes.stream().filter(note -> note.getId().equals(pack.getNoteId()))
                            .findFirst().orElseThrow()));
        }
    }

    UUID preparePool(String mode) {
        StudyPackEntity pack = pack(0);
        pool = new ExamQuestionPoolEntity();
        pool.setId(id(item.caseId() + ":pool:" + mode));
        pool.setStudyPackId(pack.getId());
        pool.setMode(mode);
        pool.setQuestions(List.of());
        pool.setPoolSize(0);
        pool.setGenerationStatus("PENDING");
        pool.setGenerationStatusAt(OffsetDateTime.now(ZoneOffset.UTC));
        pool.setServedQuestionKeys(List.of());
        pool.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(mocked(ExamQuestionPoolRepository.class).findByIdForUpdate(pool.getId()))
                .thenAnswer(invocation -> Optional.of(pool));
        when(mocked(ExamQuestionPoolRepository.class).save(any(ExamQuestionPoolEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mocked(StudyPackRepository.class).findById(pack.getId())).thenReturn(Optional.of(pack));
        return pool.getId();
    }

    List<QuizItem> persistedPoolQuestions(UUID poolId) {
        return mocked(ExamQuestionPoolRepository.class).findByIdForUpdate(poolId)
                .orElseThrow().getQuestions();
    }

    void prepareNoteAdaptive() {
        StudyPackEntity pack = pack(0);
        QuickReviewSessionEntity completed = new QuickReviewSessionEntity();
        completed.setId(id(item.caseId() + ":completed-review"));
        completed.setUserId(userId);
        completed.setStudyPackId(pack.getId());
        completed.setNoteId(pack.getNoteId());
        completed.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        completed.setStatus(QuickReviewSessionStatus.COMPLETED);
        completed.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        completed.setSessionMetadata(Map.of("weakConcepts", List.of(pack.getKeyConcepts().getFirst())));
        when(mocked(QuickReviewSessionRepository.class)
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        eq(userId), eq(pack.getId()), eq(QuickReviewSessionMode.QUICK_REVIEW), any()))
                .thenReturn(List.of(completed));
        when(mocked(ConceptHealthService.class).getDueConcepts(eq(userId), eq(pack.getId()), any(), any()))
                .thenReturn(List.of());
    }

    UUID preparePlanAdaptive() {
        UUID collectionId = id(item.caseId() + ":collection");
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(userId);
        collection.setTitle("Synthetic plan");
        when(mocked(NoteCollectionRepository.class).findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.of(collection));
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        List<StudyPackProgressProjection> projections = new ArrayList<>();
        for (int index = 0; index < packs.size(); index++) {
            StudyPackEntity pack = pack(index);
            NoteCollectionItemEntity itemRow = new NoteCollectionItemEntity();
            itemRow.setNoteId(pack.getNoteId());
            itemRow.setPosition(index);
            items.add(itemRow);
            StudyPackProgressProjection projection = mock(StudyPackProgressProjection.class);
            when(projection.getId()).thenReturn(pack.getId());
            when(projection.getNoteId()).thenReturn(pack.getNoteId());
            when(projection.getOwnerUserId()).thenReturn(userId);
            when(projection.getKeyConcepts()).thenReturn(pack.getKeyConcepts());
            when(projection.getStatus()).thenReturn(StudyPackStatus.DONE);
            projections.add(projection);
        }
        when(mocked(NoteCollectionItemRepository.class).findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(items);
        when(mocked(StudyPackRepository.class).findProgressViewsByNoteIdIn(any()))
                .thenReturn(projections);
        when(mocked(StudyPackRepository.class).findByOwnerUserIdAndNoteIdInAndStatus(
                eq(userId), any(), eq(StudyPackStatus.DONE)))
                .thenAnswer(invocation -> {
                    List<UUID> requested = invocation.getArgument(1);
                    return packs.stream().filter(pack -> requested.contains(pack.getNoteId())).toList();
                });
        Map<UUID, List<String>> due = new HashMap<>();
        for (StudyPackEntity pack : packs) due.put(pack.getId(), List.of(pack.getKeyConcepts().getFirst()));
        when(mocked(ConceptHealthService.class).getDueConceptsByStudyPackIds(eq(userId), any(), any()))
                .thenReturn(due);
        return collectionId;
    }

    void prepareBoardExam() {
        QuickReviewSessionRepository repository = mocked(QuickReviewSessionRepository.class);
        when(repository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(repository.findByIdForUpdate(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(repository.findStatusById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0)))
                        .map(QuickReviewSessionEntity::getStatus));
    }

    private void buildSources() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int index = 0; index < item.sources().size(); index++) {
            ClassACaseManifest.Source source = item.sources().get(index);
            NoteEntity note = new NoteEntity();
            note.setId(id(item.caseId() + ":note:" + index));
            note.setOwnerUserId(userId);
            note.setTitle(source.title());
            note.setContent(source.noteText());
            note.setSubject(source.subject());
            note.setCourseProgram(source.courseProgram());
            note.setDomainContext(source.domainContext());
            note.setLearnerLevel(source.noteLevel());
            note.setTags(source.tags() == null ? new String[0] : source.tags().toArray(String[]::new));
            note.setStatus(NoteStatus.DRAFT);
            note.setVisibility(NoteVisibility.PRIVATE);
            note.setTargetProfileType(NoteTargetProfileType.forOwnerProfile(
                    switch (item.workflow()) {
                        case BOARD_EXAM_DIRECT -> ProfileType.BOARD_EXAM;
                        case INTERVIEW_PRACTICE -> ProfileType.PROFESSIONAL;
                        case TEACHER_QUIZ -> ProfileType.TEACHER;
                        default -> ProfileType.STUDENT;
                    }));
            note.setCreatedAt(now.minusDays(1));
            note.setUpdatedAt(now.minusDays(1));
            notes.add(note);
            StudyPackEntity pack = new StudyPackEntity();
            pack.setId(id(item.caseId() + ":pack:" + index));
            pack.setNoteId(note.getId());
            pack.setOwnerUserId(userId);
            pack.setInputType(InputType.TEXT);
            pack.setTitle(source.title());
            pack.setSummary(source.summary());
            pack.setSubject(source.subject());
            pack.setTags(note.getTags());
            pack.setKeyConcepts(source.keyConcepts());
            pack.setQuiz(List.of(new QuizItem("Existing synthetic question?",
                    List.of("Correct", "Wrong B", "Wrong C", "Wrong D"), 0,
                    source.keyConcepts().getFirst(), "Existing synthetic explanation.")));
            pack.setStatus(StudyPackStatus.DONE);
            pack.setModelTier(ModelTier.FREE);
            pack.setModelUsed("stub");
            pack.setCreatedAt(now.minusDays(1));
            pack.setUpdatedAt(now.minusDays(1));
            packs.add(pack);
            when(mocked(NoteRepository.class).findByIdAndOwnerUserId(note.getId(), userId))
                    .thenReturn(Optional.of(note));
            when(mocked(StudyPackRepository.class).findByIdAndOwnerUserIdForUpdate(pack.getId(), userId))
                    .thenReturn(Optional.of(pack));
            when(mocked(StudyPackRepository.class).findByOwnerUserIdAndNoteIdForUpdate(userId, note.getId()))
                    .thenReturn(Optional.of(pack));
        }
    }

    private static UUID id(String text) {
        return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
