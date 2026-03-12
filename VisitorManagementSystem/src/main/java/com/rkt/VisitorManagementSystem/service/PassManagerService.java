package com.rkt.VisitorManagementSystem.service;

/**
 * Manages lifecycle of a visitor pass:
 * - persists/updates PassEntity (issuedAt, deadline, gate, status, nonce)
 * - then generates a PDF bytes for preview/download
 */
public interface PassManagerService {

    /**
     * Create or update a pass for the given visitor, persist it, and return the generated PDF.
     *
     * @param visitorId        ID of the Visitor
     * @param validityMinutes  validity window in minutes (null/<=0 -> use default)
     * @param gateNo           gate number (null/blank -> use default)
     * @return PDF bytes representing the pass
     */
    byte[] generateAndPersist(Long visitorId, Integer validityMinutes, String gateNo);
}
