package com.aguardientes.azarcafetero.gateway_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Endpoint de autenticación en el gateway.
 *
 * Flujo:
 *   1. Frontend envía el idToken de Google a POST /auth/google
 *   2. Gateway llama al auth-service y obtiene el JWT propio
 *   3. Gateway guarda el JWT en una cookie HttpOnly (nunca expuesta a JS)
 *   4. En cada request siguiente, el browser envía la cookie automáticamente
 *   5. El JwtCookieFilter lee la cookie y la inyecta como header Authorization
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final String COOKIE_NAME = "AUTH_TOKEN";

    private final WebClient.Builder webClientBuilder;

    @Value("${AUTH_SERVICE_URL:http://localhost:8081}")
    private String authServiceUrl;

    /**
     * Login — llama al auth-service, recibe el JWT y lo guarda en cookie HttpOnly.
     */
    @PostMapping("/auth/google")
    public Mono<ResponseEntity<Map<String, Object>>> googleLogin(
            @RequestBody Map<String, String> body,
            ServerHttpResponse response) {

        String idToken = body.get("idToken");

        if (idToken == null || idToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "idToken requerido")));
        }

        return webClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/auth/google")
                .bodyValue(Map.of("idToken", idToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(authResponse -> {
                    String jwt = (String) authResponse.get("token");

                    // Cookie HttpOnly — JS nunca puede leerla
                    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, jwt)
                            .httpOnly(true)                  // ← JS no puede acceder
                            .secure(false)                   // ← cambiar a true en producción (HTTPS)
                            .path("/")
                            .maxAge(Duration.ofDays(1))
                            .sameSite("Strict")
                            .build();

                    response.addCookie(cookie);

                    // Devolver info del usuario pero SIN el token
                    return ResponseEntity.ok(Map.of(
                            "name",      authResponse.getOrDefault("name", ""),
                            "avatarUrl", authResponse.getOrDefault("avatarUrl", ""),
                            "isNewUser", authResponse.getOrDefault("isNewUser", false)
                    ));
                })
                .onErrorResume(ex -> {
                    log.error("Error al autenticar con auth-service: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("error", "Error de autenticación")));
                });
    }

    /**
     * Logout — borra la cookie del browser.
     */
    @PostMapping("/auth/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(ServerHttpResponse response) {
        ResponseCookie deleteCookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ZERO)   // maxAge=0 borra la cookie
                .sameSite("Strict")
                .build();

        response.addCookie(deleteCookie);
        return Mono.just(ResponseEntity.ok(Map.of("message", "Sesión cerrada")));
    }

    /**
     * Verifica si hay sesión activa (la cookie existe y es válida).
     * El JwtCookieFilter ya habrá rechazado el request si el token es inválido.
     */
    @GetMapping("/auth/me")
    public Mono<ResponseEntity<Map<String, String>>> me(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No autenticado")));
        }
        return Mono.just(ResponseEntity.ok(Map.of("userId", userId)));
    }
}