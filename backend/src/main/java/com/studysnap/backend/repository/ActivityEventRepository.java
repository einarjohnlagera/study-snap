package com.studysnap.backend.repository;

import com.studysnap.backend.entity.UserActivityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<UserActivityEventEntity, UUID> {
}
