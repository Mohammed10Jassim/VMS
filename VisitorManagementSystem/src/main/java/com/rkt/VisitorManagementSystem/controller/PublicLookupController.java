// src/main/java/com/rkt/VisitorManagementSystem/controller/PublicLookupController.java
package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.DepartmentDto;
import com.rkt.VisitorManagementSystem.mapper.DepartmentMapper;
import com.rkt.VisitorManagementSystem.repository.DepartmentRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rkt/public")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = {"http://localhost:5173","http://localhost:3000","http://localhost:4200"})
public class PublicLookupController {

    private final DepartmentRepository departments;
    private final DepartmentMapper deptMapper;
    private final UserRepository users;

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return deptMapper.toDtoList(departments.findAll());
    }

    @GetMapping("/hosts")
    public List<Map<String,Object>> hostsByDepartment(@RequestParam @NotNull Long departmentId) {
        return users.findAll().stream()
                .filter(u -> u.getRole()!=null && u.getRole().getDepartment()!=null
                        && departmentId.equals(u.getRole().getDepartment().getId()))
                .map(u -> Map.<String,Object>of(
                        "id", u.getId(),
                        "name", u.getUserName()))
                .toList();
    }
}
