package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.DepartmentDto;
import com.rkt.VisitorManagementSystem.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/rkt/departments")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5173")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public List<DepartmentDto> list(
            @RequestParam(value = "userId", required = false) Long userId) {

        if (userId == null) {
            return departmentService.findAll();           // existing behavior
        } else {
            // return department(s) filtered by user
            return departmentService.findAllByUserId(userId);
        }
    }



    @GetMapping("/{id}")
    public DepartmentDto get(@PathVariable Long id) {
        return departmentService.get(id);
    }

    @PostMapping
    public ResponseEntity<DepartmentDto> create(@RequestBody @Valid DepartmentDto dto) {
        DepartmentDto created = departmentService.create(dto);
        return ResponseEntity
                .created(URI.create("/rkt/departments/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public DepartmentDto update(@PathVariable Long id, @RequestBody @Valid DepartmentDto dto) {
        return departmentService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

