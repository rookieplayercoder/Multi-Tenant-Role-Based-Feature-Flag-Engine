package com.prateek.featureflag.audit;

import com.prateek.featureflag.audit.dto.AuditLogResponse;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read access to an organization's activity trail. Matches
 * {@code /api/organizations/**}, which falls under the "everything else
 * requires authentication" rule in {@code SecurityConfig}.
 * <p>
 * Any member of the organization — regardless of role — may view its audit
 * log; this is a read of the org's own history, not a privileged action
 * like inviting members, so {@link OrganizationAuthorizationService#requireRole}
 * is called with all four {@link MemberRole} values rather than restricting
 * to {@code OWNER}/{@code ADMIN}. A caller with no membership row at all is
 * still denied (fails closed), same as every other authorization check in
 * this codebase.
 */
@RestController
@RequestMapping("/api/organizations")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public AuditLogController(AuditLogService auditLogService,
                              OrganizationAuthorizationService organizationAuthorizationService) {
        this.auditLogService = auditLogService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    /**
     * Returns the organization's audit log, most recent first. Paginated
     * (default page size 20) since the trail is unbounded and only grows —
     * mirrors {@link AuditLogService#recentActivity}, which already returns
     * a {@link Page} for the same reason.
     */
    @GetMapping("/{organizationId}/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> list(@PathVariable UUID organizationId,
                                                       @PageableDefault(size = 20) Pageable pageable,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR, MemberRole.VIEWER);

        Page<AuditLogResponse> page = auditLogService.recentActivity(organizationId, pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(page);
    }
}
