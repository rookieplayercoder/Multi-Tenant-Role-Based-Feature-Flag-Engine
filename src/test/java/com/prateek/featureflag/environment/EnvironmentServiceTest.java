package com.prateek.featureflag.environment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
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
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private AuditLogService auditLogService;

    private EnvironmentService environmentService;
    private Project project;
    private User actor;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentService(environmentRepository, auditLogService);
        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        actor = new User("dev@example.com", "hash", "Dev");
        setId(actor, UUID.randomUUID());
    }

    @Test
    void create_threeArg_persistsButDoesNotAudit() {
        when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                .thenReturn(Optional.empty());
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        Environment environment = environmentService.create(project, "Development", EnvironmentType.DEV);

        assertThat(environment.getKey()).isEqualTo(EnvironmentType.DEV);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_fourArg_persistsAndAudits() {
        // Same discrepancy as ProjectService: EnvironmentController.create actually
        // calls this audited overload, not the unlogged 3-arg one the class Javadoc
        // describes. Test locks in the real (audited) behavior.
        when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                .thenReturn(Optional.empty());
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });

        Environment environment = environmentService.create(project, "Development", EnvironmentType.DEV, actor);

        verify(auditLogService).record(eq(project.getOrganization()), eq(actor), eq(AuditAction.ENVIRONMENT_CREATED),
                any(), eq(environment.getId()), any());
    }

    @Test
    void create_throwsConflict_whenKeyAlreadyExistsForProject() {
        when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                .thenReturn(Optional.of(new Environment(project, "Existing Dev", EnvironmentType.DEV)));

        assertThatThrownBy(() -> environmentService.create(project, "Development", EnvironmentType.DEV))
                .isInstanceOf(IllegalStateException.class);

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void rename_updatesNameAndAudits() {
        Environment environment = new Environment(project, "Old", EnvironmentType.DEV);
        setId(environment, UUID.randomUUID());
        when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        Environment renamed = environmentService.rename(environment.getId(), "New", actor);

        assertThat(renamed.getName()).isEqualTo("New");
        verify(auditLogService).record(eq(project.getOrganization()), eq(actor), eq(AuditAction.ENVIRONMENT_RENAMED),
                any(), eq(environment.getId()), any());
    }

    @Test
    void softDelete_setsDeletedAtAndAudits() {
        Environment environment = new Environment(project, "Development", EnvironmentType.DEV);
        setId(environment, UUID.randomUUID());
        when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        environmentService.softDelete(environment.getId(), actor);

        assertThat(environment.isDeleted()).isTrue();
        verify(auditLogService).record(eq(project.getOrganization()), eq(actor), eq(AuditAction.ENVIRONMENT_DELETED),
                any(), eq(environment.getId()), any());
    }

    @Test
    void getActiveById_throwsNotFound_whenSoftDeleted() {
        Environment environment = new Environment(project, "Development", EnvironmentType.DEV);
        setId(environment, UUID.randomUUID());
        environment.setDeletedAt(java.time.Instant.now());
        when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));

        assertThatThrownBy(() -> environmentService.getActiveById(environment.getId()))
                .isInstanceOf(EntityNotFoundException.class);
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
