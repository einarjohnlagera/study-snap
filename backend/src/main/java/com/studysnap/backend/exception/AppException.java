package com.studysnap.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
	private final String code;
	private final HttpStatus status;
	private final String details;
	private final String action;

	public AppException(String code, String message, HttpStatus status) {
		this(code, message, null, null, status);
	}

	public AppException(String code, String message, String details, HttpStatus status) {
		this(code, message, details, null, status);
	}

	public AppException(String code, String message, String details, String action, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
		this.details = details;
		this.action = action;
	}
}
