// src/main/java/com/rkt/VisitorManagementSystem/entity/enums/PassStatus.java
package com.rkt.VisitorManagementSystem.entity.enums;

/**
 * Pass lifecycle:
 * ISSUED -> EXPIRED (deadline passed) | REVOKED (manual)
 * EXPIRED, REVOKED are terminal.
 */
public enum PassStatus {
    ISSUED,
    EXPIRED,
    REVOKED
}
