package com.prateek.featureflag.segment;

import com.prateek.featureflag.organization.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A reusable, named group of external end-user identifiers (see
 * {@link SegmentUser}) that a {@code FeatureRule} can reference for
 * targeting. Maps exactly to the {@code segments} table in
 * V1__initial_schema.sql.
 * <p>
 * The relationship to {@link Organization} is unidirectional — Organization
 * is not modified in this batch, same reasoning as Project/Segment in
 * earlier batches.
 */
@Entity
@Table(
        name = "segments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_segments_org_name",
                columnNames = {"organization_id", "name"}
        )
)
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 2000)
    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inverse side of Segment <-> SegmentUser. Both entities are new in
     * this batch, so this bidirectional PERSIST/MERGE-cascade relationship
     * is unconstrained by "don't modify prior entities". No REMOVE cascade,
     * consistent with the project-wide convention of explicit,
     * service-layer-driven deletion.
     */
    @OneToMany(mappedBy = "segment", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<SegmentUser> segmentUsers = new HashSet<>();

    protected Segment() {
    }

    public Segment(Organization organization, String name) {
        this.organization = organization;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
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

    public Set<SegmentUser> getSegmentUsers() {
        return Set.copyOf(segmentUsers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Segment other)) {
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
        return "Segment{id=%s, name='%s'}".formatted(id, name);
    }
}
