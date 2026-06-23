package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserLibraryFilterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLibraryFilterRepository extends JpaRepository<UserLibraryFilterEntity, UUID> {
    List<UserLibraryFilterEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<UserLibraryFilterEntity> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}
