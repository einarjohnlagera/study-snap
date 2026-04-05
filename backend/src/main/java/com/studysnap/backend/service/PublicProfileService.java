package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.PublicProfileNotFoundException;
import com.studysnap.backend.exception.PublicProfilePrivateException;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.ContentPreviewUtils;
import com.studysnap.backend.util.SummaryPreviewUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicProfileService {
    private static final String OFFICIAL_AUTHOR_DISPLAY_NAME = "NoteLib";
    private static final String OFFICIAL_AUTHOR_EMAIL = "einar.lagera@gmail.com";
    private static final String DEFAULT_AUTHOR_NAME = "Anonymous learner";
    private static final String DEFAULT_PUBLIC_TITLE_SLUG = "untitled-note";
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 180;
    private static final int SUMMARY_PREVIEW_MAX_LENGTH = 180;

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;

    public PublicProfileResponse getByUserId(String userIdRaw, UUID viewerUserId) {
        UUID userId = UuidParsingUtils.parseUuidOrThrow(userIdRaw, PublicProfileNotFoundException::new);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(PublicProfileNotFoundException::new);
        boolean publicProfileVisible = Boolean.TRUE.equals(user.getPublicProfileVisible());
        boolean viewerOwnsProfile = userId.equals(viewerUserId);
        if (!publicProfileVisible && !viewerOwnsProfile) {
            throw new PublicProfilePrivateException();
        }

        List<NoteEntity> publicNotes = noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC);
        Map<UUID, Long> copyCountsByNoteId = loadCopyCounts(publicNotes);
        Map<UUID, StudyPackEntity> studyPackByNoteId = loadStudyPacks(publicNotes);
        long totalCopies = copyCountsByNoteId.values().stream().mapToLong(Long::longValue).sum();

        return new PublicProfileResponse(
                resolvePublicDisplayName(user),
                normalizeOptionalText(user.getBio()),
                user.getLearnerLevel() == null ? null : user.getLearnerLevel().name(),
                normalizeOptionalText(user.getCourseProgram()),
                user.getProfileType() == null ? null : user.getProfileType().name(),
                isOfficialAuthor(user),
                publicProfileVisible,
                publicNotes.size(),
                totalCopies,
                publicNotes.stream()
                        .map(note -> new PublicProfileNoteResponse(
                                note.getId().toString(),
                                note.getTitle(),
                                normalizeOptionalText(note.getCourseProgram()),
                                note.getSubject(),
                                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                                SummaryPreviewUtils.buildSummaryPreview(
                                        studyPackByNoteId.get(note.getId()) == null ? null : studyPackByNoteId.get(note.getId()).getSummary(),
                                        SUMMARY_PREVIEW_MAX_LENGTH
                                ),
                                copyCountsByNoteId.getOrDefault(note.getId(), 0L),
                                slugify(note.getTitle(), DEFAULT_PUBLIC_TITLE_SLUG)
                        ))
                        .toList()
        );
    }

    private Map<UUID, Long> loadCopyCounts(List<NoteEntity> publicNotes) {
        if (publicNotes.isEmpty()) {
            return Map.of();
        }

        List<UUID> noteIds = publicNotes.stream()
                .map(NoteEntity::getId)
                .toList();
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (NoteCopyCountProjection projection : noteRepository.countCopiedPublicNotesBySourceNoteIds(noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getCopyCount());
            }
        }
        return countsByNoteId;
    }

    private Map<UUID, StudyPackEntity> loadStudyPacks(List<NoteEntity> publicNotes) {
        if (publicNotes.isEmpty()) {
            return Map.of();
        }

        List<UUID> noteIds = publicNotes.stream()
                .map(NoteEntity::getId)
                .toList();
        Map<UUID, StudyPackEntity> studyPackByNoteId = new HashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByNoteIdIn(noteIds)) {
            if (studyPack.getNoteId() != null) {
                studyPackByNoteId.put(studyPack.getNoteId(), studyPack);
            }
        }
        return studyPackByNoteId;
    }

    private String resolvePublicDisplayName(UserEntity user) {
        if (isNoteLibOfficialAccount(user)) {
            return OFFICIAL_AUTHOR_DISPLAY_NAME;
        }
        String displayName = normalizeOptionalText(user == null ? null : user.getDisplayName());
        if (displayName != null) {
            return displayName;
        }
        String firstName = normalizeOptionalText(user == null ? null : user.getFirstName());
        if (firstName != null) {
            return firstName;
        }
        return DEFAULT_AUTHOR_NAME;
    }

    private boolean isOfficialAuthor(UserEntity user) {
        return isNoteLibOfficialAccount(user) || (user != null && user.getRole() == UserRole.ADMIN);
    }

    private boolean isNoteLibOfficialAccount(UserEntity user) {
        String email = normalizeOptionalText(user == null ? null : user.getEmail());
        return OFFICIAL_AUTHOR_EMAIL.equalsIgnoreCase(email);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String slugify(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }
}
