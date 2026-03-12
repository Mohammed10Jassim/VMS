package com.rkt.VisitorManagementSystem.mapper;

import com.rkt.VisitorManagementSystem.dto.responseDto.PassResponseDto;
import com.rkt.VisitorManagementSystem.entity.PassEntity;
import lombok.*;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
//@NoArgsConstructor
@Builder
@Component
public class PassMapper {

    public static  PassResponseDto toPassResponseDto(PassEntity pass)
    {
        return PassResponseDto.builder()
                .passId(pass.getId())
                .visitorName(pass.getVisitor().getVisitorName())
                .visitStatus(pass.getVisitor().getVisitStatus())
                .issuedAt(pass.getIssuedAt())
                .checkinDeadline(pass.getCheckinDeadline())
                .checkinAt(pass.getCheckinAt())
                .checkoutAt(pass.getCheckoutAt())
                .updatedCheckoutAt(pass.getUpdatedCheckoutAt())
                .checkoutUpdateReason(pass.getCheckoutUpdateReason())
                .checkoutUpdatedByName(pass.getCheckoutUpdatedByName())
                .createdAt(pass.getCreatedAt())
                .passStatus(pass.getStatus())
                .gateNo(pass.getGateNo())
                .build();
    }
}
