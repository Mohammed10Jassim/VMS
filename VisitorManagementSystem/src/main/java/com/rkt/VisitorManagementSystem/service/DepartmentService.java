package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.DepartmentDto;

import java.util.List;

public interface DepartmentService {
    List<DepartmentDto> findAll();
    List<DepartmentDto> findAllByUserId(Long userId);   // NEW
    DepartmentDto get(Long id);
    DepartmentDto create(DepartmentDto dto);
    DepartmentDto update(Long id, DepartmentDto dto);
    void delete(Long id);
}
