package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.UserDepartmentDto;
import com.rkt.VisitorManagementSystem.dto.UserDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.PageResponse;
import com.rkt.VisitorManagementSystem.dto.responseDto.UserResponseDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitorResponseDto;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.service.UserService;
import com.rkt.VisitorManagementSystem.service.VisitorService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/rkt/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5173") // Allow requests from React dev server
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final VisitorService visitorService;

    //    @GetMapping
    //    @PreAuthorize("hasRole('HR_MANAGER')")
    //    public ResponseEntity<List<UserResponseDto>> list() {
    //        return ResponseEntity.ok(userService.findAll());
    //    }

    @GetMapping
    @PreAuthorize("hasRole('HR_MANAGER')")
    public ResponseEntity<PageResponse<UserResponseDto>> list(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String[] sortParams,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "departmentName", required = false) String departmentName,
            @RequestParam(value = "roleName", required = false) String roleName,
            @RequestParam(value = "isActive", required = false) Boolean isActive
    ) {

        Sort sort = Sort.unsorted();
        if (sortParams != null && sortParams.length > 0) {
            for (String sp : sortParams) {
                if (sp == null || sp.isBlank()) continue;
                String[] parts = sp.split(",");
                String prop = parts[0].trim();
                Sort.Direction dir = Sort.Direction.ASC;
                if (parts.length > 1) {
                    try {
                        dir = Sort.Direction.fromString(parts[1].trim());
                    } catch (IllegalArgumentException ignored) { dir = Sort.Direction.ASC; }
                }
                sort = sort.and(Sort.by(dir, prop));
            }
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);
        Page<UserResponseDto> result = userService.search(pageable,user,departmentName,roleName,isActive);

        PageResponse<UserResponseDto> resp = new PageResponse<>(
                result.getContent(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.isLast(),
                result.isFirst()
        );

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_MANAGER')")
    public ResponseEntity<UserResponseDto> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(userService.get(id));
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('HR_MANAGER')")

    public ResponseEntity<UserResponseDto> create(
            @RequestBody @Validated(UserDto.Create.class) UserDto dto) {
        UserResponseDto created = userService.create(dto);
        return ResponseEntity.created(URI.create("/rkt/users/" + created.getId())).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> update(
            @PathVariable @Positive Long id,
            @RequestBody @Validated(UserDto.Update.class) UserDto dto) {
        return ResponseEntity.ok(userService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/visitors")
    public ResponseEntity<List<VisitorResponseDto>> myVisitors(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        final String rawName = authentication.getName();
        final String email = (rawName == null) ? null : rawName.trim().toLowerCase();

        if (email == null || email.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        UserEntity u = userRepository.findWithRoleByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + email));

        List<VisitorResponseDto> visitors = visitorService.findByHost(u.getId());
        return ResponseEntity.ok(visitors);
    }

    @GetMapping("/{id}/department")
    public ResponseEntity<UserDepartmentDto> getUserDepartment(@PathVariable("id") Long id) {
        return userService.getUserDepartment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
