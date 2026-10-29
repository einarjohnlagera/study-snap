package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PublicLibraryRankingBusyException extends AppException {
    public PublicLibraryRankingBusyException() {
        super("PUBLIC_LIBRARY_RANKING_BUSY",
                "This page is a little busy right now. Please try again in a moment.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
