package com.rkt.VisitorManagementSystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "visitor_approval_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorApprovalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to visitor
    @Column(name = "visitor_id", nullable = false)
    private Long visitorId;

    // Stage: "initial_request" or "confirmation"
    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    // Requested action: APPROVE or REJECT
    @Column(name = "action", length = 12)
    private String action;

    // IP and UA of the clicker
    @Column(name = "requester_ip", length = 64)
    private String requesterIp;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    // Outcome: requested, confirmed, invalid_key, expired, blocked, failed
    @Column(name = "outcome", length = 32)
    private String outcome;

    // Free-form note for additional info (e.g. lock reason)
    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
