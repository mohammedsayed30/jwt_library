package com.mohammedshaaban.jwtauth.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import com.mohammedshaaban.jwtauth.config.JwtProperties;

/**
 * Creates signed JWTs and reads their claims using the configured signing
 * secret.
 *
 * <p>The token subject is the {@link UserDetails#getUsername() username}
 * supplied by Spring Security. Generated tokens are signed with an HMAC key
 * derived from the Base64-encoded secret in {@link JwtProperties}.
 */
public class JwtService {

	/** Base64-encoded secret used to derive the token signing key. */
	private String secretkey;

	/** Token lifetime, in milliseconds, captured when this service is created. */
	private final long expirationMillis;

	/**
	 * Creates a service using the supplied JWT configuration.
	 *
	 * @param jwtProperties properties containing the signing secret and token
	 *                      lifetime
	 */
	public JwtService(JwtProperties jwtProperties) {
		this.secretkey = jwtProperties.getSecret();
		this.expirationMillis = jwtProperties.getExpirationTime().toMillis();
	}

	/**
	 * Generates a signed token for the supplied user without additional
	 * application claims.
	 *
	 * @param userDetails user whose username becomes the token subject
	 * @return a compact, signed JWT
	 */
	public String generateToken(UserDetails userDetails) {
		return generateToken(new HashMap<>(), userDetails);
	}

	/**
	 * Generates a signed token with application claims, subject, issue time,
	 * and expiration time. The service sets the standard subject and time
	 * claims in addition to the supplied claims.
	 *
	 * @param Claims additional application claims to include in the token
	 * @param userDetails user whose username becomes the token subject
	 * @return a compact, signed JWT
	 */
	public String generateToken(Map<String, Object> Claims, UserDetails userDetails) {

		long now = System.currentTimeMillis();
		return Jwts.builder()
				.claims(Claims)
				.subject(userDetails.getUsername())
				.issuedAt(new Date(now))
				.expiration(new Date(now + expirationMillis))
				.signWith(getSigningKey())
				.compact();
	}

	/**
	 * Extracts the subject from a signed token.
	 *
	 * @param token compact JWT, optionally prefixed with {@code Bearer }
	 * @return the token subject, or {@code null} if the token has no subject
	 */
	public String extractUsername(String token) {
		return extractClaim(token, claims -> claims.getSubject());
	}

	/**
	 * Extracts the expiration claim from a token.
	 *
	 * @param token compact JWT to parse
	 * @return the token expiration time
	 */
	private Date extractExpiration(String token) {
		return extractClaim(token, claims -> claims.getExpiration());
	}

	/**
	 * Parses and verifies the token, then resolves a value from its claims.
	 *
	 * @param <T> type of the value returned by the resolver
	 * @param token compact JWT to parse
	 * @param claimsResolver function that selects a value from the verified claims
	 * @return the value produced by the resolver
	 */
	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	/**
	 * Parses a signed token and returns its verified claims.
	 *
	 * <p>A leading {@code Bearer } prefix is removed when present. Signature
	 * verification failures and malformed tokens are reported by the JWT
	 * library as exceptions.
	 *
	 * @param token compact JWT to parse
	 * @return the verified claims
	 */
	private Claims extractAllClaims(String token) {
		String cleanToken = token;
		if (token != null && token.startsWith("Bearer ")) {
			cleanToken = token.replace("Bearer ", "").trim();
		}

		return Jwts
				.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(cleanToken)
				.getPayload();

	}

	/**
	 * Decodes the configured Base64 secret and derives the HMAC signing key.
	 *
	 * @return key used to sign and verify JWTs
	 */
	private SecretKey getSigningKey() {
		byte[] KeyBytes = Decoders.BASE64.decode(secretkey);
		return Keys.hmacShaKeyFor(KeyBytes);
	}

	/**
	 * Determines whether the token's expiration time is earlier than the
	 * current time.
	 *
	 * @param token compact JWT to inspect
	 * @return {@code true} if the token is expired
	 */
	public boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	/**
	 * Checks that the token subject matches the user and that the token has not
	 * expired.
	 *
	 * @param token compact JWT to validate
	 * @param userDetails user that the token is expected to identify
	 * @return {@code true} if the subject matches and the token is not expired
	 */
	public boolean isTokenValid(String token, UserDetails userDetails) {
		String userName = extractUsername(token);
		return (userName.equals(userDetails.getUsername())) && !isTokenExpired(token);
	}
}
