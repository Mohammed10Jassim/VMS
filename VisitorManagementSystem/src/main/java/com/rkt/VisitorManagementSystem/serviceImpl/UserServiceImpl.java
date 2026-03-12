package com.rkt.VisitorManagementSystem.serviceImpl;
import com.rkt.VisitorManagementSystem.dto.UserDepartmentDto;
import com.rkt.VisitorManagementSystem.dto.UserDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.UserResponseDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.RoleEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.exception.customException.DuplicateResourceException;
import com.rkt.VisitorManagementSystem.exception.customException.EntityInUseException;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.exception.customException.RoleDepartmentMismatchException;
import com.rkt.VisitorManagementSystem.mapper.UserMapper;
import com.rkt.VisitorManagementSystem.repository.DepartmentRepository;
import com.rkt.VisitorManagementSystem.repository.RoleRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departments;
    private final RoleRepository roles;
    private final UserMapper mapper;
    private  final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDto> findAll() {
        return mapper.toResponseList(userRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto get(Long id) {
        UserEntity e = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return mapper.toResponse(e);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAll(Pageable pageable) {
        return findAll(pageable, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAll(Pageable pageable, Long departmentId) {
        Page<UserEntity> page;
        if (departmentId != null) {
            page = userRepository.findAllByDepartment_Id(departmentId, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        return page.map(mapper::toResponse);
    }

    @Override
    public UserResponseDto create(UserDto dto) {
        validateCreate(dto);
        String email = normalizeEmail(dto.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already in use: " + email);
        }
        DepartmentEntity dept = departments.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));
        RoleEntity role = roles.findById(dto.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + dto.getRoleId()));
        if (!role.getDepartment().getId().equals(dept.getId())) {
            throw new RoleDepartmentMismatchException("Selected role does not belong to the selected department");
        }

        dto.setEmail(email); // or use toBuilder if enabled
        UserEntity entity = mapper.toEntity(dto, dept, role);
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));

        try {
            UserEntity saved = userRepository.save(entity);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Email already in use: " + email);
        }
    }

    @Override
    public UserResponseDto update(Long id, UserDto dto) {
        UserEntity existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        DepartmentEntity targetDept = existing.getDepartment();
        if (dto.getDepartmentId() != null) {
            targetDept = departments.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));
        }

        RoleEntity targetRole = existing.getRole();
        if (dto.getRoleId() != null) {
            targetRole = roles.findById(dto.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + dto.getRoleId()));
        }

        if (!targetRole.getDepartment().getId().equals(targetDept.getId())) {
            throw new RoleDepartmentMismatchException("Selected role does not belong to the selected department");
        }

        if (dto.getEmail() != null) {
            String newEmail = normalizeEmail(dto.getEmail());
            if (!newEmail.equalsIgnoreCase(existing.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new DuplicateResourceException("Email already in use: " + newEmail);
            }
            dto.setEmail(newEmail);
        }

        mapper.updateEntity(existing, dto, targetDept, targetRole);

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        try {
            UserEntity saved = userRepository.save(existing);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Email already in use");
        }
    }

    @Override
    public void delete(Long id) {
        UserEntity existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        try {
            userRepository.delete(existing);
        } catch (DataIntegrityViolationException ex) {
            throw new EntityInUseException("User is in use and cannot be deleted");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void validateCreate(UserDto dto) {
        if (isBlank(dto.getUserName()))      throw new IllegalArgumentException("User name is required");
        if (isBlank(dto.getEmployeeCode()))  throw new IllegalArgumentException("Employee code is required");
        if (isBlank(dto.getEmail()))         throw new IllegalArgumentException("Email is required");
        if (isBlank(dto.getPassword()))      throw new IllegalArgumentException("Password is required");
        if (dto.getDepartmentId() == null)   throw new IllegalArgumentException("Department is required");
        if (dto.getRoleId() == null)         throw new IllegalArgumentException("Role is required");
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDepartmentDto> getUserDepartment(Long userId) {
        return userRepository.findWithDepartmentById(userId)
                .map(u -> new UserDepartmentDto(
                        u.getId(),
                        u.getUserName(),
                        u.getDepartment() != null ? u.getDepartment().getId() : null,
                        u.getDepartment() != null ? u.getDepartment().getName() : null
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> search(Pageable pageable, String user,
                                        String departmentName, String roleName, Boolean isActive) {

        String users = (user == null || user.trim().isEmpty()) ? null : user.trim();
        String dept  = (departmentName == null || departmentName.trim().isEmpty()) ? null : departmentName.trim();
        String rName = (roleName == null || roleName.trim().isEmpty()) ? null : roleName.trim();

        Page<UserEntity> page = userRepository.searchUsersNative(users,dept, rName, isActive,pageable);
        return page.map(mapper::toResponse);
    }

}
