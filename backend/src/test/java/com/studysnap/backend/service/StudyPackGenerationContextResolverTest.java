package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyPackGenerationContextResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;
    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;

    @Test
    void resolve_populatesNoteAuthoringAxesAndPrefersDomainContext() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("Senior High – STEM");
        note.setDomainContext(DomainContext.ENGINEERING_SCIENCES);
        note.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        note.setSubject("Physics");
        note.setTags(new String[]{"electricity"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(context.courseProgram()).isEqualTo("Senior High – STEM");
        assertThat(context.subject()).isEqualTo("Physics");
        assertThat(context.tags()).containsExactly("electricity");
        assertThat(context.domainContext()).isEqualTo(DomainContext.ENGINEERING_SCIENCES);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context))
                .isEqualTo("Engineering Sciences");
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.SENIOR_HIGH);
    }

    @Test
    void resolve_usesNoteProgramAsAuthoringDomainWhenDomainContextIsMissing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Education");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("Science");
        note.setSubject("Biology");

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(context.courseProgram()).isEqualTo("Science");
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context)).isEqualTo("Science");
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.PROFESSIONAL);
    }

    @Test
    void resolve_usesExactlyOneJoinedProgramNameBeforeThePersonalString() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setCourseProgram("Personal note program");
        when(noteCourseProgramRepository.findByNoteId(note.getId()))
                .thenReturn(List.of(new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Nursing")));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, null
        );

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Nursing");
    }

    @Test
    void resolve_neverTurnsMultipleJoinedProgramsIntoAnLlmDomain() {
        // This test used to leave userRepository.findById UNSTUBBED, so Mockito returned Optional.empty()
        // and both assertions passed because the user was not found -- not because of the multi-program
        // rule they claimed to prove. Every other test in this class stubs it. Stubbing a real user here
        // is what makes this exercise the production path.
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setDomainContext(DomainContext.NURSING);
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(
                new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Nursing"),
                new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Pharmacy")
        ));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository
        );

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        // The binding guarantee (ADR-001) is that a program LIST never becomes the authoring domain. With
        // more than one joined program the single-program branch is skipped, and Domain Context -- which
        // the invariant makes mandatory above one program -- is what reaches the prompt.
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context))
                .isEqualTo(DomainContext.NURSING.getLabel());
        assertThat(context.courseProgram()).doesNotContain("Pharmacy");
    }

    @Test
    void resolve_multiProgramNoteWithoutDomainContextFallsBackToASingleValueNotAList() {
        // This is a valid saved learner-note state now; generation readiness is checked only when a Study
        // Pack is requested. The resolver must still produce a SINGLE fallback value, never a program list.
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(
                new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Nursing"),
                new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Pharmacy")
        ));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository
        );

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
        assertThat(context.courseProgram()).doesNotContain("Nursing");
        assertThat(context.courseProgram()).doesNotContain("Pharmacy");
    }

    @Test
    void resolve_learnerOwnedNoteInheritsProfileProgram() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Software Engineering");
        user.setRole(UserRole.USER);
        user.setProfileType(ProfileType.STUDENT);
        user.setOnboardingCompletedAt(OffsetDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("   ");
        note.setSubject("Computing");
        note.setTags(new String[]{"algorithms"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context))
                .isEqualTo("Software Engineering");
    }

    @Test
    void resolve_curatorOwnedNoteDoesNotInheritTheCuratorProfileProgram() {
        UUID userId = UUID.randomUUID();
        UserEntity curator = curator(userId, "Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(curator));

        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setCourseProgram(null);

        StudyPackGenerationContext context = resolver().resolve(userId, note);

        assertThat(context.courseProgram()).isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context)).isNull();
    }

    @Test
    void resolve_twoCuratorsWithDifferentProfilesGetByteIdenticalAuthoringDomains() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(userRepository.findById(firstId)).thenReturn(Optional.of(curator(firstId, "Nursing")));
        when(userRepository.findById(secondId)).thenReturn(Optional.of(curator(secondId, "Accountancy")));
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setCourseProgram(null);
        // NO joined programs, deliberately. An earlier version of this test gave the note exactly one
        // joined program, so resolveCourseProgram returned at the `joinedPrograms.size() == 1` branch and
        // the profile fallback -- the thing this test exists to pin -- was never reached. It passed
        // identically with and without the fix. The fallback is only reachable when the joined-program
        // count is not exactly one, so that is the shape the invariant has to be asserted in.
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of());

        StudyPackGenerationContext first = resolver().resolve(firstId, note);
        StudyPackGenerationContext second = resolver().resolve(secondId, note);

        // Both curators resolve to NO authoring domain rather than to their own profile programs.
        // Restoring the profile fallback makes these "Nursing" and "Accountancy" -- different strings
        // for the same canonical note, which is exactly Decision 4's invariant being broken.
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(first))
                .isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(second))
                .isEqualTo(StudyPackGenerationContextResolver.effectiveAuthoringDomain(first));
    }

    @Test
    void resolve_curatorSingleProgramUsesJoinedCatalogNameInsteadOfProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity curator = curator(userId, "Software Engineering");
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(curator));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(
                new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), "Architecture")
        ));

        StudyPackGenerationContext context = resolver().resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Architecture");
        assertThat(context.courseProgram()).isNotEqualTo("Software Engineering");
    }

    @Test
    void resolveForBulkGeneration_curatorDoesNotInheritProfileProgram() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(curator(userId, "Software Engineering")));

        StudyPackGenerationContext context = resolver().resolveForBulkGeneration(
                userId, List.of(), null, "Physics", null, LearnerLevel.COLLEGE
        );

        assertThat(context.courseProgram()).isNull();
    }

    @Test
    void assertGenerationReady_rejectsMultipleProgramsWithoutDomainContext() {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        when(noteCourseProgramRepository.findIdsByNoteId(note.getId()))
                .thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> resolver().assertGenerationReady(note))
                .isInstanceOf(MultiProgramDomainContextRequiredException.class);
    }

    @Test
    void assertGenerationReady_allowsRetryAfterDomainContextIsSet() {
        NoteEntity note = new NoteEntity();
        note.setId(UUID.randomUUID());
        note.setDomainContext(DomainContext.ENGINEERING_SCIENCES);

        resolver().assertGenerationReady(note);
    }

    @Test
    void resolveStudyPack_prefersSourceNoteCourseProgramOverProfileCourseProgram() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setCourseProgram("Senior High STEM");
        note.setDomainContext(DomainContext.GENERAL_EDUCATION);
        note.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        note.setSubject("Physics");
        note.setTags(new String[]{"ohms-law"});
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Fallback Physics");
        studyPack.setTags(new String[]{"fallback"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolveForStudyPack(userId, studyPack);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(context.courseProgram()).isEqualTo("Senior High STEM");
        assertThat(context.subject()).isEqualTo("Physics");
        assertThat(context.tags()).containsExactly("ohms-law");
        assertThat(context.domainContext()).isEqualTo(DomainContext.GENERAL_EDUCATION);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
    }

    @Test
    void resolveStudyPack_fallsBackToProfileCourseProgramWhenSourceNoteIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.empty());

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Computing");
        studyPack.setTags(new String[]{"algorithms"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolveForStudyPack(userId, studyPack);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
        assertThat(context.subject()).isEqualTo("Computing");
        assertThat(context.tags()).containsExactly("algorithms");
        assertThat(context.domainContext()).isNull();
        assertThat(context.noteLearnerLevel()).isNull();
    }

    @Test
    void resolveForBulkGeneration_usesProfileLearnerLevelForPoolPrewarm() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        user.setCourseProgram("Profile Course");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolveForBulkGeneration(
                userId,
                "Nursing",
                "Maternal Health",
                DomainContext.NURSING,
                LearnerLevel.BOARD_EXAM_REVIEW
        );

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
        assertThat(context.courseProgram()).isEqualTo("Nursing");
        assertThat(context.subject()).isEqualTo("Maternal Health");
        assertThat(context.domainContext()).isEqualTo(DomainContext.NURSING);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
    }

    @Test
    void resolveForBulkGeneration_allowsMissingProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCourseProgram("Nursing");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolveForBulkGeneration(
                userId,
                "Nursing",
                "Maternal Health",
                null,
                null
        );

        assertThat(context.learnerLevel()).isNull();
        assertThat(context.domainContext()).isNull();
        assertThat(context.noteLearnerLevel()).isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.COLLEGE);
    }

    @Test
    void resolve_allowsNoAuthoringDomainAndDefaultsCurriculumToCollege() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = new NoteEntity();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context)).isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.COLLEGE);
    }

    private StudyPackGenerationContextResolver resolver() {
        return new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository
        );
    }

    private UserEntity curator(UUID userId, String courseProgram) {
        UserEntity curator = new UserEntity();
        curator.setId(userId);
        curator.setRole(UserRole.ADMIN);
        curator.setProfileType(ProfileType.STUDENT);
        curator.setOnboardingCompletedAt(OffsetDateTime.now());
        curator.setCourseProgram(courseProgram);
        return curator;
    }

    /**
     * ⚠️ v0.120.0 GUARD 5 -- pins a hazard that is ONE REFACTOR from becoming live, so the fixture
     * shape is load-bearing in both halves and must not be "simplified".
     *
     * <p>A curator's own profile Course/Program must never become authority over a canonical Note's
     * generation context, and therefore over its title. Two independent guards hold that today: this
     * one in {@code resolveBulkCourseProgram}, and the frontend curator branch omitting
     * {@code courseProgramText} entirely. The frontend half is inert protection only --
     * {@code bulk-generation-page-client.tsx} pre-fills the visible Course/Program field from the
     * curator's profile UNGATED by curator status, so if that request branch is ever unified the
     * profile silently becomes title context. This test is what fails when that happens.
     *
     * <p>⚠️ FOUR ids, NOT ONE. {@code resolveBulkCourseProgram} RETURNS EARLY inside its
     * {@code ids.size() == 1} branch, so a one-id fixture never reaches the curator check at all and a
     * mutant deleting {@code !CuratorAuthoringPredicate.isCurator(user)} SURVIVES it. With several ids
     * the early return is skipped, the curator check is reached, and deleting it flips the result from
     * null to the profile's value.
     *
     * <p>⚠️ AND {@code courseProgramText} MUST BE NULL, because the resolver ends in
     * {@code firstNonBlank(courseProgramText, profileCourseProgram)} -- a non-null text value would
     * supply the answer from the other argument and mask the guard entirely. Null is what the curator
     * branch sends in production.
     */
    @Test
    void resolveForBulkGeneration_neverLetsACuratorProfileCourseProgramBecomeTitleContext() {
        UUID curatorId = UUID.randomUUID();
        UserEntity curator = new UserEntity();
        curator.setId(curatorId);
        curator.setRole(UserRole.ADMIN);
        curator.setOnboardingCompletedAt(OffsetDateTime.now());
        curator.setLearnerLevel(LearnerLevel.COLLEGE);
        curator.setCourseProgram("Civil Engineering");
        when(userRepository.findById(curatorId)).thenReturn(Optional.of(curator));

        List<UUID> fourUnrelatedPrograms = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(
                userRepository, noteRepository, noteCourseProgramRepository, courseProgramCatalogRepository);

        StudyPackGenerationContext context = resolver.resolveForBulkGeneration(
                curatorId,
                fourUnrelatedPrograms,
                null,
                "Site Planning",
                DomainContext.ENGINEERING_SCIENCES,
                LearnerLevel.COLLEGE
        );

        assertThat(context.courseProgram())
                .as("a curator's own Course/Program is NOT authoring context -- this is the guard that"
                        + " keeps 'Civil Engineering' out of a canonical note's generation input")
                .isNull();
        assertThat(context.subject())
                .as("the curator's batch subject still reaches generation -- otherwise this test could"
                        + " pass against a resolver that returns nothing at all")
                .isEqualTo("Site Planning");
    }
}
