package com.rkt.VisitorManagementSystem.dto.responseDto;

import lombok.*;

@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
public class VisitStatusCountsDto {

    private long pending;
    private long approved;
    private long rejected;
    private long checkedIn;
    private long checkoutOut;
    private long totalVisitorsCount;
    private long totalUsersCount;
}
