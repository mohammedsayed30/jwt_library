package com.mohammedshaaban.jwtauth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.mohammedshaaban.jwtauth.filters.JwtAuthenticationFilter;
import com.mohammedshaaban.jwtauth.services.JwtService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configures the JWT services and servlet filter when Spring Security and the
 * servlet API are available.
 *
 * <p>The filter is registered only when the application provides a
 * {@link UserDetailsService} and is running as a servlet web application. An
 * application can replace either bean by defining its own bean of the same
 * type.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnClass({ UserDetailsService.class, OncePerRequestFilter.class })
public class JwtLibraryAutoConfiguration {

    /**
     * Creates the JWT service from the application's {@link JwtProperties}.
     *
     * @param jwtProperties the bound JWT configuration
     * @return the service used to create and validate tokens
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties jwtProperties) {
        return new JwtService(jwtProperties);
    }

    /**
     * Creates the servlet filter that authenticates requests containing a
     * bearer token.
     *
     * @param jwtService the service used to parse and validate tokens
     * @param userDetailsService the application service used to load token subjects
     * @return the JWT authentication filter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(UserDetailsService.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
            UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }
}
