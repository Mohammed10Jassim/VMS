// src/main/java/com/rkt/VisitorManagementSystem/dto/auth/LoginRequestDto.java
package com.rkt.VisitorManagementSystem.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDto {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String password;
}
