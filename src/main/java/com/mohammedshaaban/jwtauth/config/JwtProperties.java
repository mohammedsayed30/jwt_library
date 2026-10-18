package com.mohammedshaaban.jwtauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;

/**
 * Configuration properties used to sign and validate JWTs.
 *
 * <p>Configure these values with the {@code jwt.secret} and
 * {@code jwt.expiration-time} properties. The secret is expected to be
 * Base64-encoded and must contain at least 32 characters. Token expiration
 * defaults to one day.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Base64-encoded signing secret; must contain at least 32 characters. */
    @NotBlank(message = "JWT secret key must not be blank")
    @Size(min = 32, message = "JWT secret key must be at least 32 characters long")
    private String secret;

    /** Lifetime assigned to newly generated tokens; defaults to one day. */
    @NotNull(message = "JWT expiration time must not be null")
    private Duration expirationTime = Duration.ofDays(1);

    /**
     * Returns the Base64-encoded signing secret.
     *
     * @return the configured signing secret
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the Base64-encoded signing secret.
     *
     * @param secret the signing secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Returns the lifetime used when generating tokens.
     *
     * @return the configured token lifetime
     */
    public Duration getExpirationTime() {
        return expirationTime;
    }

    /**
     * Sets the lifetime used when generating tokens.
     *
     * @param expirationTime the token lifetime
     */
    public void setExpirationTime(Duration expirationTime) {
        this.expirationTime = expirationTime;
    }
}
