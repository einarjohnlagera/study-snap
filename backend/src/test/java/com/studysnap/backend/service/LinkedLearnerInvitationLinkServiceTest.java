package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CreateLinkedLearnerInvitationLinkRequest;
import com.studysnap.backend.dto.LinkedLearnerInvitationLinkResolveResponse;
import com.studysnap.backend.dto.RedeemLinkedLearnerInvitationLinkRequest;
import com.studysnap.backend.entity.LinkedLearnerInvitationLinkEntity;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerInvitationLinkNotFoundException;
import com.studysnap.backend.exception.InvalidLinkedLearnerBirthYearException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import com.studysnap.backend.exception.LinkedLearnerRelationshipAlreadyExistsException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.repository.LinkedLearnerInvitationLinkRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.InvitationRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerInvitationLinkServiceTest {
    private static final String TOKEN = "AbCdEf0123456789GhIjKl";

    @Mock private LinkedLearnerInvitationLinkRepository linkRepository;
    @Mock private UserRepository userRepository;
    @Mock private LinkedLearnerService linkedLearnerService;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private AuthService authService;
    @Mock private InvitationRateLimitService invitationRateLimitService;

    private LinkedLearnerInvitationLinkService service;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getBilling().setFrontendBaseUrl("https://notelib.test");
        service = new LinkedLearnerInvitationLinkService(
                linkRepository,
                userRepository,
                linkedLearnerService,
                onboardingGuardService,
                authService,
                invitationRateLimitService,
                properties
        );
    }

    @Test
    void createUsesA131BitTokenAndTheLinkSpecificRateBucket() {
        UUID creatorId = UUID.randomUUID();
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user(creatorId, "Creator")));
        when(linkRepository.existsByToken(anyString())).thenReturn(false);
        when(linkRepository.save(any(LinkedLearnerInvitationLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                creatorId, new CreateLinkedLearnerInvitationLinkRequest(LinkedLearnerSide.SUPPORTER, null));

        assertThat(response.token()).hasSize(22).matches("[0-9A-Za-z]{22}");
        assertThat(response.url()).isEqualTo("https://notelib.test/linked-learners/invite/" + response.token());
        verify(invitationRateLimitService).assertLinkCreationAllowed(creatorId);
    }

    @Test
    void learnerCreatorBirthYearUsesTheExistingLockedWriterBeforeCreation() {
        UUID creatorId = UUID.randomUUID();
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user(creatorId, "Learner")));
        when(linkRepository.existsByToken(anyString())).thenReturn(false);
        when(linkRepository.save(any(LinkedLearnerInvitationLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(creatorId,
                new CreateLinkedLearnerInvitationLinkRequest(LinkedLearnerSide.LEARNER, 2012));

        verify(linkedLearnerService).captureLearnerBirthYearIfMissing(creatorId, 2012);
    }

    @Test
    void authenticatedResolveReturnsOnlyDisplayNameAndRoleNeverEmailOrUserId() {
        UUID callerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity link = link(creatorId, LinkedLearnerSide.LEARNER);
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(link));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user(creatorId, "Taylor")));

        LinkedLearnerInvitationLinkResolveResponse response = service.resolve(callerId, TOKEN);

        assertThat(response.inviterName()).isEqualTo("Taylor");
        assertThat(Arrays.stream(LinkedLearnerInvitationLinkResolveResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("inviterName", "inviterRole")
                .noneMatch(name -> name.toLowerCase().contains("email") || name.toLowerCase().contains("userid"));
        verify(authService).requireEmailVerified(callerId);
        verify(onboardingGuardService).assertProfileComplete(callerId);
    }

    @Test
    void redeemCreatesPendingWithTheRedeemerAsInitiatorAndNeverAcceptsDirectly() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity link = link(creatorId, LinkedLearnerSide.SUPPORTER);
        LinkedLearnerRelationshipEntity relationship = relationship(creatorId, redeemerId);
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(link));
        when(linkRepository.markRedeemedIfUsable(eq(TOKEN), eq(redeemerId), any())).thenReturn(1);
        when(linkedLearnerService.prepareProvisionalBirthYearForLinkRedemption(redeemerId, 2012))
                .thenReturn(2012);
        when(linkedLearnerService.createPendingRelationship(
                eq(redeemerId), eq(creatorId), eq(LinkedLearnerSide.LEARNER), any()))
                .thenReturn(new LinkedLearnerService.PendingRelationshipCreation(relationship, true));

        var response = service.redeem(
                redeemerId, TOKEN, new RedeemLinkedLearnerInvitationLinkRequest(2012));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.PENDING);
        verify(linkedLearnerService).prepareProvisionalBirthYearForLinkRedemption(redeemerId, 2012);
        verify(linkedLearnerService).storeProvisionalBirthYear(
                eq(relationship.getId()), eq(redeemerId), eq(2012), any());
        verify(linkedLearnerService, never()).accept(any(), any(), any());
    }

    @Test
    void creatorCannotRedeemTheirOwnLinkAndTheTokenIsNotConsumed() {
        UUID creatorId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity link = link(creatorId, LinkedLearnerSide.SUPPORTER);
        RedeemLinkedLearnerInvitationLinkRequest request =
                new RedeemLinkedLearnerInvitationLinkRequest(2012);
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user(creatorId, "Creator")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.redeem(creatorId, TOKEN, request))
                .isInstanceOf(LinkedLearnerSelfLinkException.class);

        verify(linkRepository, never()).markRedeemedIfUsable(anyString(), any(), any());
    }

    @Test
    void duplicateRelationshipRollsBackByThrowingAfterTheConditionalClaim() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity link = link(creatorId, LinkedLearnerSide.LEARNER);
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(link));
        when(linkRepository.markRedeemedIfUsable(eq(TOKEN), eq(redeemerId), any())).thenReturn(1);
        when(linkedLearnerService.createPendingRelationship(
                eq(redeemerId), eq(creatorId), eq(LinkedLearnerSide.SUPPORTER), any()))
                .thenReturn(new LinkedLearnerService.PendingRelationshipCreation(
                        relationship(redeemerId, creatorId), false));

        assertThatThrownBy(() -> service.redeem(
                redeemerId, TOKEN, new RedeemLinkedLearnerInvitationLinkRequest(null)))
                .isInstanceOf(LinkedLearnerRelationshipAlreadyExistsException.class);
    }

    @Test
    void learnerWithAnExistingAccountYearGetsNoProvisionalRow() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity link = link(creatorId, LinkedLearnerSide.SUPPORTER);
        LinkedLearnerRelationshipEntity relationship = relationship(creatorId, redeemerId);
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(link));
        when(linkRepository.markRedeemedIfUsable(eq(TOKEN), eq(redeemerId), any())).thenReturn(1);
        when(linkedLearnerService.prepareProvisionalBirthYearForLinkRedemption(redeemerId, null))
                .thenReturn(null);
        when(linkedLearnerService.createPendingRelationship(
                eq(redeemerId), eq(creatorId), eq(LinkedLearnerSide.LEARNER), any()))
                .thenReturn(new LinkedLearnerService.PendingRelationshipCreation(relationship, true));

        service.redeem(redeemerId, TOKEN, new RedeemLinkedLearnerInvitationLinkRequest(null));

        verify(linkedLearnerService, never()).storeProvisionalBirthYear(any(), any(), anyInt(), any());
    }

    @Test
    void invalidLearnerYearFailsBeforeTheTokenClaim() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any()))
                .thenReturn(Optional.of(link(creatorId, LinkedLearnerSide.SUPPORTER)));
        doThrow(new InvalidLinkedLearnerBirthYearException())
                .when(linkedLearnerService)
                .prepareProvisionalBirthYearForLinkRedemption(redeemerId, 1800);
        RedeemLinkedLearnerInvitationLinkRequest request =
                new RedeemLinkedLearnerInvitationLinkRequest(1800);

        assertThatThrownBy(() -> service.redeem(redeemerId, TOKEN, request))
                .isInstanceOf(InvalidLinkedLearnerBirthYearException.class);

        verify(linkRepository, never()).markRedeemedIfUsable(anyString(), any(), any());
    }

    @Test
    void missingLearnerYearFailsBeforeTheTokenClaim() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any()))
                .thenReturn(Optional.of(link(creatorId, LinkedLearnerSide.SUPPORTER)));
        doThrow(new LinkedLearnerBirthYearRequiredException())
                .when(linkedLearnerService)
                .prepareProvisionalBirthYearForLinkRedemption(redeemerId, null);
        RedeemLinkedLearnerInvitationLinkRequest request =
                new RedeemLinkedLearnerInvitationLinkRequest(null);

        assertThatThrownBy(() -> service.redeem(redeemerId, TOKEN, request))
                .isInstanceOf(LinkedLearnerBirthYearRequiredException.class);

        verify(linkRepository, never()).markRedeemedIfUsable(anyString(), any(), any());
    }

    @Test
    void lostTokenClaimWritesNoRelationshipOrProvisionalYear() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Redeemer")));
        when(linkRepository.findUsableByToken(eq(TOKEN), any()))
                .thenReturn(Optional.of(link(creatorId, LinkedLearnerSide.SUPPORTER)));
        when(linkedLearnerService.prepareProvisionalBirthYearForLinkRedemption(redeemerId, 2012))
                .thenReturn(2012);
        when(linkRepository.markRedeemedIfUsable(eq(TOKEN), eq(redeemerId), any())).thenReturn(0);
        RedeemLinkedLearnerInvitationLinkRequest request =
                new RedeemLinkedLearnerInvitationLinkRequest(2012);

        assertThatThrownBy(() -> service.redeem(redeemerId, TOKEN, request))
                .isInstanceOf(LinkedLearnerInvitationLinkNotFoundException.class);

        verify(linkedLearnerService, never()).createPendingRelationship(any(), any(), any(), any());
        verify(linkedLearnerService, never()).storeProvisionalBirthYear(any(), any(), anyInt(), any());
    }

    /**
     * ⚠️ NAMED FOR WHAT IT ACTUALLY CHECKS. This stubs `findUsableByToken` empty for all four token
     * strings, so the four "cases" are ONE case — it proves the exception is constructed identically,
     * nothing more. That the four STATES are genuinely indistinguishable is a property of the query
     * predicate, and is pinned by `revokedRedeemedAndExpiredInvitationLinksAreAllUnusable` in
     * `NativeQueryPostgresIntegrationTest`, against real rows. Renamed at the v0.94.0 pressure test,
     * which found the old name promised the stronger guarantee.
     */
    @Test
    void theNotFoundExceptionItselfIsIdenticalWhicheverBranchRaisesIt() {
        UUID callerId = UUID.randomUUID();
        when(linkRepository.findUsableByToken(anyString(), any())).thenReturn(Optional.empty());

        LinkedLearnerInvitationLinkNotFoundException unknown = captureNotFound(callerId, "unknown");
        LinkedLearnerInvitationLinkNotFoundException revoked = captureNotFound(callerId, "revoked");
        LinkedLearnerInvitationLinkNotFoundException expired = captureNotFound(callerId, "expired");
        LinkedLearnerInvitationLinkNotFoundException redeemed = captureNotFound(callerId, "redeemed");

        assertThat(new Object[] {revoked.getStatus(), revoked.getCode(), revoked.getMessage()})
                .isEqualTo(new Object[] {unknown.getStatus(), unknown.getCode(), unknown.getMessage()});
        assertThat(new Object[] {expired.getStatus(), expired.getCode(), expired.getMessage()})
                .isEqualTo(new Object[] {unknown.getStatus(), unknown.getCode(), unknown.getMessage()});
        assertThat(new Object[] {redeemed.getStatus(), redeemed.getCode(), redeemed.getMessage()})
                .isEqualTo(new Object[] {unknown.getStatus(), unknown.getCode(), unknown.getMessage()});
    }

    private LinkedLearnerInvitationLinkNotFoundException captureNotFound(UUID callerId, String token) {
        try {
            service.resolve(callerId, token);
            throw new AssertionError("Expected unavailable token");
        } catch (LinkedLearnerInvitationLinkNotFoundException exception) {
            return exception;
        }
    }

    private LinkedLearnerInvitationLinkEntity link(UUID creatorId, LinkedLearnerSide creatorRole) {
        LinkedLearnerInvitationLinkEntity link = new LinkedLearnerInvitationLinkEntity();
        link.setId(UUID.randomUUID());
        link.setToken(TOKEN);
        link.setCreatorUserId(creatorId);
        link.setCreatorRole(creatorRole);
        link.setCreatedAt(OffsetDateTime.now());
        link.setExpiresAt(OffsetDateTime.now().plusDays(1));
        return link;
    }

    private LinkedLearnerRelationshipEntity relationship(UUID supporterId, UUID learnerId) {
        LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(UUID.randomUUID());
        relationship.setSupporterUserId(supporterId);
        relationship.setLearnerUserId(learnerId);
        relationship.setInitiatedBy(LinkedLearnerSide.LEARNER);
        relationship.setStatus(LinkedLearnerStatus.PENDING);
        return relationship;
    }

    /**
     * ⚠️ Pins the VALUE of the no-name fallback, not just the response's shape.
     *
     * <p>A cold-agent pressure test changed `PRIVATE_DISPLAY_NAME_FALLBACK` to `user.getEmail()` and
     * nothing failed: the existing test asserts record COMPONENT NAMES, and the fallback string
     * appeared in no test anywhere. The code comment says "Never falls back to email" and the feature
     * doc says "never email or user id" — both were unenforced.
     */
    @Test
    void aCreatorWithNoNameResolvesToAPlaceholderAndNeverToTheirEmail() {
        UUID callerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UserEntity nameless = new UserEntity();
        nameless.setId(creatorId);
        nameless.setEmail("private-address@example.test");
        when(linkRepository.findUsableByToken(eq(TOKEN), any()))
                .thenReturn(Optional.of(link(creatorId, LinkedLearnerSide.SUPPORTER)));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(nameless));

        LinkedLearnerInvitationLinkResolveResponse response = service.resolve(callerId, TOKEN);

        assertThat(response.inviterName()).doesNotContain("@");
        assertThat(response.inviterName()).isNotEqualTo(nameless.getEmail());
        assertThat(response.inviterName()).isNotBlank();
    }

    /**
     * ⚠️ Pins that a SUPPORTER redeemer's birth year is never captured. AGENTS.md states birth year is
     * "collected at link time from the learner, never from the inviter"; a pressure test changed the
     * role condition to `if (true)` and all 1,786 tests stayed green.
     */
    @Test
    void aSupporterRedeemingALearnersLinkNeverHasTheirBirthYearCaptured() {
        UUID creatorId = UUID.randomUUID();
        UUID redeemerId = UUID.randomUUID();
        LinkedLearnerInvitationLinkEntity learnerCreated = link(creatorId, LinkedLearnerSide.LEARNER);
        LinkedLearnerRelationshipEntity relationship = relationship(redeemerId, creatorId);
        when(linkRepository.findUsableByToken(eq(TOKEN), any())).thenReturn(Optional.of(learnerCreated));
        when(userRepository.findById(redeemerId)).thenReturn(Optional.of(user(redeemerId, "Sam")));
        when(linkRepository.markRedeemedIfUsable(eq(TOKEN), eq(redeemerId), any())).thenReturn(1);
        when(linkedLearnerService.createPendingRelationship(
                eq(redeemerId), eq(creatorId), eq(LinkedLearnerSide.SUPPORTER), any()))
                .thenReturn(new LinkedLearnerService.PendingRelationshipCreation(relationship, true));

        service.redeem(redeemerId, TOKEN, new RedeemLinkedLearnerInvitationLinkRequest(1990));

        verify(linkedLearnerService, never())
                .prepareProvisionalBirthYearForLinkRedemption(any(), any());
        verify(linkedLearnerService, never()).storeProvisionalBirthYear(any(), any(), any(Integer.class), any());
    }

    private UserEntity user(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName(name);
        user.setEmail(name.toLowerCase() + "@example.test");
        return user;
    }
}
