package com.prateek.featureflag.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditLog}.
 * <p>
 * {@code findByOrganisationIdOrderByCreatedAtDesc} is paginated to back the
 * dominant "recent activity for this org" access pattern
 * ({@code idx_audit_logs_org_created_at}); audit trails are unbounded and
 * growing, so returning a {@link Page} rather than a full {@link List}
 * avoids loading an entire org's history at once. Note the property path
 * uses {@code organisation} (British spelling), matching AuditLog's actual
 * field name, not {@code organization} like every other entity in this
 * project — the entity's own Javadoc documents this as a deliberate,
 * pre-existing naming mismatch rather than an inconsistency introduced here.
 * {@code findByEntityTypeAndEntityIdOrderByCreatedAtDesc} backs the
 * secondary "history for this specific entity" pattern
 * ({@code idx_audit_logs_entity}).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrganisationIdOrderByCreatedAtDesc(UUID organisationId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
