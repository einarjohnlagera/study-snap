package com.studysnap.backend.repository;

import com.studysnap.backend.entity.SuppressedEmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuppressedEmailRepository extends JpaRepository<SuppressedEmailEntity, String> {
    boolean existsByAddressIgnoreCase(String address);

    Optional<SuppressedEmailEntity> findByAddressIgnoreCase(String address);
}
