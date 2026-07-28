package com.prateek.featureflag.apikey;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyGenerator {

    private static final String KEY_PREFIX_TAG = "ffe";
    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

   
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
