package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

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
        return mapToResponse(saved);
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
        return mapToResponse(saved);
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

    private NoteResponse mapToResponse(NoteEntity entity) {
        return new NoteResponse(
                entity.getId().toString(),
                entity.getTitle(),
                entity.getSubject(),
                entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
