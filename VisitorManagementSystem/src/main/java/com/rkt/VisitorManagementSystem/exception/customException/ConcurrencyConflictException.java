package com.rkt.VisitorManagementSystem.exception.customException;

/** Wraps optimistic locking/version conflicts. */
public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String message) { super(message); }
    public ConcurrencyConflictException(String message, Throwable cause) { super(message, cause); }
}