package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ReviewDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewDraftRepository extends JpaRepository<ReviewDraftEntity, UUID> {
}
