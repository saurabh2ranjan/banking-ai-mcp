package com.banking.gateway.controller;

import com.banking.common.domain.ApiResponse;
import com.banking.common.exception.BankingExceptions.BankingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
        log.warn("Banking exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Validation failed: " + errors, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Resource was concurrently modified. Please retry.", "OPTIMISTIC_LOCK_FAILURE"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getMessage(), "INVALID_ARGUMENT"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        // Never expose internal details to clients
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("An unexpected error occurred. Please contact support.", "INTERNAL_ERROR"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Request body is missing or malformed", "INVALID_REQUEST"));
    }
}
