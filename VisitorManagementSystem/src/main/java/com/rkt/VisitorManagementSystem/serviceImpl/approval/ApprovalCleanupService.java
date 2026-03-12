package com.rkt.VisitorManagementSystem.serviceImpl.approval;

import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalCleanupService {

    private final VisitorRepository visitorRepository;


    @Scheduled(fixedDelayString = "${app.approval.cleanup-ms:60000}")
    @Transactional
    public void cleanupExpiredApprovalKeys() {
        Instant now = Instant.now();
        try {
            int updated = visitorRepository.clearExpiredApprovalKeys(now);
            if (updated > 0) {
                log.info("Cleared approval keys for {} expired pending visitors", updated);
            }
        } catch (Exception ex) {
            log.warn("Approval cleanup job failed: {}", ex.getMessage());
        }
    }
}

