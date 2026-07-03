package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class OcrDisabledException extends AppException {
    public OcrDisabledException() {
        super(
                "OCR_DISABLED",
                "Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
