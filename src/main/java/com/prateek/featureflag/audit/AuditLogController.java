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
