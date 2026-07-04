package com.prateek.featureflag.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code security.jwt.*} properties. Expected in application.properties:
 * <pre>
 *   security.jwt.secret=&lt;Base64-encoded HMAC-SHA key, 256 bits minimum&gt;
 *   security.jwt.expiration-minutes=60
 * </pre>
 * A record is sufficient here (Boot binds constructor parameters directly,
 * no {@code @ConstructorBinding} needed) since these values are read-only
 * configuration, not mutable state.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
