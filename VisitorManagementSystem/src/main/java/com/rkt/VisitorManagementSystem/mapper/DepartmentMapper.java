package com.rkt.VisitorManagementSystem.mapper;

import com.rkt.VisitorManagementSystem.dto.DepartmentDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DepartmentMapper {

    /** Entity -> DTO */
    public DepartmentDto toDto(DepartmentEntity entity) {
        if (entity == null) return null;
        return DepartmentDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }

    /** DTO -> new Entity (for create) */
    public DepartmentEntity toEntity(DepartmentDto dto) {
        if (dto == null) return null;
        return DepartmentEntity.builder()
                .name(dto.getName())
                .build();
    }

    /** Patch-style update: ignores nulls from the DTO */
    public void updateEntity(DepartmentEntity target, DepartmentDto dto) {
        if (target == null || dto == null) return;
        if (dto.getName() != null) target.setName(dto.getName());
    }

    /** Convenience: list mapping */
    public List<DepartmentDto> toDtoList(List<DepartmentEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toDto).toList();
    }
}

