package com.prateek.featureflag.security;

import com.prateek.featureflag.user.User;
import com.prateek.featureflag.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for Spring Security by email, reusing the existing
 * {@link UserRepository} unmodified. Delegates the "active user" filter to
 * {@code findByEmailAndDeletedAtIsNull}, already defined in Batch 1, rather
 * than adding new query logic here.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("No active user found for email: " + email));
        return new CustomUserDetails(user);
    }
}
