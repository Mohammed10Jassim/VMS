package com.rkt.VisitorManagementSystem.exception.customException;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}