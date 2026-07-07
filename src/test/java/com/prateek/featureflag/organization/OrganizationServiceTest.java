package com.prateek.featureflag.organization;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogService auditLogService;

    private OrganizationService organizationService;
    private User owner;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationRepository, memberRepository, auditLogService);
        owner = new User("owner@example.com", "hash", "Owner");
        setId(owner, UUID.randomUUID());
    }

    @Test
    void create_persists_whenSlugIsUnique() {
        when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization organization = organizationService.create("Acme", "acme");

        assertThat(organization.getSlug()).isEqualTo("acme");
        // create() alone (not via createWithOwner) does no membership insert and no audit log -
        // that's deliberately only done by createWithOwner, per the class Javadoc.
        verify(memberRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsConflict_whenSlugAlreadyInUse() {
        when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.create("Acme", "acme"))
                .isInstanceOf(IllegalStateException.class);

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void createWithOwner_createsOrganizationOwnerMembershipAndAuditLog_atomically() {
        when(organizationRepository.existsBySlugAndDeletedAtIsNull("acme")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization org = inv.getArgument(0);
            setId(org, UUID.randomUUID());
            return org;
        });
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization organization = organizationService.createWithOwner("Acme", "acme", owner);

        verify(memberRepository).save(argThatMemberHasRole(MemberRole.OWNER));
        verify(auditLogService).record(eq(organization), eq(owner), eq(AuditAction.ORGANIZATION_CREATED),
                any(), eq(organization.getId()), any());
    }

    @Test
    void getActiveById_throwsNotFound_whenSoftDeleted() {
        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        organization.setDeletedAt(java.time.Instant.now());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> organizationService.getActiveById(organization.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void rename_updatesNameAndAudits() {
        Organization organization = new Organization("Old Name", "acme");
        setId(organization, UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization renamed = organizationService.rename(organization.getId(), "New Name", owner);

        assertThat(renamed.getName()).isEqualTo("New Name");
        verify(auditLogService).record(eq(renamed), eq(owner), eq(AuditAction.ORGANIZATION_RENAMED),
                any(), eq(organization.getId()), any());
    }

    @Test
    void softDelete_setsDeletedAtAndAudits() {
        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        organizationService.softDelete(organization.getId(), owner);

        assertThat(organization.isDeleted()).isTrue();
        verify(auditLogService).record(eq(organization), eq(owner), eq(AuditAction.ORGANIZATION_DELETED),
                any(), eq(organization.getId()), any());
    }

    private static Member argThatMemberHasRole(MemberRole role) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getRole() == role);
    }

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
