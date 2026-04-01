package com.studysnap.backend.util;

import com.studysnap.backend.exception.AppException;
import lombok.experimental.UtilityClass;

import java.util.UUID;
import java.util.function.Supplier;

@UtilityClass
public class UuidParsingUtils {

    public UUID parseUuidOrThrow(String raw, Supplier<? extends AppException> exceptionSupplier) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw exceptionSupplier.get();
        }
    }
}
