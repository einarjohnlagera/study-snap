package com.studysnap.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
	private final String code;
	private final HttpStatus status;
	private final String details;

	public AppException(String code, String message, HttpStatus status) {
		this(code, message, null, status);
	}

	public AppException(String code, String message, String details, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
		this.details = details;
	}
}
