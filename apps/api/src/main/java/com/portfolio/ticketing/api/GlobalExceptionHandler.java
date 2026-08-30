package com.portfolio.ticketing.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiModels.ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiModels.ErrorResponse(
                exception.getCode(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiModels.ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiModels.ErrorResponse(
                "VALIDATION_FAILED", message, Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiModels.ErrorResponse> handleState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiModels.ErrorResponse(
                "INVALID_STATE", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiModels.ErrorResponse> handleConstraint(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiModels.ErrorResponse(
                "CONSTRAINT_VIOLATION", "The request conflicts with current data", Instant.now()));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
