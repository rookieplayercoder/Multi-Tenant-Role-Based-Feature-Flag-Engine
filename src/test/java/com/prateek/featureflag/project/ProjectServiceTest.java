package com.prateek.featureflag.project;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.organization.Organization;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AuditLogService auditLogService;

    private ProjectService projectService;
    private Organization organization;
    private User actor;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, auditLogService);
        organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        actor = new User("dev@example.com", "hash", "Dev");
        setId(actor, UUID.randomUUID());
    }

    @Test
    void create_threeArg_persistsButDoesNotAudit() {
        when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                .thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project project = projectService.create(organization, "Web", "web");

        assertThat(project.getKey()).isEqualTo("web");
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_fourArg_persistsAndAudits() {
        // This is the overload ProjectController.create actually calls (passing
        // principal.getUser() as actor) - despite ProjectService's own Javadoc
        // claiming the controller uses the unlogged 3-arg version. Worth fixing
        // that comment separately; this test locks in the real (audited) behavior.
        when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                .thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            setId(p, UUID.randomUUID());
            return p;
        });

        Project project = projectService.create(organization, "Web", "web", actor);

        verify(auditLogService).record(eq(organization), eq(actor), eq(AuditAction.PROJECT_CREATED),
                any(), eq(project.getId()), any());
    }

    @Test
    void create_throwsConflict_whenKeyAlreadyUsedInOrganization() {
        when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                .thenReturn(Optional.of(new Project(organization, "Existing", "web")));

        assertThatThrownBy(() -> projectService.create(organization, "Web", "web"))
                .isInstanceOf(IllegalStateException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void rename_updatesNameAndAudits() {
        Project project = new Project(organization, "Old", "web");
        setId(project, UUID.randomUUID());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project renamed = projectService.rename(project.getId(), "New", actor);

        assertThat(renamed.getName()).isEqualTo("New");
        verify(auditLogService).record(eq(organization), eq(actor), eq(AuditAction.PROJECT_RENAMED),
                any(), eq(project.getId()), any());
    }

    @Test
    void updateKey_succeeds_whenNewKeyIsFree() {
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web-v2"))
                .thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project updated = projectService.updateKey(project.getId(), "web-v2");

        assertThat(updated.getKey()).isEqualTo("web-v2");
    }

    @Test
    void updateKey_throwsConflict_whenNewKeyAlreadyUsedByAnotherProject() {
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "taken"))
                .thenReturn(Optional.of(new Project(organization, "Other", "taken")));

        assertThatThrownBy(() -> projectService.updateKey(project.getId(), "taken"))
                .isInstanceOf(IllegalStateException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateKey_isNoOp_whenNewKeyEqualsCurrentKey() {
        // Guards against the self-collision false-positive: findByOrganizationIdAndKey...
        // would otherwise find the project's own row and incorrectly reject "no-op" renames.
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project updated = projectService.updateKey(project.getId(), "web");

        assertThat(updated.getKey()).isEqualTo("web");
        verify(projectRepository, never())
                .findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web");
    }

    @Test
    void softDelete_setsDeletedAtAndAudits() {
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.softDelete(project.getId(), actor);

        assertThat(project.isDeleted()).isTrue();
        verify(auditLogService).record(eq(organization), eq(actor), eq(AuditAction.PROJECT_DELETED),
                any(), eq(project.getId()), any());
    }

    @Test
    void getActiveById_throwsNotFound_whenSoftDeleted() {
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        project.setDeletedAt(java.time.Instant.now());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.getActiveById(project.getId()))
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
