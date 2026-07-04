package com.prateek.featureflag.security;

import com.prateek.featureflag.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts the existing {@link User} entity to Spring Security's
 * {@link UserDetails} contract without modifying the entity itself.
 * <p>
 * {@link User} carries no role of its own — role is per-organization via
 * {@code Member} — so every authenticated user is granted a single baseline
 * {@code ROLE_USER} here. Per-organization role checks (owner/admin/editor/
 * viewer) are an authorization concern for a later module, not authentication.
 * <p>
 * {@code isEnabled()} always returns {@code true} because
 * {@link CustomUserDetailsService} only ever loads non-soft-deleted users
 * (via {@code findByEmailAndDeletedAtIsNull}) — a deleted user simply fails
 * to load rather than loading as "disabled".
 */
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    /** Exposes the underlying domain entity for controllers/services that need it. */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
