package com.rkt.VisitorManagementSystem.exception.customException;

/** Thrown when a role doesn't belong to the selected department. */
public class RoleDepartmentMismatchException extends RuntimeException {
    public RoleDepartmentMismatchException(String message) { super(message); }
}