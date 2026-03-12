package com.rkt.VisitorManagementSystem.repository;

import com.rkt.VisitorManagementSystem.entity.VisitorApprovalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitorApprovalAuditRepository extends JpaRepository<VisitorApprovalAudit, Long> {

    // Page recent audits for admin UI
    Page<VisitorApprovalAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
