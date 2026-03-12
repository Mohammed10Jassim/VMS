// src/main/java/com/rkt/VisitorManagementSystem/service/security/AppUserDetailsService.java
package com.rkt.VisitorManagementSystem.service.security;

import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity u = users.findWithRoleByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String rawRole = (u.getRole() != null ? u.getRole().getName() : "USER");
        String slug    = rawRole.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + slug));

        return User.builder()
                .username(u.getEmail())
                .password(u.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!u.isActive())
                .build();
    }

}
