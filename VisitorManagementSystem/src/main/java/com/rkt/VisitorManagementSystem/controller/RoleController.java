package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.RoleDto;
import com.rkt.VisitorManagementSystem.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/rkt/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5173")
public class RoleController {

    private final RoleService roleService;

    // GET /rkt/roles  or  /rkt/roles?departmentId=3
    @GetMapping
    public ResponseEntity<List<RoleDto>> list(@RequestParam(required = false) Long departmentId) {
        List<RoleDto> result = (departmentId == null)
                ? roleService.findAll()
                : roleService.findByDepartment(departmentId);
        return ResponseEntity.ok(result);
    }

    // GET /rkt/roles/{id}
    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(roleService.get(id));
    }

    // POST /rkt/roles
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleDto> create(@RequestBody @Valid RoleDto dto) {
        RoleDto created = roleService.create(dto);
        return ResponseEntity
                .created(URI.create("/rkt/roles/" + created.getId()))
                .body(created);
    }

    // PUT /rkt/roles/{id}
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleDto> update(@PathVariable @Positive Long id,
                                          @RequestBody @Valid RoleDto dto) {
        return ResponseEntity.ok(roleService.update(id, dto));
    }

    // DELETE /rkt/roles/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
