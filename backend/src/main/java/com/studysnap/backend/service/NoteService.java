package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final String STUDY_PACK_STATUS_DRAFT = "DRAFT";
    private static final String STUDY_PACK_STATUS_READY = "STUDY_PACK_READY";
    private static final String DEFAULT_PUBLIC_SUBJECT_SLUG = "general";
    private static final String DEFAULT_PUBLIC_TITLE_SLUG = "untitled-note";
    private static final String DEFAULT_AUTHOR_NAME = "Anonymous learner";
    private static final String OFFICIAL_AUTHOR_DISPLAY_NAME = "NoteLib";
    private static final String OFFICIAL_AUTHOR_EMAIL = "einar.lagera@gmail.com";

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final AnalyticsService analyticsService;

    public NoteResponse create(UpsertNoteRequest request, UUID ownerUserId) {
        NoteEntity entity = new NoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(normalizeOptionalText(request.subject()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setContent(normalizeRequiredContent(request.content()));
        entity.setStatus(NoteStatus.DRAFT);
        entity.setVisibility(NoteVisibility.PRIVATE);
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
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));

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
        entity.setSubject(normalizeOptionalText(request.subject()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(entity);
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(saved.getId());
        return mapToResponse(saved, linkedStudyPack);
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));
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
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity source = noteRepository.findById(noteId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));
        boolean isOwner = source.getOwnerUserId() != null && source.getOwnerUserId().equals(ownerUserId);
        if (!isOwner && resolveVisibility(source) != NoteVisibility.PUBLIC) {
            throw new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND);
        }

        NoteEntity copy = new NoteEntity();
        copy.setId(UUID.randomUUID());
        copy.setOwnerUserId(ownerUserId);
        copy.setTitle(source.getTitle());
        copy.setSubject(source.getSubject());
        copy.setTags(source.getTags() == null ? new String[0] : Arrays.copyOf(source.getTags(), source.getTags().length));
        copy.setContent(source.getContent());
        copy.setStatus(NoteStatus.DRAFT);
        copy.setVisibility(NoteVisibility.PRIVATE);
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

    public void deleteById(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));

        studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId)
                .ifPresent(studyPackRepository::delete);
        noteRepository.delete(entity);
    }

    public NoteResponse updateVisibility(String id, String visibilityRaw, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));

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
        return toListItems(notes, ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listPublic(UUID viewerUserId) {
        List<NoteEntity> notes = noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);
        return toListItems(notes, viewerUserId);
    }

    @Transactional(readOnly = true)
    public List<String> listMineSubjects(UUID ownerUserId) {
        return normalizeSubjects(noteRepository.findSubjectValuesByOwnerUserId(ownerUserId));
    }

    @Transactional(readOnly = true)
    public List<String> listPublicSubjects() {
        return normalizeSubjects(noteRepository.findSubjectValuesByVisibility(NoteVisibility.PUBLIC));
    }

    @Transactional(readOnly = true)
    public PublicNoteDetailResponse getPublicById(String id, UUID viewerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));
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
                : noteRepository.findByVisibilityAndSubjectIgnoreCaseOrderByUpdatedAtDesc(
                        NoteVisibility.PUBLIC,
                        unslugify(normalizedSubjectSlug)
                );

        NoteEntity matched = candidates.stream()
                .filter(note -> slugify(note.getSubject(), DEFAULT_PUBLIC_SUBJECT_SLUG).equals(normalizedSubjectSlug))
                .filter(note -> slugify(note.getTitle(), DEFAULT_PUBLIC_TITLE_SLUG).equals(normalizedTitleSlug))
                .findFirst()
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));

        StudyPackEntity linkedStudyPack = findLinkedStudyPack(matched.getId());
        LinkedHashMap<String, Object> analyticsMetadata = new LinkedHashMap<>();
        analyticsMetadata.put("pathType", "seo");
        analyticsMetadata.put("subjectSlug", normalizedSubjectSlug);
        analyticsMetadata.put("titleSlug", normalizedTitleSlug);
        analyticsService.trackEvent(viewerUserId, AnalyticsEventType.PUBLIC_NOTE_VIEWED, matched.getId(), analyticsMetadata);
        return mapToPublicDetail(matched, linkedStudyPack, viewerUserId);
    }

    private List<NoteListItemResponse> toListItems(List<NoteEntity> notes, UUID viewerUserId) {
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream().map(NoteEntity::getId).toList();
        List<UUID> ownerIds = notes.stream()
                .map(NoteEntity::getOwnerUserId)
                .distinct()
                .toList();
        Map<UUID, StudyPackEntity> studyPackByNoteId = new HashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByNoteIdIn(noteIds)) {
            if (studyPack.getNoteId() != null) {
                studyPackByNoteId.put(studyPack.getNoteId(), studyPack);
            }
        }
        Map<UUID, UserEntity> ownerById = new HashMap<>();
        for (UserEntity owner : userRepository.findAllById(ownerIds)) {
            ownerById.put(owner.getId(), owner);
        }

        return notes.stream()
                .map(note -> mapToListItemResponse(
                        note,
                        studyPackByNoteId.get(note.getId()),
                        ownerById.get(note.getOwnerUserId()),
                        viewerUserId
                ))
                .toList();
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
            if (rawTag == null) {
                continue;
            }
            String normalized = rawTag.trim();
            if (normalized.isBlank()) {
                continue;
            }
            normalizedByKey.putIfAbsent(normalized.toLowerCase(), normalized);
        }
        return List.copyOf(normalizedByKey.values());
    }

    private NoteResponse mapToResponse(NoteEntity entity, StudyPackEntity studyPack) {
        List<String> keyConcepts = studyPack == null || studyPack.getKeyConcepts() == null
                ? List.of()
                : studyPack.getKeyConcepts();
        List<com.studysnap.backend.dto.QuizItem> quiz = studyPack == null || studyPack.getQuiz() == null
                ? List.of()
                : studyPack.getQuiz();
        int quizCount = quiz.size();
        boolean hasGeneratedQuiz = !quiz.isEmpty();
        PlanType planType = subscriptionService.resolvePlan(entity.getOwnerUserId());
        return new NoteResponse(
                entity.getId().toString(),
                entity.getTitle(),
                entity.getSubject(),
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
            UserEntity owner,
            UUID viewerUserId
    ) {
        boolean isOfficialAuthor = isOfficialAuthor(owner);
        return new NoteListItemResponse(
                note.getId().toString(),
                note.getOwnerUserId() == null ? null : note.getOwnerUserId().toString(),
                note.getTitle(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                toContentPreview(note.getContent()),
                resolveVisibility(note).name(),
                studyPack == null ? null : studyPack.getId().toString(),
                resolveStudyPackStatus(note, studyPack),
                studyPack == null || studyPack.getQuiz() == null ? null : studyPack.getQuiz().size(),
                resolvePublicAuthorName(owner),
                isOfficialAuthor,
                isCurrentUser(note.getOwnerUserId(), viewerUserId),
                note.getUpdatedAt()
        );
    }

    private PublicNoteDetailResponse mapToPublicDetail(NoteEntity note, StudyPackEntity studyPack, UUID viewerUserId) {
        UserEntity owner = userRepository.findById(note.getOwnerUserId()).orElse(null);
        boolean isOfficialAuthor = isOfficialAuthor(owner);
        return new PublicNoteDetailResponse(
                note.getId().toString(),
                note.getOwnerUserId() == null ? null : note.getOwnerUserId().toString(),
                note.getTitle(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                toContentPreview(note.getContent()),
                resolveStudyPackStatus(note, studyPack),
                studyPack == null ? null : studyPack.getSummary(),
                studyPack == null || studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                studyPack == null || studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz(),
                resolvePublicAuthorName(owner),
                isOfficialAuthor,
                isCurrentUser(note.getOwnerUserId(), viewerUserId),
                note.getUpdatedAt()
        );
    }

    private String resolvePublicAuthorName(UserEntity user) {
        if (isOfficialAuthor(user)) {
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

    private boolean isOfficialAuthor(UserEntity user) {
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
                .map(this::normalizeOptionalText)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(subject -> normalized.putIfAbsent(subject.toLowerCase(Locale.ROOT), subject));
        return List.copyOf(normalized.values());
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

    private String unslugify(String slug) {
        return slug.replace('-', ' ').trim();
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

    private String toContentPreview(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= CONTENT_PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CONTENT_PREVIEW_MAX_LENGTH - 3) + "...";
    }
}
