package com.rkt.VisitorManagementSystem.mapper;

import com.rkt.VisitorManagementSystem.dto.RoleDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.RoleEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RoleMapper {

    /** Entity -> DTO */
    public RoleDto toDto(RoleEntity entity) {
        if (entity == null) return null;
        return RoleDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .build();
    }

    /** DTO -> new Entity (for create). Pass the loaded DepartmentEntity from service. */
    public RoleEntity toEntity(RoleDto dto, DepartmentEntity department) {
        if (dto == null) return null;
        return RoleEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .department(department) // must not be null
                .build();
    }

    /**
     * DTO -> new Entity without department (not recommended).
     * Use only if you will set department later in service.
     */
    public RoleEntity toEntity(RoleDto dto) {
        if (dto == null) return null;
        return RoleEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }

    /** Patch-style update (nulls ignored). Pass department if you intend to change it. */
    public void updateEntity(RoleEntity target, RoleDto dto, DepartmentEntity department) {
        if (target == null || dto == null) return;
        if (dto.getName() != null)        target.setName(dto.getName());
        if (dto.getDescription() != null) target.setDescription(dto.getDescription());
        if (department != null)           target.setDepartment(department);
    }

    /** Convenience: list mapping */
    public List<RoleDto> toDtoList(List<RoleEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toDto).toList();
    }
}
