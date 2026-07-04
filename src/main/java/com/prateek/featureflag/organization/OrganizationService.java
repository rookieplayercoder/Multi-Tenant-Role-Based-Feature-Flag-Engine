package com.prateek.featureflag.organization;

import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service layer for {@link Organization}. Colocated with the entity and
 * repository in the same package, per the convention established in
 * Module 3 Batch 1.
 * <p>
 * Read methods run in a read-only transaction (class-level default);
 * mutating methods are individually annotated to open a writable one.
 * Uniqueness is pre-checked here via {@link OrganizationRepository}'s
 * existing {@code existsBySlugAndDeletedAtIsNull}, so callers get a clear
 * {@link IllegalStateException} instead of a raw DB constraint violation.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;

    public OrganizationService(OrganizationRepository organizationRepository, MemberRepository memberRepository) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Organization create(String name, String slug) {
        if (organizationRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new IllegalStateException("Slug already in use: " + slug);
        }
        return organizationRepository.save(new Organization(name, slug));
    }

    /**
     * Creates an organization and its founding {@code OWNER} membership in
     * one transaction. An organization with no owner is a broken state, so
     * this must not be two separately-committed operations — if the Member
     * insert fails, the Organization insert rolls back with it.
     */
    @Transactional
    public Organization createWithOwner(String name, String slug, User owner) {
        Organization organization = create(name, slug);
        memberRepository.save(new Member(organization, owner, MemberRole.OWNER));
        return organization;
    }

    public Organization getActiveById(UUID id) {
        return organizationRepository.findById(id)
                .filter(org -> !org.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Organization not found: " + id));
    }

    public Organization getActiveBySlug(String slug) {
        return organizationRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found for slug: " + slug));
    }

    @Transactional
    public Organization rename(UUID id, String newName) {
        Organization organization = getActiveById(id);
        organization.setName(newName);
        return organizationRepository.save(organization);
    }

    @Transactional
    public void softDelete(UUID id) {
        Organization organization = getActiveById(id);
        organization.setDeletedAt(Instant.now());
        organizationRepository.save(organization);
    }
}
