// src/main/java/com/rkt/VisitorManagementSystem/config/SecurityConfig.java
package com.rkt.VisitorManagementSystem.config;

import com.rkt.VisitorManagementSystem.filter.JwtFilterClass;
import com.rkt.VisitorManagementSystem.service.security.AppUserDetailsService;
import com.rkt.VisitorManagementSystem.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;

@Configuration
@EnableMethodSecurity(prePostEnabled =true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;
    private final AppUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationProvider provider) throws Exception {
        JwtFilterClass jwtFilter = new JwtFilterClass(jwtUtils, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .authenticationProvider(provider) // ensure our DaoAuthenticationProvider is used
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/rkt/auth/**", "/rkt/roles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/rkt/public/**").permitAll()
                        .requestMatchers("/files/**").permitAll()
                        .requestMatchers("/public/visitors/**").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/rkt/visitors/respond").permitAll()
                        .requestMatchers("/rkt/visitors/approve").permitAll()
                        .requestMatchers("/rkt/visitors/respond-redirect").permitAll()
                        .requestMatchers("/rkt/dev/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-ui/index.html",
                                "/docs",
                                "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("""
                {"status":401,"error":"Unauthorized","message":"Invalid or missing token"}
                """);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","Origin","X-Requested-With"));
        cfg.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        cfg.setAllowedOriginPatterns(List.of("*"));
        //cfg.setAllowedOrigins(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // default strength 10
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider provider) {
        // Build an AuthenticationManager that uses ONLY the provider we configured
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);   //AppUserDetailsService
        provider.setPasswordEncoder(passwordEncoder);        // BCrypt
        return provider;
    }

    @Bean
    public TemplateEngine templateEngine() {
        TemplateEngine engine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        engine.setTemplateResolver(resolver);
        return engine;
    }


}
