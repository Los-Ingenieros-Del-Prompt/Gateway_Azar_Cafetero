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

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "${FRONTEND_URL:http://localhost:3000}", allowCredentials = "true")
public class AuthController {

    private static final String COOKIE_NAME = "AUTH_TOKEN";

    private final WebClient.Builder webClientBuilder;

    @Value("${AUTH_SERVICE_URL:http://localhost:8081}")
    private String authServiceUrl;

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

                    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, jwt)
                            .httpOnly(true)
                            .secure(false)           // true en producción (HTTPS)
                            .path("/")
                            .maxAge(Duration.ofDays(1))
                            .sameSite("Lax")         // Lax en vez de Strict para compatibilidad local
                            .build();

                    response.addCookie(cookie);

                    return ResponseEntity.ok(Map.of(
                        "name",      authResponse.getOrDefault("name", ""),
                        "avatarUrl", authResponse.getOrDefault("avatarUrl", ""),
                        "isNewUser", authResponse.getOrDefault("isNewUser", false),
                        "userId",    authResponse.getOrDefault("userId", "")
                    ));
                })
                .onErrorResume(ex -> {
                    log.error("Error al autenticar con auth-service: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("error", "Error de autenticación")));
                });
    }

    @PostMapping("/auth/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(ServerHttpResponse response) {
        ResponseCookie deleteCookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();

        response.addCookie(deleteCookie);
        return Mono.just(ResponseEntity.ok(Map.of("message", "Sesión cerrada")));
    }

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
