package com.studysnap.backend.dto;

public record PublicNoteLikeResponse(
        boolean liked,
        long likeCount
) {
}
