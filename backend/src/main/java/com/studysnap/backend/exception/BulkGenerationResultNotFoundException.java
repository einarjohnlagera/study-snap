package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class BulkGenerationResultNotFoundException extends AppException {
    public BulkGenerationResultNotFoundException() {
        super("BULK_GENERATION_RESULT_NOT_FOUND", "Bulk generation result not found.", HttpStatus.NOT_FOUND);
    }
}
