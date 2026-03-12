package com.rkt.VisitorManagementSystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "roles",
        // composite unique constraint: department + name
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_department_name",
                columnNames = {"department_id", "name"}
        ),
        indexes = {
                @Index(name = "idx_roles_department", columnList = "department_id"),
                @Index(name = "idx_roles_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 256)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_department"))
    private DepartmentEntity department;
}
