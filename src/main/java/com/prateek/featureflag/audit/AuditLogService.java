package com.prateek.featureflag.audit;

import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(Organization organization, User actor, AuditAction action, ResourceType entityType,
                           UUID entityId, String metadata) {
        return auditLogRepository.save(new AuditLog(organization, actor, action, entityType, entityId, metadata));
    }

    public Page<AuditLog> recentActivity(UUID organizationId, Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    public List<AuditLog> historyForEntity(ResourceType entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
