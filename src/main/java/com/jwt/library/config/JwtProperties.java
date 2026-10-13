package com.jwt.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank(message = "JWT secret key must not be blank")
    @Size(min = 32, message = "JWT secret key must be at least 32 characters long")
    private String secret;

    @NotNull(message = "JWT expiration time must not be null")
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
