package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteSubjectCountProjection;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.dto.ApplicableProgramResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private PublicProfileService publicProfileService;

    @BeforeEach
    void setUp() {
        publicProfileService = new PublicProfileService(userRepository, noteRepository, noteCourseProgramRepository, studyPackRepository, analyticsEventRepository);
    }

    @Test
    void getByUserId_returnsPublicProfileWithAggregatedCopyCounts() {
        UUID userId = UUID.randomUUID();
        UUID noteOneId = UUID.randomUUID();
        UUID noteTwoId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Study Buddy");
        user.setUsername("studybuddy");
        user.setBio("Biology teacher sharing board-style review notes.");
        user.setFirstName("Study");
        user.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        user.setCourseProgram("Biology");
        user.setProfileType(ProfileType.TEACHER);
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        NoteEntity noteOne = buildPublicNote(noteOneId, userId, "Plant Cells", "Biology", new String[]{"cells", "plants"});
        NoteEntity noteTwo = buildPublicNote(noteTwoId, userId, "Atomic Bonds", "Chemistry", new String[]{"atoms"});
        StudyPackEntity noteOneStudyPack = buildStudyPack(noteOneId, "Cells make up plant tissue.");
        StudyPackEntity noteTwoStudyPack = buildStudyPack(noteTwoId, "Bonds hold atoms together.");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(noteOne, noteTwo));
        when(studyPackRepository.findByNoteIdIn(List.of(noteOneId, noteTwoId)))
                .thenReturn(List.of(noteOneStudyPack, noteTwoStudyPack));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(List.of(noteOneId, noteTwoId)))
                .thenReturn(List.of(
                        projection(noteOneId, 5L),
                        projection(noteTwoId, 2L)
                ));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(AnalyticsEventType.PUBLIC_NOTE_SHARED, List.of(noteOneId, noteTwoId)))
                .thenReturn(List.of(
                        eventProjection(noteOneId, 3L),
                        eventProjection(noteTwoId, 1L)
                ));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(AnalyticsEventType.PUBLIC_NOTE_VIEWED, List.of(noteOneId, noteTwoId)))
                .thenReturn(List.of(
                        eventProjection(noteOneId, 12L),
                        eventProjection(noteTwoId, 8L)
                ));
        when(analyticsEventRepository.countByEventTypeAndEntityId(AnalyticsEventType.PUBLIC_PROFILE_SHARED, userId))
                .thenReturn(6L);
        when(noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(
                        subjectProjection("Biology", 1L),
                        subjectProjection("Chemistry", 1L)
                ));

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Study Buddy");
        assertThat(response.username()).isEqualTo("studybuddy");
        assertThat(response.bio()).isEqualTo("Biology teacher sharing board-style review notes.");
        assertThat(response.learnerLevel()).isEqualTo("BOARD_EXAM_REVIEW");
        assertThat(response.courseProgram()).isEqualTo("Biology");
        assertThat(response.profileType()).isEqualTo("TEACHER");
        assertThat(response.isOfficial()).isFalse();
        assertThat(response.publicProfileVisible()).isTrue();
        assertThat(response.userId()).isEqualTo(userId.toString());
        assertThat(response.publicNotesCount()).isEqualTo(2);
        assertThat(response.totalCopies()).isEqualTo(7);
        assertThat(response.totalShares()).isEqualTo(4);
        assertThat(response.totalViews()).isEqualTo(20);
        assertThat(response.totalProfileShares()).isEqualTo(6);
        assertThat(response.notesBySubject())
                .extracting("subject", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Biology", 1),
                        org.assertj.core.groups.Tuple.tuple("Chemistry", 1)
                );
        assertThat(response.totalPublicSubjectCount()).isEqualTo(2);
        assertThat(response.publicNotes())
                .extracting(
                        PublicProfileNoteResponse::noteId,
                        PublicProfileNoteResponse::domainContext,
                        PublicProfileNoteResponse::learnerLevel,
                        PublicProfileNoteResponse::contentPreview,
                        PublicProfileNoteResponse::summaryPreview,
                        PublicProfileNoteResponse::copyCount,
                        PublicProfileNoteResponse::shareCount,
                        PublicProfileNoteResponse::viewCount,
                        PublicProfileNoteResponse::slug
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                noteOneId.toString(),
                                DomainContext.GENERAL_EDUCATION.name(),
                                LearnerLevel.COLLEGE.name(),
                                "Plant Cells source note content",
                                "Cells make up plant tissue.",
                                5L,
                                3L,
                                12L,
                                "plant-cells"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                noteTwoId.toString(),
                                DomainContext.GENERAL_EDUCATION.name(),
                                LearnerLevel.COLLEGE.name(),
                                "Atomic Bonds source note content",
                                "Bonds hold atoms together.",
                                2L,
                                1L,
                                8L,
                                "atomic-bonds"
                        )
                );
    }

    @Test
    void getByUserId_resolvesApplicableProgramsFromTheJoinForACuratedNote() {
        // M3/L3 regression. The mock was previously left unstubbed, so it returned an empty map and the
        // join-first path was never exercised -- every assertion passed off the legacy string alone.
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setUsername("creator");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        // A curated note: legacy string null by ADR-001 definition, applicability only in the join.
        NoteEntity curated = buildPublicNote(noteId, userId, "Algebra", "Mathematics", new String[]{});
        curated.setCourseProgram(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(curated));
        when(noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());
        when(noteCourseProgramRepository.findByNoteIds(List.of(noteId))).thenReturn(Map.of(
                noteId,
                List.of(
                        new ApplicableProgramResponse(UUID.randomUUID(), "Civil Engineering"),
                        new ApplicableProgramResponse(UUID.randomUUID(), "Mechanical Engineering")
                )
        ));

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.publicNotes()).singleElement()
                .satisfies(note -> {
                    assertThat(note.courseProgram()).isNull();
                    assertThat(note.applicablePrograms())
                            .containsExactly("Civil Engineering", "Mechanical Engineering");
                });
    }

    @Test
    void getByUserId_returnsEmptyNotesForExistingUserWithoutPublicNotes() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setUsername("creator");
        user.setBio(null);
        user.setLearnerLevel(null);
        user.setCourseProgram(null);
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());
        when(noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Creator");
        assertThat(response.bio()).isNull();
        assertThat(response.learnerLevel()).isNull();
        assertThat(response.courseProgram()).isNull();
        assertThat(response.publicNotesCount()).isZero();
        assertThat(response.totalCopies()).isZero();
        assertThat(response.totalShares()).isZero();
        assertThat(response.totalViews()).isZero();
        assertThat(response.notesBySubject()).isEmpty();
        assertThat(response.totalPublicSubjectCount()).isZero();
        assertThat(response.publicNotes()).isEmpty();
    }

    @Test
    void getByUserId_returnsSinglePublicSubjectStat() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());
        when(noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(subjectProjection("Biology", 4L)));

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.notesBySubject())
                .extracting("subject", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Biology", 4));
        assertThat(response.totalPublicSubjectCount()).isEqualTo(1);
        verify(noteRepository).countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC);
    }

    @Test
    void getByUserId_capsPublicSubjectStatsAtFiveAndKeepsDistinctSubjectTotal() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());
        when(noteRepository.countSubjectsByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(
                        subjectProjection("Biology", 9L),
                        subjectProjection("Chemistry", 8L),
                        subjectProjection("Physics", 7L),
                        subjectProjection("Anatomy", 6L),
                        subjectProjection("Microbiology", 5L),
                        subjectProjection("Pharmacology", 4L)
                ));

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.notesBySubject())
                .extracting("subject", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Biology", 9),
                        org.assertj.core.groups.Tuple.tuple("Chemistry", 8),
                        org.assertj.core.groups.Tuple.tuple("Physics", 7),
                        org.assertj.core.groups.Tuple.tuple("Anatomy", 6),
                        org.assertj.core.groups.Tuple.tuple("Microbiology", 5)
                );
        assertThat(response.totalPublicSubjectCount()).isEqualTo(6);
    }

    @Test
    void getByUsername_returnsMatchingPublicProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setUsername("creator");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        when(userRepository.findByUsernameIgnoreCase("creator")).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUsername("creator", null);

        assertThat(response.displayName()).isEqualTo("Creator");
        assertThat(response.username()).isEqualTo("creator");
        assertThat(response.publicNotes()).isEmpty();
    }

    @Test
    void getByUserId_marksAdminAccountsAsOfficial() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("admin@example.com");
        user.setDisplayName("Moderator Mia");
        user.setRole(UserRole.ADMIN);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Moderator Mia");
        assertThat(response.isOfficial()).isTrue();
    }

    @Test
    void getByUserId_rejectsMissingUsers() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        String id = userId.toString();
        assertThatThrownBy(() -> publicProfileService.getByUserId(id, null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("PUBLIC_PROFILE_NOT_FOUND");
    }

    @Test
    void getByUserId_blocksPrivateProfilesForOtherViewers() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Hidden Helper");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String id = userId.toString();
        UUID uuid = UUID.randomUUID();
        assertThatThrownBy(() -> publicProfileService.getByUserId(id, uuid))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode(), Throwable::getMessage)
                .containsExactly("PUBLIC_PROFILE_PRIVATE", "This profile is private.");
    }

    @Test
    void getByUserId_allowsOwnerToViewPrivateProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Hidden Helper");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), userId);

        assertThat(response.displayName()).isEqualTo("Hidden Helper");
        assertThat(response.publicProfileVisible()).isFalse();
    }

    private NoteEntity buildPublicNote(UUID noteId, UUID ownerUserId, String title, String subject, String[] tags) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setSubject(subject);
        note.setDomainContext(DomainContext.GENERAL_EDUCATION);
        note.setLearnerLevel(LearnerLevel.COLLEGE);
        note.setTags(tags);
        note.setContent(title + " source note content");
        note.setVisibility(NoteVisibility.PUBLIC);
        note.setCreatedAt(OffsetDateTime.now().minusDays(1));
        note.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return note;
    }

    private StudyPackEntity buildStudyPack(UUID noteId, String summary) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setNoteId(noteId);
        studyPack.setSummary(summary);
        return studyPack;
    }

    private NoteCopyCountProjection projection(UUID noteId, long copyCount) {
        return new NoteCopyCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getCopyCount() {
                return copyCount;
            }
        };
    }

    private PublicNoteEventCountProjection eventProjection(UUID noteId, long totalCount) {
        return new PublicNoteEventCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getTotalCount() {
                return totalCount;
            }
        };
    }

    private NoteSubjectCountProjection subjectProjection(String subject, long noteCount) {
        return new NoteSubjectCountProjection() {
            @Override
            public String getSubject() {
                return subject;
            }

            @Override
            public long getNoteCount() {
                return noteCount;
            }
        };
    }
}
