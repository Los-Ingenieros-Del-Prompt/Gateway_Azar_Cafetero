package com.aguardientes.azarcafetero.gateway_service.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret compartido con auth-service.
     * Se inyecta desde la variable de entorno JWT_SECRET (via dotenv).
     */
    private String secret;
}
