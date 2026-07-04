package com.prateek.featureflag.audit;

import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic, append-only activity trail entry for an {@link Organization}.
 * Maps exactly to the {@code audit_logs} table in V1__initial_schema.sql.
 * <p>
 * Deliberately generic rather than per-entity: {@code entityType} +
 * {@code entityId} identify what changed, without a direct FK to any
 * specific entity table, since audit queries are almost always
 * "recent activity for this org" rather than entity-specific joins (see
 * Module 1 rationale).
 * <p>
 * No {@code deleted_at} — append-only, enforced at the application/service
 * layer. Unidirectional {@code @ManyToOne} to both {@link Organization} and
 * {@link User}; {@code actor} follows the same author-attribution pattern
 * as {@code FeatureFlag.createdBy} and {@code FlagVersion.changedBy}: LAZY,
 * no cascade, protected by the DB's {@code ON DELETE RESTRICT}.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organisation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @NotBlank
    @Size(max = 100)
    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false)
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Organization organisation, User actor, String action, String entityType, UUID entityId) {
        this(organisation, actor, action, entityType, entityId, null);
    }

    public AuditLog(Organization organisation, User actor, String action, String entityType, UUID entityId,
                     String metadata) {
        this.organisation = organisation;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganisation() {
        return organisation;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLog other)) {
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
        return "AuditLog{id=%s, action='%s', entityType='%s', entityId=%s}"
                .formatted(id, action, entityType, entityId);
    }
}
