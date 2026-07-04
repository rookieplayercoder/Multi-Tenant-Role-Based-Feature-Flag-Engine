package com.prateek.featureflag.flag;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Core entity: one row represents a single flag's configuration in a single
 * {@link Environment}. Maps exactly to the {@code feature_flags} table in
 * V1__initial_schema.sql.
 * <p>
 * Does not yet expose {@code @OneToMany} collections to FeatureRule or
 * FlagVersion — those entities are introduced in a later batch, and adding
 * a {@code mappedBy} reference to a nonexistent class would break
 * compilation. Those collections will be added when the respective
 * entities are generated.
 */
@Entity
@Table(
        name = "feature_flags",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_feature_flags_env_key",
                columnNames = {"environment_id", "key"}
        )
)
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Owning side of the FK. LAZY explicitly set to override the JPA-spec
     * EAGER default for @ManyToOne.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @NotBlank
    @Size(max = 150)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "key must be lowercase alphanumeric with hyphens only")
    @Column(name = "key", nullable = false)
    private String key;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 2000)
    @Column(name = "description")
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 20)
    private FlagType flagType = FlagType.BOOLEAN;

    /**
     * Application-managed domain counter, incremented in step with rows in
     * {@code flag_versions}. This is deliberately NOT a Hibernate
     * {@code @Version} optimistic-lock field — conflating the two would
     * cause Hibernate to silently increment this business counter on every
     * unrelated update. Incrementing this value is the responsibility of
     * the (future) service layer whenever a new FlagVersion snapshot is
     * written.
     */
    @NotNull
    @Min(1)
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Author attribution. LAZY, no cascade — saving a flag must never
     * cascade-create or modify a User. DB-level ON DELETE RESTRICT (see
     * V1 migration) already protects this attribute from disappearing out
     * from under existing flags.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FeatureFlag() {
    }

    public FeatureFlag(Environment environment, String key, String name, User createdBy) {
        this.environment = environment;
        this.key = key;
        this.name = name;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FlagType getFlagType() {
        return flagType;
    }

    public void setFlagType(FlagType flagType) {
        this.flagType = flagType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureFlag other)) {
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
        return "FeatureFlag{id=%s, key='%s', enabled=%s, flagType=%s, version=%d}"
                .formatted(id, key, enabled, flagType, version);
    }
}
