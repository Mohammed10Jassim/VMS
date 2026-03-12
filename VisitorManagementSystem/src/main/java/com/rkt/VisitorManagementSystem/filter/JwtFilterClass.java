package com.rkt.VisitorManagementSystem.filter;

import com.rkt.VisitorManagementSystem.service.security.AppUserDetailsService;
import com.rkt.VisitorManagementSystem.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtFilterClass extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AppUserDetailsService userDetailsService;
    private final AntPathMatcher matcher = new AntPathMatcher();

    // Keep this in sync with SecurityConfig's permitAll patterns
    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/rkt/auth/**",
            "/rkt/public/**",
            "/files/**",
            "/public/**",
            "/rkt/dev/**",
            "/actuator/**"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // Allow CORS preflight (OPTIONS) through without auth processing
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Skip known public endpoints (keeps in sync with SecurityConfig permitAll())
        for (String p : PUBLIC_PATTERNS) {
            if (matcher.match(p, path)) return true;
        }

        return false; // filter applies
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String jwt = null;
        String username = null;

        // Only try to extract when header present and starts with Bearer
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                username = jwtUtils.extractUserName(jwt);
            } catch (Exception ignored) {
                // Invalid/expired token — let AuthenticationEntryPoint handle it later if authentication is required.
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtUtils.validateToken(jwt, userDetails)) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        chain.doFilter(request, response);
    }
}
