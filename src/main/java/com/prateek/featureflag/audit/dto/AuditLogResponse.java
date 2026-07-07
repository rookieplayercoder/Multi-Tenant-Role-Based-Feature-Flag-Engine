package com.prateek.featureflag.audit.dto;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLog;
import com.prateek.featureflag.audit.ResourceType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format mirror of {@link AuditLog}, kept distinct from the entity so
 * the {@code actor} is flattened to its id/email rather than serializing
 * the lazy {@code User}/{@code Organization} associations directly.
 */
public record AuditLogResponse(UUID id, AuditAction action, ResourceType entityType, UUID entityId,
                               UUID actorId, String actorEmail, String metadata, Instant createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getActor().getId(),
                auditLog.getActor().getEmail(),
                auditLog.getMetadata(),
                auditLog.getCreatedAt());
    }
}
