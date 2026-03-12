package com.rkt.VisitorManagementSystem.dto.responseDto;

import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PassResponseDto {

    private Long passId;
    private String visitorName;
    private VisitStatus visitStatus;
    private Instant issuedAt;
    private Instant checkinDeadline;
    private Instant checkinAt;
    private Instant checkoutAt;
    private Instant updatedCheckoutAt;
    private String checkoutUpdateReason;
    private String checkoutUpdatedByName;
    private Instant createdAt;
    private PassStatus passStatus;
    private String gateNo;

}
