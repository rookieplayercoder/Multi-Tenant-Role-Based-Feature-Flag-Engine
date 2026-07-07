package com.prateek.featureflag.segment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Segment}. Duplicate-name check reuses
 * {@code findByOrganizationIdAndNameAndDeletedAtIsNull} — no new
 * repository method added.
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}, matching the convention already established in
 * {@code OrganizationService}/{@code ProjectService}/{@code EnvironmentService}.
 * Unlike those services, there is no pre-existing, unlogged overload to
 * preserve here — {@link Segment} has no controller yet in this codebase, so
 * every mutating method below simply takes an {@code actor} parameter
 * directly rather than needing a second overload.
 */
@Service
@Transactional(readOnly = true)
public class SegmentService {

    private static final ResourceType ENTITY_TYPE = ResourceType.SEGMENT;

    private final SegmentRepository segmentRepository;
    private final AuditLogService auditLogService;

    public SegmentService(SegmentRepository segmentRepository, AuditLogService auditLogService) {
        this.segmentRepository = segmentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Segment create(Organization organization, String name, User actor) {
        if (segmentRepository.findByOrganizationIdAndNameAndDeletedAtIsNull(organization.getId(), name).isPresent()) {
            throw new IllegalStateException("Segment name already in use in this organization: " + name);
        }
        Segment segment = segmentRepository.save(new Segment(organization, name));
        auditLogService.record(organization, actor, AuditAction.SEGMENT_CREATED, ENTITY_TYPE, segment.getId(), null);
        return segment;
    }

    public Segment getActiveById(UUID id) {
        return segmentRepository.findById(id)
                .filter(segment -> !segment.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Segment not found: " + id));
    }

    public List<Segment> listActiveByOrganization(UUID organizationId) {
        return segmentRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Transactional
    public Segment rename(UUID id, String newName, User actor) {
        Segment segment = getActiveById(id);
        segment.setName(newName);
        Segment saved = segmentRepository.save(segment);
        auditLogService.record(saved.getOrganization(), actor, AuditAction.SEGMENT_RENAMED, ENTITY_TYPE,
                saved.getId(), null);
        return saved;
    }

    @Transactional
    public Segment updateDescription(UUID id, String description, User actor) {
        Segment segment = getActiveById(id);
        segment.setDescription(description);
        Segment saved = segmentRepository.save(segment);
        auditLogService.record(saved.getOrganization(), actor, AuditAction.SEGMENT_DESCRIPTION_UPDATED, ENTITY_TYPE,
                saved.getId(), null);
        return saved;
    }

    @Transactional
    public void softDelete(UUID id, User actor) {
        Segment segment = getActiveById(id);
        segment.setDeletedAt(Instant.now());
        Segment saved = segmentRepository.save(segment);
        auditLogService.record(saved.getOrganization(), actor, AuditAction.SEGMENT_DELETED, ENTITY_TYPE,
                saved.getId(), null);
    }
}