package com.mohammedshaaban.jwtauth.filters;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.mohammedshaaban.jwtauth.services.JwtService;

/**
 * Authenticates servlet requests using a JWT supplied in the
 * {@code Authorization} header.
 *
 * <p>Requests without a {@code Bearer} token pass through unchanged. A token
 * rejected by token validation does not establish an authentication, and JWT
 * parsing failures clear the security context before the request continues
 * through the filter chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService JwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Creates a filter backed by the JWT parser and the application's user
     * lookup service.
     *
     * @param JwtService service used to extract and validate token information
     * @param userDetailsService service used to load users by token subject
     */
    public JwtAuthenticationFilter(JwtService JwtService, UserDetailsService userDetailsService) {
        this.JwtService = JwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Checks for a bearer token, loads its subject, and sets the request's
     * Spring Security authentication when the token is valid.
     *
     * <p>JWT parsing exceptions clear the security context; the request is
     * then passed to the next filter.
     *
     * @param request current servlet request
     * @param response current servlet response
     * @param filterChain remaining filters to invoke
     * @throws ServletException if a servlet error occurs while processing the request
     * @throws IOException if an input or output error occurs while processing the request
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Only bearer tokens are handled; other requests pass through unchanged.
        final String AuthHeader = request.getHeader("Authorization");
        final String jwtToken;
        final String userEmail;

        if (AuthHeader == null || !AuthHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwtToken = AuthHeader.substring(7).trim();

        try {
            userEmail = JwtService.extractUsername(jwtToken);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (JwtService.isTokenValid(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
                            null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }

            }
        } catch (JwtException e) {
            // Invalid or expired JWTs must not leave an authentication in the context.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

}
