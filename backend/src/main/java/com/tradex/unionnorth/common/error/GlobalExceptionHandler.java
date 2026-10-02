package com.tradex.unionnorth.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.tradex.unionnorth")
public class GlobalExceptionHandler {

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleMalformed(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the request fields and identifiers", request, Map.of());
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "VERSION_CONFLICT", "This record changed. Reload before saving again.", request, Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        return build(exception.getStatus(), exception.getCode(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, Object> violations = new LinkedHashMap<>();
        exception.getConstraintViolations()
                .forEach(violation -> violations.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "The request conflicts with existing data", request, Map.of());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleHttpStatus(org.springframework.web.server.ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return build(status, "HTTP_REJECTED", status.getReasonPhrase(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details) {
        request.setAttribute("safeErrorCode", code);
        ApiErrorResponse response = new ApiErrorResponse(code, message, Instant.now(), request.getRequestURI(), details);
        return ResponseEntity.status(status).body(response);
    }
}
