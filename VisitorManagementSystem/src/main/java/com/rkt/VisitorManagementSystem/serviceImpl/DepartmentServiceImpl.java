package com.rkt.VisitorManagementSystem.serviceImpl;


import com.rkt.VisitorManagementSystem.dto.DepartmentDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.exception.customException.DuplicateResourceException;
import com.rkt.VisitorManagementSystem.exception.customException.EntityInUseException;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.mapper.DepartmentMapper;
import com.rkt.VisitorManagementSystem.repository.DepartmentRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper mapper;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll() {
        List<DepartmentEntity> all = departmentRepository.findAll();
        return mapper.toDtoList(all);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDto get(Long id) {
        DepartmentEntity entity = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        return mapper.toDto(entity);
    }

    @Override
    public DepartmentDto create(DepartmentDto dto) {
        String name = normalize(dto.getName());

        departmentRepository.findByName(name).ifPresent(existing -> {
            throw new DuplicateResourceException("Department already exists: " + name);
        });

        DepartmentEntity toSave = mapper.toEntity(
                DepartmentDto.builder().name(name).build()
        );

        try {
            DepartmentEntity saved = departmentRepository.save(toSave);
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            // DB unique/length constraints fallback
            throw new DuplicateResourceException("Department already exists: " + name);
        }
    }

    @Override
    public DepartmentDto update(Long id, DepartmentDto dto) {
        DepartmentEntity existing = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));

        if (dto.getName() != null) {
            String name = normalize(dto.getName());
            departmentRepository.findByName(name).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new DuplicateResourceException("Department already exists: " + name);
                }
            });
            dto.setName(name);
        }

        mapper.updateEntity(existing, dto);

        try {
            DepartmentEntity saved = departmentRepository.save(existing);
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Department already exists");
        }
    }

    @Override
    public void delete(Long id) {
        DepartmentEntity existing = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        try {
            departmentRepository.delete(existing);
        } catch (DataIntegrityViolationException ex) {
            // likely FK constraint (roles/users linked)
            throw new EntityInUseException("Department is in use and cannot be deleted");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDto> findAllByUserId(Long userId) {
        if (userId == null) return findAll();

        // fetch user and return the user's department if present
        return userRepository.findById(userId)
                .map(user -> {
                    if (user.getDepartment() == null) {
                        // Option A: return empty list if user has no department
                        return Collections.<DepartmentDto>emptyList();

                        // Option B (alternative): return all departments instead:
                        // return findAll();
                    }
                    DepartmentDto dto = mapper.toDto(user.getDepartment());
                    return List.of(dto);
                })
                .orElseGet(() -> Collections.emptyList());
    }


    // --- helpers ---
    private String normalize(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }
}
