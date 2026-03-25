package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    long countByEmailVerifiedAtIsNotNull();
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndInactivityRemindersEnabledTrue(UserStatus status);
    List<UserEntity> findByStatusAndEmailVerifiedAtIsNotNullAndWeakConceptRemindersEnabledTrue(UserStatus status);

    @Query("select u.id from UserEntity u")
    List<UUID> findAllUserIds();
}
