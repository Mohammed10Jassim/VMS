package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.dto.RoleDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.RoleEntity;
import com.rkt.VisitorManagementSystem.exception.customException.DuplicateResourceException;
import com.rkt.VisitorManagementSystem.exception.customException.EntityInUseException;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.mapper.RoleMapper;
import com.rkt.VisitorManagementSystem.repository.DepartmentRepository;
import com.rkt.VisitorManagementSystem.repository.RoleRepository;
import com.rkt.VisitorManagementSystem.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return mapper.toDtoList(roleRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDto> findByDepartment(Long departmentId) {
        // ensure department exists (optional but clearer errors)
        departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
        return mapper.toDtoList(roleRepository.findByDepartmentId(departmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDto get(Long id) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        return mapper.toDto(entity);
    }

    @Override
    public RoleDto create(RoleDto dto) {
        String name = normalize(dto.getName());
        DepartmentEntity dept = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));

        RoleEntity entity = mapper.toEntity(
                RoleDto.builder()
                        .name(name)
                        .description(dto.getDescription())
                        .departmentId(dept.getId())
                        .build(),
                dept
        );

        try {
            RoleEntity saved = roleRepository.save(entity);
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            // uk_role_name (global) or length issues
            throw new DuplicateResourceException("Role already exists: " + name);
        }
    }

    @Override
    public RoleDto update(Long id, RoleDto dto) {
        RoleEntity existing = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        DepartmentEntity newDept = null;
        if (dto.getDepartmentId() != null) {
            newDept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));
        }

        // Normalize name if provided
        if (dto.getName() != null) {
            dto.setName(normalize(dto.getName()));
        }

        mapper.updateEntity(existing, dto, newDept);

        try {
            RoleEntity saved = roleRepository.save(existing);
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            // covers unique name conflicts, etc.
            throw new DuplicateResourceException("Role already exists");
        }
    }

    @Override
    public void delete(Long id) {
        RoleEntity existing = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        try {
            roleRepository.delete(existing);
        } catch (DataIntegrityViolationException ex) {
            // users likely still reference this role
            throw new EntityInUseException("Role is in use and cannot be deleted");
        }
    }

    // --- helpers ---
    private String normalize(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }
}

