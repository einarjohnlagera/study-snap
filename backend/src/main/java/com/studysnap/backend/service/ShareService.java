package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.ShareLinkEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ShareService {
    private final ShareLinkRepository shareLinkRepository;
    private final StudyPackRepository studyPackRepository;

    public ShareLinkResponse createShareLink(String studyPackId) {
        UUID id;
        try {
            id = UUID.fromString(studyPackId);
        } catch (IllegalArgumentException ex) {
            throw new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND);
        }

        StudyPackEntity studyPack = studyPackRepository.findById(id)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        ShareLinkEntity share = new ShareLinkEntity();
        String token = generateToken();
        share.setToken(token);
        share.setStudyPack(studyPack);
        share.setIsPublic(true);
        share.setCreatedAt(OffsetDateTime.now());
        share.setViewCount(0);
        shareLinkRepository.save(share);

        return new ShareLinkResponse(token, "/share/" + token);
    }

    @Transactional(readOnly = true)
    public PublicShareResponse getPublicShare(String token) {
        ShareLinkEntity share = shareLinkRepository.findById(token)
                .orElseThrow(() -> new AppException("SHARE_NOT_FOUND", "Share link not found.", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(share.getIsPublic())) {
            throw new AppException("SHARE_NOT_FOUND", "Share link not found.", HttpStatus.NOT_FOUND);
        }

        StudyPackEntity studyPack = share.getStudyPack();
        return new PublicShareResponse(
                studyPack.getId().toString(),
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts(),
                studyPack.getQuiz()
        );
    }

    private String generateToken() {
        byte[] random = UUID.randomUUID().toString().getBytes();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random).substring(0, 22);
    }
}

