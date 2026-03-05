package com.studysnap.backend.exception;

import com.studysnap.backend.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex) {
		return ResponseEntity.status(ex.getStatus()).body(
				new ApiErrorResponse(new ApiErrorResponse.ApiError(ex.getCode(), ex.getMessage(), ex.getDetails()))
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
		String message = fieldError != null ? fieldError.getDefaultMessage() : "Invalid request.";
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				new ApiErrorResponse(new ApiErrorResponse.ApiError("VALIDATION_ERROR", message, null))
		);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				new ApiErrorResponse(new ApiErrorResponse.ApiError(
						"VALIDATION_ERROR",
						"Invalid request.",
						ex.getMessage()
				))
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				new ApiErrorResponse(new ApiErrorResponse.ApiError(
						"INTERNAL_ERROR",
						"Something went wrong while processing your request. Please try again.",
						null
				))
		);
	}
}
