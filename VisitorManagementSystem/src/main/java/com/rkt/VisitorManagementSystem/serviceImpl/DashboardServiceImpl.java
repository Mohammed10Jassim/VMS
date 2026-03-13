package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.dto.responseDto.DashboardCountsDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitStatusCountsDto;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.DashboardService;
import com.rkt.VisitorManagementSystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final VisitorRepository visitorRepository;
    private final UserService userService;

    @Override
    public DashboardCountsDto getVisitStatusCounts(Integer year, Integer month) {

        UserEntity user = userService.getUserByToken();

        Long userId = user.getId();

        boolean isAdmin = user.getRole().getName().equalsIgnoreCase("HR-MANAGER");

        Map<String,Object> myRow = visitorRepository.fetchMyVisitorStatusCounts(userId, year, month);

        VisitStatusCountsDto myCounts = mapCounts(myRow);

        VisitStatusCountsDto overallCounts = null;

        if (isAdmin)
        {

            Map<String,Object> overallRow = visitorRepository.fetchOverallVisitorStatusCounts(year, month);

            overallCounts = mapCounts(overallRow);
        }

        return DashboardCountsDto.builder().myVisitorCounts(myCounts).overallVisitorCounts(overallCounts).build();
    }

    private VisitStatusCountsDto mapCounts(Map<String,Object> row){

        long pending = toLong(row.get("pending"));
        long approved = toLong(row.get("approved"));
        long rejected = toLong(row.get("rejected"));
        long checkedIn = toLong(row.get("checked_in"));
        long checkedOut = toLong(row.get("checked_out"));
        long totalVisitors = toLong(row.get("total_visitors_count"));
        long totalUsers = toLong(row.getOrDefault("total_users_count",0));

        return new VisitStatusCountsDto(pending, approved, rejected, checkedIn, checkedOut, totalVisitors, totalUsers);
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number)o).longValue();
        try { return Long.parseLong(o.toString()); }
        catch (Exception e) { return 0L; }
    }
}

