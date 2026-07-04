package com.prateek.featureflag.organization;

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
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Associative entity resolving the many-to-many between {@link Organization}
 * and {@link User}, carrying the {@link MemberRole} for that specific pairing.
 * <p>
 * Maps exactly to the {@code members} table in V1__initial_schema.sql — no
 * soft delete column exists here by design: per the Module 1 schema,
 * membership removal is a final, real event. Deletion of a Member should go
 * through an explicit repository call in the (future) service layer, not a
 * cascading/orphan removal from Organization or User.
 */
@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_members_org_user",
                columnNames = {"organization_id", "user_id"}
        )
)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Owning side of the relationship — this entity holds the FK column.
     * LAZY explicitly set: @ManyToOne defaults to EAGER per the JPA spec,
     * which would otherwise silently pull the full Organization graph on
     * every Member load.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @CreationTimestamp
    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    /** Null while the invite is pending; set once the user accepts. */
    @Column(name = "joined_at")
    private Instant joinedAt;

    protected Member() {
    }

    public Member(Organization organization, User user, MemberRole role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Member other)) {
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
        return "Member{id=%s, role=%s}".formatted(id, role);
    }
}
