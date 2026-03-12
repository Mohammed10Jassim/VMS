package com.rkt.VisitorManagementSystem.mapper;

import com.rkt.VisitorManagementSystem.dto.UserDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.UserResponseDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.RoleEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class UserMapper {


    public UserDto toDto(UserEntity e) {
        if (e == null) return null;
        return UserDto.builder()
                .id(e.getId())
                .userName(e.getUserName())
                .employeeCode(e.getEmployeeCode())
                .email(e.getEmail())
                // .password(e.getPassword())
                .isActive(e.isActive())
                .mobileNumber(e.getMobileNumber())
                .departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null)

                .roleId(e.getRole() != null ? e.getRole().getId() : null)
                .build();
    }

    public UserEntity toEntity(UserDto dto, DepartmentEntity dept, RoleEntity role) {
        if (dto == null) return null;
        return UserEntity.builder()
                .userName(normalize(dto.getUserName()))
                .employeeCode(dto.getEmployeeCode().trim())
                .email(dto.getEmail().trim())
                .password(dto.getPassword()) // hash in service before save
                .isActive(dto.getIsActive() == null ? true : dto.getIsActive())
                .mobileNumber(trimToNull(dto.getMobileNumber()))
                .department(dept)
                .role(role)
                .build();
    }

    public UserResponseDto toResponse(UserEntity e) {
        if (e == null) return null;
        return UserResponseDto.builder()
                .id(e.getId())
                .userName(e.getUserName())
                .employeeCode(e.getEmployeeCode())
                .email(e.getEmail())
                .isActive(e.isActive())
                .mobileNumber(e.getMobileNumber())
                .departmentName(e.getDepartment() != null ? e.getDepartment().getName() : null)
                //.departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null) // NEW
                .roleName(e.getRole() != null ? e.getRole().getName() : null)
                .build();
    }


    public List<UserResponseDto> toResponseList(List<UserEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toResponse).toList();
    }

    /** Patch-style update (nulls ignored). Pass dept/role if changing them. */
    public void updateEntity(UserEntity target, UserDto dto, DepartmentEntity dept, RoleEntity role) {
        if (target == null || dto == null) return;

        if (dto.getUserName() != null)     target.setUserName(normalize(dto.getUserName()));
        if (dto.getEmployeeCode() != null) target.setEmployeeCode(dto.getEmployeeCode().trim());
        if (dto.getEmail() != null)        target.setEmail(dto.getEmail().trim());
        if (dto.getPassword() != null)     target.setPassword(dto.getPassword()); // hash in service
        if (dto.getIsActive() != null)      target.setActive(dto.getIsActive());
        if (dto.getMobileNumber() != null) target.setMobileNumber(trimToNull(dto.getMobileNumber()));
        if (dept != null)                  target.setDepartment(dept);
        if (role != null)                  target.setRole(role);
    }

//    /** Convenience: list mapping */
//    public List<UserDto> toDtoList(List<UserEntity> entities) {
//        if (entities == null) return Collections.emptyList();
//        return entities.stream().map(this::toDto).toList();
//    }


    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

