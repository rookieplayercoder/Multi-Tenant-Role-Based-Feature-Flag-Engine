package com.prateek.featureflag.user;

import com.prateek.featureflag.organization.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Global login identity. A User is NOT tenant-scoped — the same person can
 * hold different {@link com.prateek.featureflag.organization.MemberRole roles}
 * across multiple organizations via {@link Member}. See Module 1 schema
 * notes for why role lives on Member rather than here.
 * <p>
 * Maps exactly to the {@code users} table in V1__initial_schema.sql.
 * {@code deleted_at} is a plain nullable column — no automatic Hibernate
 * filtering is applied; callers must check {@link #isDeleted()} explicitly.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * BCrypt/Argon2 hash — never the raw password. Deliberately excluded
     * from {@link #toString()} so it never ends up in logs.
     */
    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank
    @Size(max = 255)
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inverse side of the User <-> Member relationship. Same cascade
     * reasoning as Organization.members: PERSIST/MERGE only, no REMOVE,
     * so membership deletion stays an explicit, auditable service action.
     */
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Member> memberships = new HashSet<>();

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Set<Member> getMemberships() {
        return Set.copyOf(memberships);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        // password_hash intentionally omitted.
        return "User{id=%s, email='%s', fullName='%s'}".formatted(id, email, fullName);
    }
}
