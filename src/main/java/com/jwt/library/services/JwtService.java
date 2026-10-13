package com.jwt.library.services;

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
import com.jwt.library.config.JwtProperties;

public class JwtService {

	private String secretkey;
	private final long expirationMillis;

	public JwtService(JwtProperties jwtProperties) {
		this.secretkey = jwtProperties.getSecret();
		this.expirationMillis = jwtProperties.getExpirationTime().toMillis();
	}

	// generate token only with the email without any other claims
	public String generateToken(UserDetails userDetails) {
		return generateToken(new HashMap<>(), userDetails);
	}

	// generate the token with extra tokens
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

	// extract the email from the token
	public String extractUsername(String token) {
		return extractClaim(token, claims -> claims.getSubject());
	}

	// extract expiration
	private Date extractExpiration(String token) {
		return extractClaim(token, claims -> claims.getExpiration());
	}

	// extract one claim
	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	// extract all the claims
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

	// get the secret key for the JWT signature
	private SecretKey getSigningKey() {
		// Auto-generated method stub
		byte[] KeyBytes = Decoders.BASE64.decode(secretkey);
		return Keys.hmacShaKeyFor(KeyBytes);
	}

	// check if the token is expired or not
	public boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	// check if this JWT belongs to that user or not and also the token not expired
	public boolean isTokenValid(String token, UserDetails userDetails) {
		String userName = extractUsername(token);
		return (userName.equals(userDetails.getUsername())) && !isTokenExpired(token);
	}
}
