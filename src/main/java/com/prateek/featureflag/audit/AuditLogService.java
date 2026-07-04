package com.prateek.featureflag.audit;

import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link AuditLog}. Append-only, matching the entity — no
 * update/delete methods, only {@code record} (create) and reads. Note the
 * {@code organization} parameter/property naming mirrors the entity's own
 * (British-spelled) field, a pre-existing inconsistency documented in the
 * Batch 1 repository notes rather than something changed here.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(Organization organization, User actor, String action, String entityType,
                            UUID entityId, String metadata) {
        return auditLogRepository.save(new AuditLog(organization, actor, action, entityType, entityId, metadata));
    }

    public Page<AuditLog> recentActivity(UUID organizationId, Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    public List<AuditLog> historyForEntity(String entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
