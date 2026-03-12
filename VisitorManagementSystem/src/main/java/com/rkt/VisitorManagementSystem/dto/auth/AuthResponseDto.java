// src/main/java/com/rkt/VisitorManagementSystem/dto/auth/AuthResponseDto.java
package com.rkt.VisitorManagementSystem.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private String token;
    private Long userId;
    private String email;
    private String role;
    private String userName;
    private Boolean isActive;
}
