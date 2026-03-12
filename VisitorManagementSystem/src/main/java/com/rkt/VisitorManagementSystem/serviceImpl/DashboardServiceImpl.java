package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.dto.responseDto.VisitStatusCountsDto;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final VisitorRepository visitorRepository;

    @Override
    public VisitStatusCountsDto getVisitStatusCounts() {
        Map<String, Object> row = visitorRepository.fetchVisitStatusCounts();

        long pending = toLong(row.getOrDefault("pending", 0));
        long approved = toLong(row.getOrDefault("approved", 0));
        long rejected = toLong(row.getOrDefault("rejected", 0));
        long checkedIn = toLong(row.getOrDefault("checked_in", 0));
        long checkoutOut = toLong(row.getOrDefault("checked_out", 0));
        long totalVisitors = toLong(row.getOrDefault("total_visitors_count", pending + approved + rejected + checkedIn + checkoutOut));
        long totalUsers = toLong(row.getOrDefault("total_users_count", 0));

        return new VisitStatusCountsDto(pending, approved, rejected, checkedIn, checkoutOut, totalVisitors, totalUsers);
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }
}
