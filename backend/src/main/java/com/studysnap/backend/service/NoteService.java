package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.PublicNoteLikeEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.PublicNoteLikeCountProjection;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final int SUMMARY_PREVIEW_MAX_LENGTH = 180;
    private static final String STUDY_PACK_STATUS_DRAFT = "DRAFT";
    private static final String STUDY_PACK_STATUS_GENERATING = "GENERATING";
    private static final String STUDY_PACK_STATUS_FAILED = "FAILED";
    private static final String STUDY_PACK_STATUS_READY = "STUDY_PACK_READY";
    private static final String DEFAULT_PUBLIC_SUBJECT_SLUG = "general";
    private static final String DEFAULT_PUBLIC_TITLE_SLUG = "untitled-note";
    private static final String DEFAULT_AUTHOR_NAME = "Anonymous learner";
    private static final String OFFICIAL_AUTHOR_DISPLAY_NAME = "NoteLib";
    private static final String OFFICIAL_AUTHOR_EMAIL = "einar.lagera@gmail.com";
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

    public NoteResponse create(UpsertNoteRequest request, UUID ownerUserId) {
        UserEntity owner = getOwnerOrThrow(ownerUserId);
        NoteEntity entity = new NoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(resolveCanonicalSubject(request.subject()));
        entity.setCourseProgram(resolveRequestedCourseProgram(request.courseProgram(), owner));
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
        String normalizedRequestedContent = normalizeRequiredContent(request.content());
        NoteStatus currentStatus = resolveStatus(entity);
        if (currentStatus == NoteStatus.GENERATED) {
            String currentContent = entity.getContent() == null ? "" : entity.getContent().trim();
            if (!currentContent.equals(normalizedRequestedContent)) {
                throw new AppException(
                        "NOTE_CONTENT_LOCKED",
                        "Note content is locked after generating a Study Pack. Make a copy to change the note itself.",
                        HttpStatus.CONFLICT
                );
            }
        } else {
            entity.setContent(normalizedRequestedContent);
        }

        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(resolveCanonicalSubject(request.subject()));
        entity.setCourseProgram(normalizeOptionalCourseProgram(request.courseProgram()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setTargetProfileType(resolveTargetProfileType(request.targetProfileType(), owner));
        entity.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(entity);
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
        if (note.studyPackId() == null || STUDY_PACK_STATUS_DRAFT.equals(note.studyPackStatus())) {
            throw new AppException(
                    "NOTE_STUDY_PACK_NOT_READY",
                    "Generate a Study Pack for this note first.",
                    HttpStatus.CONFLICT
            );
        }
        return note.studyPackId();
    }

    public NoteResponse copyNote(String id, UUID ownerUserId) {
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
                StudyPackEntity existingStudyPack = findLinkedStudyPack(existingCopy.get().getId());
                return mapToResponse(existingCopy.get(), existingStudyPack);
            }
        }

        NoteEntity copy = new NoteEntity();
        copy.setId(UUID.randomUUID());
        copy.setOwnerUserId(ownerUserId);
        copy.setTitle(source.getTitle());
        copy.setSubject(resolveCanonicalSubject(source.getSubject()));
        copy.setCourseProgram(normalizeOptionalCourseProgram(source.getCourseProgram()));
        copy.setTags(source.getTags() == null ? new String[0] : Arrays.copyOf(source.getTags(), source.getTags().length));
        copy.setContent(source.getContent());
        copy.setStatus(NoteStatus.DRAFT);
        copy.setVisibility(NoteVisibility.PRIVATE);
        copy.setTargetProfileType(resolveTargetProfileType(source));
        copy.setSourceNoteId(source.getId());
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
        }
        copy.setCreatedAt(OffsetDateTime.now());
        copy.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(copy);
        if (!isOwner) {
            analyticsService.trackEvent(ownerUserId, AnalyticsEventType.PUBLIC_NOTE_COPIED, source.getId(), buildMetadata(
                    "copiedNoteId", saved.getId().toString(),
                    "sourceOwnerUserId", source.getOwnerUserId() == null ? null : source.getOwnerUserId().toString()
            ));
        }
        return mapToResponse(saved, null);
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
        entity.setVisibility(visibility);
        entity.setUpdatedAt(OffsetDateTime.now());
        NoteEntity saved = noteRepository.save(entity);
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(saved.getId());
        return mapToResponse(saved, linkedStudyPack);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listMine(UUID ownerUserId) {
        List<NoteEntity> notes = noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId);
        return toListItems(notes, ownerUserId, true);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listPublic(
            UUID viewerUserId,
            String search,
            String sort,
            String subject,
            List<String> tags,
            String courseProgram,
            NoteTargetProfileType targetProfileType
    ) {
        List<NoteEntity> notes = targetProfileType == null
                ? noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)
                : noteRepository.findByVisibilityAndTargetProfileTypeOrderByUpdatedAtDesc(NoteVisibility.PUBLIC, targetProfileType);
        List<NoteListItemResponse> items = toListItems(notes, viewerUserId, false);
        items = filterPublicLibraryItems(items, search, subject, tags, courseProgram);
        return sortPublicLibraryItems(items, sort);
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

    private List<NoteListItemResponse> sortPublicLibraryItems(List<NoteListItemResponse> items, String sort) {
        if (sort == null) {
            return items;
        }

        String normalizedSort = sort.trim().toLowerCase();
        return switch (normalizedSort) {
            case PUBLIC_SORT_FEATURED -> PublicNotesScoringUtils.sortByFeatured(items);
            case PUBLIC_SORT_POPULAR, PUBLIC_SORT_COPIED -> PublicNotesScoringUtils.sortByPopular(items);
            case PUBLIC_SORT_RECENT -> PublicNotesScoringUtils.sortByRecent(items);
            case PUBLIC_SORT_VIEWS -> items.stream()
                    .sorted(Comparator
                            .comparingLong((NoteListItemResponse item) -> item.viewCount() == null ? 0L : item.viewCount())
                            .reversed()
                            .thenComparing(NoteListItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            case PUBLIC_SORT_TITLE -> items.stream()
                    .sorted(Comparator.comparing(
                            item -> StringUtils.defaultIfBlank(item.title(), "Untitled note"),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
            default -> items;
        };
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
        return normalizeCoursePrograms(noteRepository.findCourseProgramValuesByVisibility(NoteVisibility.PUBLIC));
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

    private List<NoteListItemResponse> toListItems(List<NoteEntity> notes, UUID viewerUserId, boolean includeOwnerUserId) {
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream().map(NoteEntity::getId).toList();
        List<UUID> ownerIds = notes.stream()
                .map(NoteEntity::getOwnerUserId)
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
        Map<UUID, OffsetDateTime> lastSessionCompletedAtByNoteId = quizSessionHistoryService
                .findLatestSessionCompletedAtByNoteIds(viewerUserId, noteIds);
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

    private NoteTargetProfileType resolveTargetProfileType(NoteEntity note) {
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
                resolveStudyPackStatus(entity, studyPack),
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
                hasGeneratedQuiz && featureGateService.hasFeatureAccess(planType, Feature.ADAPTIVE_QUIZ),
                hasGeneratedQuiz && featureGateService.hasFeatureAccess(planType, Feature.DIFFICULTY_SELECTION)
        );
    }

    private NoteListItemResponse mapToListItemResponse(
            NoteEntity note,
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
                resolveTargetProfileType(note).name(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                studyPack == null ? "" : SummaryPreviewUtils.buildSummaryPreview(studyPack.getSummary(), SUMMARY_PREVIEW_MAX_LENGTH),
                resolveVisibility(note).name(),
                studyPack == null ? null : studyPack.getId().toString(),
                resolveStudyPackStatus(note, studyPack),
                studyPack == null || studyPack.getQuiz() == null ? null : studyPack.getQuiz().size(),
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
                likedByCurrentUser
        );
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
                resolveStudyPackStatus(note, studyPack),
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
        if (isNoteLibOfficialAccount(user)) {
            return OFFICIAL_AUTHOR_DISPLAY_NAME;
        }
        if (user == null) {
            return DEFAULT_AUTHOR_NAME;
        }
        String displayName = normalizeOptionalText(user.getDisplayName());
        if (displayName != null) {
            return displayName;
        }
        String firstName = normalizeOptionalText(user.getFirstName());
        if (firstName != null) {
            return firstName;
        }
        return DEFAULT_AUTHOR_NAME;
    }

    private String resolvePublicAuthorUsername(UserEntity user) {
        return normalizeOptionalText(user == null ? null : user.getUsername());
    }

    private boolean isOfficialAuthor(UserEntity user) {
        return isNoteLibOfficialAccount(user) || (user != null && user.getRole() == UserRole.ADMIN);
    }

    private boolean isNoteLibOfficialAccount(UserEntity user) {
        String email = user == null ? null : normalizeOptionalText(user.getEmail());
        return OFFICIAL_AUTHOR_EMAIL.equalsIgnoreCase(email);
    }

    private boolean isCurrentUser(UUID ownerUserId, UUID viewerUserId) {
        return ownerUserId != null && ownerUserId.equals(viewerUserId);
    }

    private String resolveStudyPackStatus(NoteEntity note, StudyPackEntity studyPack) {
        NoteStatus noteStatus = resolveStatus(note);
        if (noteStatus == NoteStatus.GENERATED) {
            return STUDY_PACK_STATUS_READY;
        }
        if (noteStatus == NoteStatus.GENERATING) {
            return STUDY_PACK_STATUS_GENERATING;
        }
        if (noteStatus == NoteStatus.FAILED) {
            return STUDY_PACK_STATUS_FAILED;
        }
        if (studyPack == null) {
            return STUDY_PACK_STATUS_DRAFT;
        }
        return STUDY_PACK_STATUS_READY;
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

    private NoteVisibility resolveVisibility(NoteEntity note) {
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

    private NoteStatus resolveStatus(NoteEntity note) {
        return note.getStatus() == null ? NoteStatus.DRAFT : note.getStatus();
    }

}
