package com.prateek.featureflag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes the {@link PasswordEncoder} used for both hashing new passwords
 * (registration) and verifying them (login via {@code DaoAuthenticationProvider}).
 * BCrypt is the standard choice — adaptive cost factor, salt built in.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
