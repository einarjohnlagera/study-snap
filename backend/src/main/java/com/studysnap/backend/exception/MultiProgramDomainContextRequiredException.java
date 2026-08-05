package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MultiProgramDomainContextRequiredException extends AppException {
    private static final String CODE = "MULTI_PROGRAM_DOMAIN_CONTEXT_REQUIRED";
    private static final String MESSAGE =
            "A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in.";

    public MultiProgramDomainContextRequiredException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
