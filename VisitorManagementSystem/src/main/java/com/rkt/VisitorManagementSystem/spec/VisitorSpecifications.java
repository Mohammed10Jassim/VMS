// src/main/java/com/rkt/VisitorManagementSystem/spec/VisitorSpecifications.java
package com.rkt.VisitorManagementSystem.spec;

import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.util.Locale;

public final class VisitorSpecifications {
    private VisitorSpecifications() {}

//    public static Specification<VisitorEntity> freeText(final String q) {
//        if (q == null || q.isBlank()) return null;
//        final String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
//        return (root, query, cb) -> {
//            // join host for host.userName
//            Join<Object, Object> host = root.join("personWhomToMeet", JoinType.LEFT);
//            Expression<String> visitorName = cb.lower(root.get("visitorName"));
//            Expression<String> company = cb.lower(root.get("company"));
//            Expression<String> hostName = cb.lower(host.get("userName"));
//            return cb.or(
//                    cb.like(visitorName, like),
//                    cb.like(company, like),
//                    cb.like(hostName, like)
//            );
//        };
//    }

    public static Specification<VisitorEntity> freeText(final String q) {

        return (root, query, cb) -> {

            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }

            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";

            Join<Object, Object> host = root.join("personWhomToMeet", JoinType.LEFT);

            Expression<String> visitorName = cb.lower(root.get("visitorName"));
            Expression<String> company = cb.lower(root.get("company"));
            Expression<String> hostName = cb.lower(host.get("userName"));

            return cb.or(
                    cb.like(visitorName, like),
                    cb.like(company, like),
                    cb.like(hostName, like)
            );
        };
    }

    public static Specification<VisitorEntity> visitPurpose(final VisitPurpose p) {
        if (p == null) return null;
        return (root, query, cb) -> cb.equal(root.get("visitPurpose"), p);
    }

    public static Specification<VisitorEntity> departmentId(final Long deptId) {
        if (deptId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("department").get("id"), deptId);
    }

    public static Specification<VisitorEntity> hostUserId(final Long hostId) {
        if (hostId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("personWhomToMeet").get("id"), hostId);
    }

//    public static Specification<VisitorEntity> dateOfVisiting(final LocalDate dateOfVisiting) {
//        if (dateOfVisiting == null) return null;
//        return (root, query, cb) -> cb.equal(root.get("dateOfVisiting"), dateOfVisiting);
//    }

    public static Specification<VisitorEntity> passStatus(final PassStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<PassEntity> p = sq.from(PassEntity.class);
            sq.select(p.get("id"));
            Predicate visitorMatch = cb.equal(p.get("visitor").get("id"), root.get("id"));
            Predicate statusMatch = cb.equal(p.get("status"), status);
            sq.where(cb.and(visitorMatch, statusMatch));
            return cb.exists(sq);
        };
    }


    public static Specification<VisitorEntity> visitStatus(final VisitStatus vs) {
        if (vs == null) return null;
        return (root, query, cb) -> cb.equal(root.get("visitStatus"), vs);
    }

    // Combine helper (safe null handling)
    public static Specification<VisitorEntity> combine(
            Specification<VisitorEntity> base,
            Specification<VisitorEntity> next) {

        if (base == null) return next;
        if (next == null) return base;

        return base.and(next);
    }
//    public static Specification<VisitorEntity> dateRange(LocalDate from, LocalDate to) {
//
//        return (root, query, cb) -> {
//
//            Path<LocalDate> visitDate = root.get("dateOfVisiting");
//
//            if (from != null && to != null) {
//                return cb.and(
//                        cb.greaterThanOrEqualTo(visitDate, from),
//                        cb.lessThanOrEqualTo(visitDate, to)
//                );
//            }
//
//            if (from != null) {
//                return cb.greaterThanOrEqualTo(visitDate, from);
//            }
//
//            if (to != null) {
//                return cb.lessThanOrEqualTo(visitDate, to);
//            }
//
//            return cb.conjunction();
//        };
//    }

    public static Specification<VisitorEntity> dateRange(LocalDate from, LocalDate to) {

        return (root, query, cb) -> {

            if (from == null && to == null) {
                return cb.conjunction();
            }

            Path<LocalDate> visitDate = root.get("dateOfVisiting");

            if (from != null && to != null) {
                return cb.between(visitDate, from, to);
            }

            if (from != null) {
                return cb.greaterThanOrEqualTo(visitDate, from);
            }

            return cb.lessThanOrEqualTo(visitDate, to);
        };
    }
}
