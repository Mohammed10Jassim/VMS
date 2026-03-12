package com.rkt.VisitorManagementSystem.exception.customException;

/** Thrown when attempting to delete an entity referenced by others. */
public class EntityInUseException extends RuntimeException {
    public EntityInUseException(String message) { super(message); }
}