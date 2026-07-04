package com.prateek.featureflag.segment;

import com.prateek.featureflag.segment.Segment;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link SegmentUser}, mirroring the
 * {@code PRIMARY KEY (segment_id, user_identifier)} defined on the
 * {@code segment_users} table in V1__initial_schema.sql.
 * <p>
 * The {@code segmentId} field's column name matches the {@code @JoinColumn}
 * used by {@link SegmentUser}, since that association uses
 * {@code @MapsId} to populate this field.
 */
@Embeddable
public class SegmentUserId implements Serializable {

    @NotNull
    @Column(name = "segment_id")
    private UUID segmentId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "user_identifier")
    private String userIdentifier;

    protected SegmentUserId() {
    }

    public SegmentUserId(UUID segmentId, String userIdentifier) {
        this.segmentId = segmentId;
        this.userIdentifier = userIdentifier;
    }

    public UUID getSegmentId() {
        return segmentId;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SegmentUserId other)) {
            return false;
        }
        return Objects.equals(segmentId, other.segmentId)
                && Objects.equals(userIdentifier, other.userIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segmentId, userIdentifier);
    }
}
