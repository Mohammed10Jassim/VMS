package com.rkt.VisitorManagementSystem.repository;

import com.rkt.VisitorManagementSystem.entity.PassEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PassRepository extends JpaRepository<PassEntity, Long> {
    Optional<PassEntity> findByVisitor_Id(Long visitorId);
    boolean existsByVisitor_Id(Long visitorId);
    void deleteByVisitor_Id(Long visitorId);

    @Query("""
       SELECT p
       FROM PassEntity p
        LEFT JOIN FETCH p.visitor v
       """)
    Page<PassEntity> findAllWithVisitor(Pageable pageable);

}
