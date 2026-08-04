package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.dto.SubjectCount;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.PublicProfileNotFoundException;
import com.studysnap.backend.exception.PublicProfilePrivateException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteSubjectCountProjection;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
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
    private static final int PUBLIC_SUBJECT_COUNT_LIMIT = 5;

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    public PublicProfileResponse getByUserId(String userIdRaw, UUID viewerUserId) {
        UUID userId = UuidParsingUtils.parseUuidOrThrow(userIdRaw, PublicProfileNotFoundException::new);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(PublicProfileNotFoundException::new);
        return buildPublicProfile(user, userId, viewerUserId);
    }

    public PublicProfileResponse getByUsername(String usernameRaw, UUID viewerUserId) {
        String username = normalizeOptionalText(usernameRaw);
        if (username == null) {
            throw new PublicProfileNotFoundException();
        }
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(PublicProfileNotFoundException::new);
        return buildPublicProfile(user, user.getId(), viewerUserId);
    }

    private PublicProfileResponse buildPublicProfile(UserEntity user, UUID userId, UUID viewerUserId) {
        boolean publicProfileVisible = Boolean.TRUE.equals(user.getPublicProfileVisible());
        boolean viewerOwnsProfile = userId.equals(viewerUserId);
        if (!publicProfileVisible && !viewerOwnsProfile) {
            throw new PublicProfilePrivateException();
        }

        List<NoteEntity> publicNotes = noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC);
        Map<UUID, Long> copyCountsByNoteId = loadCopyCounts(publicNotes);
        Map<UUID, Long> shareCountsByNoteId = loadPublicEventCounts(publicNotes, AnalyticsEventType.PUBLIC_NOTE_SHARED);
        Map<UUID, Long> viewCountsByNoteId = loadPublicEventCounts(publicNotes, AnalyticsEventType.PUBLIC_NOTE_VIEWED);
        Map<UUID, StudyPackEntity> studyPackByNoteId = loadStudyPacks(publicNotes);
        long totalCopies = copyCountsByNoteId.values().stream().mapToLong(Long::longValue).sum();
        long totalShares = shareCountsByNoteId.values().stream().mapToLong(Long::longValue).sum();
        long totalViews = viewCountsByNoteId.values().stream().mapToLong(Long::longValue).sum();
        long totalProfileShares = analyticsEventRepository.countByEventTypeAndEntityId(AnalyticsEventType.PUBLIC_PROFILE_SHARED, userId);
        List<NoteSubjectCountProjection> publicSubjectCounts = noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC);
        List<SubjectCount> notesBySubject = publicSubjectCounts.stream()
                .limit(PUBLIC_SUBJECT_COUNT_LIMIT)
                .map(subjectCount -> new SubjectCount(subjectCount.getSubject(), Math.toIntExact(subjectCount.getNoteCount())))
                .toList();

        return new PublicProfileResponse(
                resolvePublicDisplayName(user),
                normalizeOptionalText(user.getUsername()),
                normalizeOptionalText(user.getBio()),
                user.getLearnerLevel() == null ? null : user.getLearnerLevel().name(),
                normalizeOptionalText(user.getCourseProgram()),
                user.getProfileType() == null ? null : user.getProfileType().name(),
                isOfficialAuthor(user),
                publicProfileVisible,
                viewerOwnsProfile,
                userId.toString(),
                publicNotes.size(),
                totalCopies,
                totalShares,
                totalViews,
                totalProfileShares,
                notesBySubject,
                publicSubjectCounts.size(),
                publicNotes.stream()
                        .map(note -> new PublicProfileNoteResponse(
                                note.getId().toString(),
                                note.getTitle(),
                                normalizeOptionalText(note.getCourseProgram()),
                                note.getDomainContext() == null ? null : note.getDomainContext().name(),
                                note.getLearnerLevel() == null ? null : note.getLearnerLevel().name(),
                                note.getSubject(),
                                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                                SummaryPreviewUtils.buildSummaryPreview(
                                        studyPackByNoteId.get(note.getId()) == null ? null : studyPackByNoteId.get(note.getId()).getSummary(),
                                        SUMMARY_PREVIEW_MAX_LENGTH
                                ),
                                copyCountsByNoteId.getOrDefault(note.getId(), 0L),
                                shareCountsByNoteId.getOrDefault(note.getId(), 0L),
                                viewCountsByNoteId.getOrDefault(note.getId(), 0L),
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

    private Map<UUID, Long> loadPublicEventCounts(List<NoteEntity> publicNotes, AnalyticsEventType eventType) {
        if (publicNotes.isEmpty()) {
            return Map.of();
        }

        List<UUID> noteIds = publicNotes.stream()
                .map(NoteEntity::getId)
                .toList();
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (PublicNoteEventCountProjection projection : analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(eventType, noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getTotalCount());
            }
        }
        return countsByNoteId;
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
