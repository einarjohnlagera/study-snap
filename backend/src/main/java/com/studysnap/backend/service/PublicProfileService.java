package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.dto.PublicProfileSummaryResponse;
import com.studysnap.backend.dto.PublicProfileFocusResponse;
import com.studysnap.backend.dto.SubjectCount;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.PublicProfileNotFoundException;
import com.studysnap.backend.exception.PublicProfilePrivateException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicProfileMetricsRepository;
import com.studysnap.backend.repository.NoteSubjectCountProjection;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.ContentPreviewUtils;
import com.studysnap.backend.util.SummaryPreviewUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

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
    private final NoteCourseProgramRepository noteCourseProgramRepository;
    private final StudyPackRepository studyPackRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final PublicProfileMetricsRepository publicProfileMetricsRepository;

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

    public PublicProfileSummaryResponse getSummaryByUserId(String userIdRaw, UUID viewerUserId) {
        UUID userId = UuidParsingUtils.parseUuidOrThrow(userIdRaw, PublicProfileNotFoundException::new);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(PublicProfileNotFoundException::new);
        return buildSummary(user, userId, viewerUserId);
    }

    public PublicProfileSummaryResponse getSummaryByUsername(String usernameRaw, UUID viewerUserId) {
        String username = normalizeOptionalText(usernameRaw);
        if (username == null) {
            throw new PublicProfileNotFoundException();
        }
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(PublicProfileNotFoundException::new);
        return buildSummary(user, user.getId(), viewerUserId);
    }

    public PublicProfileFocusResponse getFocusByUserId(String userIdRaw, UUID viewerUserId) {
        UUID userId = UuidParsingUtils.parseUuidOrThrow(userIdRaw, PublicProfileNotFoundException::new);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(PublicProfileNotFoundException::new);
        assertProfileVisible(user, userId, viewerUserId);
        return publicProfileMetricsRepository.focus(userId);
    }

    public PublicProfileFocusResponse getFocusByUsername(String usernameRaw, UUID viewerUserId) {
        String username = normalizeOptionalText(usernameRaw);
        if (username == null) {
            throw new PublicProfileNotFoundException();
        }
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(PublicProfileNotFoundException::new);
        assertProfileVisible(user, user.getId(), viewerUserId);
        return publicProfileMetricsRepository.focus(user.getId());
    }

    private PublicProfileSummaryResponse buildSummary(UserEntity user, UUID userId, UUID viewerUserId) {
        assertProfileVisible(user, userId, viewerUserId);
        return new PublicProfileSummaryResponse(resolvePublicDisplayName(user),
                normalizeOptionalText(user.getBio()),
                noteRepository.countByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC));
    }

    private void assertProfileVisible(UserEntity user, UUID userId, UUID viewerUserId) {
        if (!Boolean.TRUE.equals(user.getPublicProfileVisible()) && !userId.equals(viewerUserId)) {
            throw new PublicProfilePrivateException();
        }
    }

    private PublicProfileResponse buildPublicProfile(UserEntity user, UUID userId, UUID viewerUserId) {
        boolean publicProfileVisible = Boolean.TRUE.equals(user.getPublicProfileVisible());
        boolean viewerOwnsProfile = userId.equals(viewerUserId);
        assertProfileVisible(user, userId, viewerUserId);

        int publicNotesCount = Math.toIntExact(noteRepository.countByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC));
        PublicProfileMetricsRepository.Totals totals = publicProfileMetricsRepository.totals(userId);
        List<UUID> candidateIds = publicProfileMetricsRepository.candidateNoteIds(userId);
        Set<UUID> candidateIdSet = new HashSet<>(candidateIds);
        List<NoteEntity> publicNotes = candidateIds.isEmpty() ? List.of() : noteRepository.findAllById(candidateIds)
                .stream()
                .filter(note -> userId.equals(note.getOwnerUserId())
                        && note.getVisibility() == NoteVisibility.PUBLIC
                        && candidateIdSet.contains(note.getId()))
                .sorted(Comparator.comparing(NoteEntity::getUpdatedAt).reversed())
                .toList();
        // One batched lookup for the whole profile rather than a query per card.
        Map<UUID, List<String>> programNamesByNoteId = new LinkedHashMap<>();
        noteCourseProgramRepository
                .findByNoteIds(publicNotes.stream().map(NoteEntity::getId).toList())
                .forEach((noteId, programs) -> programNamesByNoteId.put(
                        noteId,
                        programs.stream().map(ApplicableProgramResponse::name).toList()
                ));
        Map<UUID, Long> copyCountsByNoteId = loadCopyCounts(publicNotes);
        Map<UUID, Long> shareCountsByNoteId = loadPublicEventCounts(publicNotes, AnalyticsEventType.PUBLIC_NOTE_SHARED);
        Map<UUID, Long> viewCountsByNoteId = loadPublicEventCounts(publicNotes, AnalyticsEventType.PUBLIC_NOTE_VIEWED);
        Map<UUID, String> studyPackSummariesByNoteId = loadStudyPackSummaries(publicNotes);
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
                publicNotesCount,
                totals.copies(),
                totals.shares(),
                totals.views(),
                totalProfileShares,
                notesBySubject,
                publicSubjectCounts.size(),
                publicNotes.stream()
                        .map(note -> new PublicProfileNoteResponse(
                                note.getId().toString(),
                                note.getTitle(),
                                normalizeOptionalText(note.getCourseProgram()),
                                // M3/L3: profile cards rendered no program for curated notes, whose legacy
                                // string is null by definition. Batched once above rather than per note.
                                programNamesByNoteId.getOrDefault(note.getId(), List.of()),
                                note.getDomainContext() == null ? null : note.getDomainContext().name(),
                                note.getLearnerLevel() == null ? null : note.getLearnerLevel().name(),
                                note.getSubject(),
                                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                                ContentPreviewUtils.buildContentPreview(note.getContent(), CONTENT_PREVIEW_MAX_LENGTH),
                                SummaryPreviewUtils.buildSummaryPreview(
                                        studyPackSummariesByNoteId.get(note.getId()),
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

    private Map<UUID, String> loadStudyPackSummaries(List<NoteEntity> publicNotes) {
        if (publicNotes.isEmpty()) {
            return Map.of();
        }

        List<UUID> noteIds = publicNotes.stream()
                .map(NoteEntity::getId)
                .toList();
        Map<UUID, String> summariesByNoteId = new HashMap<>();
        for (StudyPackRepository.NoteSummary studyPack : studyPackRepository.findSummariesByNoteIdIn(noteIds)) {
            if (studyPack.getNoteId() != null) {
                summariesByNoteId.put(studyPack.getNoteId(), studyPack.getSummary());
            }
        }
        return summariesByNoteId;
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
