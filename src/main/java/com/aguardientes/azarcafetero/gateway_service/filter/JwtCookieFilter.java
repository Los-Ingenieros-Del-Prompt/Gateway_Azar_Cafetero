package com.aguardientes.azarcafetero.gateway_service.filter;

import com.aguardientes.azarcafetero.gateway_service.config.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro para rutas protegidas.
 *
 * Lee el JWT desde la cookie HttpOnly "AUTH_TOKEN",
 * lo valida, y lo propaga como header Authorization al microservicio downstream.
 * El frontend nunca manipula el token directamente.
 *
 * Uso en application.yml:
 *   filters:
 *     - JwtCookieFilter
 */
@Slf4j
@Component
public class JwtCookieFilter extends AbstractGatewayFilterFactory<JwtCookieFilter.Config> {

    private static final String COOKIE_NAME      = "AUTH_TOKEN";
    private static final String HEADER_USER_ID   = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtValidator jwtValidator;

    public JwtCookieFilter(JwtValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);

            String path = exchange.getRequest().getURI().getPath();

            if (path.startsWith("/api/building")) {
                return chain.filter(exchange);
            }

            if (cookie == null || cookie.getValue().isBlank()) {
                log.debug("Cookie AUTH_TOKEN ausente — 401");
                return unauthorized(exchange);
            }

            try {
                Claims claims = jwtValidator.validate(cookie.getValue());

                // Inyectar el JWT como Authorization header hacia el microservicio
                // y propagar el userId extraído del token
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.set("Authorization", "Bearer " + cookie.getValue());
                            headers.set(HEADER_USER_ID,   claims.getSubject());
                            headers.set(HEADER_USER_ROLE, claims.get("role", String.class));
                        }))
                        .build();

                return chain.filter(mutated);

            } catch (JwtException e) {
                log.warn("Cookie con JWT inválido: {}", e.getMessage());
                return unauthorized(exchange);
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {}
}