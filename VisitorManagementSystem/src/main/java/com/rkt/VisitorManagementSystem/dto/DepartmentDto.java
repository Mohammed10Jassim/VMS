package com.rkt.VisitorManagementSystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentDto {

    private Long id; // optional in create; present in responses/updates

    @NotBlank(message = "Department name is required")
    @Size(max = 80, message = "Department name must be ≤ 80 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .'-]+$",
            message = "Department name has invalid characters"
    )
    private String name;
}
