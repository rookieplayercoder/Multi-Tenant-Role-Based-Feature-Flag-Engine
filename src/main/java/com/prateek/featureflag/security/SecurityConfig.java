package com.prateek.featureflag.security;

import com.prateek.featureflag.security.apikey.ApiKeyAuthenticationFilter;
import com.prateek.featureflag.security.jwt.JwtAuthenticationFilter;
import com.prateek.featureflag.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Core HTTP security wiring: stateless sessions (JWT, not cookies), CSRF
 * disabled (meaningless without server-side session state), and two
 * pre-authentication filters installed ahead of Spring Security's own
 * username/password filter — one for JWT-bearing dashboard users, one for
 * API-key-bearing SDK clients ({@link ApiKeyAuthenticationFilter}, added
 * this batch). Both populate the same {@code SecurityContextHolder}; which
 * one actually authenticates a given request depends only on which header
 * it carries ({@code Authorization: Bearer ...} vs {@code X-Api-Key}).
 * <p>
 * {@code /api/auth/**} is left open for login/registration — everything
 * else, including the new SDK evaluation endpoint, requires authentication
 * via one of the two filters. The {@link AuthenticationManager} bean
 * delegates to {@link AuthenticationConfiguration}, which Spring Boot
 * already assembles from the {@code PasswordEncoder} and
 * {@code UserDetailsService} beans present in context; no manual
 * {@code DaoAuthenticationProvider} wiring is needed.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
