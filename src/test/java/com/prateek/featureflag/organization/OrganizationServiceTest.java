package com.prateek.featureflag.organization;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrganizationService}. Repositories and
 * {@link AuditLogService} are mocked; entity IDs that would normally be
 * assigned by Hibernate on persist are set via {@link ReflectionTestUtils}
 * to mimic post-save state.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogService auditLogService;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationRepository, memberRepository, auditLogService);
    }

    private static Organization persistedOrganization(String name, String slug) {
        Organization organization = new Organization(name, slug);
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        return organization;
    }

    private static User persistedUser() {
        User user = new User("owner@example.com", "hash", "Owner Name");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Nested
    class Create {

        @Test
        void savesOrganizationWhenSlugIsAvailable() {
            when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(false);
            when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
                Organization organization = invocation.getArgument(0);
                ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
                return organization;
            });

            Organization result = organizationService.create("Acme Inc", "acme");

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("Acme Inc");
            assertThat(result.getSlug()).isEqualTo("acme");
            verify(organizationRepository).save(any(Organization.class));
        }

        @Test
        void throwsWhenSlugAlreadyInUse() {
            when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(true);

            assertThatThrownBy(() -> organizationService.create("Acme Inc", "acme"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("acme");

            verify(organizationRepository, never()).save(any());
        }
    }

    @Nested
    class CreateWithOwner {

        @Test
        void savesOrganizationAndOwnerMembershipAndLogsCreation() {
            User owner = persistedUser();
            when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(false);
            when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
                Organization organization = invocation.getArgument(0);
                ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
                return organization;
            });
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Organization result = organizationService.createWithOwner("Acme Inc", "acme", owner);

            assertThat(result.getId()).isNotNull();
            verify(memberRepository).save(argThat(member ->
                    member.getOrganization().equals(result)
                            && member.getUser().equals(owner)
                            && member.getRole() == MemberRole.OWNER));
            verify(auditLogService).record(eq(result), eq(owner), eq(AuditAction.ORGANIZATION_CREATED),
                    eq(ResourceType.ORGANIZATION), eq(result.getId()), isNull());
        }

        @Test
        void propagatesSlugConflictWithoutCreatingMembershipOrAuditLog() {
            User owner = persistedUser();
            when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(true);

            assertThatThrownBy(() -> organizationService.createWithOwner("Acme Inc", "acme", owner))
                    .isInstanceOf(IllegalStateException.class);

            verify(memberRepository, never()).save(any());
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class GetActiveById {

        @Test
        void returnsOrganizationWhenActive() {
            Organization organization = persistedOrganization("Acme Inc", "acme");
            when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

            Organization result = organizationService.getActiveById(organization.getId());

            assertThat(result).isEqualTo(organization);
        }

        @Test
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(organizationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getActiveById(id))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void throwsWhenSoftDeleted() {
            Organization organization = persistedOrganization("Acme Inc", "acme");
            organization.setDeletedAt(Instant.now());
            when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.getActiveById(organization.getId()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class GetActiveBySlug {

        @Test
        void returnsOrganizationWhenFound() {
            Organization organization = persistedOrganization("Acme Inc", "acme");
            when(organizationRepository.findBySlugAndDeletedAtIsNull("acme")).thenReturn(Optional.of(organization));

            Organization result = organizationService.getActiveBySlug("acme");

            assertThat(result).isEqualTo(organization);
        }

        @Test
        void throwsWhenNotFound() {
            when(organizationRepository.findBySlugAndDeletedAtIsNull("acme")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getActiveBySlug("acme"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class Rename {

        @Test
        void updatesNameAndLogsRename() {
            Organization organization = persistedOrganization("Acme Inc", "acme");
            User actor = persistedUser();
            when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizationRepository.save(organization)).thenReturn(organization);

            Organization result = organizationService.rename(organization.getId(), "Acme Corp", actor);

            assertThat(result.getName()).isEqualTo("Acme Corp");
            verify(auditLogService).record(organization, actor, AuditAction.ORGANIZATION_RENAMED,
                    ResourceType.ORGANIZATION, organization.getId(), null);
        }

        @Test
        void throwsWhenOrganizationNotFound() {
            UUID id = UUID.randomUUID();
            User actor = persistedUser();
            when(organizationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.rename(id, "Acme Corp", actor))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class SoftDelete {

        @Test
        void marksDeletedAndLogsDeletion() {
            Organization organization = persistedOrganization("Acme Inc", "acme");
            User actor = persistedUser();
            when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizationRepository.save(organization)).thenReturn(organization);

            organizationService.softDelete(organization.getId(), actor);

            assertThat(organization.isDeleted()).isTrue();
            verify(auditLogService).record(organization, actor, AuditAction.ORGANIZATION_DELETED,
                    ResourceType.ORGANIZATION, organization.getId(), null);
        }
    }
}