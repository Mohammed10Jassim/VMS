package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.PassScanResultDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.PassResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PassService {
    /**
     * Generate a Visitor Pass PDF as bytes.
     * @param visitorId the visitor id
     * @param validityMinutes how many minutes after issue the check-in deadline should be (null uses default)
     * @param gateNo optional override (null uses default)
     */
    byte[] generateVisitorPassPdf(Long visitorId, Integer validityMinutes, String gateNo);

    Page<PassResponseDto> getAllPassDetails(Pageable pageable);
}
