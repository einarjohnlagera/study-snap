package com.studysnap.backend.exception;

import com.studysnap.backend.entity.DomainContext;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

public class InvalidDomainContextException extends AppException {
    private static final String CODE = "INVALID_DOMAIN_CONTEXT";
    private static final String MESSAGE = "Invalid domainContext. Valid values: "
            + Arrays.stream(DomainContext.values())
            .map(DomainContext::getLabel)
            .collect(Collectors.joining(", "))
            + ".";

    public InvalidDomainContextException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
