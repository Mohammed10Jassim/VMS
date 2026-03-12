// src/main/java/com/rkt/VisitorManagementSystem/service/UserService.java
package com.rkt.VisitorManagementSystem.service;

import com.rkt.VisitorManagementSystem.dto.UserDepartmentDto;
import com.rkt.VisitorManagementSystem.dto.UserDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserService {
    List<UserResponseDto> findAll();
    // existing
    Page<UserResponseDto> findAll(Pageable pageable);

    Page<UserResponseDto> findAll(Pageable pageable, Long departmentId);

    UserResponseDto get(Long id);
    UserResponseDto create(UserDto dto);
    UserResponseDto update(Long id, UserDto dto);
    void delete(Long id);

    Optional<UserDepartmentDto> getUserDepartment(Long userId);

    Page<UserResponseDto> search(Pageable pageable,
                                 String user,
                                 String departmentName,
                                 String roleName,
                                 Boolean isActive);

}
