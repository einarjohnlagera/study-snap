package com.studysnap.backend.repository;

import com.studysnap.backend.entity.StudyPackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudyPackRepository extends JpaRepository<StudyPackEntity, UUID> {
}

