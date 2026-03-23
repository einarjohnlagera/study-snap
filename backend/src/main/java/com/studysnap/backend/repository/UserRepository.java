package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    long countByEmailVerifiedAtIsNotNull();

    @Query("select u.id from UserEntity u")
    List<UUID> findAllUserIds();
}
