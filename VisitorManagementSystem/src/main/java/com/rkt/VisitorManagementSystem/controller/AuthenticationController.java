// src/main/java/com/rkt/VisitorManagementSystem/controller/AuthenticationController.java
package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.ResponseDto;
import com.rkt.VisitorManagementSystem.dto.auth.AuthResponseDto;
import com.rkt.VisitorManagementSystem.dto.auth.LoginRequestDto;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rkt/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthenticationController {

    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;
    private final UserRepository users;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDto<AuthResponseDto>> login(@RequestBody @Valid LoginRequestDto req) {
        final String email = req.getEmail().trim().toLowerCase();

        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.getPassword()));
        } catch (AuthenticationException ex) {
            // Clear, consistent message for wrong email/password
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseDto.of("Invalid email or password", null));
        }

        UserEntity u = users.findWithRoleByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Normalize role to ROLE_<SLUG>
        String rawRole = (u.getRole() != null ? u.getRole().getName() : "USER");   // e.g. "HR-MANAGER"
        String slug    = rawRole.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_"); // -> "HR_MANAGER"

        var springUser = User.builder()
                .username(u.getEmail())
                .password(u.getPassword()) // already BCrypt in DB
                .authorities("ROLE_" + slug)
                .disabled(!u.isActive())
                .build();

        String token = jwtUtils.generateToken(springUser);

        AuthResponseDto body = AuthResponseDto.builder()
                .token(token)
                .userId(u.getId())
                .email(u.getEmail())
                .role(rawRole)
                .userName(u.getUserName())
                .isActive(u.isActive())
                .build();

        return ResponseEntity.ok(ResponseDto.of("Login successful", body));
    }
}
