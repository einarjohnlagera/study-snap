package com.studysnap.backend.repository;

import com.studysnap.backend.entity.StudyPackDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudyPackDraftRepository extends JpaRepository<StudyPackDraftEntity, UUID> {
}

