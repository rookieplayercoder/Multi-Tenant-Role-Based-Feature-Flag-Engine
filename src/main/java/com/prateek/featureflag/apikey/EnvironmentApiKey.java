package com.prateek.featureflag.apikey;

import com.prateek.featureflag.environment.Environment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * SDK authentication credential, scoped to a single {@link Environment}.
 * Maps exactly to the {@code environment_api_keys} table in
 * V1__initial_schema.sql.
 * <p>
 * Only {@code keyHash} is persisted — the raw key is never stored, same
 * principle as password hashing. {@code keyPrefix} (e.g. {@code ffe_a1b2})
 * is safe to display in the dashboard for identification purposes.
 * {@code revokedAt} is this entity's soft-delete equivalent, matching the
 * Module 1 design decision — there is no separate {@code deleted_at}
 * column here.
 * <p>
 * Unidirectional {@code @ManyToOne} to {@link Environment}, since Environment
 * is not modified in this batch.
 */
@Entity
@Table(
        name = "environment_api_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_environment_api_keys_key_hash",
                columnNames = {"key_hash"}
        )
)
public class EnvironmentApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @NotBlank
    @Size(max = 255)
    @Column(name = "key_hash", nullable = false, updatable = false)
    private String keyHash;

    @NotBlank
    @Size(max = 20)
    @Column(name = "key_prefix", nullable = false, updatable = false)
    private String keyPrefix;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected EnvironmentApiKey() {
    }

    public EnvironmentApiKey(Environment environment, String keyHash, String keyPrefix, String name) {
        this.environment = environment;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnvironmentApiKey other)) {
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
        // keyHash intentionally omitted.
        return "EnvironmentApiKey{id=%s, keyPrefix='%s', name='%s', revoked=%s}"
                .formatted(id, keyPrefix, name, isRevoked());
    }
}
