package com.rkt.VisitorManagementSystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String userName;

    @Column(nullable = false, length = 80)
    private String employeeCode;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @Builder.Default
    @Column(name = "isActive", nullable = false)
    private boolean isActive = true;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_department"))
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role"))
    private RoleEntity role;
}
