package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteEntity;
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
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(saved.getId(), ownerUserId);
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
        StudyPackEntity linkedStudyPack = findLinkedStudyPack(entity.getId(), ownerUserId);
        return mapToResponse(entity, linkedStudyPack);
    }

    public NoteResponse cloneNote(String id, UUID ownerUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(
                id,
                "NOTE_NOT_FOUND",
                "Note not found.",
                HttpStatus.NOT_FOUND
        );
        NoteEntity source = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new AppException("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND));

        NoteEntity clone = new NoteEntity();
        clone.setId(UUID.randomUUID());
        clone.setOwnerUserId(ownerUserId);
        clone.setTitle(source.getTitle());
        clone.setSubject(source.getSubject());
        clone.setTags(source.getTags() == null ? new String[0] : Arrays.copyOf(source.getTags(), source.getTags().length));
        clone.setContent(source.getContent());
        clone.setCreatedAt(OffsetDateTime.now());
        clone.setUpdatedAt(OffsetDateTime.now());

        NoteEntity saved = noteRepository.save(clone);
        return mapToResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public List<NoteListItemResponse> listMine(UUID ownerUserId) {
        List<NoteEntity> notes = noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId);
        if (notes.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = notes.stream().map(NoteEntity::getId).toList();
        Map<UUID, StudyPackEntity> studyPackByNoteId = new HashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByOwnerUserIdAndNoteIdIn(ownerUserId, noteIds)) {
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
                studyPack == null ? null : studyPack.getId().toString(),
                resolveStudyPackStatus(studyPack),
                note.getUpdatedAt()
        );
    }

    private String resolveStudyPackStatus(StudyPackEntity studyPack) {
        if (studyPack == null) {
            return STUDY_PACK_STATUS_DRAFT;
        }
        return STUDY_PACK_STATUS_READY;
    }

    private StudyPackEntity findLinkedStudyPack(UUID noteId, UUID ownerUserId) {
        return studyPackRepository.findByOwnerUserIdAndNoteId(ownerUserId, noteId).orElse(null);
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
