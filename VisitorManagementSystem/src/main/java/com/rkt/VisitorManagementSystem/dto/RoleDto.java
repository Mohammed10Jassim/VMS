package com.rkt.VisitorManagementSystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDto {
    private Long id;

    @NotBlank(message = "Role name is required")
    @Size(max = 64, message = "Role name must be ≤ 64 characters")
    @Pattern(regexp = "^[\\p{L}0-9 .'-]+$", message = "Role name has invalid characters")
    private String name;

    @Size(max = 256, message = "Description must be ≤ 256 characters")
    private String description;

    /** Selected department in the dropdown */
    @NotNull(message = "Department is required for a role")
    private Long departmentId;
}
