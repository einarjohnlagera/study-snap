package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ShareLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, String> {
}
