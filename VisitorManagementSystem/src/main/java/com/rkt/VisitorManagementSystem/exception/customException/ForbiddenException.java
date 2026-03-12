package com.rkt.VisitorManagementSystem.exception.customException;

/** 403 – Authenticated but not permitted. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
