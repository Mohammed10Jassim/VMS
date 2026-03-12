package com.rkt.VisitorManagementSystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_dept_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private Set<RoleEntity> roles;
}

