// src/main/java/com/rkt/VisitorManagementSystem/utils/JwtUtils.java
package com.rkt.VisitorManagementSystem.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secretB64;

    @Value("${jwt.secret.previous}")
    private String previousSecretB64;

    @Value("${jwt.expiration-minutes}")
    private long expirationMinutes;

    // ----- secrets -----

    public String getSecretKey() { return secretB64; }  // as stored (Base64)
    public Key getSigningKey()  { return decodeKey(secretB64); }
    private Key getPreviousSigningKey() { return isBlank(previousSecretB64) ? null : decodeKey(previousSecretB64); }

    private SecretKey decodeKey(String base64) {
        if (isBlank(base64)) {
            throw new IllegalStateException("JWT secret is not configured. Set env JWT_SECRET (base64).");
        }
        byte[] keyBytes = Decoders.BASE64.decode(base64);
        return Keys.hmacShaKeyFor(keyBytes); // validates length (>=256-bit recommended)
    }

    // ----- extractors -----

    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        final Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        // Try current key then (optionally) previous key for rotation compatibility
        try {
            return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody();
        } catch (JwtException e) {
            Key prev = getPreviousSigningKey();
            if (prev != null) {
                return Jwts.parserBuilder().setSigningKey(prev).build()
                        .parseClaimsJws(token).getBody();
            }
            throw e;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ----- generators -----

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // You can add user id / role as claims if needed:
        // claims.put("role", userDetails.getAuthorities().stream().findFirst().map(GrantedAuthority::getAuthority).orElse(null));
        return createToken(claims, userDetails.getUsername());
    }

    public String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMinutes * 60_000);
        System.out.println("JWT issued: " + now + " expires: " + exp); // debug log
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ----- validators -----

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUserName(token);
        return username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
