package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.FacetCount;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.NotesLibraryFilterOptionsResponse;
import com.studysnap.backend.dto.NotesLibraryIdsResponse;
import com.studysnap.backend.dto.NotesLibraryPageResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicLibraryDiscoverySectionsResponse;
import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.SubjectFacetCount;
import com.studysnap.backend.dto.SubjectStatsResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.PublicNoteLikeEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidLibraryQueryException;
import com.studysnap.backend.exception.InvalidPublicLibraryQueryException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.model.NoteListItemView;
import com.studysnap.backend.model.NoteLibraryReadiness;
import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.PublicLibrarySort;
import com.studysnap.backend.model.PublicLibrarySource;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteLibraryCandidateProjection;
import com.studysnap.backend.repository.NoteLibraryFilterCriteria;
import com.studysnap.backend.repository.NoteLibrarySubjectIdProjection;
import com.studysnap.backend.repository.NoteLibrarySubjectProjection;
import com.studysnap.backend.repository.NoteLibrarySubjectView;
import com.studysnap.backend.repository.NoteLibraryValueCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteStatusProjection;
import com.studysnap.backend.repository.PublicNoteLikeCountProjection;
import com.studysnap.backend.repository.PublicLibraryCandidateProjection;
import com.studysnap.backend.repository.PublicLibraryFilterCriteria;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.ContentPreviewUtils;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import com.studysnap.backend.util.PublicNotesScoringUtils;
import com.studysnap.backend.util.SubjectNormalizationUtils;
import com.studysnap.backend.util.SummaryPreviewUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final int SUMMARY_PREVIEW_MAX_LENGTH = 180;
    private static final String DEFAULT_PUBLIC_SUBJECT_SLUG = "general";
    private static final String DEFAULT_PUBLIC_TITLE_SLUG = "untitled-note";
    private static final String DEFAULT_AUTHOR_NAME = "Anonymous learner";
    private static final String OFFICIAL_AUTHOR_DISPLAY_NAME = "NoteLib";
    private static final String NOTE_TARGET_PROFILE_TYPE_REQUIRED_CODE = "NOTE_TARGET_PROFILE_TYPE_REQUIRED";
    private static final String NOTE_TARGET_PROFILE_TYPE_INVALID_CODE = "NOTE_TARGET_PROFILE_TYPE_INVALID";
    private static final String NOTE_TARGET_PROFILE_TYPE_REQUIRED_MESSAGE = "Please choose who this note is for.";
    private static final String NOTE_TARGET_PROFILE_TYPE_INVALID_MESSAGE = "Please choose Student, Exam Reviewer, or Professional for this note.";
    private static final String PUBLIC_SORT_FEATURED = "featured";
    private static final String PUBLIC_SORT_POPULAR = "popular";
    private static final String PUBLIC_SORT_RECENT = "recent";
    private static final String PUBLIC_SORT_COPIED = "copied";
    private static final String PUBLIC_SORT_VIEWS = "views";
    private static final String PUBLIC_SORT_TITLE = "title";
    private static final String PUBLIC_SORT_MOST_COPIED = "most_copied";
    private static final String PUBLIC_SORT_RECOMMENDED = "recommended";
    private static final String SORT_PARAMETER_NAME = "sort";
    private static final String ANALYTICS_METADATA_PREVIOUS_VISIBILITY = "previousVisibility";
    private static final String ANALYTICS_METADATA_NEW_VISIBILITY = "newVisibility";
    private static final String PUBLIC_RANKING_PLACEHOLDER = "available";
    private static final int PUBLIC_DISCOVERY_SECTION_LIMIT = 6;
    private static final String LIBRARY_SUBJECT_FALLBACK = "General";
    private static final String LIBRARY_UNTITLED_NOTE = "Untitled note";
    private static final OffsetDateTime LIBRARY_UNREVIEWED_AT = OffsetDateTime.ofInstant(
            Instant.EPOCH,
            ZoneOffset.UTC
    );
    private static final int LIBRARY_SUBJECT_FACET_LIMIT = 6;
    private static final int MAX_LIBRARY_SELECT_ALL_RESULTS = 1000;
    private static final Comparator<String> COURSE_PROGRAM_DISPLAY_COMPARATOR = (left, right) -> {
        int caseInsensitive = left.compareToIgnoreCase(right);
        return caseInsensitive != 0 ? caseInsensitive : left.compareTo(right);
    };
    private static final Comparator<String> SUBJECT_DISPLAY_COMPARATOR = (left, right) -> {
        int caseInsensitive = left.compareToIgnoreCase(right);
        return caseInsensitive != 0 ? caseInsensitive : left.compareTo(right);
    };

    private final NoteRepository noteRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final PublicNoteLikeRepository publicNoteLikeRepository;
    private final StudyPackRepository studyPackRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final UserRepository userRepository;
    private final QuizSessionHistoryService quizSessionHistoryService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final AnalyticsService analyticsService;
    private final ContentModerationService contentModerationService;
    private final OnboardingGuardService onboardingGuardService;
    private final OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    private final NoteApplicableProgramsMaintenanceService noteApplicableProgramsMaintenanceService;
    private final NoteCourseProgramRepository noteCourseProgramRepository;

    public NoteResponse create(UpsertNoteRequest request, UUID ownerUserId) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        UserEntity owner = getOwnerOrThrow(ownerUserId);
        NoteEntity entity = new NoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(resolveCanonicalSubject(request.subject()));
        entity.setCourseProgram(resolveRequestedCourseProgram(request.courseProgram(), owner));
        entity.setDomainContext(NoteAuthoringMetadataParser.parseDomainContextOrThrow(request.domainContext()));
        entity.setLearnerLevel(NoteAuthoringMetadataParser.parseLearnerLevelOrThrow(request.learnerLevel()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setContent(normalizeRequiredContent(request.content()));
        entity.setStatus(NoteStatus.DRAFT);
        entity.setVisibility(NoteVisibility.PRIVATE);
        entity.setTargetProfileType(resolveTargetProfileType(request.targetProfileType(), owner));
        entity.setSourceNoteId(null);
        entity.setCopiedFromNoteId(null);
        entity.setCopiedFromUserId(null);
        entity.setCopiedFromTitle(null);
        entity.setCopiedFromPublic(Boolean.FALSE);
        entity.setCopiedAt(null);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        NoteEntity saved = noteRepository.save(entity);
        noteRepository.flush();
        noteApplicableProgramsMaintenanceService.seedDerivedSet(saved.getId(), saved.getCourseProgram());
        analyticsService.trackEvent(ownerUserId, AnalyticsEventType.NOTE_CREATED, saved.getId(), buildMetadata(
                "subject", saved.getSubject(),
                "visibility", resolveVisibility(saved).name()
        ));
        return mapToResponse(saved, null);
    }

    public NoteResponse update(String id, UpsertNoteRequest request, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);
        UserEntity owner = getOwnerOrThrow(ownerUserId);
        String previousCourseProgram = entity.getCourseProgram();
        boolean hasDerivedApplicablePrograms = noteApplicableProgramsMaintenanceService.isDerivedSet(
                entity.getId(),
                previousCourseProgram
        );
        String normalizedRequestedContent = normalizeRequiredContent(request.content());
        DomainContext domainContext = NoteAuthoringMetadataParser.parseDomainContextOrThrow(request.domainContext());
        LearnerLevel learnerLevel = NoteAuthoringMetadataParser.parseLearnerLevelOrThrow(request.learnerLevel());

        entity.setContent(normalizedRequestedContent);
        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(resolveCanonicalSubject(request.subject()));
        entity.setCourseProgram(normalizeOptionalCourseProgram(request.courseProgram()));
        entity.setDomainContext(domainContext);
        entity.setLearnerLevel(learnerLevel);
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setTargetProfileType(resolveTargetProfileTypeForUpdate(
                request.targetProfileType(),
                owner,
                entity.getTargetProfileType()
        ));
        entity.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(entity);
        noteRepository.flush();
        if (hasDerivedApplicablePrograms) {
            noteApplicableProgramsMaintenanceService.replaceWithDerivedSet(saved.getId(), saved.getCourseProgram());
        }
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(saved.getId());
        if (linkedStudyPack != null && !Objects.equals(linkedStudyPack.getSubject(), saved.getSubject())) {
            linkedStudyPack.setSubject(saved.getSubject());
            studyPackRepository.save(linkedStudyPack);
        }
        return mapToResponse(saved, linkedStudyPack);
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(entity.getId());
        return mapToResponse(entity, linkedStudyPack);
    }

    @Transactional(readOnly = true)
    public String getOwnedStudyPackIdOrThrow(String noteIdRaw, UUID ownerUserId) {
        NoteResponse note = getById(noteIdRaw, ownerUserId);
        if (note.studyPackId() == null || NoteStudyPackStatusResolver.DRAFT.equals(note.studyPackStatus())) {
            throw new AppException(
                    "NOTE_STUDY_PACK_NOT_READY",
                    "Generate a Study Pack for this note first.",
                    HttpStatus.CONFLICT
            );
        }
        return note.studyPackId();
    }

    public NoteResponse copyNote(String id, UUID ownerUserId) {
        return copyNote(id, ownerUserId, true);
    }

    public NoteResponse copyNote(String id, UUID ownerUserId, boolean includeStudyPack) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity source = noteRepository.findById(noteId)
                .orElseThrow(NoteNotFoundException::new);
        boolean isOwner = source.getOwnerUserId() != null && source.getOwnerUserId().equals(ownerUserId);
        if (!isOwner && resolveVisibility(source) != NoteVisibility.PUBLIC) {
            throw new NoteNotFoundException();
        }
        if (!isOwner) {
            Optional<NoteEntity> existingCopy = noteRepository
                    .findByOwnerUserIdAndCopiedFromNoteIdAndCopiedFromPublicTrue(ownerUserId, source.getId());
            if (existingCopy.isPresent()) {
                NoteEntity existing = existingCopy.get();
                StudyPackEntity existingStudyPack = findLinkedStudyPack(existing.getId());
                if (existingStudyPack == null && includeStudyPack) {
                    StudyPackEntity sourceStudyPack = studyPackRepository.findByNoteId(source.getId()).orElse(null);
                    if (sourceStudyPack != null) {
                        existingStudyPack = copySourceStudyPack(sourceStudyPack, existing);
                        existing.setStatus(NoteStatus.GENERATED);
                        existing.setUpdatedAt(OffsetDateTime.now());
                        existing = noteRepository.save(existing);
                    }
                }
                return mapToResponse(existing, existingStudyPack);
            }
        }

        NoteEntity copy = new NoteEntity();
        copy.setId(UUID.randomUUID());
        copy.setOwnerUserId(ownerUserId);
        copy.setTitle(source.getTitle());
        copy.setSubject(resolveCanonicalSubject(source.getSubject()));
        copy.setCourseProgram(normalizeOptionalCourseProgram(source.getCourseProgram()));
        copy.setDomainContext(source.getDomainContext());
        copy.setLearnerLevel(source.getLearnerLevel());
        copy.setTags(source.getTags() == null ? new String[0] : Arrays.copyOf(source.getTags(), source.getTags().length));
        copy.setContent(source.getContent());
        copy.setStatus(NoteStatus.DRAFT);
        copy.setVisibility(NoteVisibility.PRIVATE);
        copy.setTargetProfileType(resolveTargetProfileType(source));
        copy.setSourceNoteId(source.getId());
        StudyPackEntity sourceStudyPack = null;
        if (isOwner) {
            copy.setCopiedFromNoteId(null);
            copy.setCopiedFromUserId(null);
            copy.setCopiedFromTitle(null);
            copy.setCopiedFromPublic(Boolean.FALSE);
            copy.setCopiedAt(null);
        } else {
            copy.setCopiedFromNoteId(source.getId());
            copy.setCopiedFromUserId(source.getOwnerUserId());
            copy.setCopiedFromTitle(source.getTitle());
            copy.setCopiedFromPublic(Boolean.TRUE);
            copy.setCopiedAt(OffsetDateTime.now());
            if (includeStudyPack) {
                sourceStudyPack = studyPackRepository.findByNoteId(source.getId()).orElse(null);
                if (sourceStudyPack != null) {
                    copy.setStatus(NoteStatus.GENERATED);
                }
            }
        }
        copy.setCreatedAt(OffsetDateTime.now());
        copy.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(copy);
        StudyPackEntity copiedStudyPack = null;
        if (!isOwner) {
            copiedStudyPack = copySourceStudyPack(sourceStudyPack, saved);
            analyticsService.trackEvent(ownerUserId, AnalyticsEventType.PUBLIC_NOTE_COPIED, source.getId(), buildMetadata(
                    "copiedNoteId", saved.getId().toString(),
                    "sourceOwnerUserId", source.getOwnerUserId() == null ? null : source.getOwnerUserId().toString()
            ));
        }
        return mapToResponse(saved, copiedStudyPack);
    }

    private StudyPackEntity copySourceStudyPack(StudyPackEntity sourceStudyPack, NoteEntity copy) {
        if (sourceStudyPack == null) {
            return null;
        }

        StudyPackEntity copiedStudyPack = new StudyPackEntity();
        copiedStudyPack.setId(UUID.randomUUID());
        copiedStudyPack.setOwnerUserId(copy.getOwnerUserId());
        copiedStudyPack.setNoteId(copy.getId());
        copiedStudyPack.setInputType(sourceStudyPack.getInputType());
        copiedStudyPack.setTitle(sourceStudyPack.getTitle());
        copiedStudyPack.setSummary(sourceStudyPack.getSummary());
        copiedStudyPack.setSubject(sourceStudyPack.getSubject());
        copiedStudyPack.setKeyConcepts(sourceStudyPack.getKeyConcepts() == null
                ? null
                : new ArrayList<>(sourceStudyPack.getKeyConcepts()));
        copiedStudyPack.setQuiz(sourceStudyPack.getQuiz() == null
                ? null
                : new ArrayList<>(sourceStudyPack.getQuiz()));
        copiedStudyPack.setTags(sourceStudyPack.getTags() == null
                ? new String[0]
                : Arrays.copyOf(sourceStudyPack.getTags(), sourceStudyPack.getTags().length));
        copiedStudyPack.setModelTier(sourceStudyPack.getModelTier());
        copiedStudyPack.setModelUsed(sourceStudyPack.getModelUsed());
        copiedStudyPack.setStatus(StudyPackStatus.DONE);
        OffsetDateTime now = OffsetDateTime.now();
        copiedStudyPack.setCreatedAt(now);
        copiedStudyPack.setUpdatedAt(now);
        return studyPackRepository.save(copiedStudyPack);
    }

    public NoteResponse copyPublicNoteForSignup(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity source = noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)
                .orElseThrow(NoteNotFoundException::new);
        if (isCurrentUser(source.getOwnerUserId(), ownerUserId)) {
            StudyPackEntity linkedStudyPack = findLinkedStudyPack(source.getId());
            return mapToResponse(source, linkedStudyPack);
        }
        return copyNote(id, ownerUserId);
    }

    public PublicNoteLikeResponse togglePublicNoteLike(String id, UUID userId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)
                .orElseThrow(NoteNotFoundException::new);

        Optional<PublicNoteLikeEntity> existingLike = publicNoteLikeRepository.findByNoteIdAndUserId(noteId, userId);
        if (existingLike.isPresent()) {
            publicNoteLikeRepository.delete(existingLike.get());
            return new PublicNoteLikeResponse(false, countLikes(noteId));
        }

        PublicNoteLikeEntity like = new PublicNoteLikeEntity();
        like.setId(UUID.randomUUID());
        like.setNoteId(noteId);
        like.setUserId(userId);
        like.setCreatedAt(OffsetDateTime.now());
        publicNoteLikeRepository.save(like);
        return new PublicNoteLikeResponse(true, countLikes(noteId));
    }

    public void deleteById(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);

        studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)
                .ifPresent(studyPackRepository::delete);
        noteRepository.delete(entity);
    }

    public NoteResponse updateVisibility(String id, String visibilityRaw, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);

        NoteVisibility visibility = parseVisibility(visibilityRaw);
        NoteVisibility previousVisibility = entity.getVisibility();
        entity.setVisibility(visibility);
        entity.setUpdatedAt(OffsetDateTime.now());
        NoteEntity saved = noteRepository.save(entity);
        if (previousVisibility != NoteVisibility.PUBLIC && visibility == NoteVisibility.PUBLIC) {
            analyticsService.trackEvent(
                    ownerUserId,
                    AnalyticsEventType.PUBLIC_NOTE_PUBLISHED,
                    noteId,
                    buildMetadata(
                            ANALYTICS_METADATA_PREVIOUS_VISIBILITY,
                            previousVisibility == null ? null : previousVisibility.name(),
                            ANALYTICS_METADATA_NEW_VISIBILITY,
                            visibility.name()
                    )
            );
        }
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(saved.getId());
        officialChallengeQuizTemplateService.queueSeedIfEligible(saved, linkedStudyPack);
        return mapToResponse(saved, linkedStudyPack);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listMine(UUID ownerUserId) {
        return listMine(ownerUserId, null);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listMine(UUID ownerUserId, Integer limit) {
        Pageable pageable = limit == null ? Pageable.unpaged() : PageRequest.of(0, limit);
        List<? extends NoteListItemView> notes = noteRepository
                .findListItemProjectionsByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId, pageable);
        return toListItems(notes, ownerUserId, true);
    }

    @Transactional(readOnly = true)
    public NotesLibraryPageResponse listLibraryPage(
            UUID ownerUserId,
            String search,
            String readiness,
            String courseProgram,
            String subject,
            List<String> tags,
            String visibility,
            String sort,
            int page,
            int pageSize
    ) {
        NoteLibraryFilterCriteria criteria = buildLibraryFilterCriteria(
                ownerUserId, search, readiness, courseProgram, tags, visibility
        );
        NoteLibrarySort librarySort = parseLibrarySort(sort);
        String subjectBucket = normalizeOptionalLibrarySubject(subject);
        boolean materialize = librarySort == NoteLibrarySort.RECENTLY_REVIEWED || subjectBucket != null;

        long totalMatching;
        List<NoteListItemProjection> pageProjections;
        long offset = (long) page * pageSize;
        Map<UUID, OffsetDateTime> lastReviewedAtByNoteId = Map.of();
        boolean lastReviewedAtPrecomputed = false;
        if (materialize) {
            List<NoteLibraryCandidateProjection> candidates = filterLibrarySubjectCandidates(
                    noteRepository.findLibraryCandidates(criteria),
                    subjectBucket
            );
            totalMatching = candidates.size();
            if (librarySort == NoteLibrarySort.RECENTLY_REVIEWED) {
                lastReviewedAtByNoteId = quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(
                        ownerUserId,
                        candidates.stream().map(NoteLibraryCandidateProjection::id).toList()
                );
                lastReviewedAtPrecomputed = true;
            }
            List<UUID> pageIds = candidates.stream()
                    .sorted(libraryCandidateComparator(librarySort, lastReviewedAtByNoteId))
                    .skip(offset)
                    .limit(pageSize)
                    .map(NoteLibraryCandidateProjection::id)
                    .toList();
            pageProjections = orderListItemProjections(
                    noteRepository.findLibraryListItemProjectionsByOwnerUserIdAndIdIn(ownerUserId, pageIds),
                    pageIds
            );
        } else {
            totalMatching = noteRepository.countLibraryMatches(criteria);
            pageProjections = offset > Integer.MAX_VALUE
                    ? List.of()
                    : noteRepository.findLibraryPage(criteria, librarySort, (int) offset, pageSize);
        }

        // findLatestSessionCompletedAtByNoteIds scans all of the user's completed sessions
        // regardless of the noteIds filter, so reuse the RECENTLY_REVIEWED sort's result here
        // instead of triggering that same unbounded scan a second time inside toListItems.
        List<NoteListItemResponse> items = toListItems(
                pageProjections,
                ownerUserId,
                true,
                lastReviewedAtPrecomputed ? lastReviewedAtByNoteId : null
        );
        boolean hasMore = ((long) page + 1L) * pageSize < totalMatching;
        return new NotesLibraryPageResponse(items, page, pageSize, totalMatching, hasMore);
    }

    @Transactional(readOnly = true)
    public NotesLibraryIdsResponse listLibraryMatchingIds(
            UUID ownerUserId,
            String search,
            String readiness,
            String courseProgram,
            String subject,
            List<String> tags,
            String visibility
    ) {
        NoteLibraryFilterCriteria criteria = buildLibraryFilterCriteria(
                ownerUserId, search, readiness, courseProgram, tags, visibility
        );
        String subjectBucket = normalizeOptionalLibrarySubject(subject);
        List<UUID> matchingIds;
        long totalMatching;
        if (subjectBucket == null) {
            totalMatching = noteRepository.countLibraryMatches(criteria);
            matchingIds = noteRepository.findLibraryMatchingIds(criteria, MAX_LIBRARY_SELECT_ALL_RESULTS);
        } else {
            matchingIds = filterLibrarySubjectCandidates(
                    noteRepository.findLibrarySubjectIdCandidates(criteria),
                    subjectBucket
            ).stream()
                    .map(NoteLibrarySubjectIdProjection::id)
                    .sorted()
                    .toList();
            totalMatching = matchingIds.size();
            matchingIds = matchingIds.stream().limit(MAX_LIBRARY_SELECT_ALL_RESULTS).toList();
        }
        return new NotesLibraryIdsResponse(
                matchingIds.stream().map(UUID::toString).toList(),
                totalMatching,
                totalMatching > MAX_LIBRARY_SELECT_ALL_RESULTS
        );
    }

    @Transactional(readOnly = true)
    public SubjectStatsResponse getLibrarySubjectStats(
            UUID ownerUserId,
            String search,
            String readiness,
            String courseProgram,
            List<String> tags,
            String visibility
    ) {
        NoteLibraryFilterCriteria criteria = buildLibraryFilterCriteria(
                ownerUserId, search, readiness, courseProgram, tags, visibility
        );
        return buildLibrarySubjectStats(noteRepository.findLibrarySubjectCandidates(criteria));
    }

    @Transactional(readOnly = true)
    public NotesLibraryFilterOptionsResponse getLibraryFilterOptions(UUID ownerUserId) {
        List<FacetCount> subjects = toFacetCounts(
                countLibrarySubjectBuckets(noteRepository.findAllLibrarySubjectCandidates(ownerUserId))
        );
        List<FacetCount> coursePrograms = toFacetCounts(noteRepository.countLibraryCoursePrograms(ownerUserId));
        List<FacetCount> tags = toFacetCounts(noteRepository.countLibraryTags(ownerUserId));
        return new NotesLibraryFilterOptionsResponse(subjects, coursePrograms, tags);
    }

    @Transactional(readOnly = true)
    public List<NoteStatusResponse> listMineStatuses(UUID ownerUserId) {
        List<NoteStatusProjection> notes = noteRepository
                .findStatusProjectionsByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId);
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream()
                .map(NoteStatusProjection::id)
                .toList();
        Set<UUID> noteIdsWithStudyPacks = studyPackRepository.findProgressViewsByNoteIdIn(noteIds).stream()
                .map(StudyPackProgressProjection::getNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return notes.stream()
                .map(note -> new NoteStatusResponse(
                        note.id().toString(),
                        NoteStudyPackStatusResolver.resolve(
                                note.status(),
                                noteIdsWithStudyPacks.contains(note.id())
                        )
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicNoteListResponse listPublic(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            NoteTargetProfileType targetProfileType,
            Integer size
    ) {
        return listPublic(
                viewerUserId,
                search,
                sort,
                subject,
                tags,
                courseProgram,
                creator,
                targetProfileType,
                size,
                null,
                null,
                false,
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public PublicNoteListResponse listPublic(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            NoteTargetProfileType targetProfileType,
            Integer size,
            Integer page,
            Integer pageSize,
            boolean readyOnly,
            List<String> sourceValues
    ) {
        List<PublicLibrarySource> sources = parsePublicLibrarySources(sourceValues);
        if (page == null && pageSize == null) {
            return listPublicLegacy(
                    viewerUserId,
                    search,
                    sort,
                    subject,
                    tags,
                    courseProgram,
                    creator,
                    targetProfileType,
                    size,
                    readyOnly,
                    sources
            );
        }

        int resolvedPage = page == null ? 0 : page;
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        PublicLibrarySort publicSort = parsePublicLibrarySort(sort);
        PublicLibraryFilterCriteria criteria = buildPublicLibraryFilterCriteria(
                viewerUserId,
                search,
                subject,
                tags,
                courseProgram,
                creator,
                targetProfileType,
                readyOnly,
                sources
        );
        long offset = (long) resolvedPage * resolvedPageSize;
        long totalMatching;
        List<NoteListItemProjection> pageProjections;
        if (publicSort.isSqlOrderable()) {
            totalMatching = noteRepository.countPublicLibraryMatches(criteria);
            pageProjections = offset > Integer.MAX_VALUE
                    ? List.of()
                    : noteRepository.findPublicLibraryPage(
                            criteria,
                            publicSort,
                            (int) offset,
                            resolvedPageSize
                    );
        } else {
            List<NoteListItemResponse> rankedCandidates = sortPublicLibraryCandidates(
                    buildPublicLibraryRankingItems(noteRepository.findPublicLibraryCandidates(criteria)),
                    publicSort
            );
            totalMatching = rankedCandidates.size();
            List<UUID> pageIds = rankedCandidates.stream()
                    .skip(offset)
                    .limit(resolvedPageSize)
                    .map(item -> UUID.fromString(item.id()))
                    .toList();
            pageProjections = orderListItemProjections(
                    noteRepository.findPublicLibraryListItemProjectionsByIdIn(pageIds),
                    pageIds
            );
        }

        List<NoteListItemResponse> items = toListItems(pageProjections, viewerUserId, false);
        boolean hasMore = ((long) resolvedPage + 1L) * resolvedPageSize < totalMatching;
        int compatibleTotal = (int) Math.min(totalMatching, Integer.MAX_VALUE);
        return new PublicNoteListResponse(
                items,
                compatibleTotal,
                resolvedPage,
                resolvedPageSize,
                totalMatching,
                hasMore
        );
    }

    private PublicNoteListResponse listPublicLegacy(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            NoteTargetProfileType targetProfileType,
            Integer size,
            boolean readyOnly,
            List<PublicLibrarySource> sources
    ) {
        String normalizedCreator = normalizePublicLibraryCreator(creator);
        List<NoteEntity> notes;
        if (normalizedCreator != null) {
            notes = noteRepository.findPublicNotes(NoteVisibility.PUBLIC, targetProfileType, normalizedCreator);
        } else if (targetProfileType == null) {
            notes = noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);
        } else {
            notes = noteRepository.findByVisibilityAndTargetProfileTypeOrderByUpdatedAtDesc(NoteVisibility.PUBLIC, targetProfileType);
        }
        List<NoteListItemResponse> allItems = toListItems(notes, viewerUserId, false);
        int total = allItems.size();
        List<NoteListItemResponse> items = filterPublicLibraryItems(allItems, search, subject, tags, courseProgram);
        items = filterPublicLibraryAdditiveFilters(items, readyOnly, sources);
        items = sortPublicLibraryItems(items, sort);
        return new PublicNoteListResponse(limitPublicLibraryItems(items, size), total);
    }

    @Transactional(readOnly = true)
    public PublicLibraryDiscoverySectionsResponse getPublicLibraryDiscoverySections(
            UUID viewerUserId,
            NoteTargetProfileType targetProfileType
    ) {
        PublicLibraryFilterCriteria criteria = buildPublicLibraryFilterCriteria(
                viewerUserId,
                null,
                null,
                List.of(),
                null,
                null,
                targetProfileType,
                false,
                List.of()
        );
        List<NoteListItemResponse> candidates = buildPublicLibraryRankingItems(
                noteRepository.findPublicLibraryCandidates(criteria)
        );
        List<NoteListItemResponse> featuredCandidates = PublicNotesScoringUtils.sortByFeatured(candidates).stream()
                .limit(PUBLIC_DISCOVERY_SECTION_LIMIT)
                .toList();
        Set<String> featuredIds = featuredCandidates.stream().map(NoteListItemResponse::id).collect(Collectors.toSet());
        List<NoteListItemResponse> popularCandidates = PublicNotesScoringUtils.sortByPopular(
                candidates.stream().filter(item -> !featuredIds.contains(item.id())).toList()
        ).stream().limit(PUBLIC_DISCOVERY_SECTION_LIMIT).toList();
        Set<String> usedIds = new HashSet<>(featuredIds);
        popularCandidates.stream().map(NoteListItemResponse::id).forEach(usedIds::add);
        List<NoteListItemResponse> recentCandidates = PublicNotesScoringUtils.sortByRecent(
                candidates.stream().filter(item -> !usedIds.contains(item.id())).toList()
        ).stream().limit(PUBLIC_DISCOVERY_SECTION_LIMIT).toList();

        List<UUID> unionIds = java.util.stream.Stream.of(featuredCandidates, popularCandidates, recentCandidates)
                .flatMap(List::stream)
                .map(item -> UUID.fromString(item.id()))
                .distinct()
                .toList();
        List<NoteListItemResponse> enriched = toListItems(
                orderListItemProjections(
                        noteRepository.findPublicLibraryListItemProjectionsByIdIn(unionIds),
                        unionIds
                ),
                viewerUserId,
                false
        );
        Map<String, NoteListItemResponse> enrichedById = enriched.stream()
                .collect(Collectors.toMap(NoteListItemResponse::id, item -> item));
        return new PublicLibraryDiscoverySectionsResponse(
                enrichPublicRankingItems(featuredCandidates, enrichedById),
                enrichPublicRankingItems(popularCandidates, enrichedById),
                enrichPublicRankingItems(recentCandidates, enrichedById)
        );
    }

    @Transactional(readOnly = true)
    public List<String> listMineSubjects(UUID ownerUserId) {
        return normalizeSubjects(noteRepository.findSubjectValuesByOwnerUserId(ownerUserId));
    }

    private List<NoteListItemResponse> filterPublicLibraryItems(
            List<NoteListItemResponse> items,
            String search,
            String subject,
            List<String> tags,
            String courseProgram
    ) {
        String normalizedSearch = normalizePublicLibrarySearch(search);
        String normalizedSubjectFilter = normalizePublicLibraryFilterSlug(subject);
        String normalizedCourseProgramFilter = normalizePublicLibraryFilterSlug(courseProgram);
        List<String> normalizedTagFilters = normalizePublicLibraryFilterSlugs(tags);

        return items.stream()
                .filter(item -> matchesPublicLibrarySearch(item, normalizedSearch))
                .filter(item -> matchesPublicLibrarySubject(item, normalizedSubjectFilter))
                .filter(item -> matchesPublicLibraryCourseProgram(item, normalizedCourseProgramFilter))
                .filter(item -> matchesPublicLibraryTags(item, normalizedTagFilters))
                .toList();
    }

    private List<NoteListItemResponse> filterPublicLibraryAdditiveFilters(
            List<NoteListItemResponse> items,
            boolean readyOnly,
            List<PublicLibrarySource> sources
    ) {
        return items.stream()
                .filter(item -> !readyOnly || NoteStudyPackStatusResolver.STUDY_PACK_READY.equals(item.studyPackStatus()))
                .filter(item -> sources.isEmpty() || sources.stream().anyMatch(source -> switch (source) {
                    case BY_YOU -> item.isCurrentUser();
                    case OFFICIAL -> item.isOfficialAuthor();
                    case COMMUNITY -> !item.isCurrentUser() && !item.isOfficialAuthor();
                }))
                .toList();
    }

    private List<NoteListItemResponse> sortPublicLibraryItems(List<NoteListItemResponse> items, String sort) {
        if (sort == null) {
            return items;
        }

        String normalizedSort = sort.trim().toLowerCase();
        return switch (normalizedSort) {
            case PUBLIC_SORT_FEATURED -> PublicNotesScoringUtils.sortByFeatured(items);
            case PUBLIC_SORT_POPULAR, PUBLIC_SORT_COPIED -> PublicNotesScoringUtils.sortByPopular(items);
            case PUBLIC_SORT_RECENT -> PublicNotesScoringUtils.sortByRecent(items);
            case PUBLIC_SORT_RECOMMENDED -> sortPublicLibraryCandidates(items, PublicLibrarySort.RECOMMENDED);
            case PUBLIC_SORT_MOST_COPIED -> sortPublicLibraryCandidates(items, PublicLibrarySort.MOST_COPIED);
            case PUBLIC_SORT_VIEWS -> items.stream()
                    .sorted(Comparator
                            .comparingLong((NoteListItemResponse item) -> item.viewCount() == null ? 0L : item.viewCount())
                            .reversed()
                            .thenComparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            case PUBLIC_SORT_TITLE -> items.stream()
                    .sorted(Comparator.comparing(
                            item -> StringUtils.defaultIfBlank(item.title(), LIBRARY_UNTITLED_NOTE),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
            default -> items;
        };
    }

    private List<NoteListItemResponse> buildPublicLibraryRankingItems(
            List<PublicLibraryCandidateProjection> candidates
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<UUID> noteIds = candidates.stream().map(PublicLibraryCandidateProjection::id).toList();
        Map<UUID, Long> copyCounts = loadCopyCounts(noteIds);
        Map<UUID, Long> likeCounts = loadLikeCounts(noteIds);
        Map<UUID, Long> shareCounts = loadPublicEventCounts(noteIds, AnalyticsEventType.PUBLIC_NOTE_SHARED);
        Map<UUID, Long> viewCounts = loadPublicEventCounts(noteIds, AnalyticsEventType.PUBLIC_NOTE_VIEWED);
        return candidates.stream()
                .map(candidate -> new NoteListItemResponse(
                        candidate.id().toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        NoteTargetProfileType.STUDENT.name(),
                        null,
                        List.of(),
                        candidate.hasContent() ? PUBLIC_RANKING_PLACEHOLDER : "",
                        candidate.hasSummary() ? PUBLIC_RANKING_PLACEHOLDER : "",
                        NoteVisibility.PUBLIC.name(),
                        null,
                        NoteStudyPackStatusResolver.resolve(candidate.status(), candidate.hasStudyPack()),
                        candidate.quizCount(),
                        null,
                        copyCounts.getOrDefault(candidate.id(), 0L),
                        likeCounts.getOrDefault(candidate.id(), 0L),
                        shareCounts.getOrDefault(candidate.id(), 0L),
                        viewCounts.getOrDefault(candidate.id(), 0L),
                        DEFAULT_AUTHOR_NAME,
                        null,
                        false,
                        false,
                        candidate.createdAt(),
                        candidate.updatedAt(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        List.of()
                ))
                .toList();
    }

    private List<NoteListItemResponse> sortPublicLibraryCandidates(
            List<NoteListItemResponse> items,
            PublicLibrarySort sort
    ) {
        Comparator<NoteListItemResponse> createdAtDesc = Comparator.comparing(
                NoteListItemResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        Comparator<NoteListItemResponse> copyCountDesc = Comparator.comparingLong(
                (NoteListItemResponse item) -> publicMetricValue(item.copyCount())
        ).reversed();
        Comparator<NoteListItemResponse> viewCountDesc = Comparator.comparingLong(
                (NoteListItemResponse item) -> publicMetricValue(item.viewCount())
        ).reversed();
        return switch (sort) {
            case FEATURED -> PublicNotesScoringUtils.sortByFeatured(items);
            case POPULAR, COPIED -> PublicNotesScoringUtils.sortByPopular(items);
            case VIEWS -> items.stream().sorted(viewCountDesc.thenComparing(createdAtDesc)).toList();
            case MOST_COPIED -> items.stream().sorted(copyCountDesc.thenComparing(createdAtDesc)).toList();
            case RECOMMENDED -> {
                Instant now = Instant.now();
                yield items.stream()
                        .sorted(Comparator
                                .comparingDouble((NoteListItemResponse item) ->
                                        PublicNotesScoringUtils.computeScore(item, now))
                                .reversed()
                                .thenComparing(copyCountDesc)
                                .thenComparing(viewCountDesc)
                                .thenComparing(createdAtDesc))
                        .toList();
            }
            case RECENT -> PublicNotesScoringUtils.sortByRecent(items);
            case TITLE -> items.stream()
                    .sorted(Comparator.comparing(
                            item -> StringUtils.defaultIfBlank(item.title(), LIBRARY_UNTITLED_NOTE),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
        };
    }

    private long publicMetricValue(Long value) {
        return value == null ? 0L : value;
    }

    private List<NoteListItemResponse> enrichPublicRankingItems(
            List<NoteListItemResponse> rankedItems,
            Map<String, NoteListItemResponse> enrichedById
    ) {
        return rankedItems.stream()
                .map(item -> enrichedById.get(item.id()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<NoteListItemResponse> limitPublicLibraryItems(List<NoteListItemResponse> items, Integer size) {
        if (size == null) {
            return items;
        }
        return items.stream()
                .limit(size)
                .toList();
    }

    private boolean matchesPublicLibrarySearch(NoteListItemResponse item, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }

        return containsIgnoreCase(item.title(), normalizedSearch)
                || containsIgnoreCase(item.subject(), normalizedSearch)
                || containsIgnoreCase(item.courseProgram(), normalizedSearch)
                || containsIgnoreCase(item.contentPreview(), normalizedSearch)
                || containsIgnoreCase(item.summaryPreview(), normalizedSearch)
                || item.tags().stream().anyMatch(tag -> containsIgnoreCase(tag, normalizedSearch));
    }

    private boolean matchesPublicLibrarySubject(NoteListItemResponse item, String normalizedSubjectFilter) {
        if (normalizedSubjectFilter == null) {
            return true;
        }
        return normalizedSubjectFilter.equals(normalizePublicLibraryFilterSlug(item.subject()));
    }

    private boolean matchesPublicLibraryCourseProgram(NoteListItemResponse item, String normalizedCourseProgramFilter) {
        if (normalizedCourseProgramFilter == null) {
            return true;
        }
        return normalizedCourseProgramFilter.equals(normalizePublicLibraryFilterSlug(item.courseProgram()));
    }

    private boolean matchesPublicLibraryTags(NoteListItemResponse item, List<String> normalizedTagFilters) {
        if (normalizedTagFilters.isEmpty()) {
            return true;
        }

        List<String> itemTagSlugs = item.tags().stream()
                .map(this::normalizePublicLibraryFilterSlug)
                .filter(Objects::nonNull)
                .toList();
        return normalizedTagFilters.stream().anyMatch(itemTagSlugs::contains);
    }

    private String normalizePublicLibrarySearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizePublicLibraryCreator(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PublicLibraryFilterCriteria buildPublicLibraryFilterCriteria(
            UUID viewerUserId,
            String search,
            String subject,
            List<String> tags,
            String courseProgram,
            String creator,
            NoteTargetProfileType targetProfileType,
            boolean readyOnly,
            List<PublicLibrarySource> sources
    ) {
        return new PublicLibraryFilterCriteria(
                viewerUserId,
                AccountPurgeService.DELETED_USER_ID,
                toLibrarySearchPattern(search),
                normalizePublicLibraryFilterSlug(subject),
                normalizePublicLibraryFilterSlugs(tags),
                normalizePublicLibraryFilterSlug(courseProgram),
                normalizePublicLibraryCreator(creator),
                targetProfileType,
                readyOnly,
                sources
        );
    }

    private PublicLibrarySort parsePublicLibrarySort(String sort) {
        String normalized = sort == null || sort.isBlank()
                ? PUBLIC_SORT_RECOMMENDED
                : sort.trim().toLowerCase(Locale.ROOT);
        try {
            return PublicLibrarySort.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidPublicLibraryQueryException(SORT_PARAMETER_NAME);
        }
    }

    private List<PublicLibrarySource> parsePublicLibrarySources(List<String> sourceValues) {
        if (sourceValues == null || sourceValues.isEmpty()) {
            return List.of();
        }
        try {
            return sourceValues.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> PublicLibrarySource.valueOf(value.toUpperCase(Locale.ROOT)))
                    .distinct()
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new InvalidPublicLibraryQueryException("source");
        }
    }

    private List<String> normalizePublicLibraryFilterSlugs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizePublicLibraryFilterSlug)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizePublicLibraryFilterSlug(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase().contains(normalizedSearch);
    }

    @Transactional(readOnly = true)
    public List<String> listMineCoursePrograms(UUID ownerUserId) {
        List<String> values = new java.util.ArrayList<>(noteRepository.findCourseProgramValuesByOwnerUserId(ownerUserId));
        values.addAll(noteCourseProgramRepository.findNamesByOwnerUserId(ownerUserId));
        userRepository.findById(ownerUserId)
                .map(UserEntity::getCourseProgram)
                .ifPresent(values::add);
        return normalizeCoursePrograms(values);
    }

    @Transactional(readOnly = true)
    public List<String> listPublicSubjects() {
        return normalizeSubjects(noteRepository.findSubjectValuesByVisibility(NoteVisibility.PUBLIC));
    }

    @Transactional(readOnly = true)
    public List<String> listPublicCoursePrograms() {
        List<String> values = new ArrayList<>(
                noteRepository.findCourseProgramValuesByVisibility(NoteVisibility.PUBLIC)
        );
        values.addAll(noteCourseProgramRepository.findNamesByVisibility(NoteVisibility.PUBLIC.name()));
        return normalizeCoursePrograms(values);
    }

    @Transactional(readOnly = true)
    public List<String> listPublicTags() {
        return normalizeTags(noteRepository.findDistinctPublicTags()).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicNoteDetailResponse getPublicById(String id, UUID viewerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(id, NoteNotFoundException::new);
        NoteEntity entity = noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)
                .orElseThrow(NoteNotFoundException::new);
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(entity.getId());
        analyticsService.trackEvent(viewerUserId, AnalyticsEventType.PUBLIC_NOTE_VIEWED, entity.getId(), buildMetadata(
                "pathType", "id",
                "subject", entity.getSubject()
        ));
        return mapToPublicDetail(entity, linkedStudyPack, viewerUserId);
    }

    @Transactional(readOnly = true)
    public PublicNoteDetailResponse getPublicBySeoPath(String subjectSlug, String titleSlug, UUID viewerUserId) {
        String normalizedSubjectSlug = normalizeSlug(subjectSlug);
        String normalizedTitleSlug = normalizeSlug(titleSlug);
        List<NoteEntity> candidates = DEFAULT_PUBLIC_SUBJECT_SLUG.equals(normalizedSubjectSlug)
                ? noteRepository.findByVisibilityAndSubjectIsNullOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)
                : noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);

        NoteEntity matched = candidates.stream()
                .filter(note -> slugify(note.getSubject(), DEFAULT_PUBLIC_SUBJECT_SLUG).equals(normalizedSubjectSlug))
                .filter(note -> slugify(note.getTitle(), DEFAULT_PUBLIC_TITLE_SLUG).equals(normalizedTitleSlug))
                .findFirst()
                .orElseThrow(NoteNotFoundException::new);

        StudyPackEntity linkedStudyPack = findLinkedStudyPack(matched.getId());
        LinkedHashMap<String, Object> analyticsMetadata = new LinkedHashMap<>();
        analyticsMetadata.put("pathType", "seo");
        analyticsMetadata.put("subjectSlug", normalizedSubjectSlug);
        analyticsMetadata.put("titleSlug", normalizedTitleSlug);
        analyticsService.trackEvent(viewerUserId, AnalyticsEventType.PUBLIC_NOTE_VIEWED, matched.getId(), analyticsMetadata);
        return mapToPublicDetail(matched, linkedStudyPack, viewerUserId);
    }

    private List<NoteListItemResponse> toListItems(
            List<? extends NoteListItemView> notes,
            UUID viewerUserId,
            boolean includeOwnerUserId
    ) {
        return toListItems(notes, viewerUserId, includeOwnerUserId, null);
    }

    private List<NoteListItemResponse> toListItems(
            List<? extends NoteListItemView> notes,
            UUID viewerUserId,
            boolean includeOwnerUserId,
            Map<UUID, OffsetDateTime> precomputedLastSessionCompletedAtByNoteId
    ) {
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream().map(NoteListItemView::getId).toList();
        List<UUID> ownerIds = notes.stream()
                .map(NoteListItemView::getOwnerUserId)
                .distinct()
                .toList();
        Map<UUID, Long> copyCountsByNoteId = loadCopyCounts(noteIds);
        Map<UUID, Long> likeCountsByNoteId = loadLikeCounts(noteIds);
        Map<UUID, Long> shareCountsByNoteId = loadPublicEventCounts(noteIds, AnalyticsEventType.PUBLIC_NOTE_SHARED);
        Map<UUID, Long> viewCountsByNoteId = loadPublicEventCounts(noteIds, AnalyticsEventType.PUBLIC_NOTE_VIEWED);
        HashSet<UUID> likedNoteIds = loadLikedNoteIds(noteIds, viewerUserId);
        Map<UUID, StudyPackEntity> studyPackByNoteId = new HashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByNoteIdIn(noteIds)) {
            if (studyPack.getNoteId() != null) {
                studyPackByNoteId.put(studyPack.getNoteId(), studyPack);
            }
        }
        Map<UUID, GeneratedQuizEntity> generatedQuizByNoteId = new HashMap<>();
        if (viewerUserId != null) {
            for (GeneratedQuizEntity generatedQuiz : generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(viewerUserId, noteIds)) {
                if (generatedQuiz.getNoteId() != null) {
                    generatedQuizByNoteId.put(generatedQuiz.getNoteId(), generatedQuiz);
                }
            }
        }
        Map<UUID, OffsetDateTime> lastSessionCompletedAtByNoteId = precomputedLastSessionCompletedAtByNoteId != null
                ? precomputedLastSessionCompletedAtByNoteId
                : quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(viewerUserId, noteIds);
        Map<UUID, UserEntity> ownerById = new HashMap<>();
        for (UserEntity owner : userRepository.findAllById(ownerIds)) {
            ownerById.put(owner.getId(), owner);
        }

        return notes.stream()
                .map(note -> mapToListItemResponse(
                        note,
                        studyPackByNoteId.get(note.getId()),
                        copyCountsByNoteId.getOrDefault(note.getId(), 0L),
                        likeCountsByNoteId.getOrDefault(note.getId(), 0L),
                        shareCountsByNoteId.getOrDefault(note.getId(), 0L),
                        viewCountsByNoteId.getOrDefault(note.getId(), 0L),
                        ownerById.get(note.getOwnerUserId()),
                        viewerUserId,
                        likedNoteIds.contains(note.getId()),
                        generatedQuizByNoteId.get(note.getId()),
                        lastSessionCompletedAtByNoteId.get(note.getId()),
                        includeOwnerUserId
                ))
                .toList();
    }

    private Map<UUID, Long> loadCopyCounts(List<UUID> noteIds) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (NoteCopyCountProjection projection : noteRepository.countCopiedPublicNotesBySourceNoteIds(noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getCopyCount());
            }
        }
        return countsByNoteId;
    }

    private Map<UUID, Long> loadPublicEventCounts(List<UUID> noteIds, AnalyticsEventType eventType) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (PublicNoteEventCountProjection projection : analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(eventType, noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getTotalCount());
            }
        }
        return countsByNoteId;
    }

    private Map<UUID, Long> loadLikeCounts(List<UUID> noteIds) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (PublicNoteLikeCountProjection projection : publicNoteLikeRepository.countLikesByNoteIds(noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getLikeCount());
            }
        }
        return countsByNoteId;
    }

    private HashSet<UUID> loadLikedNoteIds(List<UUID> noteIds, UUID viewerUserId) {
        if (viewerUserId == null) {
            return new HashSet<>();
        }
        return new HashSet<>(publicNoteLikeRepository.findLikedNoteIdsByUserIdAndNoteIdIn(viewerUserId, noteIds));
    }

    private long countLikes(UUID noteId) {
        return loadLikeCounts(List.of(noteId)).getOrDefault(noteId, 0L);
    }

    private String normalizeRequiredContent(String rawContent) {
        String normalized = rawContent == null ? "" : rawContent.trim();
        if (normalized.isBlank()) {
            throw new AppException(
                    "EMPTY_NOTE_CONTENT",
                    "Please provide note content before saving.",
                    HttpStatus.BAD_REQUEST
            );
        }
        contentModerationService.validateOrThrow(normalized);
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }

        LinkedHashMap<String, String> normalizedByKey = new LinkedHashMap<>();
        for (String rawTag : rawTags) {
            String normalized = StringUtils.defaultString(rawTag).trim();
            if (normalized.isBlank()) {
                continue;
            }
            normalizedByKey.putIfAbsent(normalized.toLowerCase(), normalized);
        }
        return List.copyOf(normalizedByKey.values());
    }

    private UserEntity getOwnerOrThrow(UUID ownerUserId) {
        return userRepository.findById(ownerUserId)
                .orElseThrow(UserNotFoundException::new);
    }

    private String resolveRequestedCourseProgram(String requestedCourseProgram, UserEntity owner) {
        String normalizedRequested = normalizeOptionalCourseProgram(requestedCourseProgram);
        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        return normalizeOptionalCourseProgram(owner.getCourseProgram());
    }

    private String normalizeOptionalCourseProgram(String value) {
        return CourseProgramNormalizationUtils.normalizeForStorage(value);
    }

    private NoteTargetProfileType resolveTargetProfileType(String requestedTargetProfileType, UserEntity owner) {
        if (isTeacherSelectableOwner(owner)) {
            return parseSelectableTargetProfileTypeOrThrow(requestedTargetProfileType);
        }
        return mapOwnerProfileTypeToNoteTarget(owner.getProfileType());
    }

    private NoteTargetProfileType resolveTargetProfileTypeForUpdate(
            String requestedTargetProfileType,
            UserEntity owner,
            NoteTargetProfileType storedTargetProfileType
    ) {
        if (isTeacherSelectableOwner(owner)) {
            return parseSelectableTargetProfileTypeOrThrow(requestedTargetProfileType);
        }
        // The audience select is not rendered for these owners, so the request carries no intent about
        // it. Re-deriving from the owner's *current* profile let an unrelated metadata save — a title
        // fix from Note Detail's inline panel — silently rewrite the note's audience, and
        // notes.target_profile_type is a live Public Library discovery filter. It also discarded the
        // author's audience on a copied note. Preserve what is stored; fall back to the profile mapping
        // only for legacy rows that carry none.
        return storedTargetProfileType != null
                ? storedTargetProfileType
                : mapOwnerProfileTypeToNoteTarget(owner.getProfileType());
    }

    private boolean isTeacherSelectableOwner(UserEntity owner) {
        return owner.getRole() == UserRole.ADMIN || owner.getProfileType() == ProfileType.TEACHER;
    }

    private NoteTargetProfileType parseSelectableTargetProfileTypeOrThrow(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AppException(
                    NOTE_TARGET_PROFILE_TYPE_REQUIRED_CODE,
                    NOTE_TARGET_PROFILE_TYPE_REQUIRED_MESSAGE,
                    HttpStatus.BAD_REQUEST
            );
        }
        NoteTargetProfileType targetProfileType;
        try {
            targetProfileType = NoteTargetProfileType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new AppException(
                    NOTE_TARGET_PROFILE_TYPE_INVALID_CODE,
                    NOTE_TARGET_PROFILE_TYPE_INVALID_MESSAGE,
                    HttpStatus.BAD_REQUEST
            );
        }
        if (targetProfileType == NoteTargetProfileType.STUDENT
                || targetProfileType == NoteTargetProfileType.BOARD_TAKER
                || targetProfileType == NoteTargetProfileType.PROFESSIONAL) {
            return targetProfileType;
        }
        throw new AppException(
                NOTE_TARGET_PROFILE_TYPE_INVALID_CODE,
                NOTE_TARGET_PROFILE_TYPE_INVALID_MESSAGE,
                HttpStatus.BAD_REQUEST
        );
    }

    private NoteTargetProfileType mapOwnerProfileTypeToNoteTarget(ProfileType profileType) {
        if (profileType == ProfileType.BOARD_EXAM) {
            return NoteTargetProfileType.BOARD_TAKER;
        }
        if (profileType == ProfileType.PROFESSIONAL) {
            return NoteTargetProfileType.PROFESSIONAL;
        }
        return NoteTargetProfileType.STUDENT;
    }

    private NoteTargetProfileType resolveTargetProfileType(NoteListItemView note) {
        return note.getTargetProfileType() == null ? NoteTargetProfileType.STUDENT : note.getTargetProfileType();
    }

    private NoteResponse mapToResponse(NoteEntity entity, StudyPackEntity studyPack) {
        List<String> keyConcepts = studyPack == null || studyPack.getKeyConcepts() == null
                ? List.of()
                : studyPack.getKeyConcepts();
        List<com.studysnap.backend.dto.QuizItem> quiz = studyPack == null || studyPack.getQuiz() == null
                ? List.of()
                : studyPack.getQuiz();
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findByNoteId(entity.getId()).orElse(null);
        int quizCount = quiz.size();
        boolean hasGeneratedQuiz = !quiz.isEmpty();
        PlanType planType = subscriptionService.resolvePlan(entity.getOwnerUserId());
        return new NoteResponse(
                entity.getId().toString(),
                entity.getTitle(),
                entity.getSubject(),
                entity.getCourseProgram(),
                entity.getDomainContext() == null ? null : entity.getDomainContext().name(),
                entity.getLearnerLevel() == null ? null : entity.getLearnerLevel().name(),
                resolveTargetProfileType(entity).name(),
                entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()),
                entity.getContent(),
                resolveVisibility(entity).name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCopiedFromNoteId() == null ? null : entity.getCopiedFromNoteId().toString(),
                entity.getCopiedFromUserId() == null ? null : entity.getCopiedFromUserId().toString(),
                entity.getCopiedFromTitle(),
                Boolean.TRUE.equals(entity.getCopiedFromPublic()),
                entity.getCopiedAt(),
                studyPack == null ? null : studyPack.getId().toString(),
                NoteStudyPackStatusResolver.resolve(entity, studyPack),
                studyPack == null ? null : studyPack.getSummary(),
                keyConcepts,
                quiz,
                generatedQuiz == null ? null : new com.studysnap.backend.dto.GeneratedQuizResponse(
                        generatedQuiz.getId().toString(),
                        entity.getId().toString(),
                        generatedQuiz.getQuestions() == null ? List.of() : generatedQuiz.getQuestions(),
                        generatedQuiz.getGeneratedAt()
                ),
                generatedQuizRepository.findLatestTargetLearnerLevelByNoteId(entity.getId())
                        .map(Enum::name)
                        .orElse(null),
                quizCount,
                hasGeneratedQuiz,
                hasGeneratedQuiz,
                hasGeneratedQuiz && featureGateService.hasFeatureAccess(planType, Feature.ADAPTIVE_QUIZ)
        );
    }

    private NoteListItemResponse mapToListItemResponse(
            NoteListItemView note,
            StudyPackEntity studyPack,
            long copyCount,
            long likeCount,
            long shareCount,
            long viewCount,
            UserEntity owner,
            UUID viewerUserId,
            boolean likedByCurrentUser,
            GeneratedQuizEntity generatedQuiz,
            OffsetDateTime lastSessionCompletedAt,
            boolean includeOwnerUserId
    ) {
        boolean isOfficialAuthor = isOfficialAuthor(owner);
        return new NoteListItemResponse(
                note.getId().toString(),
                includeOwnerUserId && note.getOwnerUserId() != null ? note.getOwnerUserId().toString() : null,
                note.getTitle(),
                normalizeOptionalText(note.getCourseProgram()),
                note.getDomainContext() == null ? null : note.getDomainContext().name(),
                note.getLearnerLevel() == null ? null : note.getLearnerLevel().name(),
                resolveTargetProfileType(note).name(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                studyPack == null ? "" : SummaryPreviewUtils.buildSummaryPreview(studyPack.getSummary(), SUMMARY_PREVIEW_MAX_LENGTH),
                resolveVisibility(note).name(),
                studyPack == null ? null : studyPack.getId().toString(),
                NoteStudyPackStatusResolver.resolve(note.getStatus(), studyPack != null),
                studyPack == null || studyPack.getQuiz() == null ? null : studyPack.getQuiz().size(),
                studyPack == null || studyPack.getKeyConcepts() == null ? null : studyPack.getKeyConcepts().size(),
                copyCount,
                likeCount,
                shareCount,
                viewCount,
                resolvePublicAuthorName(owner),
                resolvePublicAuthorUsername(owner),
                isOfficialAuthor,
                isCurrentUser(note.getOwnerUserId(), viewerUserId),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                lastSessionCompletedAt,
                generatedQuiz == null ? null : generatedQuiz.getId().toString(),
                generatedQuiz == null ? null : generatedQuiz.getGeneratedAt(),
                generatedQuiz == null || generatedQuiz.getQuestions() == null ? null : generatedQuiz.getQuestions().size(),
                note.getCopiedFromNoteId() == null ? null : note.getCopiedFromNoteId().toString(),
                Boolean.TRUE.equals(note.getCopiedFromPublic()),
                likedByCurrentUser,
                applicablePrograms(note)
        );
    }

    private List<String> applicablePrograms(NoteListItemView note) {
        if (!(note instanceof NoteListItemProjection projection) || projection.getApplicablePrograms() == null) {
            return List.of();
        }
        return Arrays.asList(projection.getApplicablePrograms());
    }

    private PublicNoteDetailResponse mapToPublicDetail(NoteEntity note, StudyPackEntity studyPack, UUID viewerUserId) {
        UserEntity owner = userRepository.findById(note.getOwnerUserId()).orElse(null);
        boolean isOfficialAuthor = isOfficialAuthor(owner);
        return new PublicNoteDetailResponse(
                note.getId().toString(),
                null,
                note.getTitle(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                note.getContent(),
                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                NoteStudyPackStatusResolver.resolve(note, studyPack),
                studyPack == null ? null : studyPack.getSummary(),
                studyPack == null || studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                studyPack == null || studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz(),
                resolvePublicAuthorName(owner),
                resolvePublicAuthorUsername(owner),
                isOfficialAuthor,
                isCurrentUser(note.getOwnerUserId(), viewerUserId),
                note.getUpdatedAt()
        );
    }

    private String resolvePublicAuthorName(UserEntity user) {
        if (user == null) {
            return DEFAULT_AUTHOR_NAME;
        }
        if (isDeletedUserSentinel(user)) {
            return "Deleted user";
        }
        String displayName = normalizeOptionalText(user.getDisplayName());
        if (displayName != null) {
            return displayName;
        }
        if (isOfficialAuthor(user)) {
            return OFFICIAL_AUTHOR_DISPLAY_NAME;
        }
        String firstName = normalizeOptionalText(user.getFirstName());
        return firstName != null ? firstName : DEFAULT_AUTHOR_NAME;
    }

    private String resolvePublicAuthorUsername(UserEntity user) {
        if (isDeletedUserSentinel(user)) {
            return null;
        }
        return normalizeOptionalText(user == null ? null : user.getUsername());
    }

    private boolean isOfficialAuthor(UserEntity user) {
        return user != null && user.getRole() == UserRole.ADMIN && !isDeletedUserSentinel(user);
    }

    private boolean isDeletedUserSentinel(UserEntity user) {
        return user != null && AccountPurgeService.DELETED_USER_ID.equals(user.getId());
    }

    private boolean isCurrentUser(UUID ownerUserId, UUID viewerUserId) {
        return ownerUserId != null && ownerUserId.equals(viewerUserId);
    }

    private StudyPackEntity findLinkedStudyPack(UUID noteId) {
        return studyPackRepository.findByNoteId(noteId).orElse(null);
    }

    private List<String> normalizeSubjects(List<String> rawSubjects) {
        Map<String, String> normalized = new LinkedHashMap<>();
        rawSubjects.stream()
                .map(SubjectNormalizationUtils::normalizeForStorage)
                .filter(Objects::nonNull)
                .sorted(SUBJECT_DISPLAY_COMPARATOR)
                .forEach(subject -> normalized.putIfAbsent(SubjectNormalizationUtils.normalizeForLookup(subject), subject));
        return List.copyOf(normalized.values());
    }

    private List<String> normalizeCoursePrograms(List<String> rawCoursePrograms) {
        Map<String, String> normalized = new LinkedHashMap<>();
        rawCoursePrograms.stream()
                .map(CourseProgramNormalizationUtils::normalizeForStorage)
                .filter(Objects::nonNull)
                .sorted(COURSE_PROGRAM_DISPLAY_COMPARATOR)
                .forEach(courseProgram -> normalized.merge(
                        CourseProgramNormalizationUtils.normalizeForLookup(courseProgram),
                        courseProgram,
                        this::preferReadableCourseProgram
                ));
        return List.copyOf(normalized.values());
    }

    private String preferReadableCourseProgram(String existing, String candidate) {
        int existingScore = courseProgramReadabilityScore(existing);
        int candidateScore = courseProgramReadabilityScore(candidate);
        if (candidateScore != existingScore) {
            return candidateScore > existingScore ? candidate : existing;
        }
        return existing.length() <= candidate.length() ? existing : candidate;
    }

    private int courseProgramReadabilityScore(String value) {
        String[] words = value.split("[\\s/–-]+");
        int titleCaseWords = 0;
        for (String word : words) {
            if (!word.isEmpty() && Character.isUpperCase(word.charAt(0))) {
                titleCaseWords++;
            }
        }
        int hasUppercase = value.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        return (titleCaseWords * 10) + hasUppercase;
    }

    private String resolveCanonicalSubject(String requestedSubject) {
        String normalizedRequested = SubjectNormalizationUtils.normalizeForStorage(requestedSubject);
        if (normalizedRequested == null) {
            return null;
        }

        String lookup = SubjectNormalizationUtils.normalizeForLookup(normalizedRequested);
        return noteRepository.findAllSubjectValues().stream()
                .map(SubjectNormalizationUtils::normalizeForStorage)
                .filter(Objects::nonNull)
                .sorted(SUBJECT_DISPLAY_COMPARATOR)
                .filter(existing -> SubjectNormalizationUtils.normalizeForLookup(existing).equals(lookup))
                .findFirst()
                .orElse(normalizedRequested);
    }

    private NoteLibraryFilterCriteria buildLibraryFilterCriteria(
            UUID ownerUserId,
            String search,
            String readiness,
            String courseProgram,
            List<String> tags,
            String visibility
    ) {
        return new NoteLibraryFilterCriteria(
                ownerUserId,
                toLibrarySearchPattern(search),
                parseLibraryReadiness(readiness),
                normalizeOptionalText(courseProgram),
                normalizeLibraryFilterTags(tags),
                parseLibraryVisibility(visibility)
        );
    }

    private String toLibrarySearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private List<String> normalizeLibraryFilterTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    private NoteLibraryReadiness parseLibraryReadiness(String readiness) {
        String normalized = readiness == null || readiness.isBlank()
                ? NoteLibraryReadiness.ALL.name()
                : readiness.trim().toUpperCase(Locale.ROOT);
        try {
            return NoteLibraryReadiness.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidLibraryQueryException("readiness");
        }
    }

    private NoteVisibility parseLibraryVisibility(String visibility) {
        if (visibility == null || visibility.isBlank() || NoteLibraryReadiness.ALL.name().equalsIgnoreCase(visibility)) {
            return null;
        }
        try {
            return NoteVisibility.valueOf(visibility.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidLibraryQueryException("visibility");
        }
    }

    private NoteLibrarySort parseLibrarySort(String sort) {
        String normalized = sort == null || sort.isBlank()
                ? NoteLibrarySort.RECENTLY_UPDATED.name()
                : sort.trim().toUpperCase(Locale.ROOT);
        try {
            return NoteLibrarySort.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidLibraryQueryException(SORT_PARAMETER_NAME);
        }
    }

    private String normalizeOptionalLibrarySubject(String subject) {
        return SubjectNormalizationUtils.normalizeForStorage(subject);
    }

    private <T extends NoteLibrarySubjectView> List<T> filterLibrarySubjectCandidates(
            List<T> candidates,
            String subjectBucket
    ) {
        if (subjectBucket == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> resolveLibrarySubjectBucket(
                        candidate.subject(), candidate.courseProgram()
                ).equals(subjectBucket))
                .toList();
    }

    private String resolveLibrarySubjectBucket(String subject, String courseProgram) {
        String normalizedSubject = SubjectNormalizationUtils.normalizeForStorage(subject);
        if (normalizedSubject != null) {
            return normalizedSubject;
        }
        String normalizedCourseProgram = CourseProgramNormalizationUtils.normalizeForStorage(courseProgram);
        return normalizedCourseProgram == null ? LIBRARY_SUBJECT_FALLBACK : normalizedCourseProgram;
    }

    private Comparator<NoteLibraryCandidateProjection> libraryCandidateComparator(
            NoteLibrarySort sort,
            Map<UUID, OffsetDateTime> lastReviewedAtByNoteId
    ) {
        Comparator<NoteLibraryCandidateProjection> updatedDesc = Comparator
                .comparing(NoteLibraryCandidateProjection::updatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed();
        Comparator<NoteLibraryCandidateProjection> primary = switch (sort) {
            case TITLE_ASC -> Comparator.<NoteLibraryCandidateProjection, String>comparing(
                    candidate -> candidate.title() == null ? LIBRARY_UNTITLED_NOTE : candidate.title(),
                    this::compareLibraryLabels
            );
            case TITLE_DESC -> Comparator.<NoteLibraryCandidateProjection, String>comparing(
                    candidate -> candidate.title() == null ? LIBRARY_UNTITLED_NOTE : candidate.title(),
                    this::compareLibraryLabels
            ).reversed();
            case OLDEST -> Comparator.comparing(
                    NoteLibraryCandidateProjection::createdAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            );
            case NEWEST -> Comparator.comparing(
                    NoteLibraryCandidateProjection::createdAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            ).reversed();
            case RECENTLY_REVIEWED -> Comparator.<NoteLibraryCandidateProjection, OffsetDateTime>comparing(
                    candidate -> Objects.requireNonNullElse(
                            lastReviewedAtByNoteId.get(candidate.id()),
                            LIBRARY_UNREVIEWED_AT
                    )
            ).reversed();
            case RECENTLY_UPDATED -> updatedDesc;
        };
        Comparator<NoteLibraryCandidateProjection> withUpdatedTiebreak = sort == NoteLibrarySort.RECENTLY_UPDATED
                ? primary
                : primary.thenComparing(updatedDesc);
        return withUpdatedTiebreak.thenComparing(NoteLibraryCandidateProjection::id);
    }

    private int compareLibraryLabels(String left, String right) {
        return java.text.Collator.getInstance().compare(left, right);
    }

    private List<NoteListItemProjection> orderListItemProjections(
            List<NoteListItemProjection> projections,
            List<UUID> orderedIds
    ) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, NoteListItemProjection> byId = projections.stream()
                .collect(Collectors.toMap(NoteListItemProjection::getId, projection -> projection));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private SubjectStatsResponse buildLibrarySubjectStats(List<NoteLibrarySubjectProjection> candidates) {
        List<FacetCount> sortedCounts = toFacetCounts(countLibrarySubjectBuckets(candidates));
        List<SubjectFacetCount> topSubjects = sortedCounts.stream()
                .limit(LIBRARY_SUBJECT_FACET_LIMIT)
                .map(count -> new SubjectFacetCount(count.value(), count.count()))
                .toList();
        long otherSubjectsCount = sortedCounts.stream()
                .skip(LIBRARY_SUBJECT_FACET_LIMIT)
                .mapToLong(FacetCount::count)
                .sum();
        return new SubjectStatsResponse(topSubjects, otherSubjectsCount, candidates.size());
    }

    private Map<String, Long> countLibrarySubjectBuckets(List<? extends NoteLibrarySubjectView> candidates) {
        Map<String, Long> counts = new HashMap<>();
        for (NoteLibrarySubjectView candidate : candidates) {
            counts.merge(
                    resolveLibrarySubjectBucket(candidate.subject(), candidate.courseProgram()),
                    1L,
                    Long::sum
            );
        }
        return counts;
    }

    private List<FacetCount> toFacetCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new FacetCount(entry.getKey(), entry.getValue()))
                .sorted(facetCountComparator())
                .toList();
    }

    private List<FacetCount> toFacetCounts(List<NoteLibraryValueCountProjection> counts) {
        return counts.stream()
                .map(count -> new FacetCount(count.value(), count.count()))
                .sorted(facetCountComparator())
                .toList();
    }

    private Comparator<FacetCount> facetCountComparator() {
        return Comparator.comparingLong(FacetCount::count)
                .reversed()
                .thenComparing(FacetCount::value, this::compareLibraryLabels);
    }

    private NoteVisibility parseVisibility(String visibilityRaw) {
        if (visibilityRaw == null || visibilityRaw.isBlank()) {
            throw new AppException("INVALID_VISIBILITY", "Visibility is required.", HttpStatus.BAD_REQUEST);
        }
        try {
            return NoteVisibility.valueOf(visibilityRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException("INVALID_VISIBILITY", "Visibility must be PRIVATE or PUBLIC.", HttpStatus.BAD_REQUEST);
        }
    }

    private NoteVisibility resolveVisibility(NoteListItemView note) {
        return note.getVisibility() == null ? NoteVisibility.PRIVATE : note.getVisibility();
    }

    private static String slugify(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase();
    }

    private Map<String, Object> buildMetadata(String key1, Object value1, String key2, Object value2) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        putMetadataValue(metadata, key1, value1);
        putMetadataValue(metadata, key2, value2);
        return metadata;
    }

    private void putMetadataValue(Map<String, Object> metadata, String key, Object value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }
}
