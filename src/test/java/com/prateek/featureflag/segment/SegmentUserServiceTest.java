package com.prateek.featureflag.segment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
 * Unit tests for {@link SegmentUserService}. {@link ObjectMapper} is mocked
 * here (unlike in {@code RuleEvaluator} tests) since only the metadata
 * serialization call itself needs to be observed, not exercised end to end.
 */
@ExtendWith(MockitoExtension.class)
class SegmentUserServiceTest {

    private static final String METADATA_JSON = "{\"userIdentifier\":\"user-1\"}";

    @Mock
    private SegmentUserRepository segmentUserRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ObjectMapper objectMapper;

    private SegmentUserService segmentUserService;

    @BeforeEach
    void setUp() {
        segmentUserService = new SegmentUserService(segmentUserRepository, auditLogService, objectMapper);
    }

    private static Organization persistedOrganization() {
        Organization organization = new Organization("Acme Inc", "acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        return organization;
    }

    private static Segment persistedSegment(Organization organization, String name) {
        Segment segment = new Segment(organization, name);
        ReflectionTestUtils.setField(segment, "id", UUID.randomUUID());
        return segment;
    }

    private static User persistedUser() {
        User user = new User("actor@example.com", "hash", "Actor Name");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Nested
    class AddMember {

        @Test
        void savesMembershipAndLogsWhenUserIsNotAlreadyAMember() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            User actor = persistedUser();
            SegmentUserId expectedId = new SegmentUserId(segment.getId(), "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.empty());
            when(segmentUserRepository.save(any(SegmentUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn(METADATA_JSON);

            SegmentUser result = segmentUserService.addMember(segment, "user-1", actor);

            assertThat(result.getUserIdentifier()).isEqualTo("user-1");
            assertThat(result.getSegment()).isEqualTo(segment);
            verify(auditLogService).record(organization, actor, AuditAction.SEGMENT_MEMBER_ADDED,
                    ResourceType.SEGMENT, segment.getId(), METADATA_JSON);
        }

        @Test
        void throwsWhenUserIsAlreadyAMember() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            User actor = persistedUser();
            SegmentUserId expectedId = new SegmentUserId(segment.getId(), "user-1");
            SegmentUser existing = new SegmentUser(segment, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> segmentUserService.addMember(segment, "user-1", actor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1");

            verify(segmentUserRepository, never()).save(any());
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class ListMembers {

        @Test
        void delegatesToRepositoryBySegmentId() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            List<SegmentUser> expected = List.of(new SegmentUser(segment, "user-1"));
            when(segmentUserRepository.findBySegmentId(segment.getId())).thenReturn(expected);

            List<SegmentUser> result = segmentUserService.listMembers(segment.getId());

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class ListSegmentsForUser {

        @Test
        void delegatesToRepositoryByUserIdentifier() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            List<SegmentUser> expected = List.of(new SegmentUser(segment, "user-1"));
            when(segmentUserRepository.findByIdUserIdentifier("user-1")).thenReturn(expected);

            List<SegmentUser> result = segmentUserService.listSegmentsForUser("user-1");

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class RemoveMember {

        @Test
        void deletesMembershipAndLogsWhenUserIsAMember() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            User actor = persistedUser();
            SegmentUserId expectedId = new SegmentUserId(segment.getId(), "user-1");
            SegmentUser existing = new SegmentUser(segment, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.of(existing));
            when(objectMapper.writeValueAsString(any())).thenReturn(METADATA_JSON);

            segmentUserService.removeMember(segment, "user-1", actor);

            verify(segmentUserRepository).delete(existing);
            verify(auditLogService).record(organization, actor, AuditAction.SEGMENT_MEMBER_REMOVED,
                    ResourceType.SEGMENT, segment.getId(), METADATA_JSON);
        }

        @Test
        void throwsWhenUserIsNotAMember() {
            Organization organization = persistedOrganization();
            Segment segment = persistedSegment(organization, "beta-testers");
            User actor = persistedUser();
            SegmentUserId expectedId = new SegmentUserId(segment.getId(), "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> segmentUserService.removeMember(segment, "user-1", actor))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("user-1");

            verify(segmentUserRepository, never()).delete(any());
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }
    }
}