package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPendingEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    Optional<UserEntity> findByPendingEmailIgnoreCase(String email);
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    long countByEmailVerifiedAtIsNotNull();
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNull(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus status);

    @Query("select u.id from UserEntity u")
    List<UUID> findAllUserIds();

    @Query("select u.createdAt from UserEntity u where u.id = :userId")
    Optional<OffsetDateTime> findCreatedAtById(@Param("userId") UUID userId);
}
