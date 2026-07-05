package com.prateek.featureflag.apikey;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a raw SDK API key and its SHA-256 hash. Kept as its own class
 * rather than folded into {@link EnvironmentApiKeyService}, whose own
 * Javadoc explicitly scopes it to already-hashed input — "raw key
 * generation/hashing is out of scope" there, by design. The hash algorithm
 * here must stay in lockstep with
 * {@code ApiKeyAuthenticationService.sha256Hex}, since that's the exact-match
 * lookup a generated key has to satisfy later; both independently hash with
 * plain SHA-256 (no salt) precisely because the lookup is by-hash, not
 * verify-style, so a salted/non-deterministic scheme like BCrypt would break
 * it.
 */
@Component
public class ApiKeyGenerator {

    private static final String KEY_PREFIX_TAG = "ffe";
    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * A newly generated raw key plus its hash, ready to hand to
     * {@link EnvironmentApiKeyService#issue}. {@code rawKey} must be
     * returned to the caller immediately and never persisted or logged —
     * this is the only place in the system it ever exists in memory.
     */
    public record GeneratedKey(String rawKey, String keyHash, String keyPrefix) {
    }

    public GeneratedKey generate() {
        byte[] secretBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String rawKey = KEY_PREFIX_TAG + "_" + secret;
        String keyPrefix = rawKey.substring(0, Math.min(DISPLAY_PREFIX_LENGTH, rawKey.length()));
        String keyHash = sha256Hex(rawKey);

        return new GeneratedKey(rawKey, keyHash, keyPrefix);
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
