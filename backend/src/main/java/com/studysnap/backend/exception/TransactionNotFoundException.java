package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends AppException {
    public TransactionNotFoundException() {
        super("TRANSACTION_NOT_FOUND", "Transaction not found.", HttpStatus.NOT_FOUND);
    }
}
