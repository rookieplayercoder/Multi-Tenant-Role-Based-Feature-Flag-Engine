package com.prateek.featureflag.security.apikey;

import com.prateek.featureflag.environment.Environment;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code X-Api-Key} header once per request and, if it resolves
 * to an active {@link Environment}, populates the
 * {@link SecurityContextHolder} so {@code authorizeHttpRequests} sees an
 * authenticated principal — same overall shape as
 * {@code JwtAuthenticationFilter}, but for machine/SDK clients rather than
 * logged-in users.
 * <p>
 * The principal is the {@link Environment} itself (via
 * {@link PreAuthenticatedAuthenticationToken}, Spring Security's built-in
 * type for "authenticated by a mechanism other than a
 * {@code UserDetailsService} lookup"), granted a single {@code ROLE_SDK}
 * authority. There is no {@code User} behind an SDK request, so
 * {@code CustomUserDetails} doesn't apply here.
 * <p>
 * An unresolvable or revoked key is swallowed, not rejected, matching
 * {@code JwtAuthenticationFilter}'s convention: the request proceeds
 * unauthenticated, and {@code SecurityConfig}'s {@code anyRequest().authenticated()}
 * rule is what actually returns 401.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticationService apiKeyAuthenticationService) {
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String rawApiKey = request.getHeader(API_KEY_HEADER);

        if (rawApiKey != null && !rawApiKey.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Environment environment = apiKeyAuthenticationService.authenticate(rawApiKey);

                PreAuthenticatedAuthenticationToken authToken = new PreAuthenticatedAuthenticationToken(
                        environment, null, List.of(new SimpleGrantedAuthority("ROLE_SDK")));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (EntityNotFoundException unknownOrRevokedKey) {
                // Leave unauthenticated; authorizeHttpRequests rejects the request downstream.
            }
        }

        filterChain.doFilter(request, response);
    }
}
