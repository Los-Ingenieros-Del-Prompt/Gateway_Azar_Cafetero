package com.aguardientes.azarcafetero.gateway_service.config;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;

/**
 * Componente reutilizable que valida y parsea JWTs firmados con HS256.
 * Mismo secret que usa JwtTokenAdapter en auth-service.
 */
@Component
public class JwtValidator {

    private final Key signingKey;

    public JwtValidator(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(props.getSecret().getBytes());
    }

    /**
     * Valida el token y retorna los claims si es válido.
     *
     * @throws JwtException si el token es inválido o expiró
     */
    public Claims validate(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Retorna true si el token es válido, false en caso contrario.
     * No lanza excepción — útil para decisiones en filtros.
     */
    public boolean isValid(String token) {
        try {
            validate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
