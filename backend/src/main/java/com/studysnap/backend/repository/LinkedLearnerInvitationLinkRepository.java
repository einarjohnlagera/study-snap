package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerInvitationLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerInvitationLinkRepository
        extends JpaRepository<LinkedLearnerInvitationLinkEntity, UUID> {

    boolean existsByToken(String token);

    @Query("""
            select link from LinkedLearnerInvitationLinkEntity link
             where link.token = :token
               and link.revokedAt is null
               and link.redeemedAt is null
               and link.expiresAt > :now
            """)
    Optional<LinkedLearnerInvitationLinkEntity> findUsableByToken(
            @Param("token") String token,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            select link from LinkedLearnerInvitationLinkEntity link
             where link.creatorUserId = :creatorUserId
               and link.revokedAt is null
               and link.redeemedAt is null
               and link.expiresAt > :now
             order by link.createdAt desc
            """)
    List<LinkedLearnerInvitationLinkEntity> findLiveByCreator(
            @Param("creatorUserId") UUID creatorUserId,
            @Param("now") OffsetDateTime now
    );

    /**
     * The single-use boundary. Revocation and competing redemptions update the same row under the
     * database lock acquired by this conditional statement; exactly one transaction can change a
     * still-live token. Zero rows is deliberately indistinguishable from a token that never existed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LinkedLearnerInvitationLinkEntity link
               set link.redeemedAt = :redeemedAt,
                   link.redeemedByUserId = :redeemerUserId
             where link.token = :token
               and link.revokedAt is null
               and link.redeemedAt is null
               and link.expiresAt > :redeemedAt
            """)
    int markRedeemedIfUsable(
            @Param("token") String token,
            @Param("redeemerUserId") UUID redeemerUserId,
            @Param("redeemedAt") OffsetDateTime redeemedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LinkedLearnerInvitationLinkEntity link
               set link.revokedAt = :revokedAt
             where link.id = :id
               and link.creatorUserId = :creatorUserId
               and link.revokedAt is null
               and link.redeemedAt is null
               and link.expiresAt > :revokedAt
            """)
    int markRevokedIfUsable(
            @Param("id") UUID id,
            @Param("creatorUserId") UUID creatorUserId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );
}

