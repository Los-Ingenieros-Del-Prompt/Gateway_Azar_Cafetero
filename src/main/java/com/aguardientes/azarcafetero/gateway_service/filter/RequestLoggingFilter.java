package com.aguardientes.azarcafetero.gateway_service.filter;


import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Filtro global que registra cada petición entrante y el tiempo de respuesta.
 * Se ejecuta antes que cualquier otro filtro (ORDER = -1).
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        long start = Instant.now().toEpochMilli();

        log.info("--> {} {} (from {})",
                req.getMethod(),
                req.getURI().getPath(),
                req.getRemoteAddress() != null ? req.getRemoteAddress().getAddress() : "unknown");

        return chain.filter(exchange).doFinally(signal -> {
            long elapsed = Instant.now().toEpochMilli() - start;
            log.info("<-- {} {} {}ms — {}",
                    req.getMethod(),
                    req.getURI().getPath(),
                    elapsed,
                    exchange.getResponse().getStatusCode());
        });
    }
}
