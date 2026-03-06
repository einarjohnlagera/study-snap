package com.studysnap.backend.exception;

import com.studysnap.backend.config.RequestIdFilter;
import com.studysnap.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
		String requestId = getRequestId(request);
		log.warn(
				"app_exception requestId={} code={} status={} message={}",
				requestId,
				ex.getCode(),
				ex.getStatus().value(),
				ex.getMessage()
		);
		return ResponseEntity.status(ex.getStatus()).body(
				new ApiErrorResponse(requestId, new ApiErrorResponse.ApiError(ex.getCode(), ex.getMessage(), ex.getDetails()))
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String requestId = getRequestId(request);
		FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
		String message = fieldError != null ? fieldError.getDefaultMessage() : "Invalid request.";
		log.warn("validation_exception requestId={} message={}", requestId, message);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				new ApiErrorResponse(requestId, new ApiErrorResponse.ApiError("VALIDATION_ERROR", message, null))
		);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
		String requestId = getRequestId(request);
		log.warn("constraint_exception requestId={} message={}", requestId, ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				new ApiErrorResponse(requestId, new ApiErrorResponse.ApiError(
						"VALIDATION_ERROR",
						"Invalid request.",
						ex.getMessage()
				))
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
		String requestId = getRequestId(request);
		log.error("unhandled_exception requestId={}", requestId, ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				new ApiErrorResponse(requestId, new ApiErrorResponse.ApiError(
						"INTERNAL_ERROR",
						"Something went wrong while processing your request. Please try again.",
						null
				))
		);
	}

	private String getRequestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR);
		return requestId != null ? requestId.toString() : "unknown";
	}
}
