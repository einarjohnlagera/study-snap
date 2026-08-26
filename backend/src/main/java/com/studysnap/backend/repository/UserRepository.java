package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPendingEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    Optional<UserEntity> findByPendingEmailIgnoreCase(String email);
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    List<UserEntity> findByRole(UserRole role);
    long countByEmailVerifiedAtIsNotNull();
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNull(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndWeeklySummaryRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndDueConceptsDigestRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndKnowledgeImpactDigestRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndMarketingEmailsEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndDeletedAtLessThanEqual(UserStatus status, OffsetDateTime deletedAt);

    @Query("select u.id from UserEntity u where u.status = com.studysnap.backend.entity.UserStatus.ACTIVE")
    List<UUID> findAllUserIds();

    @Query("select u.createdAt from UserEntity u where u.id = :userId")
    Optional<OffsetDateTime> findCreatedAtById(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") UUID userId);

    /**
     * Read a birth year as a SCALAR, deliberately.
     *
     * <p>⚠️ {@link #findByIdForUpdate} genuinely takes the row lock, but when the entity is already
     * in the persistence context Hibernate returns the MANAGED INSTANCE and discards the state it
     * just read. Every caller of the lock loads the user first — the verified-email check and the
     * onboarding guard both do — so an entity-based read after locking returns the PRE-LOCK value.
     * The lock is real; the read was not. A scalar projection has no entity to return from the
     * identity map, so it always reflects the row.
     *
     * <p>An empty result means the birth year is NULL. Callers establish existence via the lock
     * read above first, so the two cases are never confused.
     */
    @Query("select u.birthYear from UserEntity u where u.id = :userId")
    Optional<Integer> findBirthYearById(@Param("userId") UUID userId);

    /**
     * Write a birth year with a TARGETED update rather than {@code save()} on a loaded entity.
     * {@code UserEntity} has no {@code @DynamicUpdate}, so saving a stale snapshot rewrites EVERY
     * column — and these paths deliberately hold an entity across a blocking lock wait, which is
     * exactly when it is most likely to be stale.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.birthYear = :birthYear, u.birthYearUpdatedAt = :updatedAt,"
            + " u.updatedAt = :updatedAt where u.id = :userId")
    int writeBirthYear(
            @Param("userId") UUID userId,
            @Param("birthYear") Integer birthYear,
            @Param("updatedAt") OffsetDateTime updatedAt);
}
