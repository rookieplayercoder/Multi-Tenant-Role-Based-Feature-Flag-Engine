package com.prateek.featureflag.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link User}.
 * <p>
 * {@code findByEmailAndDeletedAtIsNull} / {@code existsByEmailAndDeletedAtIsNull}
 * mirror {@code uq_users_email}, the partial unique index scoped to
 * non-deleted rows — used for login lookup and registration uniqueness checks.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
