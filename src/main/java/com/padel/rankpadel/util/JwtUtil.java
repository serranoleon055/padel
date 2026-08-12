package com.padel.rankpadel.util;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.padel.rankpadel.enums.RolUsuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public static final String DEFAULT_ROLE = "ROLE_ADMIN";

    public String generateToken(String username, RolUsuario rol) {
        return Jwts.builder()
                .subject(username)
                .claim("role", "ROLE_" + (rol != null ? rol : RolUsuario.DUENIO).name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * El rol que viaja en el token. Un token viejo trae {@code ROLE_ADMIN}, de antes de
     * que existieran los roles: se lo trata como dueño, que es lo que era.
     */
    public String extractRole(String token) {
        Object role = extractAllClaims(token).get("role");
        if (role == null || DEFAULT_ROLE.equals(role.toString())) {
            return "ROLE_" + RolUsuario.DUENIO.name();
        }
        return role.toString();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
