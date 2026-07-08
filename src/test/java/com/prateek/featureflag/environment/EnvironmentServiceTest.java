package com.prateek.featureflag.environment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
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
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private AuditLogService auditLogService;

    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentService(environmentRepository, auditLogService);
    }

    private static Organization persistedOrganization() {
        Organization organization = new Organization("Acme Inc", "acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        return organization;
    }

    private static Project persistedProject(Organization organization) {
        Project project = new Project(organization, "Web App", "web");
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        return project;
    }

    private static Environment persistedEnvironment(Project project, String name, EnvironmentType key) {
        Environment environment = new Environment(project, name, key);
        ReflectionTestUtils.setField(environment, "id", UUID.randomUUID());
        return environment;
    }

    private static User persistedUser() {
        User user = new User("actor@example.com", "hash", "Actor Name");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Nested
    class CreateWithoutActor {

        @Test
        void savesEnvironmentWhenKeyIsAvailableInProject() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                    .thenReturn(Optional.empty());
            when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
                Environment environment = invocation.getArgument(0);
                ReflectionTestUtils.setField(environment, "id", UUID.randomUUID());
                return environment;
            });

            Environment result = environmentService.create(project, "Development", EnvironmentType.DEV);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getKey()).isEqualTo(EnvironmentType.DEV);
            verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsWhenKeyAlreadyUsedInProject() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            Environment existing = persistedEnvironment(project, "Development", EnvironmentType.DEV);
            when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> environmentService.create(project, "Development", EnvironmentType.DEV))
                    .isInstanceOf(IllegalStateException.class);

            verify(environmentRepository, never()).save(any());
        }
    }

    @Nested
    class CreateWithActor {

        @Test
        void savesEnvironmentAndLogsCreationAgainstProjectOrganization() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            User actor = persistedUser();
            when(environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), EnvironmentType.DEV))
                    .thenReturn(Optional.empty());
            when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
                Environment environment = invocation.getArgument(0);
                ReflectionTestUtils.setField(environment, "id", UUID.randomUUID());
                return environment;
            });

            Environment result = environmentService.create(project, "Development", EnvironmentType.DEV, actor);

            verify(auditLogService).record(organization, actor, AuditAction.ENVIRONMENT_CREATED,
                    ResourceType.ENVIRONMENT, result.getId(), null);
        }
    }

    @Nested
    class GetActiveById {

        @Test
        void returnsEnvironmentWhenActive() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            Environment environment = persistedEnvironment(project, "Development", EnvironmentType.DEV);
            when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));

            Environment result = environmentService.getActiveById(environment.getId());

            assertThat(result).isEqualTo(environment);
        }

        @Test
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(environmentRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> environmentService.getActiveById(id))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void throwsWhenSoftDeleted() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            Environment environment = persistedEnvironment(project, "Development", EnvironmentType.DEV);
            environment.setDeletedAt(Instant.now());
            when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));

            assertThatThrownBy(() -> environmentService.getActiveById(environment.getId()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class ListActiveByProject {

        @Test
        void delegatesToRepository() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            List<Environment> expected = List.of(persistedEnvironment(project, "Development", EnvironmentType.DEV));
            when(environmentRepository.findByProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(expected);

            List<Environment> result = environmentService.listActiveByProject(project.getId());

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class Rename {

        @Test
        void updatesNameAndLogsRenameAgainstProjectOrganization() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            Environment environment = persistedEnvironment(project, "Development", EnvironmentType.DEV);
            User actor = persistedUser();
            when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));
            when(environmentRepository.save(environment)).thenReturn(environment);

            Environment result = environmentService.rename(environment.getId(), "Dev", actor);

            assertThat(result.getName()).isEqualTo("Dev");
            verify(auditLogService).record(organization, actor, AuditAction.ENVIRONMENT_RENAMED,
                    ResourceType.ENVIRONMENT, environment.getId(), null);
        }
    }

    @Nested
    class SoftDelete {

        @Test
        void marksDeletedAndLogsDeletionAgainstProjectOrganization() {
            Organization organization = persistedOrganization();
            Project project = persistedProject(organization);
            Environment environment = persistedEnvironment(project, "Development", EnvironmentType.DEV);
            User actor = persistedUser();
            when(environmentRepository.findById(environment.getId())).thenReturn(Optional.of(environment));
            when(environmentRepository.save(environment)).thenReturn(environment);

            environmentService.softDelete(environment.getId(), actor);

            assertThat(environment.isDeleted()).isTrue();
            verify(auditLogService).record(organization, actor, AuditAction.ENVIRONMENT_DELETED,
                    ResourceType.ENVIRONMENT, environment.getId(), null);
        }
    }
}