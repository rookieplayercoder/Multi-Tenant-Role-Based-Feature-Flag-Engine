package com.prateek.featureflag.segment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch 2 verification: {@link SegmentUserService} — add/list/remove
 * membership. Uses a real {@link ObjectMapper} (tools.jackson) since audit
 * metadata serialization is part of what's being verified, not incidental
 * plumbing worth mocking away.
 */
@ExtendWith(MockitoExtension.class)
class SegmentUserServiceTest {

    @Mock
    private SegmentUserRepository segmentUserRepository;

    @Mock
    private AuditLogService auditLogService;

    private SegmentUserService segmentUserService;

    private Organization organization;
    private Segment segment;
    private User actor;

    @BeforeEach
    void setUp() {
        segmentUserService = new SegmentUserService(segmentUserRepository, auditLogService, new ObjectMapper());

        organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());

        segment = new Segment(organization, "beta-testers");
        setId(segment, UUID.randomUUID());

        actor = new User("alice@example.com", "hash", "Alice");
        setId(actor, UUID.randomUUID());
    }

    @Test
    void addMember_persistsAndAudits_whenNotAlreadyMember() {
        SegmentUserId id = new SegmentUserId(segment.getId(), "user-123");
        when(segmentUserRepository.findById(id)).thenReturn(Optional.empty());
        when(segmentUserRepository.save(any(SegmentUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SegmentUser result = segmentUserService.addMember(segment, "user-123", actor);

        assertThat(result.getUserIdentifier()).isEqualTo("user-123");
        assertThat(result.getSegment()).isEqualTo(segment);

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq(organization), eq(actor), eq(AuditAction.SEGMENT_MEMBER_ADDED),
                eq(ResourceType.SEGMENT), eq(segment.getId()), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue()).contains("user-123");
    }

    @Test
    void addMember_throwsConflict_whenAlreadyMember() {
        SegmentUserId id = new SegmentUserId(segment.getId(), "user-123");
        when(segmentUserRepository.findById(id))
                .thenReturn(Optional.of(new SegmentUser(segment, "user-123")));

        assertThatThrownBy(() -> segmentUserService.addMember(segment, "user-123", actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-123");

        verify(segmentUserRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listMembers_delegatesToFindBySegmentId() {
        SegmentUser member = new SegmentUser(segment, "user-123");
        when(segmentUserRepository.findBySegmentId(segment.getId())).thenReturn(List.of(member));

        List<SegmentUser> members = segmentUserService.listMembers(segment.getId());

        assertThat(members).containsExactly(member);
    }

    @Test
    void listSegmentsForUser_delegatesToFindByIdUserIdentifier() {
        SegmentUser member = new SegmentUser(segment, "user-123");
        when(segmentUserRepository.findByIdUserIdentifier("user-123")).thenReturn(List.of(member));

        List<SegmentUser> segments = segmentUserService.listSegmentsForUser("user-123");

        assertThat(segments).containsExactly(member);
    }

    @Test
    void removeMember_deletesAndAudits_whenMemberExists() {
        SegmentUserId id = new SegmentUserId(segment.getId(), "user-123");
        SegmentUser member = new SegmentUser(segment, "user-123");
        when(segmentUserRepository.findById(id)).thenReturn(Optional.of(member));

        segmentUserService.removeMember(segment, "user-123", actor);

        verify(segmentUserRepository).delete(member);
        verify(auditLogService).record(eq(organization), eq(actor), eq(AuditAction.SEGMENT_MEMBER_REMOVED),
                eq(ResourceType.SEGMENT), eq(segment.getId()), any());
    }

    @Test
    void removeMember_throwsNotFound_whenNotAMember() {
        SegmentUserId id = new SegmentUserId(segment.getId(), "user-123");
        when(segmentUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> segmentUserService.removeMember(segment, "user-123", actor))
                .isInstanceOf(EntityNotFoundException.class);

        verify(segmentUserRepository, never()).delete(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void removeMember_handlesUserIdentifierContainingSlash() {
        // Regression check for the path-variable bug fixed in this batch:
        // removeMember() itself is slash-agnostic — the bug was in how the
        // controller exposed userIdentifier (path variable vs query param),
        // not in this service method. Verifying the service accepts any
        // valid identifier string regardless of content.
        String identifierWithSlash = "org/user-42";
        SegmentUserId id = new SegmentUserId(segment.getId(), identifierWithSlash);
        SegmentUser member = new SegmentUser(segment, identifierWithSlash);
        when(segmentUserRepository.findById(id)).thenReturn(Optional.of(member));

        segmentUserService.removeMember(segment, identifierWithSlash, actor);

        verify(segmentUserRepository).delete(member);
    }

    /** Test-only reflection helper: entity IDs are DB-generated, no public setter exists. */
    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
