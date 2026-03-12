package com.rkt.VisitorManagementSystem.exception.customException;

/** For file/blob storage issues (photos/signatures) */
public class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}