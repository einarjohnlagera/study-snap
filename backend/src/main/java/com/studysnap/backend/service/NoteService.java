package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
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
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final String STUDY_PACK_STATUS_DRAFT = "DRAFT";
    private static final String STUDY_PACK_STATUS_READY = "STUDY_PACK_READY";

    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;

    public NoteResponse create(UpsertNoteRequest request, UUID ownerUserId) {
        NoteEntity entity = new NoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(normalizeOptionalText(request.subject()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setContent(normalizeRequiredContent(request.content()));
        entity.setVisibility(NoteVisibility.PRIVATE);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        NoteEntity saved = noteRepository.save(entity);
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

        entity.setTitle(normalizeOptionalText(request.title()));
        entity.setSubject(normalizeOptionalText(request.subject()));
        entity.setTags(normalizeTags(request.tags()).toArray(String[]::new));
        entity.setContent(normalizeRequiredContent(request.content()));
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
        copy.setVisibility(NoteVisibility.PRIVATE);
        copy.setCreatedAt(OffsetDateTime.now());
        copy.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(copy);
        return mapToResponse(saved, null);
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
        return toListItems(notes);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listPublic(UUID viewerUserId) {
        List<NoteEntity> notes = noteRepository.findByVisibilityExcludingOwnerOrderByUpdatedAtDesc(
                NoteVisibility.PUBLIC,
                viewerUserId
        );
        return toListItems(notes);
    }

    @Transactional(readOnly = true)
    public PublicNoteDetailResponse getPublicById(String id) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity entity = noteRepository.findByIdAndVisibility(noteId, NoteVisibility.PUBLIC)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(entity.getId());
        return mapToPublicDetail(entity, linkedStudyPack);
    }

    private List<NoteListItemResponse> toListItems(List<NoteEntity> notes) {
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream().map(NoteEntity::getId).toList();
        Map<UUID, StudyPackEntity> studyPackByNoteId = new HashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByNoteIdIn(noteIds)) {
            if (studyPack.getNoteId() != null) {
                studyPackByNoteId.put(studyPack.getNoteId(), studyPack);
            }
        }

        return notes.stream()
                .map(note -> mapToListItemResponse(note, studyPackByNoteId.get(note.getId())))
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
        return new NoteResponse(
                entity.getId().toString(),
                entity.getTitle(),
                entity.getSubject(),
                entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()),
                entity.getContent(),
                resolveVisibility(entity).name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                studyPack == null ? null : studyPack.getId().toString(),
                resolveStudyPackStatus(studyPack)
        );
    }

    private NoteListItemResponse mapToListItemResponse(NoteEntity note, StudyPackEntity studyPack) {
        return new NoteListItemResponse(
                note.getId().toString(),
                note.getTitle(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                toContentPreview(note.getContent()),
                resolveVisibility(note).name(),
                studyPack == null ? null : studyPack.getId().toString(),
                resolveStudyPackStatus(studyPack),
                note.getUpdatedAt()
        );
    }

    private PublicNoteDetailResponse mapToPublicDetail(NoteEntity note, StudyPackEntity studyPack) {
        return new PublicNoteDetailResponse(
                note.getId().toString(),
                note.getTitle(),
                note.getSubject(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                toContentPreview(note.getContent()),
                resolveStudyPackStatus(studyPack),
                studyPack == null ? null : studyPack.getSummary(),
                studyPack == null || studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                studyPack == null || studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz(),
                note.getUpdatedAt()
        );
    }

    private String resolveStudyPackStatus(StudyPackEntity studyPack) {
        if (studyPack == null) {
            return STUDY_PACK_STATUS_DRAFT;
        }
        return STUDY_PACK_STATUS_READY;
    }

    private StudyPackEntity findLinkedStudyPack(UUID noteId) {
        return studyPackRepository.findByNoteId(noteId).orElse(null);
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
