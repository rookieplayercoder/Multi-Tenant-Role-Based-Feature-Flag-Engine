package com.prateek.featureflag.user;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service layer for {@link User}. Password hashing itself is out of scope
 * here — callers (a future auth module) are expected to pass an already-hashed
 * value, consistent with the entity's own {@code passwordHash} Javadoc.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(String email, String passwordHash, String fullName) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new IllegalStateException("Email already registered: " + email);
        }
        return userRepository.save(new User(email, passwordHash, fullName));
    }

    public User getActiveById(UUID id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public User getActiveByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found for email: " + email));
    }

    @Transactional
    public User updateProfile(UUID id, String fullName) {
        User user = getActiveById(id);
        user.setFullName(fullName);
        return userRepository.save(user);
    }

    @Transactional
    public User changePassword(UUID id, String newPasswordHash) {
        User user = getActiveById(id);
        user.setPasswordHash(newPasswordHash);
        return userRepository.save(user);
    }

    @Transactional
    public void softDelete(UUID id) {
        User user = getActiveById(id);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }
}
