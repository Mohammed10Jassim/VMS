package com.rkt.VisitorManagementSystem.exception;

import com.rkt.VisitorManagementSystem.exception.customException.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // ===== Domain-specific =====

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return base(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return base(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(RoleDepartmentMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleRoleDeptMismatch(RoleDepartmentMismatchException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(EntityInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleEntityInUse(EntityInUseException ex, HttpServletRequest req) {
        return base(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConcurrency(ConcurrencyConflictException ex, HttpServletRequest req) {
        return base(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(StorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleStorage(StorageException ex, HttpServletRequest req) {
        return base(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiError handleRateLimit(RateLimitExceededException ex, HttpServletRequest req) {
        return base(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return base(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return base(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    // ===== Validation & DB =====

    // @Valid on request bodies (DTOs)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgNotValid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.ErrorItem> items = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiError.ErrorItem.builder()
                        .code("FIELD_ERROR")
                        .field(fe.getField())
                        .detail(fe.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        return ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .path(req.getRequestURI())
                .errors(items)
                .build();
    }

    // @Validated on params/path variables
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.ErrorItem> items = ex.getConstraintViolations().stream()
                .map(cv -> ApiError.ErrorItem.builder()
                        .code("FIELD_ERROR")
                        .field(cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : null)
                        .detail(cv.getMessage())
                        .build())
                .collect(Collectors.toList());
        return base(HttpStatus.BAD_REQUEST, "Validation failed", req, items);
    }

    // DB unique/FK constraints
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return base(HttpStatus.CONFLICT, "Data integrity violation", req);
    }

    // JPA optimistic locking
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return base(HttpStatus.CONFLICT, "Concurrent update conflict", req);
    }

    // ===== Framework / Routing (no catch-all 500) =====

    // 404: Unknown URL (requires spring.mvc.throw-exception-if-no-handler-found=true)
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNoHandler(NoHandlerFoundException ex, HttpServletRequest req) {
        return base(HttpStatus.NOT_FOUND, "Endpoint not found", req);
    }

    // 405: Wrong HTTP method
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiError handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return base(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", req);
    }

    // 415: Unsupported Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiError handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return base(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", req);
    }

    // 400: Bad JSON / unreadable body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Malformed JSON request", req);
    }

    // 400: Wrong param type (e.g., /users/abc where Long expected)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Invalid parameter type", req);
    }

    // 400: Missing required request parameter
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), req);
    }

    // ===== Helpers =====

    private ApiError base(HttpStatus status, String message, HttpServletRequest req) {
        return base(status, message, req, Collections.emptyList());
    }

    private ApiError base(HttpStatus status, String message, HttpServletRequest req,
                          List<ApiError.ErrorItem> errors) {
        if (errors == null) errors = Collections.emptyList();
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .errors(errors)
                .build();
    }

    // Keep a simple map-based handler for IllegalArgumentException so callers (and tests) get a 400 with a simple payload
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", "Bad Request");
        errorBody.put("message", ex.getMessage());
        errorBody.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        Map<String,Object> body = Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "status", 400,
                "timestamp", Instant.now().toEpochMilli()
        );
        return ResponseEntity.badRequest().body(body);
    }
}
