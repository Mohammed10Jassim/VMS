package com.rkt.VisitorManagementSystem.exception.customException;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value @Builder
public class ApiError {
    Instant timestamp;
    int status;
    String error;
    String message;
    String path;
    List<ErrorItem> errors; // empty list when none

    @Value @Builder
    public static class ErrorItem {
        String code;    // e.g., FIELD_ERROR, NO_HANDLER, METHOD_NOT_ALLOWED
        String field;   // for validation; null otherwise
        String detail;  // human-friendly detail
    }
}
