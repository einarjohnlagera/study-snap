package com.studysnap.backend.util;

import com.studysnap.backend.exception.AppException;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@UtilityClass
public class UuidParsingUtils {

    public UUID parseUuidOrThrow(String raw, String code, String message, HttpStatus status) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(code, message, status);
        }
    }
}
