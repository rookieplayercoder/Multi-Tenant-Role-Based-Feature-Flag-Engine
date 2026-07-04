package com.prateek.featureflag.security.apikey;

import com.prateek.featureflag.apikey.EnvironmentApiKey;
import com.prateek.featureflag.apikey.EnvironmentApiKeyService;
import com.prateek.featureflag.environment.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Resolves a raw SDK API key to its owning {@link Environment}.
 * <p>
 * {@link EnvironmentApiKeyService#getActiveByHash} does an exact-match
 * lookup, so the raw key must be hashed deterministically — SHA-256 (the
 * same algorithm already used in {@code RuleEvaluator.bucketOf}), not
 * BCrypt, which is salted/non-deterministic and only supports verify-style
 * matching, not indexed lookup.
 * <p>
 * The whole operation — hash lookup, usage recording, and reading the
 * LAZY {@code EnvironmentApiKey.environment} association — is wrapped in
 * one explicit {@code @Transactional} method rather than relying on
 * {@code spring.jpa.open-in-view}. This class is called from a security
 * filter, which runs earlier in the servlet filter chain than Spring Boot
 * guarantees {@code OpenEntityManagerInViewFilter} to run relative to other
 * filters — unlike a controller, which always executes after the entire
 * filter chain. An explicit transaction removes that ordering assumption
 * entirely.
 */
@Service
public class ApiKeyAuthenticationService {

    private final EnvironmentApiKeyService environmentApiKeyService;

    public ApiKeyAuthenticationService(EnvironmentApiKeyService environmentApiKeyService) {
        this.environmentApiKeyService = environmentApiKeyService;
    }

    @Transactional
    public Environment authenticate(String rawApiKey) {
        EnvironmentApiKey apiKey = environmentApiKeyService.getActiveByHash(sha256Hex(rawApiKey));
        environmentApiKeyService.recordUsage(apiKey.getId());
        return apiKey.getEnvironment();
    }

    private String sha256Hex(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
