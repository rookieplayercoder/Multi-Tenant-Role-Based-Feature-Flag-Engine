package com.prateek.featureflag.segment;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Membership of an external end-user identifier in a {@link Segment}.
 * Maps exactly to the {@code segment_users} table in
 * V1__initial_schema.sql, whose primary key is the composite
 * {@code (segment_id, user_identifier)} — there is no surrogate UUID here,
 * unlike every other entity in this project.
 * <p>
 * {@code user_identifier} is an external end-user ID from the client's own
 * system, not a FK to {@code User} — a different population from the
 * dashboard users who authenticate into this application.
 * <p>
 * {@code @MapsId("segmentId")} tells Hibernate that the {@code segment}
 * association populates the {@code segmentId} component of the embedded
 * id, rather than requiring a separate, redundant column.
 */
@Entity
@Table(name = "segment_users")
public class SegmentUser {

    @EmbeddedId
    private SegmentUserId id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("segmentId")
    @JoinColumn(name = "segment_id", nullable = false, updatable = false)
    private Segment segment;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected SegmentUser() {
    }

    public SegmentUser(Segment segment, String userIdentifier) {
        this.segment = segment;
        this.id = new SegmentUserId(segment.getId(), userIdentifier);
    }

    public SegmentUserId getId() {
        return id;
    }

    public Segment getSegment() {
        return segment;
    }

    public String getUserIdentifier() {
        return id.getUserIdentifier();
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SegmentUser other)) {
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
        return "SegmentUser{id=%s, addedAt=%s}".formatted(id, addedAt);
    }
}
