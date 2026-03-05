package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {
}
