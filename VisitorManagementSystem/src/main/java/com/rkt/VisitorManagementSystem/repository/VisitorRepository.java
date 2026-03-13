// src/main/java/com/rkt/VisitorManagementSystem/repository/VisitorRepository.java
package com.rkt.VisitorManagementSystem.repository;

import com.rkt.VisitorManagementSystem.entity.VisitorEntity;

import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository

public interface VisitorRepository extends JpaRepository<VisitorEntity, Long>, JpaSpecificationExecutor<VisitorEntity> {

    List<VisitorEntity> findAllByDateOfVisitingOrderByVisitorNameAsc(LocalDate dateOfVisiting);

    List<VisitorEntity> findAllByPersonWhomToMeet_IdOrderByDateOfVisitingDesc(Long hostUserId);

//    @EntityGraph(attributePaths = {"personWhomToMeet", "department"})
//    Page<VisitorEntity> findAll(Specification<VisitorEntity> spec, Pageable pageable);

    // 1) Cleanup expired approval keys for PENDING visitors
    @Modifying
    @Query("""
      UPDATE VisitorEntity v
      SET v.approvalKeyHash = NULL,
          v.approvalKeyExpiry = NULL,
          v.approvalForUserId = NULL
      WHERE v.approvalKeyHash IS NOT NULL
        AND v.approvalKeyExpiry < :now
        AND v.visitStatus = 'PENDING'
      """)
    int clearExpiredApprovalKeys(@Param("now") Instant now);

    // 2) Atomically consume initial key (used in INITIAL stage) — ensures token exists, not expired and was PENDING
    @Modifying
    @Query("""
      UPDATE VisitorEntity v
      SET v.approvalKeyHash = NULL,
          v.approvalKeyExpiry = NULL,
          v.approvalForUserId = NULL
      WHERE v.id = :visitorId
        AND v.approvalKeyHash IS NOT NULL
        AND v.approvalKeyExpiry > :now
        AND v.visitStatus = 'PENDING'
      """)
    int consumeInitialKey(@Param("visitorId") Long visitorId, @Param("now") Instant now);

    // 3) Atomically set confirmation key (generate confirmHash separately) — optional helper if you want one update
    @Modifying
    @Query("""
      UPDATE VisitorEntity v
      SET v.approvalKeyHash = :confirmHash,
          v.approvalKeyExpiry = :confirmExpiry,
          v.approvalForUserId = :approverId
      WHERE v.id = :visitorId
        AND v.visitStatus = 'PENDING'
      """)
    int setConfirmationKey(@Param("visitorId") Long visitorId,
                           @Param("confirmHash") String confirmHash,
                           @Param("confirmExpiry") Instant confirmExpiry,
                           @Param("approverId") Long approverId);

    // 4) Atomically consume confirmation key and set final status (approve)
    @Modifying
    @Query("""
      UPDATE VisitorEntity v
      SET v.visitStatus = 'APPROVED',
          v.approvalKeyHash = NULL,
          v.approvalKeyExpiry = NULL,
          v.approvalForUserId = NULL
      WHERE v.id = :visitorId
        AND v.approvalKeyHash IS NOT NULL
        AND v.approvalKeyExpiry > :now
        AND v.visitStatus = 'PENDING'
      """)
    int confirmApproveByVisitor(@Param("visitorId") Long visitorId, @Param("now") Instant now);

    // 5) Atomically consume confirmation key and set final status (reject)
    @Modifying
    @Query("""
      UPDATE VisitorEntity v
      SET v.visitStatus = 'REJECTED',
          v.approvalKeyHash = NULL,
          v.approvalKeyExpiry = NULL,
          v.approvalForUserId = NULL
      WHERE v.id = :visitorId
        AND v.approvalKeyHash IS NOT NULL
        AND v.approvalKeyExpiry > :now
        AND v.visitStatus = 'PENDING'
      """)
    int confirmRejectByVisitor(@Param("visitorId") Long visitorId, @Param("now") Instant now);

    @Query(value = """
        SELECT
        SUM(CASE WHEN v.visit_status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
        SUM(CASE WHEN v.visit_status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
        SUM(CASE WHEN v.visit_status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
        SUM(CASE WHEN v.visit_status = 'CHECKED_IN' THEN 1 ELSE 0 END) AS checked_in,
        SUM(CASE WHEN v.visit_status = 'CHECKED_OUT' THEN 1 ELSE 0 END) AS checked_out,
        COUNT(*) AS total_visitors_count,
        (SELECT COUNT(*) FROM users) AS total_users_count
        FROM visitors v
        WHERE
        (:year IS NULL OR YEAR(v.visit_date) = :year)
        AND (:month IS NULL OR MONTH(v.visit_date) = :month)
        """, nativeQuery = true)
    Map<String,Object> fetchOverallVisitorStatusCounts(@Param("year") Integer year, @Param("month") Integer month);


    @Query(value = """
        SELECT
        SUM(CASE WHEN v.visit_status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
        SUM(CASE WHEN v.visit_status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
        SUM(CASE WHEN v.visit_status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
        SUM(CASE WHEN v.visit_status = 'CHECKED_IN' THEN 1 ELSE 0 END) AS checked_in,
        SUM(CASE WHEN v.visit_status = 'CHECKED_OUT' THEN 1 ELSE 0 END) AS checked_out,
        COUNT(*) AS total_visitors_count
        FROM visitors v
        WHERE v.person_to_meet_id = :userId
        AND (:year IS NULL OR YEAR(v.visit_date) = :year)
        AND (:month IS NULL OR MONTH(v.visit_date) = :month)
        """, nativeQuery = true)
    Map<String,Object> fetchMyVisitorStatusCounts(@Param("userId") Long userId, @Param("year") Integer year, @Param("month") Integer month);

    @Modifying
    @Query("""
        UPDATE VisitorEntity v 
        SET v.visitStatus = 'CHECKED_IN'
        WHERE v.id = :visitorId
        AND v.visitStatus = 'APPROVED'
        """)
    int markCheckedIn(@Param("visitorId") Long visitorId);

    @Modifying
    @Query("""
           UPDATE VisitorEntity v
           SET v.visitStatus = 'CHECKED_OUT'
           WHERE v.id = :visitorId
           AND v.visitStatus = 'CHECKED_IN'
           """)
    int markCheckedOut(@Param("visitorId") Long visitorId);

    @Query("""
            SELECT v
            FROM VisitorEntity v
            WHERE (:fromDate IS NULL OR v.dateOfVisiting >= :fromDate)
            AND (:toDate IS NULL OR v.dateOfVisiting <= :toDate)
            """)
    Page<VisitorEntity> filterByVisitDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
    SELECT v
    FROM VisitorEntity v
    LEFT JOIN v.personWhomToMeet h
    LEFT JOIN v.department d
    WHERE (:q IS NULL OR LOWER(v.visitorName) LIKE LOWER(CONCAT('%',:q,'%'))
                 OR LOWER(v.company) LIKE LOWER(CONCAT('%',:q,'%'))
                 OR LOWER(h.userName) LIKE LOWER(CONCAT('%',:q,'%')))
    AND (:visitPurpose IS NULL OR v.visitPurpose = :visitPurpose)
    AND (:departmentId IS NULL OR d.id = :departmentId)
    AND (:hostUserId IS NULL OR h.id = :hostUserId)
    AND (:visitStatus IS NULL OR v.visitStatus = :visitStatus)
    AND (:fromDate IS NULL OR v.dateOfVisiting >= :fromDate)
    AND (:toDate IS NULL OR v.dateOfVisiting <= :toDate)
    """)
    Page<VisitorEntity> searchVisitors(
            @Param("q") String q,
            @Param("visitPurpose") VisitPurpose visitPurpose,
            @Param("departmentId") Long departmentId,
            @Param("hostUserId") Long hostUserId,
            @Param("visitStatus") VisitStatus visitStatus,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );



}
