package com.prateek.featureflag.auth;

import com.prateek.featureflag.auth.dto.LoginRequest;
import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.security.jwt.JwtProperties;
import com.prateek.featureflag.security.jwt.JwtService;
import com.prateek.featureflag.user.User;
import com.prateek.featureflag.user.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthenticationService(UserService userService,
                                  PasswordEncoder passwordEncoder,
                                  AuthenticationManager authenticationManager,
                                  JwtService jwtService,
                                  JwtProperties jwtProperties) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Hashes the raw password, creates the user via {@link UserService}
     * (which throws {@link IllegalStateException} on a duplicate email —
     * not re-validated here to avoid two sources of truth for that rule),
     * then immediately issues a token so the caller is logged in on
     * successful registration.
     */
    public LoginResponse register(RegisterRequest request) {
        String passwordHash = passwordEncoder.encode(request.password());
        User user = userService.register(request.email(), passwordHash, request.fullName());
        return issueTokenFor(user);
    }

    /**
     * Delegates credential verification entirely to
     * {@link AuthenticationManager#authenticate}, which throws
     * {@link org.springframework.security.core.AuthenticationException}
     * (its subtypes, e.g. {@code BadCredentialsException}) on failure —
     * left unhandled here so the controller decides the HTTP response.
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return issueTokenFor(principal.getUser());
    }

    private LoginResponse issueTokenFor(User user) {
        String token = jwtService.generateToken(new CustomUserDetails(user));
        long expiresInSeconds = jwtProperties.expirationMinutes() * 60;
        return new LoginResponse(token, "Bearer", expiresInSeconds, user.getId(), user.getEmail(), user.getFullName());
    }
}
