package com.rkt.VisitorManagementSystem.service;
import com.rkt.VisitorManagementSystem.dto.VisitorDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitorResponseDto;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface VisitorService {
    List<VisitorResponseDto> findAll();
    VisitorResponseDto get(Long id);

    // Existing JSON-only create (unchanged)
    VisitorResponseDto create(VisitorDto dto);

    // NEW: create with optional files (photo + id proof) in the same request
    VisitorResponseDto create(VisitorDto dto, MultipartFile image, MultipartFile idProof);

    VisitorResponseDto update(Long id, VisitorDto dto);
    void delete(Long id);

    List<VisitorResponseDto> findByVisitDate(LocalDate date);
    List<VisitorResponseDto> findByHost(Long hostUserId);

    // Visitor photo endpoints
    VisitorResponseDto uploadImage(Long id, MultipartFile file);
    VisitorResponseDto replaceImage(Long id, MultipartFile file);
    VisitorResponseDto deleteImage(Long id);

    // ID proof (image/pdf) endpoints
    VisitorResponseDto uploadIdProof(Long id, MultipartFile file);
    VisitorResponseDto replaceIdProof(Long id, MultipartFile file);
    VisitorResponseDto deleteIdProof(Long id);



    VisitorEntity findEntityById(Long id);

    Page<VisitorResponseDto> searchVisitorsNew(
            Pageable pageable,
            String q,
            String visitPurpose,
            Long departmentId,
            Long hostUserId,
            String visitStatus,
            LocalDate fromDate,
            LocalDate toDate
    );

}
