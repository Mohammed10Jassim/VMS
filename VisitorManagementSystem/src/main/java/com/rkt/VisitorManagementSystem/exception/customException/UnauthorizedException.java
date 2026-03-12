package com.rkt.VisitorManagementSystem.exception.customException;

/** 401 – Authentication required/failed. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
