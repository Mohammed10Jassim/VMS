package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.responseDto.VisitStatusCountsDto;
import com.rkt.VisitorManagementSystem.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/dashboard/visitors")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/status-counts")
    public ResponseEntity<VisitStatusCountsDto> getStatusCounts() {
        VisitStatusCountsDto dto = dashboardService.getVisitStatusCounts();
        return ResponseEntity.ok(dto);
    }
}
