package com.prateek.featureflag.auth;

import com.prateek.featureflag.auth.dto.LoginRequest;
import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Matches {@code /api/auth/**}, already
 * permitted without authentication in {@code SecurityConfig}.
 * <p>
 * No global exception-handler infrastructure exists yet in this project, so
 * the two expected failure modes are translated to HTTP responses locally
 * rather than propagating to a generic 500. This is intentionally narrow —
 * only the failures {@link AuthenticationService} is documented to throw
 * are handled here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            LoginResponse response = authenticationService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException duplicateEmail) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authenticationService.login(request));
        } catch (AuthenticationException badCredentials) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
