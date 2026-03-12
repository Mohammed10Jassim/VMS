package com.rkt.VisitorManagementSystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response returned to the scanner/client */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassScanResultDto {
    private Long passId;
    private Long visitorId;

    private String visitorName;
    private String visitPurpose;
    private String company;
    private String hostName;
    private String department;
    private String dateOfVisit;

    private String gateNo;
    private String issuedAt;
    private String checkinDeadline;

    private String checkinAt;   // may be null
    private String checkoutAt;  // may be null

    private String status;      // PassStatus
    private String message;     // e.g., "Checked in", "Already checked in", "Checked out", "Expired", etc.
}
