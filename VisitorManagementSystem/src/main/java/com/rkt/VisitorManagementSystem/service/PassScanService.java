package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.PassScanResultDto;

public interface PassScanService {
    PassScanResultDto handleScanToken(String token);
    PassScanResultDto handleScanByPassId(Long passId);
}
