package com.studysnap.backend.security;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.TooManyLinkedLearnerInvitationsException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationRateLimitServiceTest {

    @Test
    void invitationLinkCreationHasAnIndependentCreatorScopedBucket() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLinkedLearners().setInvitationLinksPerWindow(1);
        InvitationRateLimitService service = new InvitationRateLimitService(properties);
        UUID creatorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        service.assertLinkCreationAllowed(creatorId, now);

        assertThatThrownBy(() -> service.assertLinkCreationAllowed(creatorId, now.plusMinutes(1)))
                .isInstanceOf(TooManyLinkedLearnerInvitationsException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_INVITATIONS");
        // A different creator has a different bucket, and the link bucket does not consume either
        // of the email path's creator/address keys.
        service.assertLinkCreationAllowed(UUID.randomUUID(), now.plusMinutes(1));
        service.assertInviteAllowed(creatorId, "person@example.test", now.plusMinutes(1));
    }
}
