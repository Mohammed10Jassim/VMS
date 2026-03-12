package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.responseDto.VisitStatusCountsDto;

public interface DashboardService {

     VisitStatusCountsDto getVisitStatusCounts();
}
