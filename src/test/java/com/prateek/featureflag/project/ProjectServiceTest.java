package com.prateek.featureflag.project;

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, auditLogService);
    }

    private static Organization persistedOrganization() {
        Organization organization = new Organization("Acme Inc", "acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        return organization;
    }

    private static Project persistedProject(Organization organization, String name, String key) {
        Project project = new Project(organization, name, key);
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        return project;
    }

    private static User persistedUser() {
        User user = new User("actor@example.com", "hash", "Actor Name");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Nested
    class CreateWithoutActor {

        @Test
        void savesProjectWhenKeyIsAvailableInOrganization() {
            Organization organization = persistedOrganization();
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                    .thenReturn(Optional.empty());
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project project = invocation.getArgument(0);
                ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
                return project;
            });

            Project result = projectService.create(organization, "Web App", "web");

            assertThat(result.getId()).isNotNull();
            assertThat(result.getKey()).isEqualTo("web");
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsWhenKeyAlreadyUsedInOrganization() {
            Organization organization = persistedOrganization();
            Project existing = persistedProject(organization, "Web App", "web");
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> projectService.create(organization, "Web App", "web"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("web");

            verify(projectRepository, never()).save(any());
        }
    }

    @Nested
    class CreateWithActor {

        @Test
        void savesProjectAndLogsCreation() {
            Organization organization = persistedOrganization();
            User actor = persistedUser();
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                    .thenReturn(Optional.empty());
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project project = invocation.getArgument(0);
                ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
                return project;
            });

            Project result = projectService.create(organization, "Web App", "web", actor);

            assertThat(result.getId()).isNotNull();
            verify(auditLogService).record(organization, actor, AuditAction.PROJECT_CREATED,
                    ResourceType.PROJECT, result.getId(), null);
        }

        @Test
        void propagatesKeyConflictWithoutLogging() {
            Organization organization = persistedOrganization();
            User actor = persistedUser();
            Project existing = persistedProject(organization, "Web App", "web");
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "web"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> projectService.create(organization, "Web App", "web", actor))
                    .isInstanceOf(IllegalStateException.class);

            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class GetActiveById {

        @Test
        void returnsProjectWhenActive() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

            Project result = projectService.getActiveById(project.getId());

            assertThat(result).isEqualTo(project);
        }

        @Test
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(projectRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getActiveById(id))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void throwsWhenSoftDeleted() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            project.setDeletedAt(Instant.now());
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

            assertThatThrownBy(() -> projectService.getActiveById(project.getId()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class ListActiveByOrganization {

        @Test
        void delegatesToRepository() {
            UUID organizationId = UUID.randomUUID();
            Organization organization = persistedOrganization();
            List<Project> expected = List.of(persistedProject(organization, "Web App", "web"));
            when(projectRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId)).thenReturn(expected);

            List<Project> result = projectService.listActiveByOrganization(organizationId);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class Rename {

        @Test
        void updatesNameAndLogsRename() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            User actor = persistedUser();
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
            when(projectRepository.save(project)).thenReturn(project);

            Project result = projectService.rename(project.getId(), "Web Application", actor);

            assertThat(result.getName()).isEqualTo("Web Application");
            verify(auditLogService).record(organization, actor, AuditAction.PROJECT_RENAMED,
                    ResourceType.PROJECT, project.getId(), null);
        }
    }

    @Nested
    class UpdateKey {

        @Test
        void skipsUniquenessCheckWhenKeyUnchanged() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
            when(projectRepository.save(project)).thenReturn(project);

            Project result = projectService.updateKey(project.getId(), "web");

            assertThat(result.getKey()).isEqualTo("web");
            verify(projectRepository, never())
                    .findByOrganizationIdAndKeyAndDeletedAtIsNull(any(), any());
        }

        @Test
        void updatesKeyWhenNewKeyIsAvailable() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "webapp"))
                    .thenReturn(Optional.empty());
            when(projectRepository.save(project)).thenReturn(project);

            Project result = projectService.updateKey(project.getId(), "webapp");

            assertThat(result.getKey()).isEqualTo("webapp");
        }

        @Test
        void throwsWhenNewKeyAlreadyUsedInOrganization() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            Project conflicting = persistedProject(organization, "Other", "webapp");
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
            when(projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), "webapp"))
                    .thenReturn(Optional.of(conflicting));

            assertThatThrownBy(() -> projectService.updateKey(project.getId(), "webapp"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("webapp");

            verify(projectRepository, never()).save(any());
        }
    }

    @Nested
    class SoftDelete {

        @Test
        void marksDeletedAndLogsDeletion() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization, "Web App", "web");
            User actor = persistedUser();
            when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
            when(projectRepository.save(project)).thenReturn(project);

            projectService.softDelete(project.getId(), actor);

            assertThat(project.isDeleted()).isTrue();
            verify(auditLogService).record(organization, actor, AuditAction.PROJECT_DELETED,
                    ResourceType.PROJECT, project.getId(), null);
        }
    }
}