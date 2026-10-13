package com.jwt.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private Duration expirationTime = Duration.ofDays(1); // default to 30 days

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Duration expirationTime) {
        this.expirationTime = expirationTime;
    }
}
