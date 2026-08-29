package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CreateLinkedLearnerInvitationLinkRequest;
import com.studysnap.backend.dto.LinkedLearnerInvitationLinkRedemptionResponse;
import com.studysnap.backend.dto.LinkedLearnerInvitationLinkResolveResponse;
import com.studysnap.backend.dto.LinkedLearnerInvitationLinkResponse;
import com.studysnap.backend.dto.RedeemLinkedLearnerInvitationLinkRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerInvitationLinkEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerInvitationLinkNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerInvitationLinkTokenGenerationException;
import com.studysnap.backend.exception.LinkedLearnerRelationshipAlreadyExistsException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerInvitationLinkRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.InvitationRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerInvitationLinkService {
    private static final char[] BASE62_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    // 22 Base62 characters carry about 131 bits of entropy. This token can initiate a cross-user
    // permission relationship, so it is deliberately stronger than the anonymous quiz token.
    private static final int TOKEN_LENGTH = 22;
    private static final int MAX_TOKEN_ATTEMPTS = 10;
    private static final String INVITATION_LINK_PATH_PREFIX = "/linked-learners/invite/";
    private static final String REVOKED_MESSAGE = "Invitation link revoked.";
    private static final String PRIVATE_DISPLAY_NAME_FALLBACK = "A NoteLib user";

    private final LinkedLearnerInvitationLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final LinkedLearnerService linkedLearnerService;
    private final OnboardingGuardService onboardingGuardService;
    private final AuthService authService;
    private final InvitationRateLimitService invitationRateLimitService;
    private final StudySnapProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public LinkedLearnerInvitationLinkResponse create(
            UUID callerUserId,
            CreateLinkedLearnerInvitationLinkRequest request
    ) {
        requireVerifiedOnboarded(callerUserId);
        requireUser(callerUserId);

        // Same ordering and same locked writer as LinkedLearnerService.invite: when the creator is
        // the learner, their year is captured before the invitation exists for somebody to redeem.
        if (request.creatorRole() == LinkedLearnerSide.LEARNER) {
            linkedLearnerService.captureLearnerBirthYearIfMissing(
                    callerUserId, request.learnerBirthYear());
        }

        invitationRateLimitService.assertLinkCreationAllowed(callerUserId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LinkedLearnerInvitationLinkEntity link = new LinkedLearnerInvitationLinkEntity();
        link.setId(UUID.randomUUID());
        link.setToken(generateUniqueToken());
        link.setCreatorUserId(callerUserId);
        link.setCreatorRole(request.creatorRole());
        link.setCreatedAt(now);
        link.setExpiresAt(now.plusDays(properties.getLinkedLearners().getInvitationTtlDays()));
        return toResponse(linkRepository.save(link));
    }

    @Transactional(readOnly = true)
    public List<LinkedLearnerInvitationLinkResponse> list(UUID callerUserId) {
        requireVerifiedOnboarded(callerUserId);
        return linkRepository.findLiveByCreator(callerUserId, OffsetDateTime.now(ZoneOffset.UTC))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkedLearnerInvitationLinkResolveResponse resolve(UUID callerUserId, String token) {
        requireVerifiedOnboarded(callerUserId);
        LinkedLearnerInvitationLinkEntity link = requireUsable(token);
        if (callerUserId.equals(link.getCreatorUserId())) {
            throw new LinkedLearnerSelfLinkException();
        }
        UserEntity creator = requireUser(link.getCreatorUserId());
        return new LinkedLearnerInvitationLinkResolveResponse(
                resolvePrivateDisplayName(creator), link.getCreatorRole());
    }

    @Transactional
    public LinkedLearnerInvitationLinkRedemptionResponse redeem(
            UUID callerUserId,
            String token,
            RedeemLinkedLearnerInvitationLinkRequest request
    ) {
        requireVerifiedOnboarded(callerUserId);
        requireUser(callerUserId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LinkedLearnerInvitationLinkEntity link = linkRepository.findUsableByToken(token, now)
                .orElseThrow(LinkedLearnerInvitationLinkNotFoundException::new);
        if (callerUserId.equals(link.getCreatorUserId())) {
            throw new LinkedLearnerSelfLinkException();
        }

        LinkedLearnerSide redeemerRole = opposite(link.getCreatorRole());
        Integer provisionalBirthYear = null;
        if (redeemerRole == LinkedLearnerSide.LEARNER) {
            // Validate before consuming the token and hold the learner-row lock to transaction end.
            // A null result means an account-global year already exists, so there is nothing to
            // defer and no provisional row will be written.
            provisionalBirthYear = linkedLearnerService.prepareProvisionalBirthYearForLinkRedemption(
                    callerUserId, request.learnerBirthYear());
        }

        // Claim before relationship creation. This conditional write serializes both revoke vs
        // redeem and two redeemers; every losing/terminal case becomes the same not-found error.
        if (linkRepository.markRedeemedIfUsable(token, callerUserId, now) == 0) {
            throw new LinkedLearnerInvitationLinkNotFoundException();
        }

        LinkedLearnerService.PendingRelationshipCreation creation =
                linkedLearnerService.createPendingRelationship(
                        callerUserId, link.getCreatorUserId(), redeemerRole, now);
        if (!creation.inserted()) {
            // Throwing rolls the token claim back, so a duplicate relationship never consumes it.
            throw new LinkedLearnerRelationshipAlreadyExistsException();
        }
        // The provisional declaration is keyed by relationship id, so it can only be written after
        // the PENDING row exists. Any failure still rolls the token claim and relationship back.
        if (provisionalBirthYear != null) {
            linkedLearnerService.storeProvisionalBirthYear(
                    creation.relationship().getId(), callerUserId, provisionalBirthYear, now);
        }
        return new LinkedLearnerInvitationLinkRedemptionResponse(
                creation.relationship().getId(), LinkedLearnerStatus.PENDING);
    }

    @Transactional
    public SimpleMessageResponse revoke(UUID callerUserId, UUID linkId) {
        requireVerifiedOnboarded(callerUserId);
        if (linkRepository.markRevokedIfUsable(
                linkId, callerUserId, OffsetDateTime.now(ZoneOffset.UTC)) == 0) {
            throw new LinkedLearnerInvitationLinkNotFoundException();
        }
        return new SimpleMessageResponse(REVOKED_MESSAGE);
    }

    private void requireVerifiedOnboarded(UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
    }

    private LinkedLearnerInvitationLinkEntity requireUsable(String token) {
        return linkRepository.findUsableByToken(token, OffsetDateTime.now(ZoneOffset.UTC))
                .orElseThrow(LinkedLearnerInvitationLinkNotFoundException::new);
    }

    private LinkedLearnerSide opposite(LinkedLearnerSide role) {
        return role == LinkedLearnerSide.SUPPORTER
                ? LinkedLearnerSide.LEARNER : LinkedLearnerSide.SUPPORTER;
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            String token = generateToken();
            if (!linkRepository.existsByToken(token)) {
                return token;
            }
        }
        throw new LinkedLearnerInvitationLinkTokenGenerationException();
    }

    private String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int index = 0; index < TOKEN_LENGTH; index++) {
            token.append(BASE62_CHARS[secureRandom.nextInt(BASE62_CHARS.length)]);
        }
        return token.toString();
    }

    private LinkedLearnerInvitationLinkResponse toResponse(LinkedLearnerInvitationLinkEntity link) {
        return new LinkedLearnerInvitationLinkResponse(
                link.getId(),
                link.getToken(),
                buildInvitationUrl(link.getToken()),
                link.getCreatorRole(),
                link.getCreatedAt(),
                link.getExpiresAt()
        );
    }

    private String buildInvitationUrl(String token) {
        String baseUrl = properties.getBilling().getFrontendBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + INVITATION_LINK_PATH_PREFIX + token;
    }

    /** Never falls back to email: token resolution discloses only self-chosen/profile name data. */
    private String resolvePrivateDisplayName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? PRIVATE_DISPLAY_NAME_FALLBACK : fullName;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
