package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class LinkedLearnerInvitationRepositoryTest {

    @Autowired
    private LinkedLearnerInvitationRepository invitationRepository;

    @Test
    void outgoingListIncludesOnlyRowsInsideTheTtlSizedExpiryWindow() {
        UUID inviterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int invitationTtlDays = 9;
        LinkedLearnerInvitationEntity insideWindow = save(
                inviterId, "inside@example.com", now.minusDays(invitationTtlDays).plusMinutes(1));
        save(inviterId, "outside@example.com", now.minusDays(invitationTtlDays).minusMinutes(1));

        List<LinkedLearnerInvitationEntity> visible = invitationRepository
                .findByInviterUserIdAndStatusAndExpiresAtAfter(
                        inviterId, LinkedLearnerStatus.PENDING, now.minusDays(invitationTtlDays));

        assertThat(visible).extracting(LinkedLearnerInvitationEntity::getId)
                .containsExactly(insideWindow.getId());
    }

    @Test
    void incomingListStillExcludesExpiredRows() {
        OffsetDateTime now = OffsetDateTime.now();
        String invitedEmail = "recipient@example.com";
        LinkedLearnerInvitationEntity live = save(
                UUID.randomUUID(), invitedEmail, now.plusMinutes(1));
        save(UUID.randomUUID(), invitedEmail, now.minusMinutes(1));

        List<LinkedLearnerInvitationEntity> incoming = invitationRepository
                .findByInvitedEmailAndStatusAndExpiresAtAfter(
                        invitedEmail, LinkedLearnerStatus.PENDING, now);

        assertThat(incoming).extracting(LinkedLearnerInvitationEntity::getId)
                .containsExactly(live.getId());
    }

    private LinkedLearnerInvitationEntity save(UUID inviterId, String email, OffsetDateTime expiresAt) {
        LinkedLearnerInvitationEntity invitation = new LinkedLearnerInvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setInviterUserId(inviterId);
        invitation.setInvitedEmail(email);
        invitation.setInviterRole(LinkedLearnerSide.SUPPORTER);
        invitation.setStatus(LinkedLearnerStatus.PENDING);
        invitation.setCreatedAt(OffsetDateTime.now().minusDays(10));
        invitation.setExpiresAt(expiresAt);
        return invitationRepository.saveAndFlush(invitation);
    }
}
