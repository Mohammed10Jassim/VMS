package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.responseDto.DashboardCountsDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitStatusCountsDto;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.service.DashboardService;
import com.rkt.VisitorManagementSystem.service.UserService;
import com.rkt.VisitorManagementSystem.service.security.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/dashboard/visitors")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/status-counts")
    public ResponseEntity<DashboardCountsDto> getStatusCounts(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {

        DashboardCountsDto dto = dashboardService.getVisitStatusCounts(year, month);

        return ResponseEntity.ok(dto);
    }
}
