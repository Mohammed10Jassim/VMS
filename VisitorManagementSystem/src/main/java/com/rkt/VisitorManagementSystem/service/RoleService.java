package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.RoleDto;

import java.util.List;

public interface RoleService {
    List<RoleDto> findAll();
    List<RoleDto> findByDepartment(Long departmentId);
    RoleDto get(Long id);
    RoleDto create(RoleDto dto);
    RoleDto update(Long id, RoleDto dto);
    void delete(Long id);
}
