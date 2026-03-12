package com.rkt.VisitorManagementSystem.exception.customException;

/** For throttling OTP/notifications, etc. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) { super(message); }
}