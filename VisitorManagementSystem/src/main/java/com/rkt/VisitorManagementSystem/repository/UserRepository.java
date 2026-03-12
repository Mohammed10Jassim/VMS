// src/main/java/com/rkt/VisitorManagementSystem/repository/UserRepository.java
package com.rkt.VisitorManagementSystem.repository;

import com.rkt.VisitorManagementSystem.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    @EntityGraph(attributePaths = "role")
        // <-- eagerly fetch role for auth
    Optional<UserEntity> findWithRoleByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"department"})
    Optional<UserEntity> findWithDepartmentById(Long id);

    @EntityGraph(attributePaths = {"department", "role"})
    Page<UserEntity> findAllByDepartment_Id(Long departmentId, Pageable pageable);


    /**
     * Search & paginate users by userName, employeeCode, department name, role name and active flag.
     * Pass NULL for params you don't want to filter on.
     */
    @Query(
            value = """
    SELECT u.*
    FROM users u
    JOIN departments d ON u.department_id = d.id
    JOIN roles r ON u.role_id = r.id
    WHERE (:user IS NULL OR (
            LOWER(u.user_name) LIKE CONCAT('%', LOWER(:user), '%')
         OR LOWER(u.employee_code) LIKE CONCAT('%', LOWER(:user), '%')
        ))
      AND (:departmentName IS NULL OR LOWER(d.name) LIKE CONCAT('%', LOWER(:departmentName), '%'))
      AND (:roleName IS NULL OR LOWER(r.name) LIKE CONCAT('%', LOWER(:roleName), '%'))
      AND (:isActive IS NULL OR u.is_active = :isActive)
  """,
            countQuery = """
    SELECT COUNT(1)
    FROM users u
    JOIN departments d ON u.department_id = d.id
    JOIN roles r ON u.role_id = r.id
    WHERE (:user IS NULL OR (
            LOWER(u.user_name) LIKE CONCAT('%', LOWER(:user), '%')
         OR LOWER(u.employee_code) LIKE CONCAT('%', LOWER(:user), '%')
        ))
      AND (:departmentName IS NULL OR LOWER(d.name) LIKE CONCAT('%', LOWER(:departmentName), '%'))
      AND (:roleName IS NULL OR LOWER(r.name) LIKE CONCAT('%', LOWER(:roleName), '%'))
      AND (:isActive IS NULL OR u.is_active = :isActive)
  """,
            nativeQuery = true
    )
    Page<UserEntity> searchUsersNative(
            @Param("user") String user,
            @Param("departmentName") String departmentName,
            @Param("roleName") String roleName,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );


}
