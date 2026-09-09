package com.team10.sems.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sems.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("SEMS_JWT_SECRET must contain at least 32 characters");
        }
        issuer = issuer == null || issuer.isBlank() ? "sems-api" : issuer;
        ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
    }
}
