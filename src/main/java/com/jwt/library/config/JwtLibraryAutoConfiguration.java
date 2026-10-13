package com.jwt.library.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.jwt.library.filters.JwtAuthenticationFilter;
import com.jwt.library.services.JwtService;
import org.springframework.security.core.userdetails.UserDetailsService;

@AutoConfiguration
@ConditionalOnClass({
        com.jwt.library.filters.JwtAuthenticationFilter.class,
        com.jwt.library.services.JwtService.class,
        com.jwt.library.config.JwtProperties.class
})
public class JwtLibraryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtProperties jwtProperties() {
        return new JwtProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties jwtProperties) {
        return new JwtService(jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
            UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }
}
