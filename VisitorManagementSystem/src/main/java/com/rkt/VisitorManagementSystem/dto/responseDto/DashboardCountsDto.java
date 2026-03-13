package com.rkt.VisitorManagementSystem.dto.responseDto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardCountsDto {

    private VisitStatusCountsDto myVisitorCounts;
    private VisitStatusCountsDto overallVisitorCounts;

}
