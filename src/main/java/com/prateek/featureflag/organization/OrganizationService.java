package com.prateek.featureflag.organization;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}. {@code createWithOwner} is the only path that
 * logs "organization created" — the underlying {@code create} has no actor
 * of its own to attribute the log entry to and is not called from anywhere
 * else, so logging once at the {@code createWithOwner} level (with the
 * founding owner as actor) covers it without double-logging. {@code rename}
 * and {@code softDelete} previously had no external callers and no actor
 * parameter; an actor was added to each so the resulting audit entries can
 * be attributed, without touching any other class since nothing outside
 * this service invoked them.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private static final ResourceType ENTITY_TYPE = ResourceType.ORGANIZATION;

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public OrganizationService(OrganizationRepository organizationRepository, MemberRepository memberRepository,
                               AuditLogService auditLogService) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
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
        auditLogService.record(organization, owner, AuditAction.ORGANIZATION_CREATED, ENTITY_TYPE,
                organization.getId(), null);
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

    public List<Organization> getOrganizationsForUser(User user) {
        return memberRepository.findByUserId(user.getId())
                .stream()
                .map(Member::getOrganization)
                .filter(organization -> !organization.isDeleted())
                .toList();
    }

    @Transactional
    public Organization rename(UUID id, String newName, User actor) {
        Organization organization = getActiveById(id);
        organization.setName(newName);
        Organization saved = organizationRepository.save(organization);
        auditLogService.record(saved, actor, AuditAction.ORGANIZATION_RENAMED, ENTITY_TYPE, saved.getId(), null);
        return saved;
    }

    @Transactional
    public void softDelete(UUID id, User actor) {
        Organization organization = getActiveById(id);
        organization.setDeletedAt(Instant.now());
        Organization saved = organizationRepository.save(organization);
        auditLogService.record(saved, actor, AuditAction.ORGANIZATION_DELETED, ENTITY_TYPE, saved.getId(), null);
    }
}