package com.prateek.featureflag.flag;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only snapshot of a {@link FeatureFlag}'s full
 * configuration at a given version. Maps exactly to the
 * {@code flag_versions} table in V1__initial_schema.sql.
 * <p>
 * No {@code deleted_at} — rows here are never updated or deleted, only
 * inserted, enforced at the application/service layer.
 * <p>
 * Unidirectional {@code @ManyToOne} to {@link FeatureFlag}, since FeatureFlag
 * does not yet expose a {@code @OneToMany<FlagVersion>} collection (that
 * would have required modifying an already-built entity, which is out of
 * scope for this batch). {@code changedBy} follows the same author-attribution
 * pattern as {@code FeatureFlag.createdBy}/{@code updatedBy}: LAZY, no
 * cascade, relying on the DB's {@code ON DELETE RESTRICT} to protect
 * attribution.
 */
@Entity
@Table(
        name = "flag_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_flag_versions_flag_version",
                columnNames = {"feature_flag_id", "version"}
        )
)
public class FlagVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_flag_id", nullable = false, updatable = false)
    private FeatureFlag featureFlag;

    @NotNull
    @Min(1)
    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    /**
     * Full serialized flag + rules configuration at this version. Backed by
     * Hibernate 6's native JSON mapping onto the JSONB column, same approach
     * as {@code FeatureRule.value}.
     */
    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, updatable = false)
    private String snapshot;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false)
    private User changedBy;

    @Size(max = 500)
    @Column(name = "change_summary", updatable = false)
    private String changeSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FlagVersion() {
    }

    public FlagVersion(FeatureFlag featureFlag, Integer version, String snapshot, User changedBy) {
        this(featureFlag, version, snapshot, changedBy, null);
    }

    public FlagVersion(FeatureFlag featureFlag, Integer version, String snapshot, User changedBy,
                        String changeSummary) {
        this.featureFlag = featureFlag;
        this.version = version;
        this.snapshot = snapshot;
        this.changedBy = changedBy;
        this.changeSummary = changeSummary;
    }

    public UUID getId() {
        return id;
    }

    public FeatureFlag getFeatureFlag() {
        return featureFlag;
    }

    public Integer getVersion() {
        return version;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlagVersion other)) {
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
        return "FlagVersion{id=%s, version=%d, createdAt=%s}".formatted(id, version, createdAt);
    }
}
