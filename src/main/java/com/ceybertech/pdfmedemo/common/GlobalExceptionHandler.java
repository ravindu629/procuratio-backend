package com.ceybertech.pdfmedemo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into the same JSON shape, { "message": "..." }, with the right HTTP status.
 * The frontend shows this message to the user.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return respond(e.getStatus(), e.getMessage());
	}

	/** @Valid failed, for example an empty name. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		FieldError first = e.getBindingResult().getFieldError();
		String message = first != null ? first.getDefaultMessage() : "The request is not valid.";
		return respond(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
		return respond(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.");
	}

	/** For example /api/templates/abc, where the id is not a UUID. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return respond(HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "'.");
	}

	/** Two saves at the same moment: the database version check caught it. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
		return respond(HttpStatus.CONFLICT, "This template was changed somewhere else. Reload it before saving.");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		return respond(HttpStatus.NOT_FOUND, "Not found.");
	}

	/** Anything unexpected: log the details for developers, show a safe message to users. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("Unexpected error", e);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server. Please try again.");
	}

	private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(message));
	}

}
