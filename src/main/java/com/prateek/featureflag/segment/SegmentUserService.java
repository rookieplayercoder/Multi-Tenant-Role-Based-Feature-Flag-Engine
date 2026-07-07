package com.prateek.featureflag.segment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for {@link SegmentUser}. No soft delete — a pure
 * associative row (see the entity's own Javadoc), so membership removal is
 * a real delete keyed by the composite {@link SegmentUserId}.
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}, same convention as {@link SegmentService}. The
 * audit entry's {@code entityType}/{@code entityId} point at the
 * <em>segment</em> (a membership row has no surrogate UUID of its own to
 * use instead — see {@link SegmentUserId}), with {@code userIdentifier}
 * captured in the JSON metadata instead, the same approach
 * {@code FeatureFlagEvaluationService} already uses for evaluation-context
 * fields that don't fit as a first-class column.
 */
@Service
@Transactional(readOnly = true)
public class SegmentUserService {

    private static final ResourceType ENTITY_TYPE = ResourceType.SEGMENT;

    private final SegmentUserRepository segmentUserRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public SegmentUserService(SegmentUserRepository segmentUserRepository, AuditLogService auditLogService,
                              ObjectMapper objectMapper) {
        this.segmentUserRepository = segmentUserRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SegmentUser addMember(Segment segment, String userIdentifier, User actor) {
        SegmentUserId id = new SegmentUserId(segment.getId(), userIdentifier);
        if (segmentUserRepository.findById(id).isPresent()) {
            throw new IllegalStateException(
                    "User %s is already in segment %s".formatted(userIdentifier, segment.getId()));
        }
        SegmentUser member = segmentUserRepository.save(new SegmentUser(segment, userIdentifier));
        recordAudit(segment, actor, AuditAction.SEGMENT_MEMBER_ADDED, userIdentifier);
        return member;
    }

    public List<SegmentUser> listMembers(UUID segmentId) {
        return segmentUserRepository.findBySegmentId(segmentId);
    }

    public List<SegmentUser> listSegmentsForUser(String userIdentifier) {
        return segmentUserRepository.findByIdUserIdentifier(userIdentifier);
    }

    @Transactional
    public void removeMember(Segment segment, String userIdentifier, User actor) {
        SegmentUserId id = new SegmentUserId(segment.getId(), userIdentifier);
        SegmentUser member = segmentUserRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User %s is not in segment %s".formatted(userIdentifier, segment.getId())));
        segmentUserRepository.delete(member);
        recordAudit(segment, actor, AuditAction.SEGMENT_MEMBER_REMOVED, userIdentifier);
    }

    private void recordAudit(Segment segment, User actor, AuditAction action, String userIdentifier) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userIdentifier", userIdentifier);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        auditLogService.record(segment.getOrganization(), actor, action, ENTITY_TYPE, segment.getId(), metadataJson);
    }
}